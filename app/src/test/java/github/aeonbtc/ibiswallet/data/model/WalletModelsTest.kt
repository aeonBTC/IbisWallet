package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class WalletModelsTest : FunSpec({

    // ── TransactionDetails.vsize ──

    context("TransactionDetails.vsize") {
        test("computes ceiled vsize from weight") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = 561UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            // ceil(561 / 4.0) = 141.0
            tx.vsize shouldBe 141.0
        }

        test("returns null when weight is null") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = null,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            tx.vsize shouldBe null
        }

        test("returns null when weight is zero") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = 0UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            tx.vsize shouldBe null
        }

        test("exact multiple of 4 produces integer vsize") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = 400UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            tx.vsize shouldBe 100.0
        }

        test("non-multiple of 4 rounds up") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = 401UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            // ceil(401 / 4.0) = ceil(100.25) = 101.0
            tx.vsize shouldBe 101.0
        }
    }

    // ── TransactionDetails.feeRate ──

    context("TransactionDetails.feeRate") {
        test("computes fee rate as fee / vsize") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = 400UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            // 200 / 100.0 = 2.0 sat/vB
            tx.feeRate shouldBe 2.0
        }

        test("returns null when fee is null") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = null, weight = 400UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            tx.feeRate shouldBe null
        }

        test("returns null when weight is null") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 200UL, weight = null,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            tx.feeRate shouldBe null
        }

        test("handles fractional fee rates") {
            val tx = TransactionDetails(
                txid = "abc", amountSats = 1000L, fee = 150UL, weight = 400UL,
                confirmationTime = null, isConfirmed = false, timestamp = null,
            )
            // 150 / 100.0 = 1.5
            tx.feeRate shouldBe 1.5
        }
    }

    // ── ElectrumConfig ──

    context("ElectrumConfig.cleanUrl") {
        test("strips tcp:// prefix") {
            ElectrumConfig(url = "tcp://example.com").cleanUrl() shouldBe "example.com"
        }

        test("strips ssl:// prefix") {
            ElectrumConfig(url = "ssl://example.com").cleanUrl() shouldBe "example.com"
        }

        test("strips http:// prefix") {
            ElectrumConfig(url = "http://example.com").cleanUrl() shouldBe "example.com"
        }

        test("strips https:// prefix") {
            ElectrumConfig(url = "https://example.com").cleanUrl() shouldBe "example.com"
        }

        test("trims trailing slash") {
            ElectrumConfig(url = "example.com/").cleanUrl() shouldBe "example.com"
        }

        test("trims whitespace") {
            ElectrumConfig(url = "  example.com  ").cleanUrl() shouldBe "example.com"
        }

        test("passes through plain hostname") {
            ElectrumConfig(url = "example.com").cleanUrl() shouldBe "example.com"
        }
    }

    context("ElectrumConfig.displayName") {
        test("returns name when set") {
            ElectrumConfig(name = "My Server", url = "example.com", port = 50002)
                .displayName() shouldBe "My Server"
        }

        test("falls back to host:port when name is null") {
            ElectrumConfig(url = "example.com", port = 50002)
                .displayName() shouldBe "example.com:50002"
        }

        test("falls back to host:port when name is blank") {
            ElectrumConfig(name = "  ", url = "example.com", port = 50002)
                .displayName() shouldBe "example.com:50002"
        }
    }

    context("ElectrumConfig.isOnionAddress") {
        test("returns true for .onion") {
            ElectrumConfig(url = "abcdef1234567890.onion").isOnionAddress() shouldBe true
        }

        test("returns false for clearnet") {
            ElectrumConfig(url = "electrum.example.com").isOnionAddress() shouldBe false
        }

        test("returns true for .onion with protocol prefix") {
            ElectrumConfig(url = "ssl://abcdef.onion").isOnionAddress() shouldBe true
        }
    }

    // ── FeeEstimates.isUniform ──

    context("FeeEstimates.isUniform") {
        test("returns true when all rates are equal") {
            val fees = FeeEstimates(
                fastestFee = 5.0, halfHourFee = 5.0, hourFee = 5.0, minimumFee = 1.0,
            )
            fees.isUniform shouldBe true
        }

        test("returns false when rates differ") {
            val fees = FeeEstimates(
                fastestFee = 10.0, halfHourFee = 5.0, hourFee = 3.0, minimumFee = 1.0,
            )
            fees.isUniform shouldBe false
        }

        test("minimum fee is excluded from uniformity check") {
            val fees = FeeEstimates(
                fastestFee = 5.0, halfHourFee = 5.0, hourFee = 5.0, minimumFee = 5.0,
            )
            fees.isUniform shouldBe true
        }
    }

    // ── DryRunResult ──

    context("DryRunResult") {
        test("error factory creates error result") {
            val result = DryRunResult.error("Insufficient funds")
            result.isError shouldBe true
            result.error shouldBe "Insufficient funds"
            result.feeSats shouldBe 0L
            result.changeSats shouldBe 0L
            result.numInputs shouldBe 0
        }

        test("isError is false when error is null") {
            val result = DryRunResult(
                feeSats = 200L, changeSats = 50L, hasChange = true,
                numInputs = 1, txVBytes = 140.0, effectiveFeeRate = 1.43,
                recipientAmountSats = 1000L,
            )
            result.isError shouldBe false
            result.error shouldBe null
        }
    }

    // ── WalletImportConfig.toString redaction ──

    context("WalletImportConfig.toString") {
        test("redacts keyMaterial") {
            val config = WalletImportConfig(
                name = "Test Wallet",
                keyMaterial = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            )
            val str = config.toString()
            str shouldNotContain "abandon"
            str shouldContain "[REDACTED"
            str shouldContain "name=Test Wallet"
        }
    }

    // ── AddressType enum ──

    context("AddressType") {
        test("LEGACY has correct derivation path") {
            AddressType.LEGACY.defaultPath shouldBe "m/44'/0'/0'/0"
            AddressType.LEGACY.accountPath shouldBe "44'/0'/0'"
        }

        test("SEGWIT has correct derivation path") {
            AddressType.SEGWIT.defaultPath shouldBe "m/84'/0'/0'/0"
            AddressType.SEGWIT.accountPath shouldBe "84'/0'/0'"
        }

        test("TAPROOT has correct derivation path") {
            AddressType.TAPROOT.defaultPath shouldBe "m/86'/0'/0'/0"
            AddressType.TAPROOT.accountPath shouldBe "86'/0'/0'"
        }
    }

    // ── ImportResult ──

    context("Bip329Labels.ImportResult") {
        test("totalLabelsImported sums address and tx labels") {
            val result = github.aeonbtc.ibiswallet.util.Bip329Labels.ImportResult(
                bitcoinAddressLabels = mapOf("addr1" to "Label1", "addr2" to "Label2"),
                bitcoinTransactionLabels = mapOf("tx1" to "Tx Label"),
                liquidAddressLabels = emptyMap(),
                liquidTransactionLabels = emptyMap(),
                outputSpendable = emptyMap(),
                totalLines = 3,
                errorLines = 0,
            )
            result.totalLabelsImported shouldBe 3
            result.isEmpty shouldBe false
        }

        test("isEmpty is true when no labels imported") {
            val result = github.aeonbtc.ibiswallet.util.Bip329Labels.ImportResult(
                bitcoinAddressLabels = emptyMap(),
                bitcoinTransactionLabels = emptyMap(),
                liquidAddressLabels = emptyMap(),
                liquidTransactionLabels = emptyMap(),
                outputSpendable = emptyMap(),
                totalLines = 5,
                errorLines = 5,
            )
            result.isEmpty shouldBe true
        }
    }
})
