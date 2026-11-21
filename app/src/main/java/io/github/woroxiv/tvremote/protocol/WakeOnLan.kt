package io.github.woroxiv.tvremote.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Sends a Wake-on-LAN magic packet, used to switch on Samsung and LG TVs. */
object WakeOnLan {
    fun parseMac(mac: String): ByteArray? {
        val hex = mac.replace(":", "").replace("-", "").trim()
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun packet(mac: ByteArray): ByteArray {
        val data = ByteArray(6 + 16 * 6) { 0xFF.toByte() }
        for (i in 0 until 16) System.arraycopy(mac, 0, data, 6 + i * 6, 6)
        return data
    }

    suspend fun send(mac: String, host: String?) = withContext(Dispatchers.IO) {
        val bytes = parseMac(mac) ?: return@withContext
        val data = packet(bytes)
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val targets = listOfNotNull("255.255.255.255", host?.takeIf { it.isNotBlank() })
                repeat(3) {
                    for (t in targets) {
                        for (port in intArrayOf(9, 7)) {
                            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(t), port))
                        }
                    }
                }
            }
        }
    }
}
