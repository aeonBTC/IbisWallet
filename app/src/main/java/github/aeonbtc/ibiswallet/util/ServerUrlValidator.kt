package github.aeonbtc.ibiswallet.util

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.Locale

/**
 * Validates user-provided HTTP server base URLs without resolving DNS.
 */
object ServerUrlValidator {
    private val dnsLabelRegex = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val onionServiceRegex = Regex("^(?:[a-z2-7]{16}|[a-z2-7]{56})$")

    fun validate(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "URL cannot be empty"

        val uri =
            try {
                URI(trimmed)
            } catch (_: URISyntaxException) {
                return "URL is invalid"
            } catch (_: IllegalArgumentException) {
                return "URL is invalid"
            }

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return "URL must start with http:// or https://"
        }

        val serverUri =
            try {
                uri.parseServerAuthority()
            } catch (_: URISyntaxException) {
                return "URL host or port is invalid"
            }

        if (serverUri.rawUserInfo != null) return "URL cannot include credentials"
        if (serverUri.rawQuery != null) return "URL cannot include query parameters"
        if (serverUri.rawFragment != null) return "URL cannot include a fragment"

        val rawAuthority = serverUri.rawAuthority
        if (rawAuthority.isNullOrBlank()) return "URL must include a host"
        if (hasInvalidPort(rawAuthority, serverUri.port)) return "URL port is invalid"

        val host = serverUri.host ?: return "URL host is invalid"
        if (!isValidHost(host)) return "URL host is invalid"

        return validatePath(serverUri)
    }

    fun normalize(url: String): String = url.trim()

    private fun isValidHost(host: String): Boolean {
        val normalized = host.trim().removeSurrounding("[", "]").removeSuffix(".")
        if (normalized.isEmpty()) return false
        if (normalized.any { it.isWhitespace() || it.isISOControl() }) return false
        if (normalized.contains('%')) return false

        val lower = normalized.lowercase(Locale.US)
        return lower == "localhost" ||
            isValidIpv4Literal(lower) ||
            isValidIpv6Literal(lower) ||
            isValidOnionHost(lower) ||
            !lower.endsWith(".onion") && isValidDnsHostname(lower)
    }

    private fun isValidIpv4Literal(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            part.isNotEmpty() &&
                part.all { it.isDigit() } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isValidIpv6Literal(host: String): Boolean {
        if (!host.contains(':')) return false

        return try {
            InetAddress.getByName(host).hostAddress?.contains(':') == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidOnionHost(host: String): Boolean {
        val labels = host.split(".")
        if (labels.size != 2 || labels[1] != "onion") return false

        return onionServiceRegex.matches(labels[0])
    }

    private fun isValidDnsHostname(host: String): Boolean {
        if (host.length > MAX_DNS_HOST_LENGTH) return false
        val labels = host.split(".")
        if (labels.any { it.isEmpty() || it.length > MAX_DNS_LABEL_LENGTH }) return false
        if (labels.last().all { it.isDigit() }) return false

        return labels.all { dnsLabelRegex.matches(it) }
    }

    private fun hasInvalidPort(
        rawAuthority: String,
        parsedPort: Int,
    ): Boolean {
        if (parsedPort == 0 || parsedPort > MAX_PORT) return true

        val portText =
            if (rawAuthority.startsWith("[")) {
                val closingBracket = rawAuthority.indexOf(']')
                if (closingBracket == -1) return true
                val suffix = rawAuthority.substring(closingBracket + 1)
                when {
                    suffix.isEmpty() -> null
                    suffix.startsWith(":") -> suffix.drop(1)
                    else -> return true
                }
            } else {
                val colonCount = rawAuthority.count { it == ':' }
                when (colonCount) {
                    0 -> null
                    1 -> rawAuthority.substringAfter(':')
                    else -> null
                }
            }

        if (portText == null) return false
        val port = portText.toIntOrNull() ?: return true
        return port !in 1..MAX_PORT
    }

    private fun validatePath(uri: URI): String? {
        val rawPath = uri.rawPath ?: return null
        if (rawPath.isEmpty()) return null
        if (!rawPath.startsWith("/")) return "URL path is invalid"
        if (rawPath.contains('\\')) return "URL path is invalid"

        rawPath.split("/").forEach { segment ->
            val decodedSegment =
                try {
                    URLDecoder.decode(segment, Charsets.UTF_8.name())
                } catch (_: IllegalArgumentException) {
                    return "URL path is invalid"
                }

            if (decodedSegment == "." || decodedSegment == "..") {
                return "URL path cannot contain traversal segments"
            }
            if (decodedSegment.contains('/') || decodedSegment.contains('\\')) {
                return "URL path is invalid"
            }
        }

        return null
    }

    private const val MAX_DNS_LABEL_LENGTH = 63
    private const val MAX_DNS_HOST_LENGTH = 253
    private const val MAX_PORT = 65535
}
