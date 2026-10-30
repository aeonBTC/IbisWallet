package github.aeonbtc.ibiswallet.util

/**
 * Fail-closed classification for payloads imported into the offline PSBT
 * signer (Sign Transaction screen). The signer only accepts Bitcoin PSBTs —
 * never raw transaction hex (nothing to sign), never Liquid PSETs, and never
 * ambiguous encodings that are valid as both hex and base64.
 *
 * Pure JVM (no BDK / Android dependency) so it is unit tested on the local
 * JVM. Mirrors the ambiguity policy in [TxFileParser] and
 * `WalletRepository.isAmbiguousHexBase64`.
 */
enum class SignerInputKind {
    /** Blank input — nothing to do. */
    EMPTY,

    /** Valid as both hex and base64 — must be rejected, never guessed. */
    AMBIGUOUS,

    /** Raw transaction hex — already final, there is nothing to sign. */
    RAW_HEX,

    /** Liquid Elements PSET (binary magic `pset\xff` as base64). */
    LIQUID_PSET,

    /** Candidate Bitcoin PSBT base64 — still needs a BDK parse to confirm. */
    PSBT_CANDIDATE,

    /** Not recognizable as any supported encoding. */
    UNRECOGNIZED,
}

object SignerInputPolicy {
    /**
     * Base64 of binary PSET magic `pset\xff`, without padding: real PSET
     * payloads continue past the 5 magic bytes, so the padded 8-char form
     * (`cHNldP8=`) only ever matches a bare 5-byte magic and never a real
     * payload. The first 7 chars fully determine the first 5 decoded bytes,
     * so this prefix check is exact for the magic.
     */
    private const val PSET_BASE64_PREFIX = "cHNldP8"

    /**
     * Check order is deliberate and differs from the broadcast path:
     * - Raw hex is checked BEFORE ambiguity. Every even-length hex string is
     *   technically also valid base64, so an ambiguity-first order would
     *   report every raw-transaction paste as "ambiguous" instead of telling
     *   the user the actionable truth (a transaction is already final —
     *   there is nothing to sign). Refusal is identical either way: only
     *   [SignerInputKind.PSBT_CANDIDATE] ever reaches the BDK parser, so an
     *   ambiguous payload can never be signed under any interpretation.
     * - [SignerInputKind.AMBIGUOUS] remains as a defense-in-depth backstop
     *   (also refused) in case the hex plausibility bounds ever change.
     */
    fun classify(rawInput: String): SignerInputKind {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return SignerInputKind.EMPTY
        if (isPlausibleRawHex(trimmed)) return SignerInputKind.RAW_HEX
        if (trimmed.startsWith(PSET_BASE64_PREFIX)) return SignerInputKind.LIQUID_PSET
        if (isAmbiguousHexBase64(trimmed)) return SignerInputKind.AMBIGUOUS
        return if (isPlausibleBase64(trimmed)) {
            SignerInputKind.PSBT_CANDIDATE
        } else {
            SignerInputKind.UNRECOGNIZED
        }
    }

    /** True only for payloads the signer may attempt to parse as a PSBT. */
    fun isSignable(kind: SignerInputKind): Boolean = kind == SignerInputKind.PSBT_CANDIDATE

    private fun isPlausibleRawHex(text: String): Boolean {
        val t = text.trim()
        if (t.length < 16 || t.length % 2 != 0 || t.length > 200_000) return false
        return t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun isPlausibleBase64(text: String): Boolean {
        val t = text.trim().replace("\\s".toRegex(), "")
        if (t.length < 16 || t.length % 4 == 1 || t.length > 300_000) return false
        if (!t.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }) {
            return false
        }
        return try {
            java.util.Base64.getDecoder().decode(t).size >= 5
        } catch (_: Exception) {
            false
        }
    }

    private fun isAmbiguousHexBase64(trimmed: String): Boolean {
        if (trimmed.length < 16 || trimmed.length > 300_000) return false
        val noWs = trimmed.replace("\\s".toRegex(), "")
        if (noWs.length % 2 != 0) return false
        if (!noWs.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
        if (noWs.length % 4 == 1) return false
        if (!noWs.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }) {
            return false
        }
        return try {
            java.util.Base64.getDecoder().decode(noWs).size >= 5
        } catch (_: Exception) {
            false
        }
    }
}
