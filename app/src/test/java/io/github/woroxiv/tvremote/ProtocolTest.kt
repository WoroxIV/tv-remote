package io.github.woroxiv.tvremote

import io.github.woroxiv.tvremote.discovery.Discovery
import io.github.woroxiv.tvremote.model.IrBrand
import io.github.woroxiv.tvremote.model.RemoteKey
import io.github.woroxiv.tvremote.protocol.WakeOnLan
import io.github.woroxiv.tvremote.protocol.androidtv.ClientIdentity
import io.github.woroxiv.tvremote.protocol.androidtv.PairingSecret
import io.github.woroxiv.tvremote.protocol.androidtv.ProtoMessage
import io.github.woroxiv.tvremote.protocol.androidtv.ProtoWriter
import io.github.woroxiv.tvremote.protocol.ir.IrCodes
import io.github.woroxiv.tvremote.protocol.ir.IrEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

class ProtoTest {
    @Test
    fun `pairing request encodes like protoc`() {
        // protocol_version=2, status=200, pairing_request{service_name="atvremote", client_name="x"}
        val bytes = ProtoWriter.build {
            int(1, 2); int(2, 200)
            message(10) { string(1, "atvremote"); string(2, "x") }
        }
        val expected = byteArrayOf(0x08, 0x02, 0x10, 0xC8.toByte(), 0x01, 0x52, 0x0E, 0x0A, 0x09) +
            "atvremote".toByteArray() + byteArrayOf(0x12, 0x01) + "x".toByteArray()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `round trip through delimited stream`() {
        val msg = ProtoWriter.build { message(10) { int(1, 24); int(2, 3) } }
        val parsed = ProtoMessage.readDelimited(ByteArrayInputStream(ProtoWriter.delimited(msg)))
        val inject = parsed.message(10)!!
        assertEquals(24L, inject.int(1))
        assertEquals(3L, inject.int(2))
    }

    @Test
    fun `large varints`() {
        val msg = ProtoWriter.build { int(1, 300); int(2, 622) }
        val parsed = ProtoMessage.parse(msg)
        assertEquals(300L, parsed.int(1))
        assertEquals(622L, parsed.int(2))
    }
}

class CertificateTest {
    @Test
    fun `self signed certificate is valid and verifies with its own key`() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = ClientIdentity.selfSign(kp.public.encoded, kp.private, "atvremote")
        cert.checkValidity()
        cert.verify(kp.public)
        assertEquals(3, cert.version)
        assertEquals(kp.public, cert.publicKey)
        assertTrue(cert.subjectX500Principal.name.contains("CN=atvremote"))
        assertTrue(cert.notAfter.time > System.currentTimeMillis() + 20L * 365 * 24 * 3600 * 1000)
    }
}

