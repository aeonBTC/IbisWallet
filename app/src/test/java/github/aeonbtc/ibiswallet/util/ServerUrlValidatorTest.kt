package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ServerUrlValidatorTest : FunSpec({
    context("accepted URLs") {
        test("accepts a public HTTPS hostname") {
            ServerUrlValidator.validate("https://mempool.space") shouldBe null
        }

        test("accepts surrounding whitespace") {
            ServerUrlValidator.validate("  https://mempool.space  ") shouldBe null
        }

        test("accepts a private IPv4 address") {
            ServerUrlValidator.validate("http://192.168.1.20:3000") shouldBe null
        }

        test("accepts an IPv6 literal") {
            ServerUrlValidator.validate("http://[2001:db8::1]:8080") shouldBe null
        }

        test("accepts localhost") {
            ServerUrlValidator.validate("http://localhost:8080") shouldBe null
        }

        test("accepts a valid onion hostname") {
            ServerUrlValidator.validate(
                "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion",
            ) shouldBe null
        }

        test("accepts a base path") {
            ServerUrlValidator.validate("https://blockstream.info/liquid") shouldBe null
        }
    }

    context("rejected URLs") {
        test("rejects an empty URL") {
            ServerUrlValidator.validate("") shouldBe "URL cannot be empty"
        }

        test("rejects a missing HTTP scheme") {
            ServerUrlValidator.validate("mempool.space") shouldBe "URL must start with http:// or https://"
        }

        test("rejects unsupported schemes") {
            ServerUrlValidator.validate("ftp://mempool.space") shouldBe "URL must start with http:// or https://"
        }

        test("rejects a missing host") {
            ServerUrlValidator.validate("https://") shouldBe "URL is invalid"
        }

        test("rejects credentials") {
            ServerUrlValidator.validate("https://user:pass@example.com") shouldBe "URL cannot include credentials"
        }

        test("rejects query parameters") {
            ServerUrlValidator.validate("https://example.com/api?network=mainnet") shouldBe
                "URL cannot include query parameters"
        }

        test("rejects fragments") {
            ServerUrlValidator.validate("https://example.com/#fees") shouldBe "URL cannot include a fragment"
        }

        test("rejects underscores in hostnames") {
            ServerUrlValidator.validate("https://bad_host.example") shouldBe "URL host or port is invalid"
        }

        test("rejects labels that start with hyphens") {
            ServerUrlValidator.validate("https://-bad.example") shouldBe "URL host or port is invalid"
        }

        test("rejects invalid ports") {
            ServerUrlValidator.validate("https://example.com:99999") shouldBe "URL port is invalid"
        }

        test("rejects invalid IPv4 literals") {
            ServerUrlValidator.validate("http://999.1.1.1") shouldBe "URL host or port is invalid"
        }

        test("rejects invalid onion hostnames") {
            ServerUrlValidator.validate("http://example.onion") shouldBe "URL host is invalid"
        }

        test("rejects path traversal segments") {
            ServerUrlValidator.validate("https://example.com/api/../fees") shouldBe
                "URL path cannot contain traversal segments"
        }

        test("rejects encoded path traversal segments") {
            ServerUrlValidator.validate("https://example.com/%2e%2e/fees") shouldBe
                "URL path cannot contain traversal segments"
        }
    }

    context("normalization") {
        test("trims surrounding whitespace") {
            ServerUrlValidator.normalize("  https://mempool.space  ") shouldBe "https://mempool.space"
        }
    }
})
