package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class MaxSendAmountFinderTest : FunSpec({

    context("findMaxExactSendAmount") {
        test("returns the highest sendable exact amount") {
            runTest {
                val maxAmount =
                    findMaxExactSendAmount(upperBoundSats = 100_000L) { candidate ->
                        candidate <= 39_480L
                    }

                maxAmount shouldBe 39_480L
            }
        }

        test("returns zero when no positive amount is sendable") {
            runTest {
                val maxAmount =
                    findMaxExactSendAmount(upperBoundSats = 100_000L) { false }

                maxAmount shouldBe 0L
            }
        }

        test("returns zero for a non-positive upper bound") {
            runTest {
                findMaxExactSendAmount(upperBoundSats = 0L) { true } shouldBe 0L
            }
        }
    }

    context("findMinimumFundingAmount") {
        test("returns the smallest funding amount that satisfies the net target") {
            val fundingAmount =
                findMinimumFundingAmount(
                    targetNetAmountSats = 25_000L,
                    startingFundingAmountSats = 25_000L,
                ) { funding ->
                    funding - 500L
                }

            fundingAmount shouldBe 25_500L
        }

        test("returns the starting amount when it already satisfies the target") {
            val fundingAmount =
                findMinimumFundingAmount(
                    targetNetAmountSats = 25_000L,
                    startingFundingAmountSats = 25_000L,
                ) { funding ->
                    funding
                }

            fundingAmount shouldBe 25_000L
        }
    }
})
