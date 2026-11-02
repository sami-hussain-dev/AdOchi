package com.adochi.vpn

import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import com.adochi.core.AppNames
import com.adochi.core.AppSettings
import com.adochi.core.FilterEngine
import com.adochi.core.Stats
import com.adochi.core.Verdict
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Reads DNS queries from the tunnel, answers blocked ones on the spot and forwards the rest.
 * Only the virtual DNS server addresses are routed here, so normal traffic never passes through.
 */
class DnsTunnel(
    private val service: ShieldVpnService,
    private val tun: ParcelFileDescriptor,
    private val settings: AppSettings,
) {
    private val input = FileInputStream(tun.fileDescriptor)
    private val output = FileOutputStream(tun.fileDescriptor)
    private val resolver = UpstreamResolver(service, settings)
    private val cache = DnsCache()
    private val connectivity = service.getSystemService(ConnectivityManager::class.java)
    private val pool = ThreadPoolExecutor(
        32, 32, 30, TimeUnit.SECONDS, LinkedBlockingQueue(1024), ThreadPoolExecutor.DiscardOldestPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    // Writing to this pipe wakes the poll() so stop() never waits on a blocked read.
    private val wakePipe: Array<FileDescriptor> = Os.pipe()
    @Volatile private var running = true
    private val thread = Thread(::loop, "AdOchi-dns")

    fun start() = thread.start()

    fun stop() {
        running = false
        try {
            Os.write(wakePipe[1], byteArrayOf(1), 0, 1)
        } catch (e: Exception) {
            // Already closed.
        }
        thread.join(2000)
        pool.shutdownNow()
        wakePipe.forEach { fd -> try { Os.close(fd) } catch (e: ErrnoException) { } }
    }

    private fun loop() {
        val buffer = ByteArray(32767)
        val tunPoll = StructPollfd().apply {
            fd = tun.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val wakePoll = StructPollfd().apply {
            fd = wakePipe[0]
            events = OsConstants.POLLIN.toShort()
        }
        val fds = arrayOf(tunPoll, wakePoll)
        try {
            while (running) {
                try {
                    Os.poll(fds, -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    throw e
                }
                if (!running || wakePoll.revents.toInt() != 0) break
                val events = tunPoll.revents.toInt()
                if (events and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                if (events and OsConstants.POLLIN != 0) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) handle(buffer, n)
                }
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "Tunnel stopped", e)
        }
    }

    private fun handle(buffer: ByteArray, length: Int) {
        val query = IpPacket.parseDnsQuery(buffer, length) ?: return
        pool.execute { process(query) }
    }

    private fun process(query: DnsQuery) {
        val question = DnsMessage.parseQuestion(query.payload)
        if (question == null) {
            resolver.resolve(query.payload)?.let { write(query, it, null) }
            return
        }
        // Ask before replying: once answered, the app closes its socket and the owner can't be found.
        val app = ownerOf(query)
        val offline = app != null && app in settings.noInternetApps
        when (if (offline) Verdict.BLOCKED else FilterEngine.check(question.name)) {
            Verdict.BLOCKED -> {
                write(query, DnsMessage.blockedResponse(query.payload, question), question)
                Stats.record(question.name, Stats.Kind.BLOCKED, app)
            }
            Verdict.THREAT -> {
                write(query, DnsMessage.blockedResponse(query.payload, question), question)
                Stats.record(question.name, Stats.Kind.THREAT, app)
                Notifications.threatBlocked(service, question.name, app)
            }
            Verdict.ALLOWED -> {
                val response = cache.get(question, DnsMessage.id(query.payload))
                    ?: resolver.resolve(query.payload)?.also { cache.put(question, it) }
                    ?: return
                write(query, response, question)
                Stats.record(question.name, Stats.Kind.ALLOWED, app)
            }
        }
    }

    /** The app that sent this lookup (Android 10+, only available to the active VPN app). */
    private fun ownerOf(query: DnsQuery): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val uid = connectivity.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(query.sourceAddress), query.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(query.destinationAddress), query.destinationPort),
            )
            AppNames.keyForUid(service, uid)
        } catch (e: Exception) {
            null
        }
    }

    private fun write(query: DnsQuery, response: ByteArray, question: DnsMessage.Question?) {
        var payload = response
        if (payload.size + IpPacket.headerSize(query.ipVersion) > ShieldVpnService.MTU) {
            payload = question?.let { DnsMessage.truncatedResponse(query.payload, it) } ?: return
        }
        val packet = IpPacket.buildResponse(query, payload)
        synchronized(output) {
            try {
                output.write(packet)
            } catch (e: IOException) {
                // Tunnel is closing.
            }
        }
    }

    private companion object {
        const val TAG = "AdOchi"
    }
}
