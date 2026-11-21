package io.github.woroxiv.tvremote.protocol.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Tiny protobuf writer/reader. The Android TV remote messages are small and
 * stable, so hand-encoding avoids a code generator and a runtime dependency.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun rawVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) { out.write(v.toInt()); return }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    fun int(field: Int, value: Long): ProtoWriter = apply {
        rawVarint((field shl 3).toLong()); rawVarint(value)
    }

    fun int(field: Int, value: Int) = int(field, value.toLong())

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        rawVarint(((field shl 3) or 2).toLong()); rawVarint(value.size.toLong()); out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        fun varint(value: Long): ByteArray {
            val o = ByteArrayOutputStream()
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) { o.write(v.toInt()); return o.toByteArray() }
                o.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }

        /** A message prefixed with its varint length, as both TV sockets expect. */
        fun delimited(message: ByteArray): ByteArray = varint(message.size.toLong()) + message

        fun build(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()
    }
}

/** Decoded message: field number to values (Long for varints, ByteArray for length-delimited). */
class ProtoMessage(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun int(field: Int): Long? = fields[field]?.firstOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = LinkedHashMap<Int, MutableList<Any>>()
            var i = 0
            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (i >= data.size) throw IllegalArgumentException("Truncated varint")
                    val b = data[i++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (i < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> fields.getOrPut(field) { mutableListOf() }.add(varint())
                    1 -> i += 8
                    2 -> {
                        val len = varint().toInt()
                        require(len >= 0 && i + len <= data.size) { "Bad length" }
                        fields.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(i, i + len))
                        i += len
                    }
                    5 -> i += 4
                    else -> throw IllegalArgumentException("Unsupported wire type")
                }
            }
            return ProtoMessage(fields)
        }

        /** Reads one varint-length-prefixed message from a stream. */
        fun readDelimited(input: InputStream): ProtoMessage {
            var shift = 0
            var len = 0
            while (true) {
                val b = input.read()
                if (b < 0) throw EOFException("Connection closed")
                len = len or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(buf, read, len - read)
                if (n < 0) throw EOFException("Connection closed")
                read += n
            }
            return parse(buf)
        }
    }
}
