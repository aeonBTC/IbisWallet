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
})
