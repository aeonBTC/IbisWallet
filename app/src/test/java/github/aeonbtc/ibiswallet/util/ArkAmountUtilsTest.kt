package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkAmountUtilsTest : FunSpec({
    test("sats parses plain and comma-grouped") {
        ArkAmountUtils.parseAmountToSats("1000", useSats = true) shouldBe 1000L
        ArkAmountUtils.parseAmountToSats("1,000", useSats = true) shouldBe 1000L
        ArkAmountUtils.parseAmountToSats("1 000", useSats = true) shouldBe 1000L
    }

    test("sats rejects zero negative blank overflow") {
        ArkAmountUtils.parseAmountToSats("0", useSats = true) shouldBe null
        ArkAmountUtils.parseAmountToSats("-5", useSats = true) shouldBe null
        ArkAmountUtils.parseAmountToSats("", useSats = true) shouldBe null
        ArkAmountUtils.parseAmountToSats("1e3", useSats = true) shouldBe null
    }

    test("btc converts with 8dp and guards overflow") {
        ArkAmountUtils.parseAmountToSats("0.00000001", useSats = false) shouldBe 1L
        ArkAmountUtils.parseAmountToSats("1.0", useSats = false) shouldBe 100_000_000L
        ArkAmountUtils.parseAmountToSats("1,000", useSats = false) shouldBe 100_000_000_000L
        ArkAmountUtils.parseAmountToSats("100000000000", useSats = false) shouldBe null
    }

    test("usd converts via btc price") {
        ArkAmountUtils.parseAmountToSats("100", useSats = false, isUsdMode = true, btcPrice = 100_000.0) shouldBe 100_000L
        ArkAmountUtils.parseAmountToSats("0", useSats = false, isUsdMode = true, btcPrice = 100_000.0) shouldBe null
        ArkAmountUtils.parseAmountToSats("10", useSats = false, isUsdMode = true, btcPrice = null) shouldBe null
    }

    test("filter truncates invalid tails") {
        ArkAmountUtils.filterAmountInput("1,000", useSats = true, isUsdMode = false) shouldBe "1"
        ArkAmountUtils.filterAmountInput("12.345", useSats = false, isUsdMode = true) shouldBe "12.34"
    }
})
