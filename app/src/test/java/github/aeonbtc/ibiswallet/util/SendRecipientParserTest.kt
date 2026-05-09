package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.WalletLayer
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SendRecipientParserTest : FunSpec({
    val silentPaymentAddress = "sp1qqgste7k9hx0qftg6qmwlkqtwuy6cycyavzmzj85c6qdfhjdpdjtdgqjuexzk6murw56suy3e0rd2cgqvycxttddwsvgxe2usfpxumr70xc9pkqwv"

    context("silent payment recipients") {
        test("bare sp1 address parses as Bitcoin recipient") {
            val parsed = parseSendRecipient(silentPaymentAddress)

            parsed.shouldBeInstanceOf<ParsedSendRecipient.Bitcoin>()
            parsed.address shouldBe silentPaymentAddress
            parsed.amountSats shouldBe null
            isRecognizedSendInput(silentPaymentAddress) shouldBe true
        }

        test("bitcoin URI with sp1 address parses amount and label") {
            val parsed = parseSendRecipient("bitcoin:$silentPaymentAddress?amount=0.001&label=Donation")

            parsed.shouldBeInstanceOf<ParsedSendRecipient.Bitcoin>()
            parsed.address shouldBe silentPaymentAddress
            parsed.amountSats shouldBe 100_000L
            parsed.label shouldBe "Donation"
        }

        test("sp1 address resolves to layer 1 route") {
            val resolution =
                resolveSendRoute(
                    input = silentPaymentAddress,
                    layer1UseSats = true,
                    layer2UseSats = true,
                    isLiquidAvailable = true,
                )

            resolution.route shouldBe WalletLayer.LAYER1
            resolution.draft.recipientAddress shouldBe silentPaymentAddress
        }
    }

    context("nested SegWit recipients remain sendable") {
        test("mainnet 3-address parses as Bitcoin recipient") {
            val parsed = parseSendRecipient("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")

            (parsed is ParsedSendRecipient.Bitcoin) shouldBe true
            (parsed as ParsedSendRecipient.Bitcoin).address shouldBe "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"
            isRecognizedSendInput("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy") shouldBe true
        }

        test("testnet 2-address is rejected") {
            val parsed = parseSendRecipient("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc")

            (parsed is ParsedSendRecipient.Unknown) shouldBe true
            (parsed as ParsedSendRecipient.Unknown).errorMessage shouldBe BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
            isRecognizedSendInput("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc") shouldBe false
        }

        test("3-address resolves to layer 1 send route") {
            val resolution =
                resolveSendRoute(
                    input = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
                    layer1UseSats = true,
                    layer2UseSats = true,
                    isLiquidAvailable = true,
                )

            resolution.route shouldBe WalletLayer.LAYER1
            resolution.draft.recipientAddress shouldBe "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"
        }

        test("testnet BIP21 is rejected") {
            val parsed = parseSendRecipient("bitcoin:tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx?amount=0.01")

            (parsed is ParsedSendRecipient.Unknown) shouldBe true
            (parsed as ParsedSendRecipient.Unknown).errorMessage shouldBe BitcoinUtils.UNSUPPORTED_NON_MAINNET_MESSAGE
        }
    }

    context("name@domain parses as BIP353 first (BOLT12 kind)") {
        test("user@example.com is treated as BIP353 / BOLT12") {
            val parsed = parseSendRecipient("user@example.com")

            parsed.shouldBeInstanceOf<ParsedSendRecipient.Lightning>()
            parsed.kind shouldBe LightningKind.BOLT12
            parsed.paymentInput shouldBe "user@example.com"
        }

        test("user@example.com routes to layer 2 when Liquid is available") {
            val resolution = resolveSendRoute(
                input = "user@example.com",
                layer1UseSats = true,
                layer2UseSats = true,
                isLiquidAvailable = true,
            )

            resolution.route shouldBe WalletLayer.LAYER2
        }

        test("user@example.com with ₿ prefix is treated as BIP353") {
            val parsed = parseSendRecipient("₿user@example.com")

            parsed.shouldBeInstanceOf<ParsedSendRecipient.Lightning>()
            parsed.kind shouldBe LightningKind.BOLT12
        }
    }

    context("Liquid BIP21 with assetid") {
        test("Liquid ParsedSendRecipient carries assetId when present") {
            val recipient = ParsedSendRecipient.Liquid(
                rawInput = "liquidnetwork:lq1...",
                address = "lq1...",
                amountSats = 500_000_000L,
                assetId = "ce091c998b83c78bb71a632313ba3760f1763d9cfcffae02258ffa9865a37bd2",
            )

            recipient.assetId shouldBe "ce091c998b83c78bb71a632313ba3760f1763d9cfcffae02258ffa9865a37bd2"
        }

        test("Liquid ParsedSendRecipient has null assetId when absent") {
            val recipient = ParsedSendRecipient.Liquid(
                rawInput = "lq1...",
                address = "lq1...",
            )

            recipient.assetId shouldBe null
        }

        test("Liquid with assetId threads to layer2 draft") {
            val assetId = "ce091c998b83c78bb71a632313ba3760f1763d9cfcffae02258ffa9865a37bd2"
            val input = "liquidnetwork:lq1...?amount=1.00000000&assetid=$assetId"

            val resolution = resolveSendRoute(
                input = input,
                layer1UseSats = true,
                layer2UseSats = true,
                isLiquidAvailable = true,
            )

            resolution.route shouldBe WalletLayer.LAYER2
            resolution.draft.recipientAddress shouldBe "lq1..."
            resolution.draft.amountInput shouldBe "100000000"
            resolution.draft.assetId shouldBe assetId
        }

        test("resolveLayer2SendDraft preserves empty assetId for L-BTC") {
            val draft = resolveLayer2SendDraft(
                input = "lq1qqtest123",
                layer2UseSats = true,
            )

            draft.assetId shouldBe null
        }
    }

    context("Spark recipients") {
        test("spark1 address parses as Spark recipient") {
            val parsed = parseSendRecipient("spark1qqexamplepaymentrequest")

            parsed.shouldBeInstanceOf<ParsedSendRecipient.Spark>()
            parsed.paymentRequest shouldBe "spark1qqexamplepaymentrequest"
            isRecognizedSendInput("spark1qqexamplepaymentrequest") shouldBe true
        }

        test("spark URI routes to layer 2 when available") {
            val resolution = resolveSendRoute(
                input = "spark:spark1qqexamplepaymentrequest?amount=0.00010000&label=Coffee",
                layer1UseSats = true,
                layer2UseSats = true,
                isLiquidAvailable = true,
            )

            resolution.route shouldBe WalletLayer.LAYER2
            resolution.draft.recipientAddress shouldBe "spark:spark1qqexamplepaymentrequest?amount=0.00010000&label=Coffee"
            resolution.draft.amountInput shouldBe "10000"
            resolution.draft.label shouldBe "Coffee"
        }

        test("spark address is rejected for Liquid send validation") {
            val parsed = parseSendRecipient("spark1qqexamplepaymentrequest")

            layer2RecipientValidationError(parsed, Layer2Provider.LIQUID) shouldBe
                "Spark requests are not supported on Liquid send"
        }

        test("spark address is accepted for Spark send validation") {
            val parsed = parseSendRecipient("spark1qqexamplepaymentrequest")

            layer2RecipientValidationError(parsed, Layer2Provider.SPARK) shouldBe null
        }

        test("Liquid address is rejected for Spark send validation") {
            val parsed = ParsedSendRecipient.Liquid(
                rawInput = "liquidnetwork:lq1qqtest",
                address = "lq1qqtest",
            )

            layer2RecipientValidationError(parsed, Layer2Provider.SPARK) shouldBe
                "Liquid requests are not supported on Spark send"
        }

        test("Bitcoin address is accepted for Spark send validation") {
            val parsed = parseSendRecipient("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")

            layer2RecipientValidationError(parsed, Layer2Provider.SPARK) shouldBe null
        }

        test("provider-specific Liquid draft does not accept Spark request") {
            val draft = resolveLayer2SendDraft(
                input = "spark1qqexamplepaymentrequest",
                layer2UseSats = true,
                provider = Layer2Provider.LIQUID,
            )

            draft.recipientAddress shouldBe "spark1qqexamplepaymentrequest"
            draft.amountInput shouldBe ""
        }
    }

    context("layer2RecipientValidationError") {
        test("LNURL Lightning recipient has no validation error") {
            val recipient = ParsedSendRecipient.Lightning(
                rawInput = "lnurl1dp68...",
                paymentInput = "https://service.com/api/v1/lnurl/pay",
                kind = LightningKind.LNURL,
            )

            layer2RecipientValidationError(recipient) shouldBe null
        }

        test("BOLT12 without amount has no validation error") {
            val recipient = ParsedSendRecipient.Lightning(
                rawInput = "lno1...",
                paymentInput = "lno1...",
                kind = LightningKind.BOLT12,
            )

            layer2RecipientValidationError(recipient) shouldBe null
        }

        test("BOLT11 without amount is rejected") {
            val recipient = ParsedSendRecipient.Lightning(
                rawInput = "lnbc1...",
                paymentInput = "lnbc1...",
                kind = LightningKind.BOLT11,
                amountSats = null,
            )

            layer2RecipientValidationError(recipient) shouldBe "Amountless Lightning invoices are not supported"
        }

        test("BOLT11 with amount has no validation error") {
            val recipient = ParsedSendRecipient.Lightning(
                rawInput = "lnbc1...",
                paymentInput = "lnbc1...",
                kind = LightningKind.BOLT11,
                amountSats = 50_000L,
            )

            layer2RecipientValidationError(recipient) shouldBe null
        }

        test("fallback error text includes Lightning Address") {
            val recipient = ParsedSendRecipient.Unknown(
                rawInput = "garbage",
                errorMessage = "Unsupported payment format",
            )

            val error = layer2RecipientValidationError(recipient)
            error shouldBe "Enter a Liquid, Spark, BOLT 11/12, or LN Address"
        }
    }
})
