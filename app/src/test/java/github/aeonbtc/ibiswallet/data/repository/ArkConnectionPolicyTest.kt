package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkConnectionPolicyTest : FunSpec({

    test("success resets streak, failure increments without going negative") {
        ArkConnectionPolicy.nextFailureStreak(ok = true, streak = 5) shouldBe 0
        ArkConnectionPolicy.nextFailureStreak(ok = false, streak = 0) shouldBe 1
        ArkConnectionPolicy.nextFailureStreak(ok = false, streak = 2) shouldBe 3
        ArkConnectionPolicy.nextFailureStreak(ok = false, streak = -3) shouldBe 0
    }

    test("disconnect flips only when connected, loaded, and over the limit") {
        ArkConnectionPolicy.shouldMarkDisconnected(
            streak = 3,
            connected = true,
            walletLoaded = true,
        ) shouldBe true
        ArkConnectionPolicy.shouldMarkDisconnected(
            streak = 2,
            connected = true,
            walletLoaded = true,
        ) shouldBe false
        ArkConnectionPolicy.shouldMarkDisconnected(
            streak = 9,
            connected = false,
            walletLoaded = true,
        ) shouldBe false
        ArkConnectionPolicy.shouldMarkDisconnected(
            streak = 9,
            connected = true,
            walletLoaded = false,
        ) shouldBe false
    }

    test("auto-reconnect needs disconnected, idle, eligible, foreground, cooled-down") {
        fun reconnect(
            connected: Boolean = false,
            connecting: Boolean = false,
            walletPresent: Boolean = true,
            arkEnabled: Boolean = true,
            foregrounded: Boolean = true,
            cooldownElapsedMs: Long = 61_000L,
        ) = ArkConnectionPolicy.shouldAutoReconnect(
            connected = connected,
            connecting = connecting,
            walletPresent = walletPresent,
            arkEnabled = arkEnabled,
            foregrounded = foregrounded,
            cooldownElapsedMs = cooldownElapsedMs,
        )

        reconnect() shouldBe true
        reconnect(connected = true) shouldBe false
        reconnect(connecting = true) shouldBe false
        reconnect(walletPresent = false) shouldBe false
        reconnect(arkEnabled = false) shouldBe false
        reconnect(foregrounded = false) shouldBe false
        reconnect(cooldownElapsedMs = 59_999L) shouldBe false
        reconnect(cooldownElapsedMs = 60_000L) shouldBe true
    }
})
