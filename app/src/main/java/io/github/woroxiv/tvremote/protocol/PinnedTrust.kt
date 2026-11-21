package io.github.woroxiv.tvremote.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use for TVs on the local network.
 *
 * TVs serve self-signed certificates that no public CA can vouch for. The first
 * time the user connects to a TV we remember its certificate fingerprint, and
 * every later connection must present the same certificate or it is refused.
 */
class PinnedTrust(
    expectedFingerprint: String?,
    private val onLearned: (String) -> Unit = {},
) : X509TrustManager {

    @Volatile
    var fingerprint: String? = expectedFingerprint
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        throw CertificateException("Client certificates are not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        if (chain.isEmpty()) throw CertificateException("Empty certificate chain")
        chain[0].checkValidity()
        val actual = fingerprintOf(chain[0])
        val pinned = fingerprint
        if (pinned == null) {
            fingerprint = actual
            onLearned(actual)
        } else if (pinned != actual) {
            throw CertificateException("The TV's certificate changed. Remove and add the TV again.")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** Hostnames are local IPs; identity is established by the pinned fingerprint instead. */
    val hostnameVerifier = HostnameVerifier { _: String, session: SSLSession ->
        val cert = session.peerCertificates.firstOrNull() as? X509Certificate
        cert != null && fingerprintOf(cert) == fingerprint
    }

    fun sslContext(keyManagers: Array<KeyManager>? = null): SSLContext =
        SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(this@PinnedTrust), SecureRandom()) }

    companion object {
        fun fingerprintOf(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it) }
    }
}
