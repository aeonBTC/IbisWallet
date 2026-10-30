package github.aeonbtc.ibiswallet.ui.screens

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class Bip21ParsingTest : FunSpec({
    context("parseBip21Uri amounts") {
        test("parses the smallest unit exactly") {
            parseBip21Uri("bitcoin:bc1qexample?amount=0.00000001").amountSats shouldBe 1L
        }

        test("parses a value that is imprecise as a Double") {
            parseBip21Uri("bitcoin:bc1qexample?amount=0.29").amountSats shouldBe 29_000_000L
        }

        test("parses full 8-decimal precision") {
            parseBip21Uri("bitcoin:bc1qexample?amount=1.23456789").amountSats shouldBe 123_456_789L
        }

        test("rejects sub-satoshi precision") {
            parseBip21Uri("bitcoin:bc1qexample?amount=0.000000001").amountSats shouldBe null
        }

        test("rejects amounts above the supply cap") {
            parseBip21Uri("bitcoin:bc1qexample?amount=21000000.00000001").amountSats shouldBe null
        }

        test("accepts the supply cap itself") {
            parseBip21Uri("bitcoin:bc1qexample?amount=21000000").amountSats shouldBe 2_100_000_000_000_000L
        }

        test("duplicate amount parameters are rejected") {
            shouldThrow<IllegalArgumentException> {
                parseBip21Uri("bitcoin:bc1qexample?amount=0.1&amount=1")
            }
        }
    }
})
