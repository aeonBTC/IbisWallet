package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkSessionDbErrorPolicyTest : FunSpec({

    test("detects bark cache-eviction sqlite errors") {
        ArkSessionDbErrorPolicy.isSessionDbError(
            "Error connecting to database /data/user/0/github.aeonbtc.ibiswallet/cache/" +
                "ark-session/3fb82581-4d52-4a12-93b3-fc90f82aae44/db.sqlite: " +
                "unable to open database file: /data/user/0/github.aeonbtc.ibiswallet/cache/ark-session",
        ) shouldBe true
        ArkSessionDbErrorPolicy.isSessionDbError("unable to open database file") shouldBe true
        ArkSessionDbErrorPolicy.isSessionDbError("disk image is malformed") shouldBe true
        ArkSessionDbErrorPolicy.isSessionDbError("dns error") shouldBe false
        ArkSessionDbErrorPolicy.isSessionDbError("connection refused") shouldBe false
        ArkSessionDbErrorPolicy.isSessionDbError(null as String?) shouldBe false
    }

    test("detects session db errors from throwables including causes") {
        ArkSessionDbErrorPolicy.isSessionDbError(Exception("disk image is malformed")) shouldBe true
        ArkSessionDbErrorPolicy.isSessionDbError(
            Exception("wrap", Exception("unable to open database file")),
        ) shouldBe true
        ArkSessionDbErrorPolicy.isSessionDbError(Exception("connection refused")) shouldBe false
        ArkSessionDbErrorPolicy.isSessionDbError(null as Throwable?) shouldBe false
    }

    test("connecting to database is not a network error") {
        ArkSessionDbErrorPolicy.isDnsOrNetworkError(
            "Error connecting to database /cache/ark-session/w/db.sqlite: unable to open database file",
        ) shouldBe false
        ArkSessionDbErrorPolicy.isDnsOrNetworkError("connection refused") shouldBe true
        ArkSessionDbErrorPolicy.isDnsOrNetworkError("dns error") shouldBe true
    }

    test("backs off reopen delay with a cap") {
        ArkSessionDbErrorPolicy.retryDelayMs(0) shouldBe 400L
        ArkSessionDbErrorPolicy.retryDelayMs(1) shouldBe 800L
        ArkSessionDbErrorPolicy.retryDelayMs(2) shouldBe 1_600L
        ArkSessionDbErrorPolicy.retryDelayMs(3) shouldBe 2_000L
        ArkSessionDbErrorPolicy.MAX_AUTO_REOPENS shouldBe 3
    }
})
