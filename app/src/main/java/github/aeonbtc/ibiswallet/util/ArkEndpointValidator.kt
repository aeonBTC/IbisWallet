package github.aeonbtc.ibiswallet.util

import java.net.URI
import java.util.Locale

/**
 * Validates Ark ASP / Esplora base URLs for settings and backup restore.
 * Clearnet requires https; .onion may use http (Tor). Rejects credentials/query/fragment.
 */
object ArkEndpointValidator {
    /**
     * @return null if valid, otherwise a short English reason (callers map to UI strings).
     */
    fun validate(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "URL cannot be empty"

        val basic = ServerUrlValidator.validate(trimmed)
        if (basic != null) return basic

        val uri =
            try {
                URI(trimmed)
            } catch (_: Exception) {
                return "URL is invalid"
            }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return "URL is invalid"
        val host = uri.host?.lowercase(Locale.US) ?: return "URL host is invalid"
        val isOnion = host.endsWith(".onion")

        if (isOnion) {
            if (scheme != "http" && scheme != "https") {
                return "Onion URL must use http:// or https://"
            }
        } else if (scheme != "https") {
            return "Clearnet URL must use https://"
        }

        return null
    }

    fun isValid(url: String): Boolean = validate(url) == null

    fun normalize(url: String): String = url.trim().trimEnd('/')
}
