package github.aeonbtc.ibiswallet.viewmodel

import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.repository.WalletRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for SweepState.totalBalanceSats — the ULong→toLong()→sumOf→toULong()
 * conversion chain. This property aggregates balances from multiple address
 * types when sweeping a WIF private key.
 *
 * The implementation uses: scanResults.sumOf { it.balanceSats.toLong() }.toULong()
 * This means each individual balance is narrowed to Long (max ~9.2 * 10^18 sats)
 * before summing. For Bitcoin (max 21M BTC = 2.1 * 10^15 sats), this is safe,
 * but we test the boundaries to document the assumption.
 */
class SweepStateTest : FunSpec({

    fun scanResult(balanceSats: ULong) = WalletRepository.SweepScanResult(
        addressType = AddressType.SEGWIT,
        address = "bc1qtest",
        balanceSats = balanceSats,
        utxoCount = 1,
    )

    context("totalBalanceSats") {

        test("empty scan results = 0") {
            val state = SweepState(scanResults = emptyList())
            state.totalBalanceSats shouldBe 0UL
        }

        test("multiple results are summed") {
            val state = SweepState(
                scanResults = listOf(
                    scanResult(50_000UL),
                    scanResult(30_000UL),
                    scanResult(20_000UL),
                ),
            )
            state.totalBalanceSats shouldBe 100_000UL
        }

        test("max Bitcoin supply: 21M BTC = 2_100_000_000_000_000 sats") {
            val maxSats = 2_100_000_000_000_000UL // 21M BTC in sats
            val state = SweepState(scanResults = listOf(scanResult(maxSats)))
            state.totalBalanceSats shouldBe maxSats
        }

        test("sum of balances equaling max supply") {
            val half = 1_050_000_000_000_000UL
            val state = SweepState(
                scanResults = listOf(scanResult(half), scanResult(half)),
            )
            state.totalBalanceSats shouldBe 2_100_000_000_000_000UL
        }
    }

    context("hasBalance") {

        test("false when no scan results") {
            SweepState(scanResults = emptyList()).hasBalance shouldBe false
        }

        test("true when scan results exist (even with 0 balance)") {
            SweepState(scanResults = listOf(scanResult(0UL))).hasBalance shouldBe true
        }
    }

    context("isComplete") {

        test("false when no sweep txids") {
            SweepState(sweepTxids = emptyList()).isComplete shouldBe false
        }

        test("true when sweep txids present") {
            SweepState(sweepTxids = listOf("txid1")).isComplete shouldBe true
        }
    }
})
