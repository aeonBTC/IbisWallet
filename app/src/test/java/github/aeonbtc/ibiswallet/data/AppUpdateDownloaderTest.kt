package github.aeonbtc.ibiswallet.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl

class AppUpdateDownloaderTest : FunSpec({
    test("allows github.com over https") {
        isAllowedUpdateDownloadUrl("https://github.com/aeonBTC/IbisWallet/releases/download/v1/app.apk".toHttpUrl()) shouldBe true
    }

    test("allows the githubusercontent redirect host over https") {
        isAllowedUpdateDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/app.apk".toHttpUrl()) shouldBe true
    }

    test("rejects cleartext github.com") {
        isAllowedUpdateDownloadUrl("http://github.com/aeonBTC/IbisWallet/app.apk".toHttpUrl()) shouldBe false
    }

    test("rejects arbitrary https hosts") {
        isAllowedUpdateDownloadUrl("https://files.example.com/app.apk".toHttpUrl()) shouldBe false
    }

    test("rejects lookalike githubusercontent suffixes") {
        isAllowedUpdateDownloadUrl("https://evilgithubusercontent.com/app.apk".toHttpUrl()) shouldBe false
    }

    test("rejects githubusercontent as a subdomain prefix") {
        isAllowedUpdateDownloadUrl("https://githubusercontent.com.evil.com/app.apk".toHttpUrl()) shouldBe false
    }

    test("parses a content range with a known total") {
        parseContentRange("bytes 1024-2047/4096") shouldBe ContentRange(start = 1024, end = 2047, total = 4096)
    }

    test("parses a content range with an unknown total") {
        parseContentRange("bytes 0-1023/*") shouldBe ContentRange(start = 0, end = 1023, total = null)
    }

    test("rejects malformed content ranges") {
        parseContentRange("items 0-1/2") shouldBe null
    }

    test("rejects a content range whose end precedes its start") {
        parseContentRange("bytes 10-9/100") shouldBe null
    }

    test("rejects a content range ending at or past the total") {
        parseContentRange("bytes 0-100/100") shouldBe null
    }
})
