package io.github.woroxiv.tvremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.woroxiv.tvremote.data.DeviceStore
import io.github.woroxiv.tvremote.discovery.Discovery
import io.github.woroxiv.tvremote.discovery.FoundTv
import io.github.woroxiv.tvremote.model.ConnState
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.model.TvDevice
import io.github.woroxiv.tvremote.protocol.LgController
import io.github.woroxiv.tvremote.protocol.RokuController
import io.github.woroxiv.tvremote.protocol.SamsungController
import io.github.woroxiv.tvremote.protocol.TvController
import io.github.woroxiv.tvremote.protocol.WakeOnLan
import io.github.woroxiv.tvremote.protocol.androidtv.AndroidTvController
import io.github.woroxiv.tvremote.protocol.ir.IrController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val discovery = Discovery(app)

    val devices: StateFlow<List<TvDevice>> = store.devices
    val scanning: StateFlow<Boolean> = discovery.scanning
    val hasIrEmitter: Boolean = IrController.isAvailable(app)

    /** Discovered TVs that are not saved yet. */
    val found: StateFlow<List<FoundTv>> = combine(discovery.found, store.devices) { found, saved ->
        found.filter { tv -> saved.none { it.host == tv.host && it.protocol == tv.protocol } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _current = MutableStateFlow<TvDevice?>(null)
    val current: StateFlow<TvDevice?> = _current

    private val _controller = MutableStateFlow<TvController?>(null)
    val controller: StateFlow<TvController?> = _controller

    /** Keys are sent one at a time, in order. */
    private val sendLock = Mutex()

    fun scan() = discovery.start()

    fun open(device: TvDevice) {
        discovery.stop()
        _controller.value?.close()
        _current.value = device
        val c = createController(device)
        _controller.value = c
        viewModelScope.launch { c.connect() }
    }

    fun reconnect() {
        _current.value?.let { open(it) }
    }

    fun closeRemote() {
        _controller.value?.close()
        _controller.value = null
        _current.value = null
        discovery.start()
    }

    private fun createController(d: TvDevice): TvController {
        val ctx = getApplication<Application>()
        val secrets = store.secrets(d.id)
        val saveMac: (String) -> Unit = { mac ->
            store.find(d.id)?.takeIf { it.mac != mac }?.let { store.upsert(it.copy(mac = mac)) }
        }
        return when (d.protocol) {
            Protocol.ANDROID_TV -> AndroidTvController(d.host, store::androidTvIdentity, secrets)
            Protocol.SAMSUNG -> SamsungController(d.host, secrets, saveMac)
            Protocol.LG -> LgController(d.host, secrets, saveMac)
            Protocol.ROKU -> RokuController(d.host)
            Protocol.IR -> IrController(ctx, d.irBrand ?: IrBrand.SAMSUNG)
        }
    }

    fun press(key: RemoteKey) {
        val c = _controller.value ?: return
        viewModelScope.launch {
            sendLock.withLock {
                // Samsung and LG drop off the network when off: wake them up with a magic packet.
                if (key == RemoteKey.POWER && c.state.value !is ConnState.Connected) {
                    val device = _current.value?.let { store.find(it.id) ?: it }
                    val mac = device?.mac
                    if (mac != null) {
                        WakeOnLan.send(mac, device.host)
                        delay(4000)
                        reconnect()
                        return@withLock
                    }
                }
                c.send(key)
            }
        }
    }

    fun sendText(text: String) {
        val c = _controller.value ?: return
        viewModelScope.launch(Dispatchers.IO) { sendLock.withLock { c.sendText(text) } }
    }

    suspend fun submitPairingCode(code: String): Boolean =
        _controller.value?.submitPairingCode(code) ?: false

    fun addFound(tv: FoundTv) {
        val device = TvDevice(
            id = "${tv.protocol.name}:${tv.host}",
            name = tv.name,
            protocol = tv.protocol,
            host = tv.host,
            model = tv.model,
        )
        store.upsert(device)
        open(device)
    }

    fun addManual(name: String, host: String, protocol: Protocol) {
        val cleanHost = host.trim()
        val device = TvDevice(
            id = "${protocol.name}:$cleanHost",
            name = name.trim().ifEmpty { protocol.label },
            protocol = protocol,
            host = cleanHost,
        )
        store.upsert(device)
        open(device)
    }

    fun addIr(brand: IrBrand) {
        val device = TvDevice(id = "IR:${brand.name}", name = "TV ${brand.label}", protocol = Protocol.IR, irBrand = brand)
        store.lastIrBrand = brand
        store.upsert(device)
        open(device)
    }

    fun rename(device: TvDevice, name: String) {
        if (name.isNotBlank()) store.rename(device.id, name.trim())
    }

    fun remove(device: TvDevice) = store.remove(device.id)

    override fun onCleared() {
        _controller.value?.close()
        discovery.stop()
    }
}
