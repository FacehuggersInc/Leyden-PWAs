package org.bond.pwaapp

import android.net.http.SslCertificate
import android.os.Build
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class Certificate(val fingerprint: String, val subject: String, val issuer: String, val until: String)

object Trust {
    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { String.format("%02X", it) }

    fun fingerprint(certificate: SslCertificate?): String? {
        if (certificate == null) return null
        if (Build.VERSION.SDK_INT >= 29) {
            certificate.x509Certificate?.let { return fingerprint(it) }
        }
        val bundle = SslCertificate.saveState(certificate)
        val bytes = bundle.getByteArray("x509-certificate") ?: return null
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { String.format("%02X", it) }
    }

    fun describe(certificate: SslCertificate?): Certificate? {
        val print = fingerprint(certificate) ?: return null
        return Certificate(
            print,
            certificate?.issuedTo?.cName.orEmpty(),
            certificate?.issuedBy?.cName.orEmpty().ifEmpty { certificate?.issuedBy?.oName.orEmpty() },
            certificate?.validNotAfterDate?.toString().orEmpty(),
        )
    }

    private fun system(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    fun pinned(fingerprint: String): X509TrustManager {
        val normal = system()
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = normal.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                if (leaf != null && fingerprint(leaf) == fingerprint) return
                normal.checkServerTrusted(chain, authType)
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = normal.acceptedIssuers
        }
    }

    fun use(connection: HttpsURLConnection, fingerprint: String?) {
        if (fingerprint == null) return
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(pinned(fingerprint)), null)
        connection.sslSocketFactory = context.socketFactory
        val host = connection.url.host
        connection.setHostnameVerifier { name, _ -> name.equals(host, ignoreCase = true) }
    }

    fun capture(address: String): Certificate? {
        var seen: X509Certificate? = null
        val capture = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                seen = chain?.firstOrNull()
                throw java.security.cert.CertificateException("captured")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(capture), null)
        val connection = URL(address).openConnection() as? HttpsURLConnection ?: return null
        connection.sslSocketFactory = context.socketFactory
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            connection.connect()
        } catch (error: Exception) {
            Unit
        } finally {
            connection.disconnect()
        }
        val certificate = seen ?: return null
        return Certificate(
            fingerprint(certificate),
            certificate.subjectX500Principal.name.substringAfter("CN=").substringBefore(','),
            certificate.issuerX500Principal.name.substringAfter("CN=").substringBefore(','),
            certificate.notAfter.toString(),
        )
    }
}
