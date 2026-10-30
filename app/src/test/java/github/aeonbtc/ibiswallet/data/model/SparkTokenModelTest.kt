package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class SparkTokenModelTest : FunSpec({

    test("USDT detection is case-insensitive on ticker") {
        SparkToken.fromMetadata("btkn1abc", "USDT", "Tether USD", 6).isUsdt shouldBe true
        SparkToken.fromMetadata("btkn1abc", "usdt", "Tether USD", 6).isUsdt shouldBe true
        SparkToken.fromMetadata("btkn1abc", "USDB", "Brale USD", 6).isUsdt shouldBe false
    }

    test("unknown token falls back to truncated identifier ticker") {
        val token = SparkToken.unknown("btkn1abcdefgh")
        token.ticker shouldBe "btkn1abc…"
        token.isUsdt shouldBe false
    }

    test("token amount parses decimal input into base units") {
        parseSparkTokenAmountToBaseUnits("12.50", 6) shouldBe BigInteger("12500000")
        parseSparkTokenAmountToBaseUnits("0.000001", 6) shouldBe BigInteger.ONE
    }

    test("token amount parsing rejects invalid input") {
        parseSparkTokenAmountToBaseUnits("", 6) shouldBe null
        parseSparkTokenAmountToBaseUnits("abc", 6) shouldBe null
        parseSparkTokenAmountToBaseUnits("0", 6) shouldBe null
        parseSparkTokenAmountToBaseUnits("-5", 6) shouldBe null
        parseSparkTokenAmountToBaseUnits("1.2.3", 6) shouldBe null
    }

    test("token amount formats Liquid-USDt style with two decimals") {
        val usdt = SparkToken.fromMetadata("btkn1abc", "USDT", "Tether USD", 6)
        formatSparkTokenAmount(BigInteger("12500000"), usdt) shouldBe "12.50 USDT"
        formatSparkTokenAmount(BigInteger.ZERO, usdt) shouldBe "0.00 USDT"
    }

    test("token input filter caps fractional digits at decimals") {
        filterSparkTokenAmountInput("12.3456789", 6) shouldBe "12.345678"
        filterSparkTokenAmountInput("12.34", 6) shouldBe "12.34"
        filterSparkTokenAmountInput("12,50", 6) shouldBe "12.50"
    }

    test("wallet state exposes token balances and USDT helpers") {
        val usdt = SparkToken.fromMetadata("btkn1usdt", "USDT", "Tether USD", 6)
        val state = SparkWalletState(
            tokenBalances = listOf(SparkTokenBalance(usdt, BigInteger("1000000"))),
            payments = listOf(
                SparkPayment(
                    id = "p1",
                    type = "RECEIVE",
                    status = "COMPLETED",
                    amountSats = 0L,
                    feeSats = 0L,
                    timestamp = 0L,
                    method = "TOKEN",
                    tokenIdentifier = "btkn1usdt",
                    tokenTicker = "USDT",
                    tokenDecimals = 6,
                    tokenAmount = "1000000",
                ),
            ),
        )
        state.hasNonBtcTokens shouldBe true
        state.balanceForToken("btkn1usdt") shouldBe BigInteger("1000000")
        state.balanceForToken("btkn1other") shouldBe BigInteger.ZERO
        state.usdtToken?.token?.ticker shouldBe "USDT"
        state.hasUsdtTransactions shouldBe true
    }

    test("empty wallet state has no tokens") {
        val state = SparkWalletState()
        state.hasNonBtcTokens shouldBe false
        state.usdtToken shouldBe null
        state.hasUsdtTransactions shouldBe false
    }

    test("BTC payment is not a token payment") {
        val payment = SparkPayment(
            id = "p1",
            type = "SEND",
            status = "COMPLETED",
            amountSats = 1000L,
            feeSats = 10L,
            timestamp = 0L,
            method = "SPARK",
        )
        payment.isToken shouldBe false
        payment.resolvedToken() shouldBe null
    }

    test("send preview flags token mode") {
        SparkSendState.Preview(
            paymentRequest = "spark1abc",
            amountSats = null,
            feeSats = 1L,
            method = "SparkInvoice",
            tokenIdentifier = "btkn1usdt",
            tokenTicker = "USDT",
            tokenDecimals = 6,
            tokenAmount = "1000000",
            tokenFee = "2",
        ).isToken shouldBe true
        SparkSendState.Preview(
            paymentRequest = "spark1abc",
            amountSats = 1000L,
            feeSats = 1L,
            method = "SparkAddress",
        ).isToken shouldBe false
    }
})
