package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.R

/**
 * Maps BDK / Electrum failures from RBF and CPFP flows to user-facing messages.
 */
object SpeedUpTransactionErrors {
    interface Localizer {
        fun get(resId: Int): String

        fun get(
            resId: Int,
            vararg formatArgs: Any,
        ): String
    }

    fun Throwable.collectErrorText(): String =
        generateSequence(this) { it.cause }
            .mapNotNull { throwable ->
                throwable.message?.trim()?.takeIf { message -> message.isNotEmpty() }
            }
            .distinct()
            .joinToString(separator = " | ")

    fun mapRbfFailure(
        localizer: Localizer,
        error: Throwable,
    ): String = mapFailure(localizer, error, isCpfp = false)

    fun mapCpfpFailure(
        localizer: Localizer,
        error: Throwable,
    ): String = mapFailure(localizer, error, isCpfp = true)

    private fun mapFailure(
        localizer: Localizer,
        error: Throwable,
        isCpfp: Boolean,
    ): String {
        val details = error.collectErrorText()
        val lower = details.lowercase()
        val classNames =
            generateSequence(error) { it.cause }
                .map { it::class.java.simpleName }
                .joinToString(separator = " ")

        if (error.isTransactionInsufficientFundsError()) {
            return localizer.get(
                if (isCpfp) {
                    R.string.speed_up_error_cpfp_insufficient
                } else {
                    R.string.speed_up_error_rbf_insufficient
                },
            )
        }

        if (isCpfp) {
            mapCpfpPattern(localizer, lower, details)?.let { return it }
            return genericFailure(localizer, R.string.speed_up_error_cpfp_generic, details)
        }

        mapRbfPattern(localizer, lower, classNames)?.let { return it }
        return genericFailure(localizer, R.string.speed_up_error_rbf_generic, details)
    }

    private fun mapRbfPattern(
        localizer: Localizer,
        lower: String,
        classNames: String,
    ): String? =
        when {
            lower.contains("not connected") ||
                lower.contains("disconnect") ||
                lower.contains("connection") && lower.contains("lost") ||
                lower.contains("timeout") && lower.contains("electrum") ->
                localizer.get(R.string.speed_up_error_not_connected)

            lower.contains("not found") ||
                lower.contains("unknown transaction") ||
                classNames.contains("TxidNotFound", ignoreCase = true) ->
                localizer.get(R.string.speed_up_error_rbf_tx_not_found)

            lower.contains("already confirmed") ||
                (lower.contains("confirmed") && !lower.contains("unconfirmed")) ->
                localizer.get(R.string.speed_up_error_rbf_confirmed)

            lower.contains("irreplaceable") ||
                lower.contains("not rbf") ||
                lower.contains("rbf-enabled") ||
                lower.contains("rbf enabled") ||
                lower.contains("not replaceable") ||
                lower.contains("rejects replacement") ->
                localizer.get(R.string.speed_up_error_rbf_not_replaceable)

            lower.contains("fee too low") ||
                classNames.contains("FeeTooLow", ignoreCase = true) ||
                lower.contains("lower than") && lower.contains("fee") ||
                lower.contains("must be higher") ||
                lower.contains("insufficient fee") && lower.contains("bump") ->
                localizer.get(R.string.speed_up_error_rbf_fee_too_low)

            lower.contains("dust") ||
                lower.contains("belowdustlimit") ||
                lower.contains("outputbelowdustlimit") ->
                localizer.get(R.string.speed_up_error_rbf_dust)

            lower.contains("broadcast") ||
                lower.contains("electrum") && (
                    lower.contains("reject") ||
                        lower.contains("refused") ||
                        lower.contains("failed") ||
                        lower.contains("error")
                    ) ||
                lower.contains("mempool") && lower.contains("reject") ||
                lower.contains("bad-txns") ||
                lower.contains("txn-mempool-conflict") ||
                lower.contains("min relay fee") ||
                lower.contains("replacement") && lower.contains("reject") ->
                localizer.get(R.string.speed_up_error_rbf_broadcast_rejected)

            lower.contains("sign") && lower.contains("fail") ||
                lower.contains("finalize") && lower.contains("fail") ->
                localizer.get(R.string.speed_up_error_rbf_sign_failed)

            lower.contains("build") && lower.contains("fail") ||
                lower.contains("create tx") ||
                lower.contains("coin selection") ||
                classNames.contains("CreateTx", ignoreCase = true) ->
                localizer.get(R.string.speed_up_error_rbf_build_failed)

            else -> null
        }

    private fun mapCpfpPattern(
        localizer: Localizer,
        lower: String,
        details: String,
    ): String? =
        when {
            lower.contains("not connected") ||
                lower.contains("disconnect") ||
                lower.contains("connection") && lower.contains("lost") ->
                localizer.get(R.string.speed_up_error_not_connected)

            lower.contains("no spendable outputs") ||
                lower.contains("no utxo") && lower.contains("parent") ->
                localizer.get(R.string.speed_up_error_cpfp_no_outputs)

            lower.contains("not found") ||
                lower.contains("parent transaction not found") ->
                localizer.get(R.string.speed_up_error_cpfp_parent_not_found)

            lower.contains("already confirmed") ||
                (lower.contains("confirmed") && !lower.contains("unconfirmed")) ->
                localizer.get(R.string.speed_up_error_cpfp_confirmed)

            lower.contains("insufficient") || lower.contains("not enough") -> {
                val match =
                    Regex(
                        "(\\d+\\.\\d+)\\s*btc\\s*available.*?(\\d+\\.\\d+)\\s*btc\\s*needed",
                        RegexOption.IGNORE_CASE,
                    ).find(details)
                if (match != null) {
                    localizer.get(
                        R.string.speed_up_error_cpfp_insufficient_detailed,
                        match.groupValues[1],
                        match.groupValues[2],
                    )
                } else {
                    localizer.get(R.string.speed_up_error_cpfp_insufficient)
                }
            }

            lower.contains("dust") ||
                lower.contains("belowdustlimit") ||
                lower.contains("outputbelowdustlimit") ->
                localizer.get(R.string.speed_up_error_cpfp_dust)

            lower.contains("broadcast") ||
                lower.contains("electrum") && lower.contains("reject") ||
                lower.contains("mempool") && lower.contains("reject") ||
                lower.contains("min relay fee") ->
                localizer.get(R.string.speed_up_error_cpfp_broadcast_rejected)

            lower.contains("build") ||
                lower.contains("coin selection") ||
                lower.contains("create tx") ->
                localizer.get(R.string.speed_up_error_cpfp_build_failed)

            else -> null
        }

    private fun genericFailure(
        localizer: Localizer,
        genericResId: Int,
        details: String,
    ): String {
        val hint = sanitizedDetail(details)
        return if (hint != null) {
            localizer.get(genericResId, hint)
        } else {
            localizer.get(genericResId, localizer.get(R.string.speed_up_error_unknown_detail))
        }
    }

    private fun sanitizedDetail(details: String): String? {
        val firstLine =
            details.lineSequence()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.isNotEmpty() &&
                        !line.contains("Exception", ignoreCase = true) &&
                        !line.startsWith("at ", ignoreCase = true)
                }
        return firstLine?.take(160)
    }
}
