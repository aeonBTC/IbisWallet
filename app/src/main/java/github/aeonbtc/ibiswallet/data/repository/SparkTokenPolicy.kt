package github.aeonbtc.ibiswallet.data.repository

/**
 * Pure gates for Spark native tokens (BTKN).
 *
 * Token identifiers are bech32m (`btkn1…`) and are never hardcoded: the
 * canonical USDT identifier is resolved at runtime from SDK metadata, so a
 * hand-typed identifier only passes a plausibility gate here — authority is
 * the metadata lookup (`getTokensMetadata`), which returns empty for unknown
 * ids. UI copy for failures lives in string resources, not here.
 */
object SparkTokenPolicy {
    const val TOKEN_ID_HRP = "btkn1"

    /** Bech32 data charset (excludes 1/b/i/o by spec). */
    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /**
     * Canonical form for storage/comparison: trimmed lowercase. Bech32m
     * payloads are lowercase by convention.
     */
    fun normalizeTokenIdentifier(raw: String): String = raw.trim().lowercase()

    /**
     * Local plausibility gate: hrp prefix, sane length, bech32 charset after
     * the `btkn` prefix. Fail before the metadata lookup burns a round trip.
     */
    fun isPlausibleTokenIdentifier(raw: String): Boolean {
        val id = normalizeTokenIdentifier(raw)
        if (id.isEmpty() || !id.startsWith(TOKEN_ID_HRP)) return false
        if (id.length < 10 || id.length > 200) return false
        val payload = id.removePrefix("btkn")
        return payload.all { it in BECH32_CHARSET || it == '1' }
    }
}
