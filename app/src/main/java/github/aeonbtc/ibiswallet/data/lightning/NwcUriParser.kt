package github.aeonbtc.ibiswallet.data.lightning

/**
 * Parsed Nostr Wallet Connect URI (NIP-47).
 * Format: nostr+walletconnect://<pubkey>?relay=<url>&secret=<hex>[&lud16=...]
 */
data class ParsedNwcUri(
    val walletPubkey: String,
    val relays: List<String>,
    val secret: String,
    val lud16: String? = null,
)

object NwcUriParser {
    private val schemeRegex =
        Regex(
            """(?i)^(?:lightning:)?(?:nostr\+walletconnect|nostrwalletconnect)://(.+)$""",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * Re-emit a canonical `nostr+walletconnect://…` URI from common wrappers /
     * case variants without destroying the authority+query.
     */
    fun normalize(raw: String): String {
        val trimmed =
            raw
                .trim()
                .trim('\uFEFF')
                .replace("\\s+".toRegex(), "")
        if (trimmed.isBlank()) return trimmed
        val match = schemeRegex.find(trimmed)
        if (match != null) {
            val rest = match.groupValues[1]
            return "nostr+walletconnect://$rest"
        }
        // Already bare "pubkey?relay=…&secret=…" after other preprocessing
        if (
            trimmed.contains("relay=", ignoreCase = true) &&
            trimmed.contains("secret=", ignoreCase = true) &&
            !trimmed.contains("://")
        ) {
            return "nostr+walletconnect://$trimmed"
        }
        return trimmed
    }

    fun parse(raw: String): ParsedNwcUri {
        val normalized = normalize(raw)
        require(normalized.isNotBlank()) { "NWC URI is required" }

        val withoutScheme =
            when {
                normalized.startsWith("nostr+walletconnect://", ignoreCase = true) ->
                    normalized.substring(normalized.indexOf("://") + 3)
                else ->
                    throw IllegalArgumentException("URI must start with nostr+walletconnect://")
            }

        val pubkeyPart = withoutScheme.substringBefore('?').trim()
        val query = withoutScheme.substringAfter('?', missingDelimiterValue = "")
        // Be strict on hex but tolerate accidental wrappers / percent-encoding from QR scanners.
        val walletPubkey = normalizeHexKey(pubkeyPart, field = "wallet pubkey")
        require(walletPubkey.length == 64) {
            "Invalid wallet pubkey in NWC URI"
        }

        val multi = parseQueryMulti(query)
        val relays =
            multi["relay"]
                .orEmpty()
                .map { it.trim() }
                .filter { it.startsWith("wss://", ignoreCase = true) || it.startsWith("ws://", ignoreCase = true) }
                .map { it.trim().replace(" ", "") }
        require(relays.isNotEmpty()) { "NWC URI must include at least one relay" }

        val secretRaw =
            multi["secret"]?.firstOrNull()?.trim()
                ?: throw IllegalArgumentException("NWC URI missing secret")
        val secret = normalizeHexKey(secretRaw, field = "NWC secret")
        require(secret.length == 64) { "Invalid NWC secret" }

        val lud16 = multi["lud16"]?.firstOrNull()?.trim()?.ifBlank { null }
        return ParsedNwcUri(
            walletPubkey = walletPubkey,
            relays = relays,
            secret = secret,
            lud16 = lud16,
        )
    }

    private fun normalizeHexKey(
        raw: String,
        field: String,
    ): String {
        var value = raw.trim()
        // Percent-encoded keys (rare but real after intermediate decoders)
        if (value.contains('%')) {
            value =
                runCatching {
                    java.net.URLDecoder.decode(
                        value.replace("+", "%2B"),
                        Charsets.UTF_8.name(),
                    )
                }.getOrDefault(value)
        }
        value =
            value
                .removePrefix("0x")
                .removePrefix("0X")
                .trim()
                .lowercase()
        // Drop whitespace & non-hex noise that dense QR misreads sometimes inject
        val hexOnly = value.filter { it in '0'..'9' || it in 'a'..'f' }
        require(hexOnly.isNotEmpty()) { "Invalid $field in NWC URI" }
        return hexOnly
    }

    private fun parseQueryMulti(query: String): Map<String, List<String>> {
        if (query.isBlank()) return emptyMap()
        val multi = linkedMapOf<String, MutableList<String>>()
        query.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val key = part.substringBefore('=').urlDecode()
            // Preserve values: protect '+' before URLDecoder turns it into space
            val value = part.substringAfter('=', "").urlDecode()
            multi.getOrPut(key) { mutableListOf() }.add(value)
        }
        return multi.mapValues { it.value.toList() }
    }

    private fun String.urlDecode(): String =
        runCatching {
            java.net.URLDecoder.decode(replace("+", "%2B"), Charsets.UTF_8.name())
        }.getOrDefault(this)
}
