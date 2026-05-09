package github.aeonbtc.ibiswallet.data.remote

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LnurlPayResolverTest : FunSpec({

    context("LnurlPayMetadata amount conversions") {
        test("minSendableSats rounds up from millisats") {
            val metadata = LnurlPayMetadata(
                callback = "https://example.com/cb",
                minSendableMsats = 1_001L,
                maxSendableMsats = 100_000_000L,
                metadata = "[]",
                isFixedAmount = false,
            )

            metadata.minSendableSats shouldBe 2L
        }

        test("maxSendableSats truncates from millisats") {
            val metadata = LnurlPayMetadata(
                callback = "https://example.com/cb",
                minSendableMsats = 1_000L,
                maxSendableMsats = 99_999_999L,
                metadata = "[]",
                isFixedAmount = false,
            )

            metadata.maxSendableSats shouldBe 99_999L
        }

        test("exact sat boundary converts cleanly") {
            val metadata = LnurlPayMetadata(
                callback = "https://example.com/cb",
                minSendableMsats = 1_000L,
                maxSendableMsats = 1_000_000_000L,
                metadata = "[]",
                isFixedAmount = false,
            )

            metadata.minSendableSats shouldBe 1L
            metadata.maxSendableSats shouldBe 1_000_000L
        }

        test("fixed amount is detected when min equals max") {
            val metadata = LnurlPayMetadata(
                callback = "https://example.com/cb",
                minSendableMsats = 50_000_000L,
                maxSendableMsats = 50_000_000L,
                metadata = "[]",
                isFixedAmount = true,
            )

            metadata.isFixedAmount shouldBe true
            metadata.minSendableSats shouldBe 50_000L
        }
    }

    context("LnurlPayResolver.fetchInvoice amount validation") {
        val resolver = LnurlPayResolver(
            baseHttpClient = okhttp3.OkHttpClient(),
            useTor = { false },
        )
        val metadata = LnurlPayMetadata(
            callback = "https://example.com/cb",
            minSendableMsats = 10_000_000L,
            maxSendableMsats = 500_000_000L,
            metadata = "[]",
            isFixedAmount = false,
        )

        test("rejects amount below minimum") {
            val error = shouldThrow<Exception> {
                resolver.fetchInvoice(metadata, amountMsats = 5_000_000L)
            }

            error.message shouldContain "below the minimum"
            error.message shouldContain "10,000 sats"
        }

        test("rejects amount above maximum") {
            val error = shouldThrow<Exception> {
                resolver.fetchInvoice(metadata, amountMsats = 600_000_000L)
            }

            error.message shouldContain "exceeds the maximum"
            error.message shouldContain "500,000 sats"
        }
    }
})
