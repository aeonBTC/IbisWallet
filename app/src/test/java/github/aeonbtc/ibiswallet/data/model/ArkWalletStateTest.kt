package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkWalletStateTest : FunSpec({
    context("totalSats") {
        test("does not double-count spendable and pending-in-round for the same vtxo sum") {
            val state =
                ArkWalletState(
                    spendableSats = 51_317L,
                    pendingInRoundSats = 51_317L,
                    vtxos =
                        listOf(
                            ArkVtxo(
                                id = "v1",
                                amountSats = 51_317L,
                                expiryHeight = 1_000_000,
                                kind = "board",
                                state = "Locked",
                                exitDepth = 0,
                                exitTxWeightWu = 0L,
                            ),
                        ),
                )
            state.totalSats shouldBe 51_317L
        }

        test("sums disjoint offchain buckets when under vtxo total") {
            val state =
                ArkWalletState(
                    spendableSats = 40_000L,
                    pendingInRoundSats = 10_000L,
                    vtxos =
                        listOf(
                            ArkVtxo(
                                id = "a",
                                amountSats = 40_000L,
                                expiryHeight = 1,
                                kind = "board",
                                state = "Spendable",
                                exitDepth = 0,
                                exitTxWeightWu = 0L,
                            ),
                            ArkVtxo(
                                id = "b",
                                amountSats = 10_000L,
                                expiryHeight = 1,
                                kind = "board",
                                state = "Locked",
                                exitDepth = 0,
                                exitTxWeightWu = 0L,
                            ),
                        ),
                )
            state.totalSats shouldBe 50_000L
        }

        test("includes inbound onchain when no double-count") {
            val state =
                ArkWalletState(
                    spendableSats = 10_000L,
                    onchainConfirmedSats = 5_000L,
                    vtxos =
                        listOf(
                            ArkVtxo(
                                id = "v1",
                                amountSats = 10_000L,
                                expiryHeight = 1,
                                kind = "board",
                                state = "Spendable",
                                exitDepth = 0,
                                exitTxWeightWu = 0L,
                            ),
                        ),
                )
            state.totalSats shouldBe 15_000L
        }
    }

    context("displayBalanceSats") {
        fun movement(
            intended: Long,
            effective: Long,
        ) = ArkMovement(
            id = 1,
            status = "failed",
            subsystemName = "Lightning",
            subsystemKind = "send",
            intendedBalanceSats = intended,
            effectiveBalanceSats = effective,
            offchainFeeSats = 0L,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )

        test("failed lightning send uses intended amount") {
            movement(intended = -12_345L, effective = 0L).displayBalanceSats() shouldBe -12_345L
        }

        test("settled send keeps effective amount") {
            movement(intended = -12_345L, effective = -12_400L).displayBalanceSats() shouldBe -12_400L
        }

        test("zero intended and effective stays zero") {
            movement(intended = 0L, effective = 0L).displayBalanceSats() shouldBe 0L
        }
    }
})
