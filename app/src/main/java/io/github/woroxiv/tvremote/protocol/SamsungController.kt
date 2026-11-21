package io.github.woroxiv.tvremote.protocol

import android.util.Base64
import io.github.woroxiv.tvremote.data.Secrets
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

/**
 * Samsung Smart TV (Tizen, 2016+) remote over WebSocket.
 * The first time, the TV asks the user to allow the phone; the TV then issues a token.
 */
class SamsungController(
    private val host: String,
    private val secrets: Secrets,
    private val onMacFound: (String) -> Unit,
) : TvController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    override val state: StateFlow<ConnState> = _state
    override val supportedKeys = KEYS.keys
    override val supportsText = true

    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false

    override suspend fun connect() = withContext(Dispatchers.IO) {
        closed = false
        _state.value = ConnState.Connecting
        scope.launch { fetchMac() }
        val secure = runCatching { open(secure = true) }
        val result = if (secure.getOrNull() == true) true else runCatching { open(secure = false) }.getOrNull() == true
        if (!result && !closed && _state.value !is ConnState.Error) {
            _state.value = ConnState.Error(
                secure.exceptionOrNull()?.let { friendlyError(it) }
                    ?: "Impossible de joindre la TV Samsung. Vérifiez qu'elle est allumée."
            )
        }
    }

    /** Returns true once the TV accepted us. */
    private suspend fun open(secure: Boolean): Boolean {
        val name = Base64.encodeToString(APP_NAME.toByteArray(), Base64.NO_WRAP)
        val token = secrets.get(TOKEN)
        val url = if (secure) {
            "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name" +
                (token?.let { "&token=$it" } ?: "")
        } else {
            "ws://$host:8001/api/v2/channels/samsung.remote.control?name=$name"
        }
        val client = if (secure) Http.pinned(PinnedTrust(secrets.get(PIN)) { secrets.put(PIN, it) }) else Http.client
        val outcome = CompletableDeferred<Boolean>()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("event")) {
                    "ms.channel.connect" -> {
                        json.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }
                            ?.let { secrets.put(TOKEN, it) }
                        _state.value = ConnState.Connected
                        outcome.complete(true)
                    }
                    "ms.channel.unauthorized" -> {
                        _state.value = ConnState.Error(
                            "La TV a refusé la connexion. Autorisez l'appareil dans Réglages > Général > " +
                                "Gestionnaire de périphériques externes."
                        )
                        outcome.complete(false)
                    }
                    "ms.channel.timeOut" -> outcome.complete(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!outcome.isCompleted) outcome.completeExceptionally(t)
                else if (!closed && socket === webSocket) _state.value = ConnState.Error("Connexion perdue avec la TV")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!outcome.isCompleted) outcome.complete(false)
                else if (!closed && socket === webSocket) _state.value = ConnState.Error("Connexion fermée par la TV")
            }
        })
        socket = ws
        // Show the "accept on your TV" hint if the TV doesn't answer straight away.
        val hint = scope.launch {
            delay(1500)
            if (!outcome.isCompleted) _state.value = ConnState.WaitingForTvApproval
        }
        val ok = try {
            withTimeoutOrNull(45_000) { outcome.await() } ?: false
        } finally {
            hint.cancel()
        }
        if (!ok) {
            ws.cancel()
            if (socket === ws) socket = null
            if (_state.value is ConnState.WaitingForTvApproval) _state.value = ConnState.Connecting
        }
        return ok
    }

    private fun fetchMac() {
        runCatching {
            Http.client.newCall(Request.Builder().url("http://$host:8001/api/v2/").build()).execute().use { r ->
                val body = r.body?.string() ?: return
                val mac = JSONObject(body).optJSONObject("device")?.optString("wifiMac")
                if (!mac.isNullOrBlank()) onMacFound(mac)
            }
        }
    }

    private fun sendJson(json: JSONObject) {
        val ws = socket ?: throw IOException("Non connecté")
        if (!ws.send(json.toString())) _state.value = ConnState.Error("Connexion perdue avec la TV")
    }

    private var playing = false

    override suspend fun send(key: RemoteKey) {
        val code = if (key == RemoteKey.PLAY_PAUSE) {
            playing = !playing
            if (playing) "KEY_PLAY" else "KEY_PAUSE"
        } else KEYS[key] ?: return
        runCatching {
            sendJson(JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
                .put("Cmd", "Click").put("DataOfCmd", code).put("Option", "false")
                .put("TypeOfRemote", "SendRemoteKey")))
        }
    }

    override suspend fun sendText(text: String) {
        val clean = text.replace("\b", "")
        if (clean.isNotEmpty()) runCatching {
            sendJson(JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
                .put("Cmd", Base64.encodeToString(clean.toByteArray(), Base64.NO_WRAP))
                .put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString")))
        }
        if (text.contains('\n')) send(RemoteKey.OK)
    }

    override fun close() {
        closed = true
        socket?.close(1000, null)
        socket = null
        scope.cancel()
    }

    companion object {
        private const val APP_NAME = "Zap Remote"
        private const val TOKEN = "token"
        private const val PIN = "pin"

        val KEYS: Map<RemoteKey, String> = mapOf(
            RemoteKey.POWER to "KEY_POWER", RemoteKey.INPUT to "KEY_SOURCE", RemoteKey.MUTE to "KEY_MUTE",
            RemoteKey.INFO to "KEY_INFO", RemoteKey.SETTINGS to "KEY_MENU",
            RemoteKey.VOL_UP to "KEY_VOLUP", RemoteKey.VOL_DOWN to "KEY_VOLDOWN",
            RemoteKey.CH_UP to "KEY_CHUP", RemoteKey.CH_DOWN to "KEY_CHDOWN",
            RemoteKey.UP to "KEY_UP", RemoteKey.DOWN to "KEY_DOWN", RemoteKey.LEFT to "KEY_LEFT",
            RemoteKey.RIGHT to "KEY_RIGHT", RemoteKey.OK to "KEY_ENTER",
            RemoteKey.BACK to "KEY_RETURN", RemoteKey.HOME to "KEY_HOME", RemoteKey.MENU to "KEY_TOOLS",
            RemoteKey.PLAY_PAUSE to "KEY_PLAY", RemoteKey.REWIND to "KEY_REWIND",
            RemoteKey.FAST_FORWARD to "KEY_FF", RemoteKey.STOP to "KEY_STOP",
        ) + (0..9).associate { RemoteKey.digit(it) to "KEY_$it" }
    }
}
