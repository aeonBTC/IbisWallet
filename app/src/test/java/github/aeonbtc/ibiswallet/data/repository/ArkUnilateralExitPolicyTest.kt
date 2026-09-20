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
        test("on-chain wallet missing refuses start") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("v1"),
                    onchainWalletPresent = false,
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.Error>()
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.ONCHAIN_WALLET_UNAVAILABLE
        }

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

        test("selected start refuses when a requested vtxo is pending refresh") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("ok", "pending"),
                    spendableVtxoIds = listOf("ok", "pending"),
                    excludedVtxoIds = listOf("pending"),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.PENDING_REFRESH
        }

        test("selected start proceeds for non-pending ids while others refresh") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = listOf("ok"),
                    spendableVtxoIds = listOf("ok", "pending"),
                    excludedVtxoIds = listOf("pending"),
                )
            val selected = plan as ArkUnilateralExitPolicy.StartExitPlan.Selected
            selected.vtxoIds shouldBe listOf("ok")
        }

        test("entire wallet refuses when any spendable vtxo is pending refresh") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("ok", "pending"),
                    excludedVtxoIds = listOf("pending"),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.PENDING_REFRESH
        }

        test("entire wallet proceeds when pending ids are already gone") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("ok"),
                    excludedVtxoIds = listOf("gone"),
                )
            plan.shouldBeInstanceOf<ArkUnilateralExitPolicy.StartExitPlan.EntireWallet>()
        }

        test("empty-selection fallback refuses when a spendable vtxo is pending refresh") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = false,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("s1", "pending"),
                    excludedVtxoIds = listOf("pending"),
                )
            (plan as ArkUnilateralExitPolicy.StartExitPlan.Error).reason shouldBe
                ArkUnilateralExitPolicy.StartExitError.PENDING_REFRESH
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

        test("claimed and canceled exits are not pending") {
            ArkUnilateralExitPolicy.computeHasPendingExits(
                barkHasPending = false,
                exitStates = listOf("Claimed", "Canceled", "VtxoAlreadySpent"),
            ) shouldBe false
        }

        test("processing exit is pending even if Bark hasPending is false") {
            ArkUnilateralExitPolicy.computeHasPendingExits(
                barkHasPending = false,
                exitStates = listOf("Claimed", "Processing"),
            ) shouldBe true
        }

        test("delete must stay blocked on Start and Claimable states") {
            // Guards deleteWalletData fail-closed path: any active or claimable
            // exit blocks wallet delete (seed alone cannot recover exits).
            ArkUnilateralExitPolicy.computeHasPendingExits(
                barkHasPending = false,
                exitStates = listOf("Start"),
            ) shouldBe true
            ArkUnilateralExitPolicy.computeHasPendingExits(
                barkHasPending = false,
                exitStates = listOf("Claimable"),
            ) shouldBe true
            ArkUnilateralExitPolicy.computeHasPendingExits(
                barkHasPending = true,
                exitStates = emptyList(),
            ) shouldBe true
        }

        test("CPFP weights prefer exit-list weights over spendable") {
            ArkUnilateralExitPolicy.cpfpWeightsForExits(
                exitVtxoWeightsWu = listOf(400L),
                spendableWeightsWu = listOf(999L),
            ) shouldBe listOf(400L)
        }

        test("CPFP weights fall back to spendable when exit list has none") {
            ArkUnilateralExitPolicy.cpfpWeightsForExits(
                exitVtxoWeightsWu = listOf(0L),
                spendableWeightsWu = listOf(400L),
            ) shouldBe listOf(400L)
        }

        test("ids to lock before start are the spendable set for entire wallet") {
            val plan =
                ArkUnilateralExitPolicy.planStartExit(
                    walletLoaded = true,
                    entireWallet = true,
                    requestedVtxoIds = emptyList(),
                    spendableVtxoIds = listOf("a", "b"),
                )
            ArkUnilateralExitPolicy.idsToLockBeforeStart(plan, listOf("a", "b")) shouldBe
                listOf("a", "b")
        }

        test("mergeExitWeights keeps previous weight when live is zero") {
            ArkUnilateralExitPolicy.mergeExitWeights(
                liveWeightsById = mapOf("x" to 0L),
                previousWeightsById = mapOf("x" to 400L),
                exitIds = listOf("x"),
            ) shouldBe mapOf("x" to 400L)
        }

        test("canCancelPendingExits only for start/processing") {
            ArkUnilateralExitPolicy.canCancelPendingExits(
                listOf("Start", "AwaitingDelta"),
            ) shouldBe true
            ArkUnilateralExitPolicy.canCancelPendingExits(
                listOf("Claimable", "Canceled"),
            ) shouldBe false
            ArkUnilateralExitPolicy.vtxosEligibleForCancel(
                mapOf("a" to "Start", "b" to "Claimable", "c" to "Processing(AwaitingCpfpBroadcast)"),
            ) shouldBe listOf("a", "c")
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

    context("estimateExitCompletionBlocks") {
        test("single-level exit adds depth, ASP delay, and claim confirmation") {
            ArkUnilateralExitPolicy.estimateExitCompletionBlocks(
                maxExitDepth = 1,
                exitDeltaBlocks = 144,
            ) shouldBe 146
        }

        test("deeper exit tree adds one block per level") {
            ArkUnilateralExitPolicy.estimateExitCompletionBlocks(
                maxExitDepth = 3,
                exitDeltaBlocks = 144,
            ) shouldBe 148
        }

        test("unknown ASP delay yields no estimate") {
            ArkUnilateralExitPolicy.estimateExitCompletionBlocks(
                maxExitDepth = 1,
                exitDeltaBlocks = null,
            ) shouldBe null
        }

        test("non-positive ASP delay yields no estimate") {
            ArkUnilateralExitPolicy.estimateExitCompletionBlocks(
                maxExitDepth = 1,
                exitDeltaBlocks = 0,
            ) shouldBe null
        }

        test("negative depth is coerced to zero") {
            ArkUnilateralExitPolicy.estimateExitCompletionBlocks(
                maxExitDepth = -2,
                exitDeltaBlocks = 144,
            ) shouldBe 145
        }
    }

    context("estimateCpfpFeeSats") {
        test("budgets twice the exit weight at the given rate") {
            // 800 WU = 200 vB parent chain; doubled for the CPFP package.
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(400L, 400L),
                feeRateSatPerVb = 5L,
            ) shouldBe 2_000L
        }

        test("rounds fractional sats up") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(401L),
                feeRateSatPerVb = 5L,
            ) shouldBe 1_003L
        }

        test("clamps runaway fee rates") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(400L),
                feeRateSatPerVb = 500L,
            ) shouldBe 40_000L
        }

        test("clamps zero rate up to the one-sat floor") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(400L),
                feeRateSatPerVb = 0L,
            ) shouldBe 200L
        }

        test("empty or non-positive weights yield no estimate") {
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = emptyList(),
                feeRateSatPerVb = 5L,
            ) shouldBe null
            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                exitTxWeightsWu = listOf(0L, -10L),
                feeRateSatPerVb = 5L,
            ) shouldBe null
        }

        test("required adds the change dust buffer") {
            ArkUnilateralExitPolicy.estimateCpfpRequiredSats(
                exitTxWeightsWu = listOf(400L, 400L),
                feeRateSatPerVb = 5L,
            ) shouldBe 2_000L + ArkUnilateralExitPolicy.CPFP_CHANGE_DUST_SATS
        }
    }

    context("emergencyExitFeeQuote") {
        test("maps Bark ULong legs onto the UI quote") {
            val quote =
                ArkUnilateralExitPolicy.mapEmergencyExitFeeQuote(
                    vtxoIds = listOf(" vtxo1 ", "", "vtxo1"),
                    broadcastFeeSats = 4_576UL,
                    claimFeeSats = 645UL,
                    totalFeeSats = 5_221UL,
                    feeRateSatPerVb = 3UL,
                    txsToBroadcast = 3UL,
                    fundable = false,
                )
            quote.vtxoIds shouldBe listOf("vtxo1")
            quote.broadcastFeeSats shouldBe 4_576L
            quote.claimFeeSats shouldBe 645L
            quote.totalFeeSats shouldBe 5_221L
            quote.feeRateSatPerVb shouldBe 3L
            quote.txsToBroadcast shouldBe 3L
            quote.fundable shouldBe false
            quote.isQuoting shouldBe false
        }

        test("clamps overflowing ULong values to Long.MAX_VALUE") {
            ArkUnilateralExitPolicy.clampBarkAmount(ULong.MAX_VALUE) shouldBe Long.MAX_VALUE
            ArkUnilateralExitPolicy.clampBarkAmount(1_000UL) shouldBe 1_000L
        }

        test("unfundable landed quote blocks start") {
            val blocked =
                ArkUnilateralExitPolicy.mapEmergencyExitFeeQuote(
                    vtxoIds = listOf("vtxo1"),
                    broadcastFeeSats = 5_000UL,
                    claimFeeSats = 500UL,
                    totalFeeSats = 5_500UL,
                    feeRateSatPerVb = 5UL,
                    txsToBroadcast = 2UL,
                    fundable = false,
                )
            ArkUnilateralExitPolicy.isEmergencyExitBlockedByQuote(blocked) shouldBe true
            ArkUnilateralExitPolicy.isEmergencyExitBlockedByQuote(null) shouldBe false
            ArkUnilateralExitPolicy.isEmergencyExitBlockedByQuote(blocked.copy(isQuoting = true)) shouldBe false
            ArkUnilateralExitPolicy.isEmergencyExitBlockedByQuote(blocked.copy(fundable = true)) shouldBe false
        }

        test("broadcast shortfall is fail-open on missing quote") {
            val quote =
                ArkUnilateralExitPolicy.mapEmergencyExitFeeQuote(
                    vtxoIds = listOf("vtxo1"),
                    broadcastFeeSats = 5_000UL,
                    claimFeeSats = 500UL,
                    totalFeeSats = 5_500UL,
                    feeRateSatPerVb = 5UL,
                    txsToBroadcast = 2UL,
                    fundable = true,
                )
            ArkUnilateralExitPolicy.emergencyExitBroadcastShortfallSats(quote, 2_000L) shouldBe 3_000L
            ArkUnilateralExitPolicy.emergencyExitBroadcastShortfallSats(quote, 9_000L) shouldBe 0L
            ArkUnilateralExitPolicy.emergencyExitBroadcastShortfallSats(null, 0L) shouldBe null
            ArkUnilateralExitPolicy.emergencyExitBroadcastShortfallSats(
                quote.copy(isQuoting = true),
                0L,
            ) shouldBe null
        }
    }

    context("resolveClaimLedgerAmount") {
        test("prefers the live execute-time amount") {
            ArkUnilateralExitPolicy.resolveClaimLedgerAmount(
                liveAmountSats = 47_400L,
                previewAmountSats = 47_000L,
            ) shouldBe 47_400L
        }

        test("falls back to the preview amount when live is missing or zero") {
            ArkUnilateralExitPolicy.resolveClaimLedgerAmount(
                liveAmountSats = null,
                previewAmountSats = 47_000L,
            ) shouldBe 47_000L
            ArkUnilateralExitPolicy.resolveClaimLedgerAmount(
                liveAmountSats = 0L,
                previewAmountSats = 47_000L,
            ) shouldBe 47_000L
        }

        test("zero only when both are missing or non-positive") {
            ArkUnilateralExitPolicy.resolveClaimLedgerAmount(
                liveAmountSats = null,
                previewAmountSats = null,
            ) shouldBe 0L
            ArkUnilateralExitPolicy.resolveClaimLedgerAmount(
                liveAmountSats = 0L,
                previewAmountSats = -5L,
            ) shouldBe 0L
        }
    }

    context("txidFromSignedHex") {
        test("genesis coinbase bytes hash to the known txid") {
            val genesisHex =
                "01000000010000000000000000000000000000000000000000000000000000000000000000" +
                    "ffffffff4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e" +
                    "63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062" +
                    "616e6b73ffffffff0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a" +
                    "828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a" +
                    "4c702b6bf11d5fac00000000"
            ArkUnilateralExitPolicy.txidFromSignedHex(genesisHex) shouldBe
                "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
        }

        test("result is 64 lowercase hex chars and deterministic") {
            val first = ArkUnilateralExitPolicy.txidFromSignedHex("abcd")
            val second = ArkUnilateralExitPolicy.txidFromSignedHex("ABCD")
            first shouldBe second
            first?.length shouldBe 64
            first?.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }

        test("malformed input yields null") {
            ArkUnilateralExitPolicy.txidFromSignedHex("") shouldBe null
            ArkUnilateralExitPolicy.txidFromSignedHex("abc") shouldBe null
            ArkUnilateralExitPolicy.txidFromSignedHex("zz") shouldBe null
            ArkUnilateralExitPolicy.txidFromSignedHex("00ffgg") shouldBe null
        }
    }
})
