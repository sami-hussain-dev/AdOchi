package com.adochi.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data class Working(val message: String, val progress: Float) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

data class UpdateResult(val failed: Int, val total: Int)

/** Downloads blocklists, compiles them into [DomainSet] files and hands them to [FilterEngine]. */
class FilterRepository(private val context: Context, private val settings: AppSettings) {

    private val dir = File(context.filesDir, "filters").apply { mkdirs() }
    private val rawDir = File(dir, "raw").apply { mkdirs() }
    private val adsFile = File(dir, "ads.bin")
    private val threatsFile = File(dir, "threats.bin")
    private val exceptionsFile = File(dir, "exceptions.bin")

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    fun loadAsync() {
        scope.launch {
            applyUserRules()
            mutex.withLock {
                val ads = DomainSet.readFrom(adsFile)
                val threats = DomainSet.readFrom(threatsFile)
                val exceptions = DomainSet.readFrom(exceptionsFile)
                if (ads != null && threats != null && exceptions != null) {
                    FilterEngine.setLists(CompiledLists(ads, threats, exceptions))
                } else {
                    compileLocked()
                }
            }
        }
    }

    fun updateAsync() {
        scope.launch { update() }
    }

    /** Re-compiles after the user toggles lists, downloading any newly enabled list first. */
    fun rebuildAsync() {
        scope.launch {
            mutex.withLock {
                val missing = enabledLists().filter { list ->
                    list.urls.indices.any { !rawFile(list.id, it).exists() }
                }
                downloadLocked(missing)
                compileLocked()
            }
        }
    }

    suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = downloadLocked(enabledLists().filter { it.urls.isNotEmpty() })
            compileLocked()
            if (result.failed < result.total || result.total == 0) {
                settings.lastUpdate = System.currentTimeMillis()
            }
            if (result.failed > 0) {
                _status.value = UpdateStatus.Failed(
                    "${result.failed} of ${result.total} lists couldn't be downloaded. Check your internet and try again."
                )
            }
            result
        }
    }

    fun applyUserRules() {
        FilterEngine.setUserRules(settings.userAllow, settings.userBlock)
    }

    fun allowDomain(domain: String) {
        settings.userBlock = settings.userBlock - domain
        settings.userAllow = settings.userAllow + domain
        applyUserRules()
    }

    fun blockDomain(domain: String) {
        settings.userAllow = settings.userAllow - domain
        settings.userBlock = settings.userBlock + domain
        applyUserRules()
    }

    fun removeRule(domain: String) {
        settings.userAllow = settings.userAllow - domain
        settings.userBlock = settings.userBlock - domain
        applyUserRules()
    }

    private fun enabledLists(): List<FilterList> {
        val enabled = settings.enabledLists
        return FilterCatalog.all.filter { it.id in enabled }
    }

    private fun rawFile(id: String, index: Int) = File(rawDir, "$id-$index.txt")

    private fun downloadLocked(lists: List<FilterList>): UpdateResult {
        val total = lists.sumOf { it.urls.size }
        var done = 0
        var failed = 0
        for (list in lists) {
            list.urls.forEachIndexed { index, url ->
                _status.value = UpdateStatus.Working("Downloading ${list.name}…", done / total.toFloat())
                try {
                    download(url, rawFile(list.id, index), "${list.id}-$index")
                } catch (e: IOException) {
                    failed++
                }
                done++
            }
        }
        return UpdateResult(failed, total)
    }

    private fun download(url: String, target: File, etagKey: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("User-Agent", "AdOchi/1.0 (Android)")
        if (target.exists()) settings.etag(etagKey)?.let { conn.setRequestProperty("If-None-Match", it) }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return
                HttpURLConnection.HTTP_OK -> Unit
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
            val tmp = File(target.path + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        written += n
                        if (written > MAX_LIST_BYTES) throw IOException("List too large")
                        output.write(buffer, 0, n)
                    }
                }
            }
            // A captive portal or error page would otherwise be parsed as a list.
            val head = tmp.bufferedReader().use { r -> CharArray(64).let { String(it, 0, r.read(it).coerceAtLeast(0)) } }
            if (head.trimStart().startsWith("<")) {
                tmp.delete()
                throw IOException("Got a web page instead of a list")
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("Could not save list")
            }
            settings.setEtag(etagKey, conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }

    private fun compileLocked() {
        _status.value = UpdateStatus.Working("Building filters…", 1f)
        val ads = DomainSet.Builder()
        val threats = DomainSet.Builder()
        val exceptions = DomainSet.Builder()

        for (list in enabledLists()) {
            val target = if (list.kind == ListKind.THREATS) threats else ads
            var count = 0
            val onBlock: (String) -> Unit = { domain ->
                target.add(domain)
                count++
            }
            val onAllow: (String) -> Unit = { domain -> exceptions.add(domain) }
            try {
                list.asset?.let { asset ->
                    context.assets.open(asset).bufferedReader().use { ListParser.parse(it, onBlock, onAllow) }
                }
                list.urls.indices.map { rawFile(list.id, it) }.filter { it.exists() }.forEach { file ->
                    file.bufferedReader().use { ListParser.parse(it, onBlock, onAllow) }
                }
            } catch (e: IOException) {
                // A corrupt file only costs that list; the others still load.
            }
            settings.setListCount(list.id, count)
        }

        val compiled = CompiledLists(ads.build(), threats.build(), exceptions.build())
        try {
            compiled.ads.writeTo(adsFile)
            compiled.threats.writeTo(threatsFile)
            compiled.exceptions.writeTo(exceptionsFile)
        } catch (e: IOException) {
            // Still usable from memory; it will be rebuilt on next start.
        }
        FilterEngine.setLists(compiled)
        _status.value = UpdateStatus.Idle
    }

    private companion object {
        const val MAX_LIST_BYTES = 60L * 1024 * 1024
    }
}
