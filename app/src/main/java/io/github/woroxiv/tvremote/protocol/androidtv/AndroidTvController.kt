package io.github.woroxiv.tvremote.protocol.androidtv

import android.os.Build
import io.github.woroxiv.tvremote.data.Secrets
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.protocol.PinnedTrust
import io.github.woroxiv.tvremote.protocol.TvController
import io.github.woroxiv.tvremote.protocol.friendlyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * Android TV Remote protocol v2 — the one used by the official Google TV app.
 * Works with Google TV, Android TV (Sony, Philips, TCL, Hisense, Sharp…) and Chromecast with Google TV.
 *
 * Pairing on port 6467 (TLS + 6-character code), commands on port 6466.
 */
class AndroidTvController(
    private val host: String,
    private val identity: () -> ClientIdentity,
    private val secrets: Secrets,
) : TvController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    override val state: StateFlow<ConnState> = _state

    override val supportedKeys: Set<RemoteKey> = KEYCODES.keys
    override val supportsText = true

    @Volatile private var remote: SSLSocket? = null
    @Volatile private var pairing: SSLSocket? = null
    @Volatile private var closed = false

    private val pinned get() = secrets.get(PIN)

    override suspend fun connect() = withContext(Dispatchers.IO) {
        closed = false
        _state.value = ConnState.Connecting
        try {
            openRemote()
        } catch (e: Exception) {
            if (closed) return@withContext
            if (needsPairing(e)) {
                try {
                    startPairing()
                } catch (p: Exception) {
                    if (!closed) _state.value = ConnState.Error(friendlyError(p))
                }
            } else {
                _state.value = ConnState.Error(friendlyError(e))
            }
        }
    }

    private fun needsPairing(e: Exception) =
        secrets.get(PAIRED) == null || e is SSLException || e is EOFException

    private fun socket(port: Int, trust: PinnedTrust): SSLSocket {
        val factory = trust.sslContext(identity().keyManagers()).socketFactory
        val s = factory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 10_000
        s.startHandshake()
        return s
    }

    // ---------------------------------------------------------------- remote

    private fun openRemote() {
        if (secrets.get(PAIRED) == null) throw EOFException("not paired")
        val s = socket(REMOTE_PORT, PinnedTrust(pinned) { secrets.put(PIN, it) })
        try {
            // The TV opens with RemoteConfigure; an unpaired client is dropped here instead.
            val first = ProtoMessage.readDelimited(s.inputStream)
            remote = s
            handle(first)
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
        s.soTimeout = 0
        _state.value = ConnState.Connected
        scope.launch { readLoop(s) }
    }

    private fun readLoop(s: SSLSocket) {
        try {
            while (!closed) handle(ProtoMessage.readDelimited(s.inputStream))
        } catch (e: Exception) {
            if (!closed && remote === s) _state.value = ConnState.Error("Connexion perdue avec la TV")
        }
    }

    private fun handle(msg: ProtoMessage) {
        when {
            msg.has(1) -> write(ProtoWriter.build {
                message(1) {
                    int(1, FEATURES)
                    message(2) {
                        string(1, Build.MODEL ?: "Phone")
                        string(2, Build.MANUFACTURER ?: "Android")
                        int(3, 1)
                        string(4, "1")
                        string(5, "atvremote")
                        string(6, "1.0.0")
                    }
                }
            })
            msg.has(2) -> write(ProtoWriter.build { message(2) { int(1, FEATURES) } })
            msg.has(8) -> {
                val v = msg.message(8)?.int(1) ?: 0
                write(ProtoWriter.build { message(9) { int(1, v) } })
            }
        }
    }

    private fun write(bytes: ByteArray) {
        val s = remote ?: return
        synchronized(this) {
            s.outputStream.write(ProtoWriter.delimited(bytes))
            s.outputStream.flush()
        }
    }

    override suspend fun send(key: RemoteKey) {
        val code = KEYCODES[key] ?: return
        sendKeyCode(code)
    }

    private suspend fun sendKeyCode(code: Int) = withContext(Dispatchers.IO) {
        try {
            write(ProtoWriter.build { message(10) { int(1, code); int(2, DIRECTION_SHORT) } })
        } catch (e: Exception) {
            if (!closed) _state.value = ConnState.Error("Connexion perdue avec la TV")
        }
    }

    override suspend fun sendText(text: String) {
        for (c in text.lowercase()) {
            val code = when (c) {
                in 'a'..'z' -> 29 + (c - 'a')
                in '0'..'9' -> 7 + (c - '0')
                ' ' -> 62
                '.' -> 56
                ',' -> 55
                '@' -> 77
                '-' -> 69
                '\n' -> 66
                '\b' -> 67
                else -> null
            } ?: continue
            sendKeyCode(code)
        }
    }

    // --------------------------------------------------------------- pairing

    private fun startPairing() {
        // A new pairing may follow a TV reset, so learn the certificate again.
        val trust = PinnedTrust(null)
        val s = socket(PAIRING_PORT, trust)
        pairing = s
        pairingTrust = trust
        exchange(s) { message(10) { string(1, "atvremote"); string(2, Build.MODEL ?: "Phone") } }
        exchange(s) {
            message(20) {
                message(1) { int(1, ENCODING_HEX); int(2, 6) }
                int(3, ROLE_INPUT)
            }
        }
        exchange(s) {
            message(30) {
                message(1) { int(1, ENCODING_HEX); int(2, 6) }
                int(2, ROLE_INPUT)
            }
        }
        s.soTimeout = 0
        _state.value = ConnState.NeedsPairingCode
    }

    @Volatile private var pairingTrust: PinnedTrust? = null

    private fun exchange(s: SSLSocket, body: ProtoWriter.() -> Unit): ProtoMessage {
        val msg = ProtoWriter.build { int(1, 2); int(2, STATUS_OK); body() }
        s.outputStream.write(ProtoWriter.delimited(msg))
        s.outputStream.flush()
        val reply = ProtoMessage.readDelimited(s.inputStream)
        val status = reply.int(2)
        if (status != STATUS_OK.toLong()) throw PairingRejected(status)
        return reply
    }

    override suspend fun submitPairingCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val s = pairing ?: return@withContext false
        val clean = code.trim().uppercase()
        if (!PairingSecret.isValidCode(clean)) return@withContext false
        val serverKey = s.session.peerCertificates.firstOrNull()?.publicKey as? RSAPublicKey
            ?: return@withContext false
        val clientKey = identity().certificate.publicKey as RSAPublicKey
        val secret = PairingSecret.compute(clientKey, serverKey, clean) ?: return@withContext false
        try {
            s.soTimeout = 10_000
            exchange(s) { message(40) { bytes(1, secret) } }
        } catch (e: Exception) {
            runCatching { s.close() }
            pairing = null
            _state.value = ConnState.Error(
                if (e is PairingRejected) "La TV a refusé le code. Réessayez." else friendlyError(e)
            )
            return@withContext false
        }
        runCatching { s.close() }
        pairing = null
        secrets.put(PAIRED, "1")
        pairingTrust?.fingerprint?.let { secrets.put(PIN, it) }
        _state.value = ConnState.Connecting
        try {
            openRemote()
        } catch (e: Exception) {
            _state.value = ConnState.Error(friendlyError(e))
        }
        true
    }

    override fun close() {
        closed = true
        runCatching { remote?.close() }
        runCatching { pairing?.close() }
        remote = null
        pairing = null
        scope.cancel()
    }

    private class PairingRejected(status: Long?) : Exception("Pairing rejected ($status)")

    companion object {
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467
        private const val PIN = "pin"
        private const val PAIRED = "paired"
        private const val STATUS_OK = 200
        private const val ENCODING_HEX = 3
        private const val ROLE_INPUT = 1
        private const val DIRECTION_SHORT = 3
        /** Feature bits announced by the official app (ping, key, IME, power, volume, app link). */
        private const val FEATURES = 622

        /** Android KeyEvent codes. */
        val KEYCODES: Map<RemoteKey, Int> = mapOf(
            RemoteKey.POWER to 26, RemoteKey.INPUT to 178, RemoteKey.MUTE to 164,
            RemoteKey.INFO to 165, RemoteKey.SETTINGS to 176,
            RemoteKey.VOL_UP to 24, RemoteKey.VOL_DOWN to 25,
            RemoteKey.CH_UP to 166, RemoteKey.CH_DOWN to 167,
            RemoteKey.UP to 19, RemoteKey.DOWN to 20, RemoteKey.LEFT to 21, RemoteKey.RIGHT to 22,
            RemoteKey.OK to 23, RemoteKey.BACK to 4, RemoteKey.HOME to 3, RemoteKey.MENU to 82,
            RemoteKey.PLAY_PAUSE to 85, RemoteKey.REWIND to 89, RemoteKey.FAST_FORWARD to 90,
            RemoteKey.STOP to 86,
        ) + (0..9).associate { RemoteKey.digit(it) to 7 + it }
    }
}
