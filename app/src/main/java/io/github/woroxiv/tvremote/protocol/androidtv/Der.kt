package io.github.woroxiv.tvremote.protocol.androidtv

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Minimal DER (ASN.1) encoder, just enough to build a self-signed X.509 certificate. */
internal object Der {
    private fun length(n: Int): ByteArray {
        if (n < 0x80) return byteArrayOf(n.toByte())
        var v = n
        val bytes = ArrayList<Byte>()
        while (v > 0) { bytes.add(0, (v and 0xFF).toByte()); v = v ushr 8 }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    fun tlv(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + length(content.size) + content

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    fun sequence(vararg parts: ByteArray) = tlv(0x30, concat(parts))
    fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))
    fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())
    fun integer(v: Long) = integer(BigInteger.valueOf(v))
    fun nullValue() = byteArrayOf(0x05, 0x00)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    fun bitString(bytes: ByteArray) = tlv(0x03, byteArrayOf(0) + bytes)
    fun explicit(tagNumber: Int, content: ByteArray) = tlv(0xA0 or tagNumber, content)

    fun oid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (p in parts.drop(2)) {
            var v = p
            val stack = ArrayList<Int>()
            stack.add((v and 0x7F).toInt())
            v = v ushr 7
            while (v > 0) { stack.add(0, ((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
            stack.forEach { out.write(it) }
        }
        return tlv(0x06, out.toByteArray())
    }

    /** UTCTime before 2050, GeneralizedTime after, as RFC 5280 requires. */
    fun time(date: Date): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = java.util.Calendar.getInstance(utc).apply { time = date }.get(java.util.Calendar.YEAR)
        return if (year < 2050) {
            val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = utc }
            tlv(0x17, f.format(date).toByteArray(Charsets.US_ASCII))
        } else {
            val f = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = utc }
            tlv(0x18, f.format(date).toByteArray(Charsets.US_ASCII))
        }
    }
}
