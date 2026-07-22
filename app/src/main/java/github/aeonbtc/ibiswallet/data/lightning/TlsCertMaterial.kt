package github.aeonbtc.ibiswallet.data.lightning

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.regex.Pattern
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import javax.net.ssl.HostnameVerifier

/**
 * Parses server/client TLS material for LND/CLN connect forms.
 *
 * Accepts:
 * - PEM certificate / private-key blocks
 * - Zeus-style base64 of:
 *   `client_key: -----BEGIN...----- client_cert: ... ca_cert: ...`
 * - Whitespace-stripped blobs that decode to PEM or the Zeus text format
 *
  * clnrest docs use self-signed server certs and `Rune` for API auth; mTLS is optional
  * when client key+cert are present (client identity reused for HTTPS).
  */
object TlsCertMaterial {
    data class Parsed(
        val certificates: List<X509Certificate> = emptyList(),
        val privateKey: PrivateKey? = null,
    )

    fun parse(raw: String): Parsed {
        val text = expandInput(raw) ?: return Parsed()
        val certificates = extractCertificates(text)
        val privateKey = extractPrivateKey(text)
        return Parsed(certificates = certificates, privateKey = privateKey)
    }

    /**
     * Apply trust (pin to the provided certs) + optional client identity for mTLS.
     *
     * Always pins when certificates parse successfully — the provided material acts as
     * the trust anchor (TOFU-style). Falls back to trust-all only when no certificates
     * are present; callers must restrict that case to Tor, where onion routing already
     * authenticates the endpoint.
     */
    fun applyToOkHttp(
        builder: OkHttpClient.Builder,
        rawMaterial: String,
    ) {
        val parsed = parse(rawMaterial)
        val trustManager =
            when {
                parsed.certificates.isEmpty() -> trustAllManager()
                else -> pinnedTrustManager(parsed.certificates)
            }
        val keyManagers =
            if (parsed.privateKey != null && parsed.certificates.isNotEmpty()) {
                // Prefer leaf labeled client_cert order: first non-CA-looking cert if multiple
                clientKeyManagers(parsed.privateKey, parsed.certificates)
            } else {
                null
            }
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(keyManagers, arrayOf<TrustManager>(trustManager), null)
            }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
    }

    fun applyInsecureTrust(builder: OkHttpClient.Builder) {
        val trustAll = trustAllManager()
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), null)
            }
        builder.sslSocketFactory(sslContext.socketFactory, trustAll)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
    }

    private fun expandInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (looksLikePem(trimmed) || looksLikeZeusLabeledPem(trimmed)) {
            return normalizeNewlines(trimmed)
        }
        val compact = trimmed.replace("\\s".toRegex(), "")
        decodesToText(compact)?.let { decoded ->
            if (looksLikePem(decoded) || looksLikeZeusLabeledPem(decoded) || decoded.contains("BEGIN")) {
                return normalizeNewlines(decoded)
            }
        }
        // Raw PEM with accidental base64-only body missing headers: try decode全部 once more
        decodesToText(trimmed)?.let { decoded ->
            if (looksLikePem(decoded) || looksLikeZeusLabeledPem(decoded)) {
                return normalizeNewlines(decoded)
            }
        }
        // Not base64 / not PEM — still feed through PEM extractors (may fail empty)
        return normalizeNewlines(trimmed)
    }

    private fun looksLikePem(text: String): Boolean =
        text.contains("BEGIN CERTIFICATE") ||
            text.contains("BEGIN PRIVATE KEY") ||
            text.contains("BEGIN RSA PRIVATE KEY") ||
            text.contains("BEGIN EC PRIVATE KEY")

    private fun looksLikeZeusLabeledPem(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("client_key") ||
            lower.contains("client_cert") ||
            lower.contains("ca_cert") ||
            lower.contains("-----begin")
    }

    private fun normalizeNewlines(text: String): String =
        text
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

    private fun decodesToText(value: String): String? {
        if (value.length < 16) return null
        val candidates =
            listOf(
                runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull(),
                runCatching { Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull(),
                runCatching { java.util.Base64.getDecoder().decode(value) }.getOrNull(),
                runCatching { java.util.Base64.getMimeDecoder().decode(value) }.getOrNull(),
            )
        for (bytes in candidates) {
            if (bytes == null || bytes.isEmpty()) continue
            val text = bytes.toString(Charsets.UTF_8)
            // Prefer decodes that look like PEM / bundle text
            if (text.contains("BEGIN") || text.contains("client_") || text.contains("ca_cert")) {
                return text
            }
        }
        return null
    }

    private fun extractCertificates(text: String): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val pattern =
            Pattern.compile(
                "-----BEGIN CERTIFICATE-----([\\s\\S]*?)-----END CERTIFICATE-----",
                Pattern.MULTILINE,
            )
        val matcher = pattern.matcher(text)
        val certs = mutableListOf<X509Certificate>()
        while (matcher.find()) {
            val block = matcher.group(0) ?: continue
            runCatching {
                factory.generateCertificate(ByteArrayInputStream(block.toByteArray(Charsets.UTF_8)))
                    as X509Certificate
            }.getOrNull()?.let { certs += it }
        }
        // Single generateCertificates pass for multi-PEM streams without markers noise
        if (certs.isEmpty() && text.contains("BEGIN CERTIFICATE")) {
            runCatching {
                factory.generateCertificates(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
                    .mapNotNull { it as? X509Certificate }
            }.getOrNull()?.let { certs += it }
        }
        return certs
    }

    private fun extractPrivateKey(text: String): PrivateKey? {
        val pkcs8 =
            extractPemBody(text, "PRIVATE KEY")
                ?: extractPemBody(text, "RSA PRIVATE KEY")
                ?: extractPemBody(text, "EC PRIVATE KEY")
                ?: return null
        val keyBytes =
            runCatching {
                Base64.decode(pkcs8.replace("\\s".toRegex(), ""), Base64.DEFAULT)
            }.getOrNull() ?: return null
        // PKCS#8 first; PKCS#1 RSA not supported without extra convert — try common algorithms
        return runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }.recoverCatching {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }.getOrNull()
    }

    private fun extractPemBody(
        text: String,
        label: String,
    ): String? {
        val pattern =
            Pattern.compile(
                "-----BEGIN $label-----([\\s\\S]*?)-----END $label-----",
                Pattern.MULTILINE,
            )
        val matcher = pattern.matcher(text)
        if (!matcher.find()) return null
        return matcher.group(1)
    }

    private fun pinnedTrustManager(certs: List<X509Certificate>): X509TrustManager {
        val keyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certs.forEachIndexed { index, cert ->
                    setCertificateEntry("pin-$index", cert)
                }
            }
        val tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    private fun clientKeyManagers(
        privateKey: PrivateKey,
        certs: List<X509Certificate>,
    ): Array<javax.net.ssl.KeyManager> {
        val password = CharArray(0)
        val keyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("client", privateKey, password, certs.toTypedArray())
            }
        val kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
        return kmf.keyManagers
    }

    private fun trustAllManager(): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
}
