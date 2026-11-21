package io.github.woroxiv.tvremote.protocol

import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.RemoteKey
import kotlinx.coroutines.flow.StateFlow

/** Common surface for every TV protocol. */
interface TvController {
    val state: StateFlow<ConnState>

    /** Keys this TV understands; the UI dims the others. */
    val supportedKeys: Set<RemoteKey>

    /** True when [sendText] can type into the TV. */
    val supportsText: Boolean get() = false

    suspend fun connect()

    suspend fun send(key: RemoteKey)

    /** Types text into the focused field on the TV, where the protocol allows it. */
    suspend fun sendText(text: String) {}

    /** Android TV pairing: the code displayed on the TV. Returns false if the code is wrong. */
    suspend fun submitPairingCode(code: String): Boolean = false

    fun close()
}
