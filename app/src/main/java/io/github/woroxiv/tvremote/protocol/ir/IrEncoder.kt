package io.github.woroxiv.tvremote.protocol.ir

/** A ready-to-send infrared signal: carrier frequency and on/off durations in microseconds. */
class IrSignal(val frequency: Int, val pattern: IntArray)

/**
 * Encoders for the common consumer IR protocols. Patterns always start with a
 * mark (LED on) and alternate mark/space, as ConsumerIrManager expects.
 */
object IrEncoder {

    /** NEC and Samsung32: 32 bits sent in the order the code is written (MSB of the hex first). */
    fun pulseDistance32(code: Long, headerMark: Int, headerSpace: Int): IrSignal {
        val p = ArrayList<Int>()
        p += headerMark; p += headerSpace
        for (i in 31 downTo 0) {
            p += 560
            p += if ((code ushr i) and 1L == 1L) 1690 else 560
        }
        p += 560
        p += 40_000
        return IrSignal(38_000, p.toIntArray())
    }

    fun nec(code: Long) = pulseDistance32(code, 9000, 4500)

    fun samsung(code: Long) = pulseDistance32(code, 4500, 4500)

    /** Sony SIRC: 7 command bits then [bits]-7 device bits, LSB first, sent 3 times at a 45 ms period. */
    fun sony(command: Int, device: Int, bits: Int = 12): IrSignal {
        val frame = ArrayList<Int>()
        frame += 2400; frame += 600
        val value = command or (device shl 7)
        for (i in 0 until bits) {
            frame += if ((value ushr i) and 1 == 1) 1200 else 600
            frame += 600
        }
        val length = frame.sum()
        frame[frame.size - 1] += (45_000 - length).coerceAtLeast(600)
        val p = ArrayList<Int>()
        repeat(3) { p.addAll(frame) }
        return IrSignal(40_000, p.toIntArray())
    }

    /** Philips RC5: Manchester coded, 889 µs half-bits, 36 kHz. */
    fun rc5(address: Int, command: Int, toggle: Boolean): IrSignal {
        val bits = ArrayList<Boolean>()
        bits += true
        bits += (command and 0x40) == 0 // field bit: inverted 7th command bit
        bits += toggle
        for (i in 4 downTo 0) bits += (address ushr i) and 1 == 1
        for (i in 5 downTo 0) bits += (command ushr i) and 1 == 1
        // A "1" is space then mark; a "0" is mark then space.
        val levels = ArrayList<Boolean>()
        for (b in bits) { levels += !b; levels += b }
        val p = ArrayList<Int>()
        var current: Boolean? = null
        for (l in levels) {
            if (l == current) p[p.size - 1] += 889 else { p += 889; current = l }
        }
        // Drop the leading space (idle) and make sure we end on a space.
        if (levels.first() == false) p.removeAt(0)
        if (p.size % 2 == 1) p += 90_000 else p[p.size - 1] += 90_000
        return IrSignal(36_000, p.toIntArray())
    }

    /** Panasonic / Kaseikyo: 48 bits, MSB of the written code first. */
    fun panasonic(address: Int, data: Long): IrSignal {
        val value = (address.toLong() shl 32) or (data and 0xFFFFFFFFL)
        val p = ArrayList<Int>()
        p += 3456; p += 1728
        for (i in 47 downTo 0) {
            p += 432
            p += if ((value ushr i) and 1L == 1L) 1296 else 432
        }
        p += 432
        p += 74_000
        return IrSignal(37_000, p.toIntArray())
    }
}
