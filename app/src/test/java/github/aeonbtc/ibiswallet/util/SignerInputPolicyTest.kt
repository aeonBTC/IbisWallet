package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SignerInputPolicyTest : FunSpec({

    context("classify") {
        test("blank input is EMPTY") {
            SignerInputPolicy.classify("") shouldBe SignerInputKind.EMPTY
            SignerInputPolicy.classify("   \n\t  ") shouldBe SignerInputKind.EMPTY
        }

        test("raw transaction hex is RAW_HEX") {
            // 202 hex chars: even length but length % 4 == 2, so it is not
            // valid unpadded base64 — unambiguous hex.
            val hex = "02" + "ab".repeat(100)
            SignerInputPolicy.classify(hex) shouldBe SignerInputKind.RAW_HEX
        }

        test("hex looking payload that also decodes as base64 reports RAW_HEX") {
            // "abcdef0123456789" is even-length hex AND valid base64. Hex is
            // checked first so the user gets the actionable message (a raw
            // transaction has nothing to sign); refusal is identical either
            // way since only PSBT_CANDIDATE ever reaches the parser.
            val ambiguous = "abcdef0123456789"
            SignerInputPolicy.classify(ambiguous) shouldBe SignerInputKind.RAW_HEX
        }

        test("liquid PSET magic prefix is LIQUID_PSET") {
            val psetBase64 = java.util.Base64.getEncoder().encodeToString(
                byteArrayOf(0x70, 0x73, 0x65, 0x74, 0xFF.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05),
            )
            SignerInputPolicy.classify(psetBase64) shouldBe SignerInputKind.LIQUID_PSET
        }

        test("bitcoin PSBT magic prefix is PSBT_CANDIDATE") {
            val psbtBase64 = java.util.Base64.getEncoder().encodeToString(
                byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05),
            )
            SignerInputPolicy.classify(psbtBase64) shouldBe SignerInputKind.PSBT_CANDIDATE
        }

        test("gibberish text is UNRECOGNIZED") {
            SignerInputPolicy.classify("not a psbt at all!!!") shouldBe SignerInputKind.UNRECOGNIZED
            SignerInputPolicy.classify("abc") shouldBe SignerInputKind.UNRECOGNIZED
        }
    }

    context("isSignable") {
        test("only PSBT_CANDIDATE is signable") {
            SignerInputPolicy.isSignable(SignerInputKind.PSBT_CANDIDATE) shouldBe true
            SignerInputPolicy.isSignable(SignerInputKind.EMPTY) shouldBe false
            SignerInputPolicy.isSignable(SignerInputKind.AMBIGUOUS) shouldBe false
            SignerInputPolicy.isSignable(SignerInputKind.RAW_HEX) shouldBe false
            SignerInputPolicy.isSignable(SignerInputKind.LIQUID_PSET) shouldBe false
            SignerInputPolicy.isSignable(SignerInputKind.UNRECOGNIZED) shouldBe false
        }
    }
})
