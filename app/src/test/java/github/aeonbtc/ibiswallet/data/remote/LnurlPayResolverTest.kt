package github.aeonbtc.ibiswallet.data.remote

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.payment.Bolt11Invoice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.security.MessageDigest

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

    context("LNURL-pay description hash binding (LUD-06)") {
        val resolver = LnurlPayResolver(
            baseHttpClient = okhttp3.OkHttpClient(),
            useTor = { false },
        )
        val metadataJson = """[["text/plain","lnurl-coffee"],["image/png","c2F0b3NoaQ=="]]"""

        test("accepts an invoice whose description hash matches sha256 of the metadata") {
            val bolt11 = buildSignedBolt11(descriptionHashOf(metadataJson))

            resolver.verifyBolt11MetadataHash(bolt11, metadataJson)
        }

        test("rejects an invoice whose description hash binds different metadata") {
            val bolt11 = buildSignedBolt11(descriptionHashOf("""[["text/plain","lnurl-rerouted"]]"""))

            val error = shouldThrow<Exception> {
                resolver.verifyBolt11MetadataHash(bolt11, metadataJson)
            }

            error.message shouldContain "does not match"
        }

        test("rejects an invoice without a description hash tag") {
            val bolt11 = buildSignedBolt11(descriptionHash = null, descriptionText = "plain description only")

            val error = shouldThrow<Exception> {
                resolver.verifyBolt11MetadataHash(bolt11, metadataJson)
            }

            error.message shouldContain "missing a description hash"
        }

        test("rejects a malformed invoice string") {
            val error = shouldThrow<Exception> {
                resolver.verifyBolt11MetadataHash("lnbcrupted", metadataJson)
            }

            error.message shouldContain "invalid Lightning invoice"
        }
    }
})

private fun descriptionHashOf(metadata: String): ByteVector32 {
    val digest = MessageDigest.getInstance("SHA-256").digest(metadata.toByteArray(Charsets.UTF_8))
    return ByteVector32(digest)
}

private fun buildSignedBolt11(
    descriptionHash: ByteVector32?,
    descriptionText: String? = null,
): String {
    val priv = PrivateKey.fromHex("01".repeat(32))
    val paymentHash = ByteVector32.fromValidHex("11".repeat(32))
    val paymentSecret = ByteVector32.fromValidHex("22".repeat(32))
    val tags = buildList {
        descriptionText?.let { add(Bolt11Invoice.TaggedField.Description(it)) }
        descriptionHash?.let { add(Bolt11Invoice.TaggedField.DescriptionHash(it)) }
        add(Bolt11Invoice.TaggedField.PaymentHash(paymentHash))
        add(Bolt11Invoice.TaggedField.Features(ByteVector.fromHex("c300")))
        add(Bolt11Invoice.TaggedField.PaymentSecret(paymentSecret))
    }
    val unsigned = Bolt11Invoice(
        prefix = "lntb",
        encodedAmount = "100m",
        timestampSeconds = 1700000000L,
        nodeId = priv.publicKey(),
        tags = tags,
        signature = ByteVector.empty,
    )
    return unsigned.sign(priv).write()
}
