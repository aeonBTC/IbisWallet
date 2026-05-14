package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.json.JSONObject

class BackupJsonAdaptersTest : FunSpec({
    test("round-trips Bitcoin transaction source and swap metadata") {
        val details = sampleSwapDetails()
        val metadataJson =
            BackupJsonAdapters.Bitcoin.metadataToJson(
                transactionSources = mapOf("txid-1" to "CHAIN_SWAP"),
                swapDetails = mapOf("txid-1" to details),
            )

        BackupJsonAdapters.Bitcoin.transactionSourcesFromMetadata(metadataJson)
            .shouldContainExactly(mapOf("txid-1" to "CHAIN_SWAP"))
        BackupJsonAdapters.Bitcoin.swapDetailsFromMetadata(metadataJson)
            .shouldContainExactly(mapOf("txid-1" to details))
    }

    test("round-trips Liquid transaction source and swap metadata") {
        val details = sampleSwapDetails()
        val metadataJson =
            BackupJsonAdapters.Liquid.metadataToJson(
                transactionSources = mapOf("txid-1" to LiquidTxSource.CHAIN_SWAP),
                swapDetails = mapOf("txid-1" to details),
            )

        BackupJsonAdapters.Liquid.transactionSourcesFromMetadata(metadataJson)
            .shouldContainExactly(mapOf("txid-1" to LiquidTxSource.CHAIN_SWAP))
        BackupJsonAdapters.Liquid.swapDetailsFromMetadata(metadataJson)
            .shouldContainExactly(mapOf("txid-1" to details))
    }

    test("round-trips Spark backup metadata") {
        val metadata =
            SparkBackupMetadata(
                transactionSources = mapOf("payment-1" to "LightningInvoice"),
                paymentRecipients = mapOf("payment-1" to "sp1recipient"),
                depositAddresses = mapOf("txid-1" to "bc1qdeposit"),
                pendingDeposits =
                    listOf(
                        SparkUnclaimedDeposit(
                            txid = "txid-1",
                            vout = 1u,
                            amountSats = 12_345L,
                            isMature = false,
                            timestamp = 1_717_171_717L,
                            address = "bc1qdeposit",
                            claimError = "pending",
                        ),
                    ),
                onchainDepositAddress = "bc1qcachedaddress",
            )

        val restored = BackupJsonAdapters.Spark.metadataFromJson(BackupJsonAdapters.Spark.metadataToJson(metadata))

        restored shouldBe metadata
    }

    test("parses missing Spark metadata fields as empty state") {
        val restored = BackupJsonAdapters.Spark.metadataFromJson(JSONObject())

        restored shouldBe
            SparkBackupMetadata(
                transactionSources = emptyMap(),
                paymentRecipients = emptyMap(),
                depositAddresses = emptyMap(),
                pendingDeposits = emptyList(),
                onchainDepositAddress = null,
            )
    }

    test("returns null when Spark metadata block is absent") {
        BackupJsonAdapters.Spark.metadataFromJson(null).shouldBeNull()
    }

    test("drops blank and malformed entries during parse") {
        val restored =
            BackupJsonAdapters.Spark.metadataFromJson(
                JSONObject(
                    """
                    {
                      "paymentRecipients": {
                        "payment-1": "sp1recipient",
                        "payment-blank": ""
                      },
                     "transactionSources": {
                       "payment-1": "LightningInvoice",
                       "payment-blank": ""
                     },
                      "depositAddresses": {
                        "txid-1": "bc1qdeposit",
                        "txid-blank": ""
                      },
                      "pendingDeposits": [
                        {
                          "txid": "txid-1",
                          "vout": 0,
                          "amountSats": 5000,
                          "isMature": true
                        },
                        {
                          "txid": "",
                          "vout": 1,
                          "amountSats": 999,
                          "isMature": false
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

        restored.shouldNotBeNull()
               restored.transactionSources.shouldContainExactly(mapOf("payment-1" to "LightningInvoice"))
        restored.paymentRecipients.shouldContainExactly(mapOf("payment-1" to "sp1recipient"))
        restored.depositAddresses.shouldContainExactly(mapOf("txid-1" to "bc1qdeposit"))
        restored.pendingDeposits.shouldContainExactly(
            SparkUnclaimedDeposit(
                txid = "txid-1",
                vout = 0u,
                amountSats = 5000L,
                isMature = true,
                timestamp = null,
                address = null,
                claimError = null,
            ),
        )
    }
})

private fun sampleSwapDetails(): LiquidSwapDetails =
    LiquidSwapDetails(
        service = SwapService.BOLTZ,
        direction = SwapDirection.BTC_TO_LBTC,
        swapId = "swap-1",
        role = LiquidSwapTxRole.FUNDING,
        depositAddress = "bc1qdeposit",
        receiveAddress = "lq1receive",
        refundAddress = "bc1qrefund",
        sendAmountSats = 10_000L,
        expectedReceiveAmountSats = 9_900L,
        paymentInput = "payment-input",
        resolvedPaymentInput = "resolved-payment-input",
        invoice = "invoice",
        status = "completed",
        timeoutBlockHeight = 900_000,
        refundPublicKey = "refund-pubkey",
        claimPublicKey = "claim-pubkey",
        swapTree = "swap-tree",
        blindingKey = "blinding-key",
    )
