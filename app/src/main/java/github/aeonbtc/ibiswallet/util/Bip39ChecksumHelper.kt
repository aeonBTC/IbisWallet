package github.aeonbtc.ibiswallet.util

import java.security.MessageDigest

/**
 * BIP39 checksum-word helper for dice-generated seeds.
 *
 * When the first 11 (of 12) or 23 (of 24) words are fixed, the last word is
 * constrained by the BIP39 checksum: 11 known words leave 128 valid
 * completions (7 free entropy bits + 4 checksum bits), 23 known words leave
 * 8 (3 free entropy bits + 8 checksum bits).
 *
 * Pure Kotlin, offline, no logging of word material.
 */
object Bip39ChecksumHelper {

    /** Supported counts of already-known words (one short of a 12/24-word seed). */
    val SUPPORTED_PREFIX_COUNTS = setOf(11, 23)

    /**
     * Minimum physical dice rolls required when generating a new seed.
     * 50d6 carry ~129 bits (covers 128-bit entropy), 100d6 ~258 bits (covers 256).
     */
    fun minGenerateDiceRolls(wordCount: Int): Int =
        when (wordCount) {
            12 -> 50
            24 -> 100
            else -> throw IllegalArgumentException("Unsupported word count: $wordCount")
        }

    /**
     * True when [rolls] (whitespace ignored) holds only d6 digits with at
     * least [minRolls] of them.
     */
    fun isValidDiceRolls(
        rolls: String,
        minRolls: Int,
    ): Boolean {
        val digits = rolls.filterNot { it.isWhitespace() }
        return digits.length >= minRolls && digits.all { it in '1'..'6' }
    }

    /**
     * Derive seed entropy solely from user dice rolls (dice-only, exclusive).
     * The rolls are hashed once with SHA-256; no phone randomness is mixed in,
     * so the output is fully determined by [rolls]. [sizeBytes] must be 16
     * (12 words, digest truncated) or 32 (24 words, full digest).
     *
     * Weak/predictable dice produce brute-forceable seeds. Callers must enforce
     * [minGenerateDiceRolls] minimums and disclose dice-only derivation in UI.
     */
    fun diceOnlyEntropy(
        rolls: String,
        sizeBytes: Int,
    ): ByteArray {
        require(sizeBytes == 16 || sizeBytes == 32) {
            "Entropy size must be 16 or 32, was $sizeBytes"
        }
        val digits = rolls.filterNot { it.isWhitespace() }
        require(digits.isNotEmpty() && digits.all { it in '1'..'6' }) {
            "Dice rolls must be d6 digits"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(digits.toByteArray(Charsets.US_ASCII))
        return if (sizeBytes == 16) digest.copyOf(16) else digest
    }

    /**
     * Shared dice-or-random decision for wallet generation screens.
     * Returns null when [diceText] is blank (caller uses platform CSPRNG),
     * otherwise dice-only entropy sized for [wordCount] (12 -> 16 bytes,
     * 24 -> 32 bytes). Throws for unsupported counts or invalid rolls.
     */
    fun diceEntropyOrNull(
        diceText: String,
        wordCount: Int,
    ): ByteArray? {
        if (diceText.filterNot { it.isWhitespace() }.isEmpty()) return null
        val sizeBytes =
            when (wordCount) {
                12 -> 16
                24 -> 32
                else -> throw IllegalArgumentException("Unsupported word count: $wordCount")
            }
        return diceOnlyEntropy(diceText, sizeBytes)
    }

    /**
     * Minimum physical dice rolls required. 5d6 carry ~12.9 bits of entropy,
     * covering the largest candidate set (128 = 7 bits).
     */
    const val MIN_DICE_ROLLS = 5

    /**
     * Return every valid checksum word completing [prefixWords], preserving
     * wordlist order. Empty when the prefix count is unsupported, the
     * wordlist is not the 2048-word English list, or any prefix word is unknown.
     */
    fun checksumCandidates(
        prefixWords: List<String>,
        wordlist: List<String>,
    ): List<String> {
        if (wordlist.size != 2048 || prefixWords.size !in SUPPORTED_PREFIX_COUNTS) return emptyList()
        val indexOf = wordlist.withIndex().associate { (index, word) -> word to index }
        val prefix = prefixWords.map { indexOf[it] ?: return emptyList() }
        val fullWordCount = prefix.size + 1
        val totalBits = fullWordCount * 11
        val entropyBits = totalBits * 32 / 33
        val result = ArrayList<String>(128)
        for (candidate in 0 until 2048) {
            if (checkBits(prefix + candidate, totalBits, entropyBits)) {
                result.add(wordlist[candidate])
            }
        }
        return result
    }

    /**
     * Validate a full BIP39 mnemonic against [wordlist] (word membership +
     * checksum). Supports 12/15/18/21/24-word lengths.
     */
    fun isValidMnemonic(
        words: List<String>,
        wordlist: List<String>,
    ): Boolean {
        if (wordlist.size != 2048 || words.isEmpty()) return false
        val totalBits = words.size * 11
        if (totalBits % 33 != 0) return false
        val indexOf = wordlist.withIndex().associate { (index, word) -> word to index }
        val indices = words.map { indexOf[it] ?: return false }
        return checkBits(indices, totalBits, totalBits * 32 / 33)
    }

    /**
     * Map user-supplied dice rolls (d6 digits, whitespace ignored) deterministically
     * to a candidate index via SHA-256. Null when input has fewer than
     * [MIN_DICE_ROLLS] digits or any digit outside 1-6, or [candidateCount]
     * is not positive.
     *
     * Modulo-bias note: the first 4 digest bytes form a uint32 reduced mod
     * [candidateCount]. This is unbiased only when [candidateCount] divides
     * 2^32 (e.g. 128 or 8, the checksum-candidate sizes used here). Do not
     * reuse with arbitrary counts without rejection sampling.
     */
    fun diceRollsToIndex(
        rolls: String,
        candidateCount: Int,
    ): Int? {
        if (candidateCount <= 0) return null
        val digits = rolls.filterNot { it.isWhitespace() }
        if (digits.length < MIN_DICE_ROLLS || digits.any { it !in '1'..'6' }) return null
        val hash = MessageDigest.getInstance("SHA-256").digest(digits.toByteArray(Charsets.US_ASCII))
        var raw = 0L
        for (i in 0 until 4) {
            raw = (raw shl 8) or (hash[i].toInt() and 0xFF).toLong()
        }
        return (raw % candidateCount).toInt()
    }

    /**
     * Check BIP39 checksum: first [entropyBits] bits are entropy, the rest must
     * equal the leading bits of SHA-256(entropy).
     */
    private fun checkBits(
        indices: List<Int>,
        totalBits: Int,
        entropyBits: Int,
    ): Boolean {
        val checksumBits = totalBits - entropyBits
        val entropy = ByteArray(entropyBits / 8)
        for (i in 0 until entropyBits) {
            val bitInWord = 10 - (i % 11)
            if ((indices[i / 11] shr bitInWord) and 1 == 1) {
                entropy[i / 8] = (entropy[i / 8].toInt() or (0x80 shr (i % 8))).toByte()
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        for (i in 0 until checksumBits) {
            val bitIndex = entropyBits + i
            val actual = (indices[bitIndex / 11] shr (10 - (bitIndex % 11))) and 1
            val expected = (hash[i / 8].toInt() shr (7 - (i % 8))) and 1
            if (actual != expected) return false
        }
        return true
    }
}
