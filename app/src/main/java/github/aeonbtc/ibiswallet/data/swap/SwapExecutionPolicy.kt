package github.aeonbtc.ibiswallet.data.swap

import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService

data class BitcoinSwapFundingRequest(
    val recipientAddress: String,
    val amountSats: Long,
    val feeRateSatPerVb: Double,
    val isMaxSend: Boolean,
)

data class LiquidSwapFundingRequest(
    val recipientAddress: String,
    val amountSats: Long,
    val feeRateSatPerVb: Double,
    val isMaxSend: Boolean,
)

data class BoltzAddressPlan(
    val providerBitcoinAddress: String,
    val liquidDestinationOverride: String?,
)

fun buildBitcoinSwapFundingRequest(
    pendingSwap: PendingSwapSession,
    feeRateSatPerVb: Double,
): BitcoinSwapFundingRequest {
    return BitcoinSwapFundingRequest(
        recipientAddress = pendingSwap.depositAddress,
        amountSats = pendingSwap.sendAmount,
        feeRateSatPerVb = feeRateSatPerVb,
        // Swap execution should always fund the reviewed exact amount.
        isMaxSend = false,
    )
}

fun buildLiquidSwapFundingRequest(
    pendingSwap: PendingSwapSession,
    feeRateSatPerVb: Double,
): LiquidSwapFundingRequest {
    val exactAmount =
        if (
            pendingSwap.service == SwapService.BOLTZ &&
            pendingSwap.direction == SwapDirection.LBTC_TO_BTC &&
            pendingSwap.usesMaxAmount
        ) {
            pendingSwap.boltzVerifiedRecipientAmountSats?.takeIf { it > 0L } ?: pendingSwap.sendAmount
        } else {
            pendingSwap.sendAmount
        }
    return LiquidSwapFundingRequest(
        recipientAddress = pendingSwap.depositAddress,
        amountSats = exactAmount,
        feeRateSatPerVb = feeRateSatPerVb,
        // Reviewed max Liquid-funded swaps should replay the same drain semantics
        // used when the provider lockup amount was normalized.
        isMaxSend = pendingSwap.direction == SwapDirection.LBTC_TO_BTC && pendingSwap.usesMaxAmount,
    )
}

fun resolveSideSwapRequestedReceiveAddress(
    direction: SwapDirection,
    destinationAddress: String?,
    bitcoinWalletAddress: String?,
    generatedLiquidAddress: String? = null,
): String {
    return when (direction) {
        SwapDirection.BTC_TO_LBTC ->
            destinationAddress?.takeIf { it.isNotBlank() }
                ?: generatedLiquidAddress
                ?: throw IllegalArgumentException("No Liquid address available for SideSwap")
        SwapDirection.LBTC_TO_BTC ->
            destinationAddress?.takeIf { it.isNotBlank() }
                ?: bitcoinWalletAddress?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("No Bitcoin address available for SideSwap payout")
    }
}

fun resolveBoltzAddressPlan(
    direction: SwapDirection,
    destinationAddress: String?,
    bitcoinWalletAddress: String?,
): BoltzAddressPlan {
    return when (direction) {
        SwapDirection.BTC_TO_LBTC ->
            BoltzAddressPlan(
                providerBitcoinAddress =
                    bitcoinWalletAddress?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("No Bitcoin refund address available for Boltz swap"),
                liquidDestinationOverride = destinationAddress?.takeIf { it.isNotBlank() },
            )

        SwapDirection.LBTC_TO_BTC ->
            BoltzAddressPlan(
                providerBitcoinAddress =
                    destinationAddress?.takeIf { it.isNotBlank() }
                        ?: bitcoinWalletAddress?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("No Bitcoin destination address available for Boltz swap"),
                liquidDestinationOverride = null,
            )
    }
}
