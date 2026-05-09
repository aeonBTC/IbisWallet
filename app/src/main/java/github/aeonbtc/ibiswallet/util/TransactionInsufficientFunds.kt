package github.aeonbtc.ibiswallet.util

fun Throwable.isTransactionInsufficientFundsError(): Boolean {
    val details =
        generateSequence(this) { it.cause }
            .mapNotNull { throwable -> throwable.message?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString(separator = " | ")
    if (
        details.contains("insufficient", ignoreCase = true) ||
        details.contains("not enough", ignoreCase = true) ||
        details.contains("coin selection", ignoreCase = true) ||
        details.contains("insufficientfunds", ignoreCase = true) ||
        details.contains("missing_sats", ignoreCase = true)
    ) {
        return true
    }
    return generateSequence(this) { it.cause }.any { t ->
        t::class.java.simpleName.contains("InsufficientFunds", ignoreCase = true)
    }
}
