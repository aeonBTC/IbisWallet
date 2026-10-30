package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.ArkTransferState
import github.aeonbtc.ibiswallet.data.model.SignedBoardFunding
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.cancellation.CancellationException

class ArkSingleTxBoardTest : FunSpec({

    val funding = ArkTransferState.BoardFundingInfo(address = "bc1qfunding", keypairIndex = 7u, expiryHeight = 900_000u)
    val signed =
        SignedBoardFunding(
            signedPsbtBase64 = "cHNidA==",
            txid = "aabbcc",
            feeSats = 200L,
            recipientAmountSats = 50_000L,
        )

    test("no funding info falls back without building") {
        var built = false
        val outcome =
            planSingleTxBoard(
                funding = null,
                build = { built = true; signed },
                register = { _, _ -> Result.success(signed.txid) },
            )
        outcome.shouldBeInstanceOf<SingleTxBoardOutcome.Fallback>()
        built shouldBe false
    }

    test("matching txid registers the signed funding") {
        val outcome =
            planSingleTxBoard(
                funding = funding,
                build = { signed },
                register = { _, _ -> Result.success("AABBCC") },
            )
        (outcome as SingleTxBoardOutcome.Registered).signed shouldBe signed
    }

    test("register receives the exact signed psbt and funding info") {
        var seenPsbt: String? = null
        var seenFunding: ArkTransferState.BoardFundingInfo? = null
        planSingleTxBoard(
            funding = funding,
            build = { signed },
            register = { psbt, info ->
                seenPsbt = psbt
                seenFunding = info
                Result.success(signed.txid)
            },
        )
        seenPsbt shouldBe signed.signedPsbtBase64
        seenFunding shouldBe funding
    }

    test("build failure falls back and keeps the cause") {
        val boom = IllegalStateException("insufficient funds")
        val outcome =
            planSingleTxBoard(
                funding = funding,
                build = { throw boom },
                register = { _, _ -> Result.success(signed.txid) },
            )
        (outcome as SingleTxBoardOutcome.Fallback).cause shouldBe boom
    }

    test("build failure never calls register") {
        var registered = false
        planSingleTxBoard(
            funding = funding,
            build = { throw IllegalStateException("x") },
            register = { _, _ -> registered = true; Result.success(signed.txid) },
        )
        registered shouldBe false
    }

    test("bark rejection falls back and keeps the cause") {
        val outcome =
            planSingleTxBoard(
                funding = funding,
                build = { signed },
                register = { _, _ -> Result.failure(IllegalStateException("expired")) },
            )
        (outcome as SingleTxBoardOutcome.Fallback).cause?.message shouldBe "expired"
    }

    test("txid mismatch fails closed instead of falling back") {
        val outcome =
            planSingleTxBoard(
                funding = funding,
                build = { signed },
                register = { _, _ -> Result.success("ffff") },
            )
        outcome shouldBe SingleTxBoardOutcome.TxidMismatch
    }

    test("cancellation during build is rethrown") {
        shouldThrow<CancellationException> {
            planSingleTxBoard(
                funding = funding,
                build = { throw CancellationException("cancelled") },
                register = { _, _ -> Result.success(signed.txid) },
            )
        }
    }

    test("cancellation during register is rethrown, not turned into a fallback") {
        shouldThrow<CancellationException> {
            planSingleTxBoard(
                funding = funding,
                build = { signed },
                register = { _, _ -> throw CancellationException("cancelled") },
            )
        }
    }
})
