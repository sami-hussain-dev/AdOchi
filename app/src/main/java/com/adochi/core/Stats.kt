package com.adochi.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/** Counters, per-app usage and the live activity log. Publishing is throttled so a busy network doesn't flood the UI. */
object Stats {

    enum class Kind { ALLOWED, BLOCKED, THREAT }

    /** [app] is the package (or "uid:N" key) that made the lookup, when Android could tell us. */
    data class Entry(val time: Long, val domain: String, val kind: Kind, val app: String?)

    data class AppUsage(val app: String, val blocked: Long, val threats: Long, val total: Long)

    data class Snapshot(
        val blockedToday: Long = 0,
        val threatsToday: Long = 0,
        val queriesToday: Long = 0,
        val blockedTotal: Long = 0,
        val threatsTotal: Long = 0,
        val browserBlocked: Long = 0,
        val scamMessages: Long = 0,
        val recent: List<Entry> = emptyList(),
        val topBlocked: List<Pair<String, Int>> = emptyList(),
        val appsToday: List<AppUsage> = emptyList(),
    )

    private const val MAX_RECENT = 500
    private const val MAX_TRACKED_DOMAINS = 5000
    private const val MAX_DOMAINS_PER_APP = 300
    private const val HISTORY_DAYS = 14

    private lateinit var prefs: SharedPreferences
    private lateinit var historyDir: File
    private val lock = Any()
    private val recent = ArrayDeque<Entry>()
    private val blockedCounts = HashMap<String, Int>()

    /** Today's counters per app: [blocked, threats, total]. */
    private val appCounts = HashMap<String, LongArray>()
    private val appDomains = HashMap<String, HashMap<String, Int>>()

