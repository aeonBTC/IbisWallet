package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LiquidRepositoryBoltzRescueTest : FunSpec({

    val mnemonic = "abandon ability able about above absent absorb abstract absurd abuse access accident"

    test("extracts rescue mnemonic from plain text") {
        extractBoltzRescueMnemonic(mnemonic) shouldBe mnemonic
    }

    test("extracts rescue mnemonic from nested rescue json") {
        val rescueFile =
            """
            {
              "version": 1,
              "payload": {
                "swap": {
                  "mnemonic": "$mnemonic"
                }
              }
            }
            """.trimIndent()

        extractBoltzRescueMnemonic(rescueFile) shouldBe mnemonic
    }

    test("returns null when rescue json has no mnemonic") {
        extractBoltzRescueMnemonic("""{"version":1,"payload":{"swapId":"123"}}""").shouldBeNull()
    }
})
