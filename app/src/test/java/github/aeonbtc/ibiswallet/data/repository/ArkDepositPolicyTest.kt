package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ArkDepositPolicyTest : FunSpec({

    context("depositDepthConfirmations") {
        test("board zero does not hide funding confs") {
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = 0,
                fundingConfirmations = 12,
            ) shouldBe 12
        }

        test("prefers larger of board and funding") {
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = 2,
                fundingConfirmations = 12,
            ) shouldBe 12
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = 5,
                fundingConfirmations = 2,
            ) shouldBe 5
        }

        test("null board falls through to funding") {
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = null,
                fundingConfirmations = 7,
            ) shouldBe 7
        }

        test("both zero yields zero not null") {
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = 0,
                fundingConfirmations = 0,
            ) shouldBe 0
        }

        test("both null yields null") {
            ArkDepositPolicy.depositDepthConfirmations(
                boardConfirmations = null,
                fundingConfirmations = null,
            ).shouldBeNull()
        }
    }

    context("progressConfirmations") {
        test("pre-board uses funding depth") {
            ArkDepositPolicy.progressConfirmations(
                boardTxid = null,
                boardConfirmations = 0,
                fundingConfirmations = 12,
            ) shouldBe 12
        }

        test("post-board uses board confs even when zero") {
            ArkDepositPolicy.progressConfirmations(
                boardTxid = "a".repeat(64),
                boardConfirmations = 0,
                fundingConfirmations = 12,
            ) shouldBe 0
            ArkDepositPolicy.progressConfirmations(
                boardTxid = "a".repeat(64),
                boardConfirmations = 2,
                fundingConfirmations = 12,
            ) shouldBe 2
        }
    }

    context("boardConfirmationsMet") {
        test("uses board confs only not funding depth") {
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = 0,
                requiredBoardConfirmations = 3,
            ) shouldBe false
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = 3,
                requiredBoardConfirmations = 3,
            ) shouldBe true
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = null,
                requiredBoardConfirmations = 3,
            ) shouldBe false
        }
    }

    context("isBelowMinBoardAmount") {
        test("null or non-positive min is never below") {
            ArkDepositPolicy.isBelowMinBoardAmount(1L, null) shouldBe false
            ArkDepositPolicy.isBelowMinBoardAmount(1L, 0L) shouldBe false
            ArkDepositPolicy.isBelowMinBoardAmount(1L, -1L) shouldBe false
        }

        test("zero amount is not below") {
            ArkDepositPolicy.isBelowMinBoardAmount(0L, 50_000L) shouldBe false
        }

        test("strictly below min is true") {
            ArkDepositPolicy.isBelowMinBoardAmount(7_000L, 50_000L) shouldBe true
            ArkDepositPolicy.isBelowMinBoardAmount(49_999L, 50_000L) shouldBe true
        }

        test("at or above min is false") {
            ArkDepositPolicy.isBelowMinBoardAmount(50_000L, 50_000L) shouldBe false
            ArkDepositPolicy.isBelowMinBoardAmount(50_001L, 50_000L) shouldBe false
        }
    }

    context("isStuckBelowMinBoard") {
        test("pending board is never stuck") {
            ArkDepositPolicy.isStuckBelowMinBoard(
                onchainConfirmedSats = 7_000L,
                pendingBoardSats = 1L,
                minBoardAmountSats = 50_000L,
            ) shouldBe false
        }

        test("confirmed below min with no pending board is stuck") {
            ArkDepositPolicy.isStuckBelowMinBoard(
                onchainConfirmedSats = 7_000L,
                pendingBoardSats = 0L,
                minBoardAmountSats = 50_000L,
            ) shouldBe true
        }

        test("confirmed at min is not stuck") {
            ArkDepositPolicy.isStuckBelowMinBoard(
                onchainConfirmedSats = 50_000L,
                pendingBoardSats = 0L,
                minBoardAmountSats = 50_000L,
            ) shouldBe false
        }
    }

    context("shortfallToMinBoard") {
        test("returns remaining sats when below min") {
            ArkDepositPolicy.shortfallToMinBoard(7_000L, 50_000L) shouldBe 43_000L
        }

        test("null when at or above min or unknown min") {
            ArkDepositPolicy.shortfallToMinBoard(50_000L, 50_000L).shouldBeNull()
            ArkDepositPolicy.shortfallToMinBoard(7_000L, null).shouldBeNull()
            ArkDepositPolicy.shortfallToMinBoard(0L, 50_000L).shouldBeNull()
        }
    }

    context("boardProgressLabel") {
        test("caps displayed depth at required") {
            ArkDepositPolicy.boardProgressLabel(12, 3) shouldBe "3/3"
            ArkDepositPolicy.boardProgressLabel(1, 3) shouldBe "1/3"
            ArkDepositPolicy.boardProgressLabel(null, 3).shouldBeNull()
        }
    }

    context("resolveOnchainBuckets") {
        test("live bark balance wins") {
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 50_000L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 10_000L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 99_000L,
                    esploraFundingConfirmations = 12,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                )
            buckets.confirmedSats shouldBe 50_000L
            buckets.pendingSats shouldBe 0L
        }

        test("pending board clears onchain paint") {
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 50_000L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 49_000L,
                    esploraAmountSats = 50_000L,
                    esploraFundingConfirmations = 12,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                )
            buckets.totalSats shouldBe 0L
        }

        test("spendable vtxo with unspent esplora utxo keeps onchain paint") {
            // Self-fund: still have VTXOs + new unboarded deposit UTXO.
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 0L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 10_000L,
                    esploraFundingConfirmations = 1,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                    spendableSats = 50_000L,
                )
            buckets.confirmedSats shouldBe 10_000L
            buckets.pendingSats shouldBe 0L
        }

        test("spendable vtxo clears stale previous paint without esplora utxo") {
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 50_000L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 0L,
                    esploraFundingConfirmations = null,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                    spendableSats = 50_000L,
                )
            buckets.totalSats shouldBe 0L
        }

        test("preserves previous when live zero and not yet spendable") {
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 40_000L,
                    previousPendingSats = 1_000L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 0L,
                    esploraFundingConfirmations = null,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                    spendableSats = 0L,
                )
            buckets.confirmedSats shouldBe 40_000L
            buckets.pendingSats shouldBe 1_000L
        }

        test("esplora inbound when bark and previous zero") {
            val confirmed =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 0L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 25_000L,
                    esploraFundingConfirmations = 12,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                )
            confirmed.confirmedSats shouldBe 25_000L
            confirmed.pendingSats shouldBe 0L

            val unconfirmed =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 0L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 25_000L,
                    esploraFundingConfirmations = 0,
                    onchainWalletPresent = true,
                    preservePreviousWhenLiveZero = true,
                )
            unconfirmed.confirmedSats shouldBe 0L
            unconfirmed.pendingSats shouldBe 25_000L
        }

        test("no onchain wallet forces zero") {
            val buckets =
                ArkDepositPolicy.resolveOnchainBuckets(
                    liveConfirmedSats = 0L,
                    livePendingSats = 0L,
                    previousConfirmedSats = 10_000L,
                    previousPendingSats = 0L,
                    pendingBoardSats = 0L,
                    esploraAmountSats = 10_000L,
                    esploraFundingConfirmations = 12,
                    onchainWalletPresent = false,
                    preservePreviousWhenLiveZero = true,
                )
            buckets.totalSats shouldBe 0L
        }
    }

    context("confirmationCount") {
        test("unconfirmed is zero") {
            ArkDepositPolicy.confirmationCount(
                confirmed = false,
                blockHeight = 100,
                tipHeight = 112,
            ) shouldBe 0
        }

        test("confirmed with tip yields depth") {
            ArkDepositPolicy.confirmationCount(
                confirmed = true,
                blockHeight = 100,
                tipHeight = 111,
            ) shouldBe 12
        }

        test("confirmed without tip is at least one") {
            ArkDepositPolicy.confirmationCount(
                confirmed = true,
                blockHeight = 100,
                tipHeight = null,
            ) shouldBe 1
        }
    }

    context("mergePreservedMovements") {
        fun movement(
            id: Int,
            createdAt: String = "2026-01-01T00:00:00Z",
            subsystemName: String = "Lightning",
            subsystemKind: String = "receive",
            amount: Long = 1_000L,
            status: String = "completed",
        ) = ArkMovement(
            id = id,
            status = status,
            subsystemName = subsystemName,
            subsystemKind = subsystemKind,
            intendedBalanceSats = amount,
            effectiveBalanceSats = amount,
            offchainFeeSats = 0L,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        test("empty live keeps previous history") {
            val previous = listOf(movement(7))
            ArkDepositPolicy.mergePreservedMovements(
                live = emptyList(),
                previous = previous,
            ) shouldContainExactly previous
        }

        test("live empty previous empty stays empty") {
            ArkDepositPolicy.mergePreservedMovements(
                live = emptyList(),
                previous = emptyList(),
            ) shouldContainExactly emptyList()
        }

        test("union preserves missing prior rows") {
            val live = listOf(movement(2, createdAt = "2026-02-01T00:00:00Z"))
            val previous =
                listOf(
                    movement(1, createdAt = "2026-01-01T00:00:00Z"),
                    movement(2, createdAt = "2026-02-01T00:00:00Z", amount = 999L),
                )
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = live,
                    previous = previous,
                )
            merged.map { it.id } shouldContainExactly listOf(2, 1)
            // Live wins on id collision.
            merged.first { it.id == 2 }.effectiveBalanceSats shouldBe 1_000L
        }

        test("drops synthetic pending deposit from previous when live omits it") {
            val pending =
                movement(
                    id = ArkDepositPolicy.PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID,
                    subsystemName = "Bitcoin",
                    subsystemKind = "board",
                    amount = 50_000L,
                )
            val board =
                movement(
                    id = 9,
                    subsystemName = "bark",
                    subsystemKind = "board",
                    amount = 50_000L,
                )
            ArkDepositPolicy.mergePreservedMovements(
                live = listOf(board),
                previous = listOf(pending),
            ).map { it.id } shouldContainExactly listOf(9)
        }

        test("keeps live synthetic pending alongside unrelated board history") {
            val pending =
                movement(
                    id = ArkDepositPolicy.PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID,
                    subsystemName = "Bitcoin",
                    subsystemKind = "board",
                    amount = 10_000L,
                )
            val oldBoard =
                movement(
                    id = 9,
                    subsystemName = "bark",
                    subsystemKind = "board",
                    amount = 50_000L,
                )
            val outbound =
                movement(
                    id = 11,
                    subsystemName = "bark",
                    subsystemKind = "offboard",
                    amount = -10_973L,
                )
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = listOf(pending, outbound, oldBoard),
                    previous = listOf(oldBoard),
                )
            merged.map { it.id }.toSet() shouldBe
                setOf(
                    ArkDepositPolicy.PENDING_ONCHAIN_DEPOSIT_MOVEMENT_ID,
                    11,
                    9,
                )
        }

        test("pending does not float above newer movements") {
            val olderPending =
                movement(
                    id = 1,
                    createdAt = "2026-08-05T14:26:00Z",
                    status = "pending",
                )
            val newerSwap =
                movement(
                    id = 2,
                    createdAt = "2026-08-05T15:20:00Z",
                    status = "completed",
                    amount = -1_510L,
                )
            val olderReceive =
                movement(
                    id = 3,
                    createdAt = "2026-08-05T14:21:00Z",
                    status = "completed",
                    amount = 1_700L,
                )
            // Live order mimics Bark pinning pending first.
            val live = listOf(olderPending, newerSwap, olderReceive)
            ArkDepositPolicy.sortMovementsChronologically(live).map { it.id } shouldContainExactly
                listOf(2, 1, 3)
            ArkDepositPolicy.mergePreservedMovements(
                live = live,
                previous = emptyList(),
            ).map { it.id } shouldContainExactly listOf(2, 1, 3)
        }
    }

    context("isNoiseArkMovement") {
        fun movement(
            intended: Long,
            effective: Long = 0L,
            status: String = "failed",
            subsystemName: String = "Lightning",
            subsystemKind: String = "send",
        ) = ArkMovement(
            id = 1,
            status = status,
            subsystemName = subsystemName,
            subsystemKind = subsystemKind,
            intendedBalanceSats = intended,
            effectiveBalanceSats = effective,
            offchainFeeSats = 0L,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )

        test("keeps failed lightning send with intended amount") {
            ArkDepositPolicy.isNoiseArkMovement(movement(intended = -12_345L)) shouldBe false
        }

        test("hides failed unpaid invoice stub") {
            ArkDepositPolicy.isNoiseArkMovement(
                movement(
                    intended = 5_000L,
                    subsystemKind = "receive",
                ),
            ) shouldBe true
        }

        test("hides expired unpaid invoice with no intended amount") {
            ArkDepositPolicy.isNoiseArkMovement(
                movement(
                    intended = 0L,
                    status = "expired",
                    subsystemKind = "receive",
                ),
            ) shouldBe true
        }
    }
})
