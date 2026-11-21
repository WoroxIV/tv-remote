package io.github.woroxiv.tvremote.protocol.ir

import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.model.RemoteKey.*

/** Built-in IR code sets for the most common TV brands. */
object IrCodes {

    private fun digits(codes: List<Long>) = codes.withIndex().associate { RemoteKey.digit(it.index) to it.value }

    /** Samsung32, written in transmission order (E0E0… prefix). */
    private val samsung: Map<RemoteKey, Long> = mapOf(
        POWER to 0xE0E040BFL, INPUT to 0xE0E0807FL, MUTE to 0xE0E0F00FL, INFO to 0xE0E0F807L,
        SETTINGS to 0xE0E058A7L, MENU to 0xE0E0D22DL,
        VOL_UP to 0xE0E0E01FL, VOL_DOWN to 0xE0E0D02FL, CH_UP to 0xE0E048B7L, CH_DOWN to 0xE0E008F7L,
        UP to 0xE0E006F9L, DOWN to 0xE0E08679L, LEFT to 0xE0E0A659L, RIGHT to 0xE0E046B9L, OK to 0xE0E016E9L,
        BACK to 0xE0E01AE5L, HOME to 0xE0E09E61L,
        PLAY_PAUSE to 0xE0E0E21DL, REWIND to 0xE0E0A25DL, FAST_FORWARD to 0xE0E012EDL, STOP to 0xE0E0629DL,
    ) + digits(listOf(0xE0E08877L, 0xE0E020DFL, 0xE0E0A05FL, 0xE0E0609FL, 0xE0E010EFL,
        0xE0E0906FL, 0xE0E050AFL, 0xE0E030CFL, 0xE0E0B04FL, 0xE0E0708FL))

    /** LG, NEC address 0x04 (20DF… prefix). */
    private val lg: Map<RemoteKey, Long> = mapOf(
        POWER to 0x20DF10EFL, INPUT to 0x20DFD02FL, MUTE to 0x20DF906FL, INFO to 0x20DF55AAL,
        SETTINGS to 0x20DFC23DL, MENU to 0x20DFA25DL,
        VOL_UP to 0x20DF40BFL, VOL_DOWN to 0x20DFC03FL, CH_UP to 0x20DF00FFL, CH_DOWN to 0x20DF807FL,
        UP to 0x20DF02FDL, DOWN to 0x20DF827DL, LEFT to 0x20DFE01FL, RIGHT to 0x20DF609FL, OK to 0x20DF22DDL,
        BACK to 0x20DF14EBL, HOME to 0x20DF3EC1L,
        PLAY_PAUSE to 0x20DF0DF2L, REWIND to 0x20DFF10EL, FAST_FORWARD to 0x20DF718EL, STOP to 0x20DF8D72L,
    ) + digits(listOf(0x20DF08F7L, 0x20DF8877L, 0x20DF48B7L, 0x20DFC837L, 0x20DF28D7L,
        0x20DFA857L, 0x20DF6897L, 0x20DFE817L, 0x20DF18E7L, 0x20DF9867L))

    /** Toshiba, NEC address 0x40 (02FD… prefix). */
    private val toshiba: Map<RemoteKey, Long> = mapOf(
        POWER to 0x02FD48B7L, MUTE to 0x02FD08F7L,
        VOL_UP to 0x02FD58A7L, VOL_DOWN to 0x02FD7887L, CH_UP to 0x02FDD827L, CH_DOWN to 0x02FDF807L,
    ) + digits(listOf(0x02FD00FFL, 0x02FD807FL, 0x02FD40BFL, 0x02FDC03FL, 0x02FD20DFL,
        0x02FDA05FL, 0x02FD609FL, 0x02FDE01FL, 0x02FD10EFL, 0x02FD906FL))

    /** Sony SIRC: (command, device, bits). */
    private val sony: Map<RemoteKey, Triple<Int, Int, Int>> = mapOf(
        POWER to Triple(21, 1, 12), INPUT to Triple(37, 1, 12), MUTE to Triple(20, 1, 12),
        INFO to Triple(58, 1, 12), HOME to Triple(96, 1, 12), BACK to Triple(35, 1, 12),
        VOL_UP to Triple(18, 1, 12), VOL_DOWN to Triple(19, 1, 12),
        CH_UP to Triple(16, 1, 12), CH_DOWN to Triple(17, 1, 12),
        UP to Triple(116, 1, 12), DOWN to Triple(117, 1, 12), LEFT to Triple(52, 1, 12),
        RIGHT to Triple(51, 1, 12), OK to Triple(101, 1, 12),
        PLAY_PAUSE to Triple(26, 0x97, 15), STOP to Triple(24, 0x97, 15),
        REWIND to Triple(27, 0x97, 15), FAST_FORWARD to Triple(28, 0x97, 15),
    ) + (0..9).associate { RemoteKey.digit(it) to Triple(if (it == 0) 9 else it - 1, 1, 12) }

    /** Philips RC5, address 0: command numbers. */
    private val philips: Map<RemoteKey, Int> = mapOf(
        POWER to 12, MUTE to 13, VOL_UP to 16, VOL_DOWN to 17, CH_UP to 32, CH_DOWN to 33,
        INFO to 15, INPUT to 56, MENU to 82,
        UP to 80, DOWN to 81, LEFT to 85, RIGHT to 86, OK to 87,
    ) + (0..9).associate { RemoteKey.digit(it) to it }

    /** Panasonic TV, address 0x4004: 32-bit data words. */
    private val panasonic: Map<RemoteKey, Long> = mapOf(
        POWER to 0x0100BCBDL, INPUT to 0x0100A0A1L, MUTE to 0x01004C4DL,
        VOL_UP to 0x01000405L, VOL_DOWN to 0x01008485L, CH_UP to 0x01002C2DL, CH_DOWN to 0x0100ACADL,
        UP to 0x01005253L, DOWN to 0x0100D2D3L, LEFT to 0x01007273L, RIGHT to 0x0100F2F3L, OK to 0x01009293L,
    ) + digits(listOf(0x01009899L, 0x01000809L, 0x01008889L, 0x01004849L, 0x0100C8C9L,
        0x01002829L, 0x0100A8A9L, 0x01006869L, 0x0100E8E9L, 0x01001819L))

    fun supportedKeys(brand: IrBrand): Set<RemoteKey> = when (brand) {
        IrBrand.SAMSUNG -> samsung.keys
        IrBrand.LG -> lg.keys
        IrBrand.SONY -> sony.keys
        IrBrand.PHILIPS -> philips.keys
        IrBrand.PANASONIC -> panasonic.keys
        IrBrand.TOSHIBA -> toshiba.keys
    }

    private var rc5Toggle = false

    fun signal(brand: IrBrand, key: RemoteKey): IrSignal? = when (brand) {
        IrBrand.SAMSUNG -> samsung[key]?.let { IrEncoder.samsung(it) }
        IrBrand.LG -> lg[key]?.let { IrEncoder.nec(it) }
        IrBrand.TOSHIBA -> toshiba[key]?.let { IrEncoder.nec(it) }
        IrBrand.SONY -> sony[key]?.let { (c, d, b) -> IrEncoder.sony(c, d, b) }
        IrBrand.PHILIPS -> philips[key]?.let { rc5Toggle = !rc5Toggle; IrEncoder.rc5(0, it, rc5Toggle) }
        IrBrand.PANASONIC -> panasonic[key]?.let { IrEncoder.panasonic(0x4004, it) }
    }
}
