package io.github.woroxiv.tvremote.protocol.androidtv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * Secret proving that the user read the code on the TV screen:
 * SHA-256(client modulus, client exponent, server modulus, server exponent, code[2..]).
 * The first byte of the hash must equal the first byte of the code.
 */
object PairingSecret {
    fun isValidCode(code: String) = code.length == 6 && code.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

    fun compute(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        require(isValidCode(code)) { "The code has 6 characters (0-9, A-F)" }
        val codeBytes = code.chunked(2).map { it.toInt(16).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(unsigned(client.modulus))
            update(unsigned(client.publicExponent))
            update(unsigned(server.modulus))
            update(unsigned(server.publicExponent))
            update(codeBytes.drop(1).toByteArray())
        }.digest()
        return if (digest[0] == codeBytes[0]) digest else null
    }

    /** Big-endian bytes without the sign byte Java adds. */
    internal fun unsignedForTest(v: BigInteger) = unsigned(v)

    private fun unsigned(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }
}
