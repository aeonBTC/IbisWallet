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

    private val nonMainnetMarkers = listOf("testnet4", "testnet", "signet", "mutinynet", "regtest")

    /**
     * Non-mainnet marker in the URL host (`testnet`/`signet`/`regtest`/…), or null
     * when the host looks like mainnet (or is unparseable, e.g. a bare host:port —
     * callers treat unknown as mainnet and only block on a positive marker mismatch).
     */
    fun networkMarker(url: String): String? {
        val host =
            runCatching { URI(url.trim()).host?.lowercase(Locale.US) }.getOrNull()
                ?: return null
        return nonMainnetMarkers.firstOrNull { marker ->
            Regex("(^|[^a-z0-9])${Regex.escape(marker)}([^a-z0-9]|$)").containsMatchIn(host)
        }
    }

    /**
     * ASP and Esplora must target the same Bitcoin network — Bark always opens
     * `Network.BITCOIN`, so a testnet URL paired with a mainnet URL passes per-URL
     * validation but can never open. Reject with a clear reason instead of a
     * cryptic handshake failure.
     *
     * @return null if consistent, otherwise an English reason.
     */
    fun validatePair(
        aspUrl: String,
        esploraUrl: String,
    ): String? {
        if (aspUrl.isBlank() || esploraUrl.isBlank()) return null
        val aspMark = networkMarker(aspUrl)
        val esploraMark = networkMarker(esploraUrl)
        if (aspMark != esploraMark) {
            return "ASP and Esplora look like different networks " +
                "(ASP: ${aspMark ?: "mainnet"}, Esplora: ${esploraMark ?: "mainnet"})"
        }
        return null
    }

    fun isConsistentPair(
        aspUrl: String,
        esploraUrl: String,
    ): Boolean = validatePair(aspUrl, esploraUrl) == null
}
