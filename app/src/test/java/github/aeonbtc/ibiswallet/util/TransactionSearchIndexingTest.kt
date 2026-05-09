package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTransaction
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TransactionSearchIndexingTest : FunSpec({
    val originalTimeZone = TimeZone.getDefault()
    beforeSpec {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
    afterSpec {
        TimeZone.setDefault(originalTimeZone)
    }

    fun utcTimestamp(value: String): Long {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.parse(value)!!.time / 1000L
    }

    context("buildTransactionSearchMatchQuery") {
        test("returns prefix terms for normalized user input") {
            buildTransactionSearchMatchQuery("Apr 14/2026") shouldBe "apr* AND 14* AND 2026*"
        }

        test("returns null for punctuation-only input") {
            buildTransactionSearchMatchQuery(" / : ").shouldBeNull()
        }

        test("does not require commas or full four digit years") {
            buildTransactionSearchMatchQuery("April 14 26") shouldBe "april* AND 14* AND 26*"
        }
    }

    context("buildBitcoinTransactionSearchDocument") {
        test("captures labels, addresses, swap flag, and expanded date tokens") {
            val timestamp = utcTimestamp("2026-04-14 12:00")
            val document =
                buildBitcoinTransactionSearchDocument(
                    walletId = "wallet-1",
                    transaction =
                        TransactionDetails(
                            txid = "abc123deadbeef",
                            amountSats = -25_000L,
                            fee = 120UL,
                            weight = 560UL,
                            confirmationTime = null,
                            isConfirmed = false,
                            timestamp = timestamp,
                            address = "bc1qrecipient123",
                            changeAddress = "bc1qchange456",
                            swapDetails = LiquidSwapDetails(
                                service = SwapService.BOLTZ,
                                direction = SwapDirection.BTC_TO_LBTC,
                                swapId = "swap-1",
                                role = LiquidSwapTxRole.FUNDING,
                                depositAddress = "bc1qrecipient123",
                            ),
                        ),
                    transactionLabel = "Cold storage",
                    addressLabel = "Treasury",
                )

            document.isSwap.shouldBeTrue()
            buildTransactionSearchableText(document).shouldContain("abc123deadbeef")
            buildTransactionSearchableText(document).shouldContain("Cold storage")
            buildTransactionSearchableText(document).shouldContain("Treasury")
            buildTransactionSearchableText(document).shouldContain("apr")
            buildTransactionSearchableText(document).shouldContain("april")
            buildTransactionSearchableText(document).shouldContain("14")
            buildTransactionSearchableText(document).shouldContain("2026")
            buildTransactionSearchableText(document).shouldContain("26")
        }
    }

    context("buildLiquidTransactionSearchDocument") {
        test("sets Liquid search flags and searchable fields") {
            val document =
                buildLiquidTransactionSearchDocument(
                    walletId = "wallet-2",
                    transaction =
                        LiquidTransaction(
                            txid = "liquidtx123",
                            balanceSatoshi = 10_000L,
                            fee = 120L,
                            assetDeltas =
                                mapOf(
                                    LiquidAsset.USDT_ASSET_ID to 5_000L,
                                ),
                            height = 101,
                            timestamp = 1_775_174_400L,
                            walletAddress = "lq1walletaddress",
                            changeAddress = "lq1changeaddress",
                            recipientAddress = "lq1recipient",
                            memo = "existing memo",
                            source = LiquidTxSource.LIGHTNING_SEND_SWAP,
                        ),
                    transactionLabel = "Invoice settlement",
                )

            document.isLightning.shouldBeTrue()
            document.isSwap.shouldBeFalse()
            document.isNative.shouldBeFalse()
            document.hasUsdt.shouldBeTrue()
            buildTransactionSearchableText(document).shouldContain("Invoice settlement")
            buildTransactionSearchableText(document).shouldContain("lq1recipient")
            buildTransactionSearchableText(document).shouldContain("2026")
        }
    }
})
