package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.R
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class SparkServiceErrorsTest : FunSpec({

    val localizer =
        object : SparkServiceErrors.Localizer {
            override fun get(resId: Int): String =
                when (resId) {
                    R.string.loc_534e1eb2 -> "Insufficient funds"
                    R.string.spark_error_connection -> "Spark connection failed"
                    R.string.spark_error_unavailable -> "Spark service unavailable"
                    R.string.spark_error_generic -> "Spark request failed"
                    else -> "unexpected:$resId"
                }
        }

    test("maps CloudFront geo-block HTML to unavailable") {
        val html =
            """
            v1=Service error: service provider error: network error: <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
            <HTML><HEAD><TITLE>ERROR</TITLE></HEAD><BODY>
            <H1>403 ERROR</H1>
            The Amazon CloudFront distribution is configured to block access from your country.
            </BODY></HTML>
            """.trimIndent()
        val mapped =
            SparkServiceErrors.mapFailure(
                localizer,
                RuntimeException(html),
                fallback = "Spark receive failed",
            )
        mapped shouldBe "Spark service unavailable"
        mapped.shouldNotContain("DOCTYPE")
        mapped.shouldNotContain("CloudFront")
    }

    test("maps gRPC PermissionDenied metadata dump to connection failed") {
        val raw =
            """
            v1=Service error: service connection error: Connection error: status: PermissionDenied, message: "permission denied", details: [], metadata: MetadataMap { headers: {"server": "awselb/2.0", "date": "Thu, 30 Jul 2026 19:44:19 GMT", "content-type": "application/grpc", "content-length": "0"} }
            """.trimIndent()
        SparkServiceErrors.mapFailure(
            localizer,
            RuntimeException(raw),
            fallback = "Spark receive failed",
        ) shouldBe "Spark connection failed"
    }

    test("maps insufficient funds") {
        SparkServiceErrors.mapFailure(
            localizer,
            RuntimeException("Insufficient funds"),
            fallback = "Spark send failed",
        ) shouldBe "Insufficient funds"
    }

    test("keeps short clean SDK messages") {
        SparkServiceErrors.mapFailure(
            localizer,
            RuntimeException("Amount below minimum"),
            fallback = "Spark receive failed",
        ) shouldBe "Amount below minimum"
    }

    test("blank message uses fallback when fallback is useful") {
        SparkServiceErrors.mapFailure(
            localizer,
            RuntimeException("   "),
            fallback = "Spark receive failed",
        ) shouldBe "Spark receive failed"
    }
})
