package github.aeonbtc.ibiswallet.data.model

import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Multi-send on Ark/Spark is sequential native-address payments only.
 * These tests lock eligibility + Multi* state shapes (no live SDK).
 */
class Layer2MultiSendTest : FunSpec({

    fun isArkMultiEligible(input: String): Boolean =
        parseSendRecipient(input.trim()) is ParsedSendRecipient.Ark

    fun isSparkMultiEligible(input: String): Boolean =
        parseSendRecipient(input.trim()) is ParsedSendRecipient.Spark

    fun filterArkMultiPairs(
        rows: List<Pair<String, Long>>,
    ): List<Pair<String, Long>> =
        rows.filter { (addr, amount) -> amount > 0L && isArkMultiEligible(addr) }

    fun filterSparkMultiPairs(
        rows: List<Pair<String, Long>>,
    ): List<Pair<String, Long>> =
        rows.filter { (addr, amount) -> amount > 0L && isSparkMultiEligible(addr) }

    context("Ark multi eligibility") {
        test("ark1 address is multi-eligible") {
            isArkMultiEligible("ark1qtestaddress") shouldBe true
            parseSendRecipient("ark1qtestaddress").shouldBeInstanceOf<ParsedSendRecipient.Ark>()
        }

        test("bitcoin address is not multi-eligible") {
            isArkMultiEligible("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy") shouldBe false
        }

        test("spark address is not multi-eligible on Ark") {
            isArkMultiEligible("spark1qqexamplepaymentrequest") shouldBe false
        }

        test("lightning invoice is not multi-eligible") {
            isArkMultiEligible(
                "lnbc100n1p3testinvoice",
            ) shouldBe false
        }

        test("filters to ark addresses only and requires positive amounts") {
            val rows =
                listOf(
                    "ark1qaaa" to 10_000L,
                    "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy" to 5_000L,
                    "ark1qbbb" to 0L,
                    "ark1qccc" to 20_000L,
                    "spark1qqexample" to 1_000L,
                )
            val filtered = filterArkMultiPairs(rows)
            filtered shouldHaveSize 2
            filtered.map { it.first } shouldBe listOf("ark1qaaa", "ark1qccc")
            filtered.sumOf { it.second } shouldBe 30_000L
        }

        test("need at least two ark recipients for multi") {
            val one = filterArkMultiPairs(listOf("ark1qaaa" to 1_000L))
            val two =
                filterArkMultiPairs(
                    listOf(
                        "ark1qaaa" to 1_000L,
                        "ark1qbbb" to 2_000L,
                    ),
                )
            (one.size >= 2) shouldBe false
            (two.size >= 2) shouldBe true
        }
    }

    context("Spark multi eligibility") {
        test("spark1 address is multi-eligible") {
            isSparkMultiEligible("spark1qqexamplepaymentrequest") shouldBe true
            parseSendRecipient("spark1qqexamplepaymentrequest")
                .shouldBeInstanceOf<ParsedSendRecipient.Spark>()
        }

        test("bitcoin address is not multi-eligible") {
            isSparkMultiEligible("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy") shouldBe false
        }

        test("ark address is not multi-eligible on Spark") {
            isSparkMultiEligible("ark1qtestaddress") shouldBe false
        }

        test("filters to spark addresses only") {
            val rows =
                listOf(
                    "spark1qqaaa" to 10_000L,
                    "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh" to 5_000L,
                    "spark1qqbbb" to 15_000L,
                    "lnbc1..." to 1_000L,
                )
            val filtered = filterSparkMultiPairs(rows)
            filtered shouldHaveSize 2
            filtered.map { it.first } shouldBe listOf("spark1qqaaa", "spark1qqbbb")
        }
    }

    context("Ark multi send states") {
        test("MultiPreview aggregates totals") {
            val items =
                listOf(
                    ArkSendState.MultiPreview.MultiItem("ark1qaaa", 10_000L, 100L),
                    ArkSendState.MultiPreview.MultiItem("ark1qbbb", 20_000L, 200L),
                )
            val preview =
                ArkSendState.MultiPreview(
                    items = items,
                    totalAmountSats = items.sumOf { it.amountSats },
                    totalFeeSats = items.sumOf { it.feeSats },
                    label = "batch",
                )
            preview.totalAmountSats shouldBe 30_000L
            preview.totalFeeSats shouldBe 300L
            preview.items shouldHaveSize 2
            preview.label shouldBe "batch"
        }

        test("MultiSending tracks progress") {
            val sending = ArkSendState.MultiSending(completed = 1, total = 3)
            sending.completed shouldBe 1
            sending.total shouldBe 3
        }

        test("MultiSent success and partial") {
            val ok = ArkSendState.MultiSent(succeeded = 2, failed = 0)
            val partial = ArkSendState.MultiSent(succeeded = 1, failed = 1, detail = "fail")
            ok.failed shouldBe 0
            partial.succeeded shouldBe 1
            partial.detail shouldBe "fail"
        }
    }

    context("Spark multi send states") {
        test("MultiPreview aggregates totals") {
            val items =
                listOf(
                    SparkSendState.MultiPreview.MultiItem("spark1qqaaa", 5_000L, 50L),
                    SparkSendState.MultiPreview.MultiItem("spark1qqbbb", 7_000L, 70L),
                )
            val preview =
                SparkSendState.MultiPreview(
                    items = items,
                    totalAmountSats = items.sumOf { it.amountSats },
                    totalFeeSats = items.sumOf { it.feeSats },
                )
            preview.totalAmountSats shouldBe 12_000L
            preview.totalFeeSats shouldBe 120L
        }

        test("MultiSending and MultiSent") {
            SparkSendState.MultiSending(0, 2).total shouldBe 2
            SparkSendState.MultiSent(2, 0).failed shouldBe 0
            SparkSendState.MultiSent(1, 1, "err").detail shouldBe "err"
        }
    }

    context("multi QR / scan mapping") {
        test("ark scan maps ark address and optional amount") {
            val parsed = parseSendRecipient("ark1qtestaddress")
            parsed.shouldBeInstanceOf<ParsedSendRecipient.Ark>()
            val pair = (parsed as ParsedSendRecipient.Ark).address to parsed.amountSats
            pair.first shouldBe "ark1qtestaddress"
            pair.second shouldBe null
        }

        test("spark scan maps spark payment request") {
            val parsed = parseSendRecipient("spark1qqexamplepaymentrequest")
            parsed.shouldBeInstanceOf<ParsedSendRecipient.Spark>()
            val pair =
                (parsed as ParsedSendRecipient.Spark).paymentRequest to parsed.amountSats
            pair.first shouldBe "spark1qqexamplepaymentrequest"
        }

        test("bitcoin scan is not ark multi eligible") {
            val parsed = parseSendRecipient("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")
            (parsed is ParsedSendRecipient.Ark) shouldBe false
            (parsed is ParsedSendRecipient.Spark) shouldBe false
        }
    }
})
