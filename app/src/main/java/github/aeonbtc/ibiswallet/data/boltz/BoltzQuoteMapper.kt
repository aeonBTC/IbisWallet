package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzPairInfo
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapQuote
import github.aeonbtc.ibiswallet.data.model.SwapService
import kotlin.math.ceil

internal fun calculateBoltzPercentageFee(
    amountSats: Long,
    pair: BoltzPairInfo,
): Long = ceil(amountSats * pair.fees.percentage / 100.0).toLong()

internal fun estimateBoltzReverseOnchainAmount(
    invoiceAmountSats: Long,
    pair: BoltzPairInfo,
): Long {
    val percentageFee = calculateBoltzPercentageFee(invoiceAmountSats, pair)
    return (invoiceAmountSats - percentageFee - pair.fees.serverMinerFee).coerceAtLeast(0L)
}

internal fun estimateBoltzReverseInvoiceAmount(
    desiredOnchainAmountSats: Long,
    pair: BoltzPairInfo,
): Long {
    require(desiredOnchainAmountSats >= 0L) { "Desired onchain amount must be non-negative" }
    val feeRate = pair.fees.percentage / 100.0
    require(feeRate < 1.0) { "Invalid Boltz reverse fee rate" }

    var invoiceAmount =
        ceil((desiredOnchainAmountSats + pair.fees.serverMinerFee).toDouble() / (1.0 - feeRate)).toLong()
            .coerceAtLeast(0L)
    while (estimateBoltzReverseOnchainAmount(invoiceAmount, pair) < desiredOnchainAmountSats) {
        invoiceAmount += 1L
    }
    return invoiceAmount
}

internal fun buildBoltzQuoteFromPair(
    direction: SwapDirection,
    amountSats: Long,
    pair: BoltzPairInfo,
    estimatedTime: String,
    typicalBtcTxVsize: Int,
    defaultLiquidSwapFeeRate: Double,
    receiveAmountOverride: Long? = null,
    serviceFeeOverride: Long? = null,
): SwapQuote {
    val fees = pair.fees
    val percentageFee = serviceFeeOverride ?: calculateBoltzPercentageFee(amountSats, pair)

    return when (direction) {
        SwapDirection.BTC_TO_LBTC -> {
            val btcNetworkFee = fees.userLockupFee
            val liquidNetworkFee = fees.userClaimFee
            val providerMinerFee = fees.serverMinerFee
            val receiveAmount =
                receiveAmountOverride ?: (
                    amountSats - percentageFee - providerMinerFee - liquidNetworkFee
                    ).coerceAtLeast(0L)
            SwapQuote(
                service = SwapService.BOLTZ,
                direction = direction,
                sendAmount = amountSats,
                receiveAmount = receiveAmount,
                serviceFee = percentageFee,
                btcNetworkFee = btcNetworkFee,
                liquidNetworkFee = liquidNetworkFee,
                providerMinerFee = providerMinerFee,
                btcFeeRate = btcNetworkFee.toDouble() / typicalBtcTxVsize.toDouble(),
                liquidFeeRate = defaultLiquidSwapFeeRate,
                minAmount = pair.limits.minimal,
                maxAmount = pair.limits.maximal,
                estimatedTime = estimatedTime,
            )
        }

        SwapDirection.LBTC_TO_BTC -> {
            val liquidNetworkFee = fees.userLockupFee
            val btcNetworkFee = fees.userClaimFee
            val providerMinerFee = fees.serverMinerFee
            val receiveAmount =
                receiveAmountOverride ?: (
                    amountSats - percentageFee - providerMinerFee - btcNetworkFee
                    ).coerceAtLeast(0L)
            SwapQuote(
                service = SwapService.BOLTZ,
                direction = direction,
                sendAmount = amountSats,
                receiveAmount = receiveAmount,
                serviceFee = percentageFee,
                btcNetworkFee = btcNetworkFee,
                liquidNetworkFee = liquidNetworkFee,
                providerMinerFee = providerMinerFee,
                btcFeeRate = btcNetworkFee.toDouble() / typicalBtcTxVsize.toDouble(),
                liquidFeeRate = defaultLiquidSwapFeeRate,
                minAmount = pair.limits.minimal,
                maxAmount = pair.limits.maximal,
                estimatedTime = estimatedTime,
            )
        }
    }
}
