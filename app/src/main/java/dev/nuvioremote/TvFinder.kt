package dev.nuvioremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Finds the TVs on the Wi-Fi, by what they announce (mDNS), so there's no address to type:
 *  - "_androidtvremote2._tcp", the Google TV remote service: the TV's name ("Living room TV"). Every Google TV has it,
 *    with USB debugging on or not.
 *  - "_adb._tcp" on port 5555: ADB over the network, what TvLink talks to. Present only with USB debugging on.
 * Matched by address. Phones announce _adb._tcp too (wireless debugging, on another port): only 5555 counts, and
 * never this phone.
 *
 * Callbacks come on the main thread.
 */
class TvFinder(context: Context, private val onChange: (List<Tv>) -> Unit) {
    data class Tv(val host: String, val name: String, val ready: Boolean)

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val names = linkedMapOf<String, String>()
    private val adb = mutableSetOf<String>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun start() {
        stop()
        names.clear()
        adb.clear()
        queue.clear()
        for (type in listOf(REMOTE, ADB)) {
            val l = listener()
            listeners += l
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) }
        }
        publish()
    }

    fun stop() {
        for (l in listeners) runCatching { nsd.stopServiceDiscovery(l) }
        listeners.clear()
    }

    private fun listener() = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            main.post {
                queue.addLast(info)
                next()
            }
        }
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) {}
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
    }

    /** NsdManager resolves one service at a time: a queue. */
    @Suppress("DEPRECATION")    // resolveService: still works; its replacement needs API 34
    private fun next() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                main.post { done() }
            }

            override fun onServiceResolved(r: NsdServiceInfo) {
                main.post {
                    // Its IPv4 address (Android 14+ lists them all; the first may be IPv6, and the two services
                    // must match by address).
                    val address = if (Build.VERSION.SDK_INT >= 34) r.hostAddresses.firstOrNull { it is Inet4Address } else r.host
                    val host = (address as? Inet4Address)?.hostAddress
                    if (host != null && host !in ownAddresses()) {
                        if (r.serviceType.contains("androidtvremote")) names[host] = r.serviceName
                        else if (r.port == 5555) adb += host
                        publish()
                    }
                    done()
                }
            }
        })
    }

    private fun done() {
        resolving = false
        next()
    }

    private fun publish() {
        val hosts = LinkedHashSet<String>().apply { addAll(names.keys); addAll(adb) }
        onChange(hosts.map { Tv(it, names[it] ?: "Android TV", it in adb) }.sortedBy { !it.ready })
    }

    private fun ownAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()
    }.getOrDefault(emptySet())

    private companion object {
        const val REMOTE = "_androidtvremote2._tcp"
        const val ADB = "_adb._tcp"
    }
}
