package io.github.woroxiv.tvremote.protocol.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.protocol.TvController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Infrared remote for phones with an IR blaster — works with TVs that are not connected at all. */
class IrController(context: Context, private val brand: IrBrand) : TvController {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    override val state: StateFlow<ConnState> = _state
    override val supportedKeys = IrCodes.supportedKeys(brand)

    override suspend fun connect() {
        _state.value = if (ir?.hasIrEmitter() == true) ConnState.Connected
        else ConnState.Error("Ce téléphone n'a pas d'émetteur infrarouge.")
    }

    override suspend fun send(key: RemoteKey) = withContext(Dispatchers.IO) {
        val signal = IrCodes.signal(brand, key) ?: return@withContext
        runCatching { ir?.transmit(signal.frequency, signal.pattern) }
            .onFailure { _state.value = ConnState.Error("Échec de l'émission infrarouge.") }
        Unit
    }

    override fun close() {}

    companion object {
        fun isAvailable(context: Context): Boolean =
            (context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true
    }
}
