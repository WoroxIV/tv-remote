package io.github.woroxiv.tvremote.protocol

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** Turns network exceptions into a short message a user can act on. */
fun friendlyError(e: Throwable): String {
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is CertificateException) {
            return "Le certificat de la TV a changé. Supprimez la TV puis ajoutez-la à nouveau."
        }
        cause = cause.cause
    }
    return when (e) {
        is SocketTimeoutException, is NoRouteToHostException ->
            "TV injoignable. Vérifiez qu'elle est allumée et sur le même Wi-Fi."
        is ConnectException ->
            "La TV refuse la connexion. Activez le contrôle à distance / réseau dans ses réglages."
        is UnknownHostException -> "Adresse de la TV introuvable."
        is SSLException -> "Connexion sécurisée impossible avec la TV."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Erreur de connexion"
    }
}
