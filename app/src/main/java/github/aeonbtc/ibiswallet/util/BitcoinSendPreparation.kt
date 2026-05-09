package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.Recipient

internal data class BitcoinSendPreparationState(
    val walletId: String?,
    val balanceSats: ULong,
    val pendingIncomingSats: ULong,
    val pendingOutgoingSats: ULong,
    val transactionCount: Int,
    val spendUnconfirmed: Boolean,
)

internal data class BitcoinSendPreparationCacheKey(
    val state: BitcoinSendPreparationState,
    val feeRateBits: Long,
    val selectedOutpoints: List<String>,
    val recipientAddresses: List<String>,
    val recipientAmounts: List<ULong>,
    val isMaxSend: Boolean,
)

internal fun buildSingleBitcoinSendPreparationKey(
    state: BitcoinSendPreparationState,
    recipientAddress: String,
    amountSats: ULong,
    feeRateSatPerVb: Double,
    selectedOutpoints: List<String>,
    isMaxSend: Boolean,
): BitcoinSendPreparationCacheKey =
    BitcoinSendPreparationCacheKey(
        state = state,
        feeRateBits = feeRateSatPerVb.toBits(),
        selectedOutpoints = selectedOutpoints.sorted(),
        recipientAddresses = listOf(recipientAddress),
        recipientAmounts = listOf(if (isMaxSend) 0UL else amountSats),
        isMaxSend = isMaxSend,
    )

internal fun buildMultiBitcoinSendPreparationKey(
    state: BitcoinSendPreparationState,
    recipients: List<Recipient>,
    feeRateSatPerVb: Double,
    selectedOutpoints: List<String>,
): BitcoinSendPreparationCacheKey =
    BitcoinSendPreparationCacheKey(
        state = state,
        feeRateBits = feeRateSatPerVb.toBits(),
        selectedOutpoints = selectedOutpoints.sorted(),
        recipientAddresses = recipients.map { it.address },
        recipientAmounts = recipients.map { it.amountSats },
        isMaxSend = false,
    )

internal fun canOpenBitcoinSendReview(
    walletInitialized: Boolean,
    isConnected: Boolean,
    isWatchOnly: Boolean,
    isMultiMode: Boolean,
    isAddressValid: Boolean,
    amountSats: Double,
    isMaxMode: Boolean,
    multiRecipientCount: Int,
    hasDryRunError: Boolean,
    isSending: Boolean,
): Boolean {
    if (!walletInitialized || isSending || hasDryRunError) return false
    if (!isWatchOnly && !isConnected) return false

    return if (isMultiMode) {
        multiRecipientCount >= 2
    } else {
        isAddressValid && (amountSats > 0.0 || isMaxMode)
    }
}
