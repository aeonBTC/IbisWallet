package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

class QrFormatParserTest : FunSpec({

    // -------------------------------------------------------------------------
    // parseServerQr — plain host:port
    // -------------------------------------------------------------------------

    context("parseServerQr - plain host:port") {
        test("parses host and numeric port") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
        }

        test("infers SSL=true for port 50002") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002")
            result.ssl!!.shouldBeTrue()
        }

        test("infers SSL=false for port 50001") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50001")
            result.ssl!!.shouldBeFalse()
        }

        test("trims whitespace around input") {
            val result = QrFormatParser.parseServerQr("  electrum.example.com:50002  ")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
        }
    }

    // -------------------------------------------------------------------------
    // parseServerQr — protocol-prefixed
    // -------------------------------------------------------------------------

    context("parseServerQr - ssl:// prefix") {
        test("strips ssl:// prefix and sets ssl=true") {
            val result = QrFormatParser.parseServerQr("ssl://electrum.example.com:50002")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl!!.shouldBeTrue()
        }
    }

    context("parseServerQr - tcp:// prefix") {
        test("strips tcp:// prefix and sets ssl=false") {
            val result = QrFormatParser.parseServerQr("tcp://electrum.example.com:50001")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl!!.shouldBeFalse()
        }
    }

    context("parseServerQr - https:// prefix") {
        test("strips https:// and sets ssl=true") {
            val result = QrFormatParser.parseServerQr("https://mynode.local:443")
            result.host shouldBe "mynode.local"
            result.port shouldBe 443
            result.ssl!!.shouldBeTrue()
        }
    }

    context("parseServerQr - http:// prefix") {
        test("strips http:// and sets ssl=false") {
            val result = QrFormatParser.parseServerQr("http://mynode.local:8080")
            result.host shouldBe "mynode.local"
            result.port shouldBe 8080
            result.ssl!!.shouldBeFalse()
        }
    }

    // -------------------------------------------------------------------------
    // parseServerQr — Electrum-style host:port:s / host:port:t suffix
    // -------------------------------------------------------------------------

    context("parseServerQr - Electrum :s/:t suffix") {
        test("host:port:s sets ssl=true") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002:s")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl!!.shouldBeTrue()
        }

        test("host:port:t sets ssl=false") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50001:t")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl!!.shouldBeFalse()
        }
    }

    // -------------------------------------------------------------------------
    // parseServerQr — no port
    // -------------------------------------------------------------------------

    context("parseServerQr - host only, no port") {
        test("returns host with null port") {
            val result = QrFormatParser.parseServerQr("electrum.example.com")
            result.host shouldBe "electrum.example.com"
            result.port.shouldBeNull()
        }
    }

    // -------------------------------------------------------------------------
    // parseServerQr — trailing slash stripped
    // -------------------------------------------------------------------------

    context("parseServerQr - trailing slash") {
        test("strips trailing slash from host:port") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002/")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
        }
    }
})
