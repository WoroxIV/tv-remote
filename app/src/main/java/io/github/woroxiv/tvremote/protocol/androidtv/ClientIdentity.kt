package io.github.woroxiv.tvremote.protocol.androidtv

import android.util.Base64
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * The TLS client certificate the Android TV remote protocol requires.
 * The TV remembers it after pairing, so it is generated once and kept.
 */
class ClientIdentity(val certificate: X509Certificate, val privateKey: PrivateKey) {

    fun keyManagers(): Array<KeyManager> {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        ks.setKeyEntry("client", privateKey, PASSWORD, arrayOf(certificate))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, PASSWORD)
        return kmf.keyManagers
    }

    fun encodedCertificate(): String = b64(certificate.encoded)
    fun encodedKey(): String = b64(privateKey.encoded)

    companion object {
        private val PASSWORD = CharArray(0)

        private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun generate(commonName: String = "atvremote"): ClientIdentity {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val certificate = selfSign(kp.public.encoded, kp.private, commonName)
            return ClientIdentity(certificate, kp.private)
        }

        fun decode(cert: String, key: String): ClientIdentity {
            val certificate = parseCertificate(Base64.decode(cert, Base64.NO_WRAP))
            val privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(key, Base64.NO_WRAP)))
            return ClientIdentity(certificate, privateKey)
        }

        internal fun parseCertificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate

        /** Builds a version 3, SHA256withRSA, self-signed certificate. */
        internal fun selfSign(subjectPublicKeyInfo: ByteArray, key: PrivateKey, cn: String): X509Certificate {
            val algorithm = Der.sequence(Der.oid("1.2.840.113549.1.1.11"), Der.nullValue())
            val name = Der.sequence(Der.set(Der.sequence(Der.oid("2.5.4.3"), Der.utf8(cn))))
            val now = System.currentTimeMillis()
            val validity = Der.sequence(
                Der.time(Date(now - TimeUnit.DAYS.toMillis(1))),
                Der.time(Date(now + TimeUnit.DAYS.toMillis(365L * 30))),
            )
            val serial = BigInteger(63, SecureRandom()).add(BigInteger.ONE)
            val tbs = Der.sequence(
                Der.explicit(0, Der.integer(2)),
                Der.integer(serial),
                algorithm,
                name,
                validity,
                name,
                subjectPublicKeyInfo,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(key); update(tbs); sign()
            }
            return parseCertificate(Der.sequence(tbs, algorithm, Der.bitString(signature)))
        }
    }
}
