package io.github.woroxiv.tvremote.data

import android.content.Context
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.Protocol
import io.github.woroxiv.tvremote.model.TvDevice
import io.github.woroxiv.tvremote.protocol.androidtv.ClientIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved TVs and the small secrets needed to reconnect (pairing tokens,
 * certificate pins). Everything stays on the phone, in private app storage.
 */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<TvDevice>> = _devices

    private fun load(): List<TvDevice> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_DEVICES, "[]"))
        (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun persist(list: List<TvDevice>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
        _devices.value = list
    }

    fun upsert(device: TvDevice) {
        val list = _devices.value.toMutableList()
        val i = list.indexOfFirst { it.id == device.id }
        if (i >= 0) list[i] = device else list.add(0, device)
        persist(list)
    }

    fun rename(id: String, name: String) {
        _devices.value.firstOrNull { it.id == id }?.let { upsert(it.copy(name = name)) }
    }

    fun remove(id: String) {
        persist(_devices.value.filterNot { it.id == id })
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("secret:$id:") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun find(id: String) = _devices.value.firstOrNull { it.id == id }

    fun secrets(deviceId: String): Secrets = object : Secrets {
        override fun get(key: String) = prefs.getString("secret:$deviceId:$key", null)
        override fun put(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove("secret:$deviceId:$key") else putString("secret:$deviceId:$key", value) }.apply()
        }
    }

    /** One client certificate for all Android TVs, created on first use. */
    @Synchronized
    fun androidTvIdentity(): ClientIdentity {
        val cert = prefs.getString(KEY_ATV_CERT, null)
        val key = prefs.getString(KEY_ATV_KEY, null)
        if (cert != null && key != null) {
            runCatching { return ClientIdentity.decode(cert, key) }
        }
        val identity = ClientIdentity.generate()
        prefs.edit()
            .putString(KEY_ATV_CERT, identity.encodedCertificate())
            .putString(KEY_ATV_KEY, identity.encodedKey())
            .apply()
        return identity
    }

    var lastIrBrand: IrBrand?
        get() = prefs.getString("last_ir_brand", null)?.let { runCatching { IrBrand.valueOf(it) }.getOrNull() }
        set(v) { prefs.edit().putString("last_ir_brand", v?.name).apply() }

    companion object {
        private const val KEY_DEVICES = "devices"
        private const val KEY_ATV_CERT = "atv_cert"
        private const val KEY_ATV_KEY = "atv_key"

        fun toJson(d: TvDevice) = JSONObject().apply {
            put("id", d.id); put("name", d.name); put("protocol", d.protocol.name); put("host", d.host)
            d.irBrand?.let { put("irBrand", it.name) }
            d.mac?.let { put("mac", it) }
            d.model?.let { put("model", it) }
        }

        fun fromJson(o: JSONObject): TvDevice? = runCatching {
            TvDevice(
                id = o.getString("id"),
                name = o.getString("name"),
                protocol = Protocol.valueOf(o.getString("protocol")),
                host = o.optString("host", ""),
                irBrand = o.optString("irBrand").takeIf { it.isNotEmpty() }?.let { IrBrand.valueOf(it) },
                mac = o.optString("mac").takeIf { it.isNotEmpty() },
                model = o.optString("model").takeIf { it.isNotEmpty() },
            )
        }.getOrNull()
    }
}

/** Per-device key/value secrets. */
interface Secrets {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}
