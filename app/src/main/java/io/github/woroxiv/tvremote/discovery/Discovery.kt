package io.github.woroxiv.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.protocol.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Request
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.coroutines.resume

/** A TV found on the local network. */
data class FoundTv(
    val host: String,
    val name: String,
    val protocol: Protocol,
    val model: String? = null,
)

/**
 * Finds TVs on the Wi-Fi network:
 *  - mDNS `_androidtvremote2._tcp` for Android TV / Google TV,
 *  - SSDP for Roku, Samsung and LG.
 */
class Discovery(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var job: Job? = null
    private var nsdListener: NsdManager.DiscoveryListener? = null
    private val nsd get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolveLock = Mutex()

    fun start() {
        if (job?.isActive == true) return
        _scanning.value = true
        job = scope.launch {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("tv-remote-discovery").apply { setReferenceCounted(false) }
            runCatching { lock.acquire() }
            startNsd()
            try {
                repeat(3) {
                    runCatching { ssdpSearch() }
                    delay(1500)
                }
                delay(4000)
            } finally {
                stopNsd()
                runCatching { lock.release() }
                _scanning.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        stopNsd()
        _scanning.value = false
    }

    private fun add(tv: FoundTv) {
        _found.update { list ->
            val existing = list.firstOrNull { it.host == tv.host }
            when {
                existing == null -> list + tv
                // A specific protocol beats a guess, Android TV beats DIAL-only matches.
                existing.protocol != Protocol.ANDROID_TV && tv.protocol == Protocol.ANDROID_TV ->
                    list.map { if (it.host == tv.host) tv else it }
                else -> list
            }
        }
    }

    // ------------------------------------------------------------------ mDNS

    private fun startNsd() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                scope.launch { resolve(serviceInfo) }
            }
        }
        nsdListener = listener
        runCatching { nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun stopNsd() {
        nsdListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        nsdListener = null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo) = resolveLock.withLock {
        // NsdManager can only resolve one service at a time on older Android versions.
        val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
            runCatching {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(null) }
        } ?: return@withLock
        val host = (if (Build.VERSION.SDK_INT >= 34) resolved.hostAddresses.firstOrNull() else resolved.host)
            ?.hostAddress ?: return@withLock
        if (host.contains(':')) return@withLock // skip IPv6
        add(FoundTv(host, resolved.serviceName, Protocol.ANDROID_TV))
    }

    // ------------------------------------------------------------------ SSDP

    private fun ssdpSearch() {
        MulticastSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(null)
            socket.soTimeout = 1500
            val group = InetAddress.getByName("239.255.255.250")
            for (st in SEARCH_TARGETS) {
                val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: $st\r\n\r\n"
                val bytes = msg.toByteArray()
                socket.send(DatagramPacket(bytes, bytes.size, group, 1900))
            }
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length)
                val headers = parseHeaders(text)
                val host = packet.address.hostAddress ?: continue
                scope.launch { classify(host, headers) }
            }
        }
    }

    private val inspected = HashSet<String>()

    private fun classify(host: String, headers: Map<String, String>) {
        val st = headers["st"].orEmpty().lowercase()
        val server = headers["server"].orEmpty().lowercase()
        val location = headers["location"]
        val protocol = when {
            st.contains("roku") || server.contains("roku") -> Protocol.ROKU
            st.contains("samsung") -> Protocol.SAMSUNG
            st.contains("lge") || server.contains("webos") -> Protocol.LG
            else -> null
        }
        val key = "$host|${protocol ?: "?"}"
        synchronized(inspected) { if (!inspected.add(key)) return }
        val info = location?.let { describe(it) }
        val manufacturer = info?.manufacturer.orEmpty().lowercase()
        val finalProtocol = protocol ?: when {
            manufacturer.contains("samsung") && server.contains("tizen") -> Protocol.SAMSUNG
            manufacturer.contains("lg ") || manufacturer == "lg electronics" -> Protocol.LG
            manufacturer.contains("roku") -> Protocol.ROKU
            else -> return
        }
        val name = info?.friendlyName?.takeIf { it.isNotBlank() } ?: defaultName(finalProtocol)
        add(FoundTv(host, name, finalProtocol, info?.model))
    }

    private fun defaultName(p: Protocol) = when (p) {
        Protocol.ROKU -> "Roku"
        Protocol.SAMSUNG -> "TV Samsung"
        Protocol.LG -> "TV LG"
        else -> "TV"
    }

    private class Description(val friendlyName: String?, val manufacturer: String?, val model: String?)

    private fun describe(location: String): Description? = runCatching {
        val url = URL(location)
        if (!url.protocol.startsWith("http")) return null
        Http.client.newCall(Request.Builder().url(location).build()).execute().use { r ->
            val xml = r.body?.string() ?: return null
            Description(tag(xml, "friendlyName"), tag(xml, "manufacturer"), tag(xml, "modelName"))
        }
    }.getOrNull()

    companion object {
        private val SEARCH_TARGETS = listOf(
            "roku:ecp",
            "urn:samsung.com:device:RemoteControlReceiver:1",
            "urn:lge-com:service:webos-second-screen:1",
            "urn:dial-multiscreen-org:service:dial:1",
        )

        fun parseHeaders(response: String): Map<String, String> =
            response.split("\r\n", "\n").drop(1).mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
            }.toMap()

        fun tag(xml: String, name: String): String? =
            Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.replace("&apos;", "'")?.replace("&quot;", "\"")?.trim()
    }
}
