package io.github.woroxiv.tvremote.protocol

import io.github.woroxiv.tvremote.data.Secrets
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LG webOS TV (2014+) over the SSAP WebSocket API.
 * On first connection the TV shows a prompt; accepting it gives us a client key.
 * Buttons go through the "pointer input" socket, volume and power through SSAP calls.
 */
class LgController(
    private val host: String,
    private val secrets: Secrets,
    private val onMacFound: (String) -> Unit,
) : TvController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    override val state: StateFlow<ConnState> = _state
    override val supportedKeys = BUTTONS.keys + setOf(RemoteKey.POWER, RemoteKey.VOL_UP, RemoteKey.VOL_DOWN)
    override val supportsText = true

    @Volatile private var main: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    @Volatile private var closed = false
    private var client: OkHttpClient = Http.client
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    override suspend fun connect() = withContext(Dispatchers.IO) {
        closed = false
        _state.value = ConnState.Connecting
        // Recent firmware only accepts the TLS port; older TVs only the plain one.
        val first = runCatching { open(secure = false) }
        val ok = first.getOrNull() == true || (!closed && runCatching { open(secure = true) }.getOrNull() == true)
        if (!ok && !closed && _state.value !is ConnState.Error) {
            _state.value = ConnState.Error(
                first.exceptionOrNull()?.let { friendlyError(it) } ?: "Impossible de joindre la TV LG."
            )
        }
        if (ok) scope.launch { openPointer(); fetchMac() }
    }

    private suspend fun open(secure: Boolean): Boolean {
        client = if (secure) Http.pinned(PinnedTrust(secrets.get(PIN)) { secrets.put(PIN, it) }) else Http.client
        val url = if (secure) "wss://$host:3001/" else "ws://$host:3000/"
        val registered = CompletableDeferred<Boolean>()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val id = json.optString("id")
                when {
                    id == "register_0" && json.optString("type") == "registered" -> {
                        json.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() }
                            ?.let { secrets.put(KEY, it) }
                        _state.value = ConnState.Connected
                        registered.complete(true)
                    }
                    id == "register_0" && json.optString("type") == "error" -> {
                        _state.value = ConnState.Error("La TV LG a refusé la connexion.")
                        registered.complete(false)
                    }
                    id == "register_0" -> {
                        if (json.optJSONObject("payload")?.optString("pairingType") == "PROMPT") {
                            _state.value = ConnState.WaitingForTvApproval
                        }
                    }
                    else -> pending.remove(id)?.complete(json)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!registered.isCompleted) registered.completeExceptionally(t)
                else if (!closed && main === webSocket) _state.value = ConnState.Error("Connexion perdue avec la TV")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!registered.isCompleted) registered.complete(false)
                else if (!closed && main === webSocket) _state.value = ConnState.Error("Connexion fermée par la TV")
            }
        })
        main = ws
        val ok = withTimeoutOrNull(60_000) { registered.await() } ?: false
        if (!ok) { ws.cancel(); if (main === ws) main = null }
        return ok
    }

    private fun registerMessage(): JSONObject {
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0")
            .put("permissions", JSONArray(PERMISSIONS))
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)
        secrets.get(KEY)?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload)
    }

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject? {
        val ws = main ?: return null
        val id = "req_${ids.getAndIncrement()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val msg = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        payload?.let { msg.put("payload", it) }
        if (!ws.send(msg.toString())) { pending.remove(id); return null }
        return withTimeoutOrNull(5_000) { deferred.await() }.also { pending.remove(id) }
    }

    private suspend fun openPointer() {
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            ?.optJSONObject("payload")?.optString("socketPath")?.takeIf { it.isNotEmpty() } ?: return
        pointer = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (pointer === webSocket) pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
            }
        })
    }

    private suspend fun fetchMac() {
        val p = request("ssap://com.webos.service.connectionmanager/getinfo")?.optJSONObject("payload") ?: return
        val mac = p.optJSONObject("wiredInfo")?.optString("macAddress")?.takeIf { it.isNotBlank() }
            ?: p.optJSONObject("wifiInfo")?.optString("macAddress")?.takeIf { it.isNotBlank() }
        mac?.let(onMacFound)
    }

    private var playing = false

    override suspend fun send(key: RemoteKey) {
        when (key) {
            RemoteKey.PLAY_PAUSE -> {
                playing = !playing
                request(if (playing) "ssap://media.controls/play" else "ssap://media.controls/pause")
            }
            RemoteKey.POWER -> request("ssap://system/turnOff")
            RemoteKey.VOL_UP -> request("ssap://audio/volumeUp")
            RemoteKey.VOL_DOWN -> request("ssap://audio/volumeDown")
            else -> {
                val name = BUTTONS[key] ?: return
                if (pointer == null) openPointer()
                pointer?.send("type:button\nname:$name\n\n")
            }
        }
    }

    override suspend fun sendText(text: String) {
        for (part in text.split('\n').withIndex()) {
            if (part.index > 0) request("ssap://com.webos.service.ime/sendEnterKey")
            val chunk = part.value
            val deletes = chunk.count { it == '\b' }
            if (deletes > 0) request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", deletes))
            val clean = chunk.replace("\b", "")
            if (clean.isNotEmpty()) {
                request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", clean).put("replace", 0))
            }
        }
    }

    override fun close() {
        closed = true
        pointer?.close(1000, null)
        main?.close(1000, null)
        pointer = null
        main = null
        scope.cancel()
    }

    companion object {
        private const val KEY = "client_key"
        private const val PIN = "pin"

        private val PERMISSIONS = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV",
            "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER",
            "READ_INSTALLED_APPS", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE",
            "READ_CURRENT_CHANNEL", "READ_POWER_STATE", "READ_TV_CHANNEL_LIST",
        )

        val BUTTONS: Map<RemoteKey, String> = mapOf(
            RemoteKey.MUTE to "MUTE", RemoteKey.INFO to "INFO", RemoteKey.SETTINGS to "MENU",
            RemoteKey.INPUT to "INPUT_HUB",
            RemoteKey.CH_UP to "CHANNELUP", RemoteKey.CH_DOWN to "CHANNELDOWN",
            RemoteKey.UP to "UP", RemoteKey.DOWN to "DOWN", RemoteKey.LEFT to "LEFT", RemoteKey.RIGHT to "RIGHT",
            RemoteKey.OK to "ENTER", RemoteKey.BACK to "BACK", RemoteKey.HOME to "HOME", RemoteKey.MENU to "QMENU",
            RemoteKey.PLAY_PAUSE to "PLAY", RemoteKey.REWIND to "REWIND", RemoteKey.FAST_FORWARD to "FASTFORWARD",
            RemoteKey.STOP to "STOP",
        ) + (0..9).associate { RemoteKey.digit(it) to "$it" }
    }
}
