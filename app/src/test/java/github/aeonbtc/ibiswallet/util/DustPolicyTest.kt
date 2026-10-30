package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DustPolicyTest : FunSpec({
    fun p2wpkhScript(): ByteArray = byteArrayOf(0x00, 0x14) + ByteArray(20) { it.toByte() }

    fun p2trScript(): ByteArray = byteArrayOf(0x51, 0x20) + ByteArray(32) { it.toByte() }

    fun p2pkhScript(): ByteArray =
        byteArrayOf(0x76.toByte(), 0xA9.toByte(), 0x14.toByte()) +
            ByteArray(20) { it.toByte() } +
            byteArrayOf(0x88.toByte(), 0xAC.toByte())

    fun p2shScript(): ByteArray =
        byteArrayOf(0xA9.toByte(), 0x14.toByte()) +
            ByteArray(20) { it.toByte() } +
            byteArrayOf(0x87.toByte())

    fun p2wshScript(): ByteArray = byteArrayOf(0x00, 0x20) + ByteArray(32) { it.toByte() }

    context("script classification") {
        test("classifies standard script types") {
            DustPolicy.classifyScript(p2pkhScript()) shouldBe DustPolicy.ScriptType.P2PKH
            DustPolicy.classifyScript(p2shScript()) shouldBe DustPolicy.ScriptType.P2SH
            DustPolicy.classifyScript(p2wpkhScript()) shouldBe DustPolicy.ScriptType.P2WPKH
            DustPolicy.classifyScript(p2wshScript()) shouldBe DustPolicy.ScriptType.P2WSH
            DustPolicy.classifyScript(p2trScript()) shouldBe DustPolicy.ScriptType.P2TR
        }

        test("unknown scripts fall back to UNKNOWN") {
            DustPolicy.classifyScript(byteArrayOf(0x6A, 0x04, 0x01, 0x02, 0x03, 0x04)) shouldBe
                DustPolicy.ScriptType.UNKNOWN
            DustPolicy.classifyScript(byteArrayOf()) shouldBe DustPolicy.ScriptType.UNKNOWN
        }
    }

    context("relay dust floors match Bitcoin Core") {
        test("per-type floors") {
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.P2PKH) shouldBe 546L
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.P2SH) shouldBe 540L
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.P2WPKH) shouldBe 294L
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.P2WSH) shouldBe 330L
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.P2TR) shouldBe 330L
            DustPolicy.relayDustFloorSats(DustPolicy.ScriptType.UNKNOWN) shouldBe 546L
        }
    }

    context("spend cost") {
        test("P2WPKH at 10 sat/vB costs 680 sats") {
            DustPolicy.spendCostSats(DustPolicy.ScriptType.P2WPKH, 10.0) shouldBe 680L
        }

        test("invalid rates fall back instead of returning zero") {
            DustPolicy.spendCostSats(DustPolicy.ScriptType.P2WPKH, 0.0) shouldBe
                DustPolicy.spendCostSats(
                    DustPolicy.ScriptType.P2WPKH,
                    DustPolicy.FALLBACK_FEE_RATE_SAT_PER_VB,
                )
            DustPolicy.spendCostSats(DustPolicy.ScriptType.P2WPKH, -5.0) shouldBe
                DustPolicy.spendCostSats(
                    DustPolicy.ScriptType.P2WPKH,
                    DustPolicy.FALLBACK_FEE_RATE_SAT_PER_VB,
                )
        }
    }

    context("dust detection") {
        test("change outputs are never dust") {
            DustPolicy.isSuspectedDust(
                amountSats = 100L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = true,
                feeRateSatPerVb = 100.0,
            ) shouldBe false
        }

        test("amounts above the cap are never dust") {
            DustPolicy.isSuspectedDust(
                amountSats = 1_001L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 1_000.0,
            ) shouldBe false
        }

        test("below relay floor is always dust, even at minimum fees") {
            DustPolicy.isSuspectedDust(
                amountSats = 200L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 1.0,
            ) shouldBe true
        }

        test("uneconomical at current rates is dust") {
            // 600 sats P2WPKH costs 680 to spend at 10 sat/vB.
            DustPolicy.isSuspectedDust(
                amountSats = 600L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 10.0,
            ) shouldBe true
        }

        test("economical at current rates is not dust") {
            // 900 sats P2WPKH costs 136 to spend at 2 sat/vB.
            DustPolicy.isSuspectedDust(
                amountSats = 900L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 2.0,
            ) shouldBe false
        }

        test("sub-floor amounts stay dust even at minimum fee quotes") {
            // 500 sats P2PKH is under the 546 relay floor: dust at any rate,
            // so a near-zero fee quote can never clear it.
            DustPolicy.isSuspectedDust(
                amountSats = 500L,
                scriptType = DustPolicy.ScriptType.P2PKH,
                isChange = false,
                feeRateSatPerVb = 1.0,
            ) shouldBe true
        }

        test("unknown fee rate uses the fallback") {
            DustPolicy.isSuspectedDust(
                amountSats = 600L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = null,
            ) shouldBe
                DustPolicy.isSuspectedDust(
                    amountSats = 600L,
                    scriptType = DustPolicy.ScriptType.P2WPKH,
                    isChange = false,
                    feeRateSatPerVb = DustPolicy.FALLBACK_FEE_RATE_SAT_PER_VB,
                )
        }

        test("cap is configurable") {
            DustPolicy.isSuspectedDust(
                amountSats = 1_500L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 100.0,
                maxDustSats = 2_000L,
            ) shouldBe true
            DustPolicy.isSuspectedDust(
                amountSats = 1_500L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 100.0,
                maxDustSats = 500L,
            ) shouldBe false
        }

        test("non-positive amounts are never dust") {
            DustPolicy.isSuspectedDust(
                amountSats = 0L,
                scriptType = DustPolicy.ScriptType.P2WPKH,
                isChange = false,
                feeRateSatPerVb = 100.0,
            ) shouldBe false
        }
    }
})
