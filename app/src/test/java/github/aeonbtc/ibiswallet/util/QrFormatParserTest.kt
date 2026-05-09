package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QrFormatParserTest : FunSpec({

    // ── parseServerQr ──

    context("parseServerQr") {
        test("plain host:port") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl shouldBe true // port 50002 implies SSL
        }

        test("ssl:// prefix sets SSL true") {
            val result = QrFormatParser.parseServerQr("ssl://electrum.example.com:50002")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl shouldBe true
        }

        test("tcp:// prefix sets SSL false") {
            val result = QrFormatParser.parseServerQr("tcp://electrum.example.com:50001")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl shouldBe false
        }

        test("http:// prefix sets SSL false") {
            val result = QrFormatParser.parseServerQr("http://electrum.example.com:50001")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl shouldBe false
        }

        test("https:// prefix sets SSL true") {
            val result = QrFormatParser.parseServerQr("https://electrum.example.com:443")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 443
            result.ssl shouldBe true
        }

        test("electrum:// prefix strips correctly") {
            val result = QrFormatParser.parseServerQr("electrum://electrum.example.com:50002")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl shouldBe true
        }

        test("Electrum-style :s suffix means SSL") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50002:s")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
            result.ssl shouldBe true
        }

        test("Electrum-style :t suffix means TCP (no SSL)") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50001:t")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl shouldBe false
        }

        test("port 443 implies SSL") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:443")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 443
            result.ssl shouldBe true
        }

        test("port 50001 implies no SSL") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:50001")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50001
            result.ssl shouldBe false
        }

        test("onion address with port") {
            val result = QrFormatParser.parseServerQr("abcdef1234567890.onion:50001")
            result.host shouldBe "abcdef1234567890.onion"
            result.port shouldBe 50001
            result.ssl shouldBe false
        }

        test("host-only (no port) returns null port") {
            val result = QrFormatParser.parseServerQr("electrum.example.com")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe null
        }

        test("strips trailing slash") {
            val result = QrFormatParser.parseServerQr("ssl://electrum.example.com:50002/")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
        }

        test("trims whitespace") {
            val result = QrFormatParser.parseServerQr("  electrum.example.com:50002  ")
            result.host shouldBe "electrum.example.com"
            result.port shouldBe 50002
        }

        test("protocol prefix is case-insensitive") {
            val result = QrFormatParser.parseServerQr("SSL://electrum.example.com:50002")
            result.ssl shouldBe true
            result.host shouldBe "electrum.example.com"
        }

        test("invalid port falls back to host-only") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:notaport")
            result.host shouldBe "electrum.example.com:notaport"
            result.port shouldBe null
        }

        test("port out of range falls back to host-only") {
            val result = QrFormatParser.parseServerQr("electrum.example.com:99999")
            result.host shouldBe "electrum.example.com:99999"
            result.port shouldBe null
        }
    }

    // ── expandAbbreviatedMnemonic ──

    context("expandAbbreviatedMnemonic") {
        val wordlist = listOf(
            "abandon", "ability", "able", "about", "above", "absent",
            "absorb", "abstract", "absurd", "abuse", "access", "accident",
        )

        test("preserves trailing space for partial input") {
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, "abandon ") shouldBe "abandon "
        }

        test("preserves raw input when word count is not a valid mnemonic count") {
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, "abandon ability ") shouldBe "abandon ability "
        }

        test("preserves single word being typed") {
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, "aban") shouldBe "aban"
        }

        test("does not strip spaces for incomplete mnemonic") {
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, "abandon ability able about above absent absorb ") shouldBe
                "abandon ability able about above absent absorb "
        }

        test("expands abbreviated 12-word mnemonic") {
            val abbreviated = "aban abil able abou abov abse abso abst absu abus acce acci"
            val expected = "abandon ability able about above absent absorb abstract absurd abuse access accident"
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, abbreviated) shouldBe expected
        }

        test("preserves trailing space after expansion") {
            val abbreviated = "aban abil able abou abov abse abso abst absu abus acce acci "
            val expected = "abandon ability able about above absent absorb abstract absurd abuse access accident "
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, abbreviated) shouldBe expected
        }

        test("returns input unchanged when all words are already full") {
            val full = "abandon ability able about above absent absorb abstract absurd abuse access accident"
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, full) shouldBe full
        }

        test("empty input returns empty") {
            QrFormatParser.expandAbbreviatedMnemonic(wordlist, "") shouldBe ""
        }
    }

    // ── suggestBip39Words ──

    context("suggestBip39Words") {
        val wordlist = listOf(
            "abandon", "ability", "able", "about", "above", "absent",
            "absorb", "abstract", "absurd", "abuse", "access", "accident",
            "bacon", "badge", "bag",
        )

        test("returns matching words for prefix") {
            QrFormatParser.suggestBip39Words(wordlist, "ab") shouldBe
                listOf("abandon", "ability", "able", "about")
        }

        test("limits results") {
            QrFormatParser.suggestBip39Words(wordlist, "ab", limit = 2) shouldBe
                listOf("abandon", "ability")
        }

        test("returns empty for blank prefix") {
            QrFormatParser.suggestBip39Words(wordlist, "") shouldBe emptyList()
            QrFormatParser.suggestBip39Words(wordlist, "  ") shouldBe emptyList()
        }

        test("returns empty when no words match") {
            QrFormatParser.suggestBip39Words(wordlist, "xyz") shouldBe emptyList()
        }

        test("prefix matching is case-insensitive") {
            QrFormatParser.suggestBip39Words(wordlist, "AB") shouldBe
                listOf("abandon", "ability", "able", "about")
        }

        test("exact word still appears in suggestions") {
            QrFormatParser.suggestBip39Words(wordlist, "able") shouldBe listOf("able")
        }

        test("different prefix returns different group") {
            QrFormatParser.suggestBip39Words(wordlist, "ba") shouldBe
                listOf("bacon", "badge", "bag")
        }
    }

    // ── completeSeedWord ──

    context("completeSeedWord") {
        test("completes first word") {
            QrFormatParser.completeSeedWord("ab", "abandon") shouldBe "abandon "
        }

        test("completes word after existing words") {
            QrFormatParser.completeSeedWord("abandon ability ab", "about") shouldBe
                "abandon ability about "
        }

        test("completes when input has trailing partial word") {
            QrFormatParser.completeSeedWord("abandon ab", "absent") shouldBe "abandon absent "
        }

        test("handles empty input") {
            QrFormatParser.completeSeedWord("", "abandon") shouldBe "abandon "
        }
    }

    // ── ServerConfig data class ──

    context("ServerConfig") {
        test("equality works") {
            QrFormatParser.ServerConfig("host", 50002, true) shouldBe
                QrFormatParser.ServerConfig("host", 50002, true)
        }
    }
})
