package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkMovement
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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

        test("zero required means register-on-sight like Bark run_confirm") {
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = 0,
                requiredBoardConfirmations = 0,
            ) shouldBe true
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = null,
                requiredBoardConfirmations = 0,
            ) shouldBe false
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = 0,
                requiredBoardConfirmations = -1,
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

    context("boardProgressLabel") {
        test("caps displayed depth at required") {
            ArkDepositPolicy.boardProgressLabel(12, 3) shouldBe "3/3"
            ArkDepositPolicy.boardProgressLabel(1, 3) shouldBe "1/3"
            ArkDepositPolicy.boardProgressLabel(null, 3).shouldBeNull()
        }

        test("zero required still labels") {
            ArkDepositPolicy.boardProgressLabel(0, 0) shouldBe "0/0"
            ArkDepositPolicy.boardProgressLabel(null, 0).shouldBeNull()
            ArkDepositPolicy.boardProgressLabel(0, -1).shouldBeNull()
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
                    movement(2, createdAt = "2026-02-01T00:00:00Z"),
                )
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = live,
                    previous = previous,
                )
            merged.map { it.id } shouldContainExactly listOf(2, 1)
            // Live wins on fingerprint collision (status/enrichment update).
            merged.first { it.id == 2 }.effectiveBalanceSats shouldBe 1_000L
        }

        test("same id with different content is a distinct payment, not an update") {
            // Bark movement ids are per-DB row ids: any session recreation restarts
            // the id space, so a new payment can reuse an old row's id.
            val live = listOf(movement(1, createdAt = "2026-03-01T00:00:00Z", amount = 2_000L))
            val previous = listOf(movement(1, createdAt = "2026-01-01T00:00:00Z", amount = 1_000L))
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = live,
                    previous = previous,
                )
            merged.map { it.effectiveBalanceSats } shouldContainExactly listOf(2_000L, 1_000L)
        }

        test("status-only update replaces instead of duplicating") {
            val live = listOf(movement(4, status = "completed"))
            val previous = listOf(movement(4, status = "pending"))
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = live,
                    previous = previous,
                )
            merged.map { it.id } shouldContainExactly listOf(4)
            merged.single().status shouldBe "completed"
        }

        test("pending send and its finished settlement are one row") {
            // Settlement mutates the row in place: effective 0→final, output VTXOs
            // filled in, completedAt set. None of that may split the payment.
            val pending =
                ArkMovement(
                    id = 6,
                    status = "pending",
                    subsystemName = "bark",
                    subsystemKind = "arkoor",
                    intendedBalanceSats = -50L,
                    effectiveBalanceSats = 0L,
                    offchainFeeSats = 0L,
                    createdAt = "2026-09-11T17:07:00Z",
                    updatedAt = "2026-09-11T17:07:00Z",
                )
            val finished =
                pending.copy(
                    status = "finished",
                    effectiveBalanceSats = -50L,
                    updatedAt = "2026-09-11T17:08:00Z",
                    completedAt = "2026-09-11T17:08:00Z",
                    outputVtxoIds = listOf("abc123:0"),
                )
            val merged =
                ArkDepositPolicy.mergePreservedMovements(
                    live = listOf(finished),
                    previous = listOf(pending),
                )
            merged.size shouldBe 1
            merged.single().status shouldBe "finished"
            merged.single().effectiveBalanceSats shouldBe -50L
        }

        test("duplicate live rows collapse to the settled copy") {
            // Bark can return the same send twice in one history() call (stale
            // pending ghost + finished row). Painting both crashes LazyColumn on
            // duplicate keys — collapse to one, settled wins either order.
            val pending =
                ArkMovement(
                    id = 4,
                    status = "pending",
                    subsystemName = "bark",
                    subsystemKind = "arkoor",
                    intendedBalanceSats = -50L,
                    effectiveBalanceSats = 0L,
                    offchainFeeSats = 0L,
                    createdAt = "2026-09-11T17:07:14.194815400-07:00",
                    updatedAt = "2026-09-11T17:07:14.194815400-07:00",
                )
            val finished =
                pending.copy(
                    status = "finished",
                    effectiveBalanceSats = -50L,
                    updatedAt = "2026-09-11T17:08:00Z",
                    completedAt = "2026-09-11T17:08:00Z",
                    outputVtxoIds = listOf("abc123:0"),
                )
            listOf(listOf(pending, finished), listOf(finished, pending)).forEach { live ->
                val merged =
                    ArkDepositPolicy.mergePreservedMovements(
                        live = live,
                        previous = emptyList(),
                    )
                merged.size shouldBe 1
                merged.single().status shouldBe "finished"
            }
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

    context("causal ordering") {
        val fundingTxid = "a".repeat(64)

        fun send(
            id: Int,
            createdAt: String,
        ) = ArkMovement(
            id = id,
            status = "pending",
            subsystemName = "bark",
            subsystemKind = "offboard",
            intendedBalanceSats = -5_450L,
            effectiveBalanceSats = -5_450L,
            offchainFeeSats = 0L,
            createdAt = createdAt,
            updatedAt = createdAt,
            onchainTxids = listOf(fundingTxid),
        )

        fun receive(
            id: Int,
            createdAt: String,
        ) = ArkMovement(
            id = id,
            status = "pending",
            subsystemName = "Bitcoin",
            subsystemKind = "board",
            intendedBalanceSats = 5_000L,
            effectiveBalanceSats = 5_000L,
            offchainFeeSats = 0L,
            createdAt = createdAt,
            updatedAt = createdAt,
            onchainTxids = listOf(fundingTxid),
        )

        test("self-transfer receive sorts above its send despite an older stamp") {
            // Bark can stamp the send movement later than the deposit's first
            // sighting; wall-clock order would invert the causal pair.
            val sendRow = send(id = 4, createdAt = "2026-09-11T19:04:50Z")
            val receiveRow = receive(id = -7, createdAt = "2026-09-11T19:04:10Z")
            val sorted =
                ArkDepositPolicy.sortMovementsChronologically(listOf(sendRow, receiveRow))
            sorted.map { it.id } shouldContainExactly listOf(-7, 4)
        }

        test("unlinked rows keep pure time order") {
            val sendRow = send(id = 4, createdAt = "2026-09-11T19:04:50Z")
            val stranger =
                receive(id = -7, createdAt = "2026-09-11T19:04:10Z").copy(onchainTxids = emptyList())
            val sorted =
                ArkDepositPolicy.sortMovementsChronologically(listOf(sendRow, stranger))
            sorted.map { it.id } shouldContainExactly listOf(4, -7)
        }
    }

    context("movementUnionKey") {
        fun movement(
            id: Int,
            createdAt: String = "2026-01-01T00:00:00Z",
            amount: Long = 1_000L,
            status: String = "completed",
        ) = ArkMovement(
            id = id,
            status = status,
            subsystemName = "bark",
            subsystemKind = "arkoor",
            intendedBalanceSats = amount,
            effectiveBalanceSats = amount,
            offchainFeeSats = 0L,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        test("stable across status, label and address enrichment") {
            val base = movement(5)
            val updated =
                base.copy(
                    status = "pending",
                    label = "groceries",
                    sentToAddresses = listOf("ark1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"),
                    updatedAt = "2026-01-02T00:00:00Z",
                    completedAt = "2026-01-02T00:00:00Z",
                )
            ArkDepositPolicy.movementUnionKey(base) shouldBe ArkDepositPolicy.movementUnionKey(updated)
        }

        test("differs across payments sharing an id") {
            val first = movement(5, createdAt = "2026-01-01T00:00:00Z", amount = 1_000L)
            val second = movement(5, createdAt = "2026-03-01T00:00:00Z", amount = 2_000L)
            ArkDepositPolicy.movementUnionKey(first) shouldNotBe ArkDepositPolicy.movementUnionKey(second)
        }

        test("distinctPaintedMovements collapses poisoned paints, settled wins") {
            val pending = movement(5, status = "pending")
            val finished = movement(5, status = "finished")
            val other = movement(9, createdAt = "2026-01-02T00:00:00Z")
            val painted = ArkDepositPolicy.distinctPaintedMovements(listOf(pending, other, finished))
            painted.map { it.id } shouldContainExactly listOf(9, 5)
            painted.single { it.id == 5 }.status shouldBe "finished"
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
