package github.aeonbtc.ibiswallet.data.repository

/**
 * Classifies a send error before deciding whether to leave a failed-send
 * tombstone.
 *
 * Bark persists Arkoor sends as wallet actions. A thrown error can arrive after
 * cosigning (inputs already spent server-side, destination VTXOs registered)
 * or from a lost response; in those cases the payment may still complete. A
 * tombstone would hide that pending movement and invite a blind retry, which
 * can pay twice. Only clearly local/validation failures may be treated as
 * definitive. Everything else is UNKNOWN and must be reconciled with sync.
 */
object ArkSendFailurePolicy {
    enum class Kind { DEFINITIVE, UNKNOWN }

    fun classify(message: String): Kind {
        val text = message.trim().lowercase()
        if (text.isBlank()) return Kind.UNKNOWN
        val definitive =
            listOf(
                "insufficient",
                "invalid address",
                "invalid invoice",
                "unsupported destination",
                "unsupported address",
                "amount required",
                "enter amount",
                "no route",
                "invoice expired",
                "expired invoice",
                "unknown delivery",
                "different server",
                "testnet",
                "not enough funds",
                "dust",
            ).any { text.contains(it) }
        return if (definitive) Kind.DEFINITIVE else Kind.UNKNOWN
    }

    fun isDefinitive(message: String): Boolean = classify(message) == Kind.DEFINITIVE
}
