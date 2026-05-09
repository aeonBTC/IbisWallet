package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe

class Bolt12SupportTest : FunSpec({

    test("amountless BOLT12 offers are allowed on Liquid send") {
        val parsed =
            ParsedSendRecipient.Lightning(
                rawInput = "lno1example",
                paymentInput = "lno1example",
                kind = LightningKind.BOLT12,
                amountSats = null,
            )

        layer2RecipientValidationError(parsed) shouldBe null
    }

    test("amountless BOLT11 invoices remain unsupported on Liquid send") {
        val parsed =
            ParsedSendRecipient.Lightning(
                rawInput = "lnbc1example",
                paymentInput = "lnbc1example",
                kind = LightningKind.BOLT11,
                amountSats = null,
            )

        layer2RecipientValidationError(parsed) shouldBe "Amountless Lightning invoices are not supported"
    }

    test("bip353 recipients are routed as BOLT12 lightning payments") {
        val parsed = parseSendRecipient("matt@mattcorallo.com")
        val lightning = parsed.shouldBeInstanceOf<ParsedSendRecipient.Lightning>()

        lightning.kind shouldBe LightningKind.BOLT12
        lightning.paymentInput shouldBe "matt@mattcorallo.com"
        layer2RecipientValidationError(lightning) shouldBe null
    }
})
