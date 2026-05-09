package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.Recipient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BitcoinSendPreparationTest : FunSpec({
    val baseState =
        BitcoinSendPreparationState(
            walletId = "wallet-1",
            balanceSats = 150_000UL,
            pendingIncomingSats = 0UL,
            pendingOutgoingSats = 0UL,
            transactionCount = 12,
            spendUnconfirmed = false,
        )

    test("max send single-recipient keys ignore entered amount") {
        val first =
            buildSingleBitcoinSendPreparationKey(
                state = baseState,
                recipientAddress = "bc1qexampleaddress0001",
                amountSats = 10_000UL,
                feeRateSatPerVb = 2.5,
                selectedOutpoints = listOf("b:1", "a:0"),
                isMaxSend = true,
            )
        val second =
            buildSingleBitcoinSendPreparationKey(
                state = baseState,
                recipientAddress = "bc1qexampleaddress0001",
                amountSats = 90_000UL,
                feeRateSatPerVb = 2.5,
                selectedOutpoints = listOf("a:0", "b:1"),
                isMaxSend = true,
            )

        first shouldBe second
    }

    test("non-max single-recipient keys change when amount changes") {
        val first =
            buildSingleBitcoinSendPreparationKey(
                state = baseState,
                recipientAddress = "bc1qexampleaddress0001",
                amountSats = 10_000UL,
                feeRateSatPerVb = 2.5,
                selectedOutpoints = emptyList(),
                isMaxSend = false,
            )
        val second =
            buildSingleBitcoinSendPreparationKey(
                state = baseState,
                recipientAddress = "bc1qexampleaddress0001",
                amountSats = 20_000UL,
                feeRateSatPerVb = 2.5,
                selectedOutpoints = emptyList(),
                isMaxSend = false,
            )

        first shouldNotBe second
    }

    test("cache key invalidates when wallet state changes") {
        val updatedState = baseState.copy(transactionCount = baseState.transactionCount + 1)
        val first =
            buildMultiBitcoinSendPreparationKey(
                state = baseState,
                recipients = listOf(Recipient("bc1qexampleaddress0001", 30_000UL)),
                feeRateSatPerVb = 1.5,
                selectedOutpoints = emptyList(),
            )
        val second =
            buildMultiBitcoinSendPreparationKey(
                state = updatedState,
                recipients = listOf(Recipient("bc1qexampleaddress0001", 30_000UL)),
                feeRateSatPerVb = 1.5,
                selectedOutpoints = emptyList(),
            )

        first shouldNotBe second
    }

    test("watch-only review can open while preview is still loading") {
        canOpenBitcoinSendReview(
            walletInitialized = true,
            isConnected = false,
            isWatchOnly = true,
            isMultiMode = false,
            isAddressValid = true,
            amountSats = 125_000.0,
            isMaxMode = false,
            multiRecipientCount = 0,
            hasDryRunError = false,
            isSending = false,
        ) shouldBe true
    }

    test("review stays blocked when preview already failed") {
        canOpenBitcoinSendReview(
            walletInitialized = true,
            isConnected = true,
            isWatchOnly = false,
            isMultiMode = false,
            isAddressValid = true,
            amountSats = 125_000.0,
            isMaxMode = false,
            multiRecipientCount = 0,
            hasDryRunError = true,
            isSending = false,
        ) shouldBe false
    }
})
