package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import uniffi.bark.BlockRef
import uniffi.bark.ExitState

class ArkBarkMappersTest : FunSpec({

    context("ExitState.VtxoSwept (bark 0.8.0+)") {
        val swept = ExitState.VtxoSwept(tipHeight = 100u, spentInputs = emptyList())

        test("maps to the swept label") {
            ArkBarkMappers.exitStateLabel(swept) shouldBe ArkBarkMappers.EXIT_VTXO_SWEPT
        }

        test("is terminal so it leaves the in-flight exit list") {
            ArkBarkMappers.isTerminalExitLabel(ArkBarkMappers.exitStateLabel(swept)) shouldBe true
        }

        test("is not cancelable after the sweep") {
            ArkBarkMappers.canCancelLabel(ArkBarkMappers.exitStateLabel(swept)) shouldBe false
        }

        test("is not treated as awaiting a push") {
            ArkBarkMappers.needsPushLabel(ArkBarkMappers.exitStateLabel(swept)) shouldBe false
        }
    }

    context("existing exit states stay classified") {
        test("claimed is terminal and not cancelable") {
            val label =
                ArkBarkMappers.exitStateLabel(
                    ExitState.Claimed(1u, "tx", BlockRef(height = 1u, hash = "hash")),
                )
            ArkBarkMappers.isTerminalExitLabel(label) shouldBe true
            ArkBarkMappers.canCancelLabel(label) shouldBe false
        }

        test("awaiting delta is neither terminal nor cancelable") {
            val label = ArkBarkMappers.EXIT_AWAITING_DELTA
            ArkBarkMappers.isTerminalExitLabel(label) shouldBe false
            ArkBarkMappers.canCancelLabel(label) shouldBe false
        }
    }
})
