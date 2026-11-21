package io.github.woroxiv.tvremote.protocol

import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** Roku External Control Protocol: plain HTTP on port 8060 (Roku TVs, TCL/Hisense Roku TV, sticks). */
class RokuController(private val host: String) : TvController {
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    override val state: StateFlow<ConnState> = _state
    override val supportedKeys = KEYS.keys
    override val supportsText = true

    private val base get() = "http://${hostPort()}"
    private fun hostPort() = if (host.contains(':')) host else "$host:8060"

    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = ConnState.Connecting
        try {
            Http.client.newCall(Request.Builder().url("$base/query/device-info").build()).execute().use {
                if (it.code == 403) throw IOException(
                    "Contrôle refusé. Sur la Roku : Paramètres > Système > Paramètres système avancés > " +
                        "Contrôle par applications mobiles > Accès réseau : Activé."
                )
                if (!it.isSuccessful) throw IOException("Réponse inattendue de la Roku (${it.code})")
            }
            _state.value = ConnState.Connected
        } catch (e: Exception) {
            _state.value = ConnState.Error(friendlyError(e))
        }
    }

    private suspend fun keypress(name: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base/keypress/$name").post(ByteArray(0).toRequestBody()).build()
            Http.client.newCall(req).execute().close()
            if (_state.value !is ConnState.Connected) _state.value = ConnState.Connected
        } catch (e: Exception) {
            _state.value = ConnState.Error(friendlyError(e))
        }
    }

    override suspend fun send(key: RemoteKey) {
        KEYS[key]?.let { keypress(it) }
    }

    override suspend fun sendText(text: String) {
        for (c in text) {
            when (c) {
                '\b' -> keypress("Backspace")
                '\n' -> keypress("Enter")
                else -> keypress("Lit_" + URLEncoder.encode(c.toString(), "UTF-8"))
            }
        }
    }

    override fun close() {}

    companion object {
        val KEYS: Map<RemoteKey, String> = mapOf(
            RemoteKey.POWER to "Power", RemoteKey.MUTE to "VolumeMute", RemoteKey.INFO to "Info",
            RemoteKey.VOL_UP to "VolumeUp", RemoteKey.VOL_DOWN to "VolumeDown",
            RemoteKey.CH_UP to "ChannelUp", RemoteKey.CH_DOWN to "ChannelDown",
            RemoteKey.UP to "Up", RemoteKey.DOWN to "Down", RemoteKey.LEFT to "Left", RemoteKey.RIGHT to "Right",
            RemoteKey.OK to "Select", RemoteKey.BACK to "Back", RemoteKey.HOME to "Home",
            RemoteKey.MENU to "Info", RemoteKey.INPUT to "InputHDMI1",
            RemoteKey.PLAY_PAUSE to "Play", RemoteKey.REWIND to "Rev", RemoteKey.FAST_FORWARD to "Fwd",
        ) + (0..9).associate { RemoteKey.digit(it) to "Lit_$it" }
    }
}