class PairingSecretTest {
    /** Same computation as the reference Python implementation, done with hex strings. */
    private fun reference(c: RSAPublicKey, s: RSAPublicKey, code: String): ByteArray {
        fun hexBytes(n: BigInteger): ByteArray {
            var h = n.toString(16)
            if (h.length % 2 == 1) h = "0$h"
            return h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(hexBytes(c.modulus)); md.update(hexBytes(c.publicExponent))
        md.update(hexBytes(s.modulus)); md.update(hexBytes(s.publicExponent))
        md.update(code.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        return md.digest()
    }

    @Test
    fun `accepts the code whose first byte matches the hash`() {
        val client = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val server = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val tail = "A1B2C3D4"
        // Find the check byte the TV would display for this tail.
        val digest = reference(client, server, "00" + tail.take(4))
        val code = "%02X".format(digest[0]) + tail.take(4)
        val secret = PairingSecret.compute(client, server, code)
        assertNotNull(secret)
        assertArrayEquals(reference(client, server, code), secret)
        val wrong = "%02X".format((digest[0] + 1) and 0xFF) + tail.take(4)
        assertNull(PairingSecret.compute(client, server, wrong))
    }

    @Test
    fun `exponent is encoded on three bytes`() {
        assertArrayEquals(byteArrayOf(1, 0, 1), PairingSecret.unsignedForTest(BigInteger.valueOf(65537)))
        assertEquals(256, PairingSecret.unsignedForTest(BigInteger.ONE.shiftLeft(2047)).size)
    }

    @Test
    fun `code validation`() {
        assertTrue(PairingSecret.isValidCode("0A1b2C"))
        assertTrue(!PairingSecret.isValidCode("12345"))
        assertTrue(!PairingSecret.isValidCode("12345G"))
    }
}

class IrTest {
    @Test
    fun `nec pattern for LG power`() {
        val s = IrEncoder.nec(0x20DF10EFL)
        assertEquals(38_000, s.frequency)
        assertEquals(2 + 64 + 2, s.pattern.size)
        assertEquals(9000, s.pattern[0])
        assertEquals(4500, s.pattern[1])
        // 0x20 = 0010 0000 -> first bits 0,0,1
        assertEquals(560, s.pattern[3])
        assertEquals(560, s.pattern[5])
        assertEquals(1690, s.pattern[7])
        // NEC: second byte is the complement of the first, fourth of the third.
        val v = 0x20DF10EFL
        assertEquals(0xFFL, ((v ushr 24) xor (v ushr 16)) and 0xFF)
        assertEquals(0xFFL, ((v ushr 8) xor v) and 0xFF)
    }

    @Test
    fun `all nec style codes have valid complements`() {
        for (brand in listOf(IrBrand.LG, IrBrand.TOSHIBA, IrBrand.SAMSUNG)) {
            for (key in IrCodes.supportedKeys(brand)) {
                val s = IrCodes.signal(brand, key)!!
                // Decode bits back from the pattern
                var v = 0L
                for (i in 0 until 32) v = (v shl 1) or (if (s.pattern[3 + i * 2] > 1000) 1L else 0L)
                assertEquals("$brand $key", 0xFFL, ((v ushr 8) xor v) and 0xFF)
                if (brand != IrBrand.SAMSUNG) assertEquals("$brand $key", 0xFFL, ((v ushr 24) xor (v ushr 16)) and 0xFF)
            }
        }
    }

    @Test
    fun `sony sends three 45ms frames`() {
        val s = IrEncoder.sony(21, 1)
        assertEquals(40_000, s.frequency)
        assertEquals(3 * 26, s.pattern.size)
        assertEquals(45_000, s.pattern.take(26).sum())
        // Command 21 = 0b0010101, LSB first: 1,0,1,0,1,0,0
        assertEquals(1200, s.pattern[2])
        assertEquals(600, s.pattern[4])
        assertEquals(1200, s.pattern[6])
    }

    @Test
    fun `rc5 starts with a mark and alternates`() {
        for (toggle in listOf(true, false)) {
            val s = IrEncoder.rc5(0, 12, toggle)
            assertEquals(36_000, s.frequency)
            assertEquals(0, s.pattern.size % 2)
            // 14 bits * 2 half bits * 889, minus the dropped leading space, plus trailing gap
            assertEquals(14 * 2 * 889 - 889 + 90_000, s.pattern.sum())
            assertTrue(s.pattern.all { it > 0 })
        }
    }

    @Test
    fun `panasonic checksum bytes are consistent`() {
        for (key in IrCodes.supportedKeys(IrBrand.PANASONIC)) {
            val s = IrCodes.signal(IrBrand.PANASONIC, key)!!
            assertEquals(2 + 96 + 2, s.pattern.size)
            var v = 0L
            for (i in 0 until 48) v = (v shl 1) or (if (s.pattern[3 + i * 2] > 1000) 1L else 0L)
            val b = (0 until 6).map { ((v ushr (40 - it * 8)) and 0xFF).toInt() }
            assertEquals(0x40, b[0]); assertEquals(0x04, b[1])
            assertEquals("$key", b[2] xor b[3] xor b[4], b[5])
        }
    }

    @Test
    fun `every brand has power and volume`() {
        for (brand in IrBrand.entries) {
            val keys = IrCodes.supportedKeys(brand)
            assertTrue("$brand", RemoteKey.POWER in keys && RemoteKey.VOL_UP in keys && RemoteKey.VOL_DOWN in keys)
        }
    }
}

class MiscTest {
    @Test
    fun `wake on lan packet`() {
        val mac = WakeOnLan.parseMac("AA:bb:CC:00:11:22")!!
        val p = WakeOnLan.packet(mac)
        assertEquals(102, p.size)
        assertTrue(p.take(6).all { it == 0xFF.toByte() })
        assertArrayEquals(mac, p.copyOfRange(96, 102))
        assertNull(WakeOnLan.parseMac("nope"))
    }

    @Test
    fun `ssdp headers and xml tags`() {
        val h = Discovery.parseHeaders("HTTP/1.1 200 OK\r\nST: roku:ecp\r\nLOCATION: http://192.168.1.5:8060/\r\n\r\n")
        assertEquals("roku:ecp", h["st"])
        assertEquals("http://192.168.1.5:8060/", h["location"])
        assertEquals("[TV] Salon", Discovery.tag("<root><friendlyName>[TV] Salon</friendlyName></root>", "friendlyName"))
    }
}
