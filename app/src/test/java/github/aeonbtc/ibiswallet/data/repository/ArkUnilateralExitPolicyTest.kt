package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Traces unilateral exit decision path used by Manage VTXOs + ArkRepository:
 * start (selected / entire / spendable fallback) → progress fee clamp → claim prep → claim execute gate.
 */
class ArkUnilateralExitPolicyTest : FunSpec({

    context("planStartExit") {
        test("wallet not loaded") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = false,
                    entireWallet = false,
                    requestedVtxoIds = listOf("a"),
                    spendableVtxoIds = listOf("a"),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.Error>()
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.WALLET_NOT_LOADED
        }

        test("UI entire-wallet button plans entire start when spendable exists") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("v1", "v2"),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.EntireWallet>()
            ArkUnilateralExitPolicy.markEntireWalletInResult(plan) shouldBe true
            ArkUnilateralExitPolicy.resolveStartedVtxoIds(plan, listOf("v1", "v2", "v3")) shouldBe
                listOf("v1", "v2", "v3")
        }

        test("entire wallet with no spendable is error") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = emptyList(),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.NO_SPENDABLE_VTXOS
        }

        test("UI selected start plans those vtxos when spendable") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("sel-a", "sel-b", "sel-a"),
                    spendableVtxoIds = listOf("sel-a", "sel-b", "other"),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.Selected>()
            val selected = plan as ArkUnilateralExitPolicy.StartExitPlan.Selected
            selected.vtxoIds shouldBe listOf("sel-a", "sel-b")
            selected.markEntireWallet shouldBe false
            ArkUnilateralExitPolicy.resolveStartedVtxoIds(plan, emptyList()) shouldBe listOf("sel-a", "sel-b")
        }

        test("selected start intersects with spendable and drops non-spendable") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("spendable", "claimable-stale", "gone"),
                    spendableVtxoIds = listOf("spendable", "other"),
                )
            val selected = plan as ArkUnilateralExitPolicy.StartExitPlan.Selected
            selected.vtxoIds shouldBe listOf("spendable")
        }

        test("selected start with only non-spendable ids is error") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("claimable-only"),
                    spendableVtxoIds = listOf("a", "b"),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.NO_SPENDABLE_VTXOS
        }

        test("empty selection falls back to all spendable and marks entire") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("s1", "s2", "s1"),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.Selected>()
            val selected = plan as ArkUnilateralExitPolicy.StartExitPlan.Selected
            selected.vtxoIds shouldBe listOf("s1", "s2")
            selected.markEntireWallet shouldBe true
        }

        test("empty selection with no spendable is error") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = emptyList(),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.Error>()
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.NO_SPENDABLE_VTXOS
        }

        test("blank requested ids are ignored") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("  ", "ok", ""),
                    spendableVtxoIds = listOf("ok"),
                )
            val selected = plan as ArkUnilateralExitPolicy.StartExitPlan.Selected
            selected.vtxoIds shouldBe listOf("ok")
        }
    }

    context("UI enablement helpers") {
        test("canStartSelectedExit requires non-empty selection that is spendable") {
            ArkUnilateralExitPolicy.canStartSelectedExit(emptyList()) shouldBe false
            ArkUnilateralExitPolicy.canStartSelectedExit(listOf("x")) shouldBe true
            ArkUnilateralExitPolicy.canStartSelectedExit(
                selectedVtxoIds = listOf("claimable-stale"),
                spendableVtxoIds = listOf("spendable"),
            ) shouldBe false
            ArkUnilateralExitPolicy.canStartSelectedExit(
                selectedVtxoIds = listOf("claimable-stale", "spendable"),
                spendableVtxoIds = listOf("spendable"),
            ) shouldBe true
        }

        test("canStartEntireExit requires spendable vtxos") {
            ArkUnilateralExitPolicy.canStartEntireExit(emptyList()) shouldBe false
            ArkUnilateralExitPolicy.canStartEntireExit(listOf("v1")) shouldBe true
        }

        test("canQuoteClaim requires non-blank destination") {
            ArkUnilateralExitPolicy.canQuoteClaim("") shouldBe false
            ArkUnilateralExitPolicy.canQuoteClaim("   ") shouldBe false
            ArkUnilateralExitPolicy.canQuoteClaim("bc1qexample") shouldBe true
        }

        test("shouldShowProgressWithClaimable when both pending and claimable") {
            ArkUnilateralExitPolicy.shouldShowProgressWithClaimable(
                hasPendingExits = true,
                hasClaimableExits = true,
            ) shouldBe true
            ArkUnilateralExitPolicy.shouldShowProgressWithClaimable(
                hasPendingExits = true,
                hasClaimableExits = false,
            ) shouldBe false
            ArkUnilateralExitPolicy.shouldShowProgressWithClaimable(
                hasPendingExits = false,
                hasClaimableExits = true,
            ) shouldBe false
        }
    }

    context("fee rate clamp") {
        test("default is 2 sat/vB") {
            ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB shouldBe 2L
        }

        test("clamps zero and negative to 1") {
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(0) shouldBe 1L
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(-5) shouldBe 1L
        }

        test("preserves positive rates within ceiling") {
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(2) shouldBe 2L
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(42) shouldBe 42L
        }

        test("clamps above max ceiling") {
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(500) shouldBe
                ArkUnilateralExitPolicy.MAX_EXIT_FEE_RATE_SAT_VB
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(
                ArkUnilateralExitPolicy.MAX_EXIT_FEE_RATE_SAT_VB,
            ) shouldBe ArkUnilateralExitPolicy.MAX_EXIT_FEE_RATE_SAT_VB
        }
    }

    context("claim destination validation") {
        test("accepts mainnet bech32 with valid checksum") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(
                "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            ) shouldBe true
        }

        test("accepts legacy P2PKH with valid checksum") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa") shouldBe true
        }

        test("rejects testnet bech32") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(
                "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
            ) shouldBe false
        }

        test("rejects blank and silent-payment-like garbage") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress("") shouldBe false
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress("sp1qqqqqqqqqqqqqqqqqqqqqqq") shouldBe false
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress("not-an-address") shouldBe false
        }

        test("rejects too-short bc1") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress("bc1short") shouldBe false
        }

        test("rejects bad bech32 checksum") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(
                "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5",
            ) shouldBe false
        }

        test("trims whitespace") {
            ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(
                "  bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4  ",
            ) shouldBe true
        }
    }

    context("planClaimPrepare") {
        test("wallet not loaded") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = false,
                    destinationAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    requestedVtxoIds = emptyList(),
                    claimableVtxoIds = listOf("c1"),
                )
            (plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.ClaimPrepareError.WALLET_NOT_LOADED
        }

        test("invalid destination") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = "nope",
                    requestedVtxoIds = emptyList(),
                    claimableVtxoIds = listOf("c1"),
                )
            (plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.ClaimPrepareError.INVALID_DESTINATION
        }

        test("no claimable when both lists empty") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    requestedVtxoIds = emptyList(),
                    claimableVtxoIds = emptyList(),
                )
            (plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.ClaimPrepareError.NO_CLAIMABLE_EXITS
        }

        test("requested ids must be claimable") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = " bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4 ",
                    requestedVtxoIds = listOf("req-1", "not-claimable", "req-2"),
                    claimableVtxoIds = listOf("req-1", "req-2", "wallet-only"),
                    feeRateSatPerVb = 0L,
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.ClaimPreparePlan.Ready>()
            val ready = plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Ready
            ready.vtxoIds shouldBe listOf("req-1", "req-2")
            ready.destinationAddress shouldBe "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
            ready.feeRateSatPerVb shouldBe 1L
        }

        test("requested non-claimable only is error") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    requestedVtxoIds = listOf("stale"),
                    claimableVtxoIds = listOf("c1"),
                )
            (plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.ClaimPrepareError.NO_CLAIMABLE_EXITS
        }

        test("when request empty uses wallet claimable ids") {
            val plan =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    requestedVtxoIds = emptyList(),
                    claimableVtxoIds = listOf("c1", "c2", "c1"),
                    feeRateSatPerVb = 5L,
                )
            val ready = plan as ArkUnilateralExitPolicy.ClaimPreparePlan.Ready
            ready.vtxoIds shouldBe listOf("c1", "c2")
            ready.feeRateSatPerVb shouldBe 5L
        }
    }

    context("planClaimExecute") {
        test("blocks without wallet") {
            ArkUnilateralExitPolicy.planClaimExecute(walletLoaded = false, hasClaimPreview = true) shouldBe
                ArkUnilateralExitPolicy.ClaimExecuteError.WALLET_NOT_LOADED
        }

        test("blocks without prepared claim preview") {
            ArkUnilateralExitPolicy.planClaimExecute(walletLoaded = true, hasClaimPreview = false) shouldBe
                ArkUnilateralExitPolicy.ClaimExecuteError.NOTHING_PREPARED
        }

        test("allows sign+broadcast when wallet and preview present") {
            ArkUnilateralExitPolicy.planClaimExecute(walletLoaded = true, hasClaimPreview = true) shouldBe null
        }
    }

    context("lifecycle flow shape") {
        test("selected start then claim uses shared fee default of 2") {
            val start =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("exit-1"),
                    spendableVtxoIds = listOf("exit-1"),
                ) as ArkUnilateralExitPolicy.StartExitPlan.Selected
            start.vtxoIds shouldBe listOf("exit-1")

            val claim =
                ArkUnilateralExitPolicy.planClaimPrepare(
                    walletLoaded = true,
                    destinationAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    requestedVtxoIds = listOf("exit-1"),
                    claimableVtxoIds = listOf("exit-1"),
                ) as ArkUnilateralExitPolicy.ClaimPreparePlan.Ready
            claim.feeRateSatPerVb shouldBe ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB

            ArkUnilateralExitPolicy.planClaimExecute(true, true) shouldBe null
        }

        test("progress fee path clamps like repository progressExits call") {
            ArkUnilateralExitPolicy.clampExitFeeRateSatPerVb(
                ArkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB,
            ) shouldBe 2L
        }

        test("firstProgressErrorFromMessages returns first non-blank") {
            ArkUnilateralExitPolicy.firstProgressErrorFromMessages(
                listOf(null, "  insufficient fee  ", "other"),
            ) shouldBe "insufficient fee"
        }

        test("needsCpfpFunding detects nested Bark processing state") {
            ArkUnilateralExitPolicy.needsCpfpFunding(
                listOf("Processing(AwaitingCpfpBroadcast)"),
            ) shouldBe true
        }

        test("needsCpfpFunding ignores confirmation states") {
            ArkUnilateralExitPolicy.needsCpfpFunding(
                listOf("Processing"),
            ) shouldBe false
        }

        test("pending exits reserve on-chain funds from auto-board") {
            ArkUnilateralExitPolicy.shouldAutoBoardOnchainFunds(hasPendingExits = true) shouldBe false
        }

        test("on-chain funds auto-board when there are no pending exits") {
            ArkUnilateralExitPolicy.shouldAutoBoardOnchainFunds(hasPendingExits = false) shouldBe true
        }

        test("CPFP estimate mirrors Bark two-times-weight estimate") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(400L, 600L),
                feeRateSatPerVb = 5L,
            ) shouldBe 2_500L
        }

        test("CPFP required funds include non-dust change") {
            ArkUnilateralExitPolicy.estimateCpfpRequiredSats(
                exitTxWeightsWu = listOf(400L, 600L),
                feeRateSatPerVb = 5L,
            ) shouldBe 2_830L
        }

        test("CPFP estimate is unavailable without exit weights") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(listOf(0L, -1L)) shouldBe null
        }

        test("claimable-stale selection cannot start exit") {
            // Regression: selection seeded from claimable ids must not enable start.
            val canStart =
                ArkUnilateralExitPolicy.canStartSelectedExit(
                    selectedVtxoIds = listOf("claimable-vtxo"),
                    spendableVtxoIds = listOf("still-spendable"),
                )
            canStart shouldBe false
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("claimable-vtxo"),
                    spendableVtxoIds = listOf("still-spendable"),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.NO_SPENDABLE_VTXOS
        }
    }
})
