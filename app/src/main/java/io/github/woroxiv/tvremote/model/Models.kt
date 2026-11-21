package io.github.woroxiv.tvremote.model

/** How the app talks to a TV. */
enum class Protocol(val label: String) {
    ANDROID_TV("Android TV / Google TV"),
    SAMSUNG("Samsung Smart TV"),
    LG("LG webOS"),
    ROKU("Roku TV"),
    IR("Infrarouge"),
}

/** Brands with built-in infrared code sets. */
enum class IrBrand(val label: String) {
    SAMSUNG("Samsung"),
    LG("LG"),
    SONY("Sony"),
    PHILIPS("Philips"),
    PANASONIC("Panasonic"),
    TOSHIBA("Toshiba"),
}

/** Every button the remote can show. Each protocol maps the keys it supports. */
enum class RemoteKey {
    POWER, INPUT, MUTE, INFO, SETTINGS,
    VOL_UP, VOL_DOWN, CH_UP, CH_DOWN,
    UP, DOWN, LEFT, RIGHT, OK,
    BACK, HOME, MENU,
    PLAY_PAUSE, REWIND, FAST_FORWARD, STOP,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9;

    companion object {
        fun digit(n: Int): RemoteKey = entries[DIGIT_0.ordinal + n]
    }
}

/** A TV the user saved. Secrets needed to reconnect live in [DeviceStore]. */
data class TvDevice(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val host: String = "",
    val irBrand: IrBrand? = null,
    val mac: String? = null,
    val model: String? = null,
) {
    val subtitle: String
        get() = when (protocol) {
            Protocol.IR -> "Infrarouge · ${irBrand?.label ?: ""}"
            else -> "${protocol.label} · $host"
        }
}

/** Connection lifecycle shown in the UI. */
sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    /** Android TV shows a 6-character code that must be typed in the app. */
    data object NeedsPairingCode : ConnState
    /** Samsung / LG ask the user to accept the connection on the TV. */
    data object WaitingForTvApproval : ConnState
    data object Connected : ConnState
    data class Error(val message: String) : ConnState
}