    private var day = 0L
    private var blockedToday = 0L
    private var threatsToday = 0L
    private var queriesToday = 0L
    private var blockedTotal = 0L
    private var threatsTotal = 0L
    private var browserBlocked = 0L
    private var scamMessages = 0L

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publishPending = AtomicBoolean(false)
    private val persistPending = AtomicBoolean(false)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("adochi_stats", Context.MODE_PRIVATE)
        historyDir = File(context.filesDir, "stats").apply { mkdirs() }
        synchronized(lock) {
            day = prefs.getLong("day", today())
            blockedTotal = prefs.getLong("blocked_total", 0)
            threatsTotal = prefs.getLong("threats_total", 0)
            browserBlocked = prefs.getLong("browser_blocked", 0)
            scamMessages = prefs.getLong("scam_messages", 0)
            if (day == today()) {
                blockedToday = prefs.getLong("blocked_today", 0)
                threatsToday = prefs.getLong("threats_today", 0)
                queriesToday = prefs.getLong("queries_today", 0)
                appCounts.putAll(readDay(day))
            } else {
                day = today()
            }
        }
        publishNow()
    }

    fun record(domain: String, kind: Kind, app: String?) {
        synchronized(lock) {
            rollDay()
            queriesToday++
            val counts = app?.let { appCounts.getOrPut(it) { LongArray(3) } }
            counts?.let { it[2]++ }
            if (kind != Kind.ALLOWED) {
                blockedToday++
                blockedTotal++
                counts?.let { it[0]++ }
                if (kind == Kind.THREAT) {
                    threatsToday++
                    threatsTotal++
                    counts?.let { it[1]++ }
                }
                if (blockedCounts.size >= MAX_TRACKED_DOMAINS && domain !in blockedCounts) blockedCounts.clear()
                blockedCounts[domain] = (blockedCounts[domain] ?: 0) + 1
                if (app != null) {
                    val domains = appDomains.getOrPut(app) { HashMap() }
                    if (domains.size < MAX_DOMAINS_PER_APP || domain in domains) domains[domain] = (domains[domain] ?: 0) + 1
                }
            }
            recent.addFirst(Entry(System.currentTimeMillis(), domain, kind, app))
            if (recent.size > MAX_RECENT) recent.removeLast()
        }
        schedule()
    }

    /** Ads, sponsored posts and tracker requests removed inside the Clean Browser. */
    fun recordBrowserBlocked() {
        synchronized(lock) { browserBlocked++ }
        schedule()
    }

    fun recordScamMessage() {
        synchronized(lock) { scamMessages++ }
        schedule()
    }

    /** Blocked domains of one app today, most frequent first. */
    fun blockedDomainsToday(app: String): List<Pair<String, Int>> = synchronized(lock) {
        appDomains[app]?.entries?.sortedByDescending { it.value }?.take(30)?.map { it.key to it.value }.orEmpty()
    }

    /** Per-app totals for the last [days] days including today. Reads small files; call off the main thread. */
    fun usageForDays(days: Int): List<AppUsage> {
        val totals = HashMap<String, LongArray>()
        fun add(map: Map<String, LongArray>) = map.forEach { (app, c) ->
            val t = totals.getOrPut(app) { LongArray(3) }
            for (i in 0..2) t[i] += c[i]
        }
        val today = synchronized(lock) {
            rollDay()
            add(appCounts)
            day
        }
        for (d in 1 until days) add(readDay(today - d))
        return totals.map { (app, c) -> AppUsage(app, c[0], c[1], c[2]) }.sortedByDescending { it.blocked }
    }

    fun clearLog() {
        synchronized(lock) {
            recent.clear()
            blockedCounts.clear()
        }
        publishNow()
    }

    fun persist() {
        if (!::prefs.isInitialized) return
        synchronized(lock) {
            prefs.edit {
                putLong("day", day)
                putLong("blocked_today", blockedToday)
                putLong("threats_today", threatsToday)
                putLong("queries_today", queriesToday)
                putLong("blocked_total", blockedTotal)
                putLong("threats_total", threatsTotal)
                putLong("browser_blocked", browserBlocked)
                putLong("scam_messages", scamMessages)
            }
            writeDay(day, appCounts)
        }
    }

    private fun schedule() {
        if (publishPending.compareAndSet(false, true)) {
            scope.launch {
                delay(600)
                publishPending.set(false)
                publishNow()
            }
        }
        if (persistPending.compareAndSet(false, true)) {
            scope.launch {
                delay(30_000)
                persistPending.set(false)
                persist()
            }
        }
    }

    private fun publishNow() {
        _state.value = synchronized(lock) {
            rollDay()
            Snapshot(
                blockedToday = blockedToday,
                threatsToday = threatsToday,
                queriesToday = queriesToday,
                blockedTotal = blockedTotal,
                threatsTotal = threatsTotal,
                browserBlocked = browserBlocked,
                scamMessages = scamMessages,
                recent = recent.toList(),
                topBlocked = blockedCounts.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
                appsToday = appCounts.map { (app, c) -> AppUsage(app, c[0], c[1], c[2]) }.sortedByDescending { it.blocked },
            )
        }
    }

    /** Must hold [lock]. Saves the finished day's per-app file before resetting. */
    private fun rollDay() {
        val now = today()
        if (now != day) {
            if (::historyDir.isInitialized) writeDay(day, appCounts)
            day = now
            blockedToday = 0
            threatsToday = 0
            queriesToday = 0
            appCounts.clear()
            appDomains.clear()
            pruneHistory()
        }
    }

    private fun dayFile(d: Long) = File(historyDir, "apps-$d.txt")

    private fun writeDay(d: Long, counts: Map<String, LongArray>) {
        if (counts.isEmpty()) return
        try {
            dayFile(d).writeText(counts.entries.joinToString("\n") { (app, c) -> "$app\t${c[0]}\t${c[1]}\t${c[2]}" })
        } catch (e: IOException) {
            // History is a nice-to-have.
        }
    }

    private fun readDay(d: Long): Map<String, LongArray> {
        if (!::historyDir.isInitialized) return emptyMap()
        val file = dayFile(d)
        if (!file.exists()) return emptyMap()
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 4) return@mapNotNull null
                parts[0] to LongArray(3) { parts[it + 1].toLongOrNull() ?: 0 }
            }.toMap()
        } catch (e: IOException) {
            emptyMap()
        }
    }

    private fun pruneHistory() {
        historyDir.listFiles()?.forEach { file ->
            val d = file.name.removePrefix("apps-").removeSuffix(".txt").toLongOrNull() ?: return@forEach
            if (d < day - HISTORY_DAYS) file.delete()
        }
    }

    private fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }
}
