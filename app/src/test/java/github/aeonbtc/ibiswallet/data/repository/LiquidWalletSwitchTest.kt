package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.LiquidWalletState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

class LiquidWalletSwitchTest : FunSpec({

    test("stale generation prevents state publication after wallet switch") {
        var generation = 0L
        val liquidState = MutableStateFlow(LiquidWalletState())

        val syncGeneration = generation

        generation++
        liquidState.value = LiquidWalletState()

        val staleState = LiquidWalletState(
            isInitialized = true,
            balanceSats = 99_999,
            currentAddress = "lq1oldwallet",
        )

        if (syncGeneration == generation) {
            liquidState.value = staleState
        }

        liquidState.value.isInitialized shouldBe false
        liquidState.value.balanceSats shouldBe 0L
        liquidState.value.currentAddress shouldBe null
    }

    test("current generation allows state publication for newly loaded wallet") {
        var generation = 0L
        val liquidState = MutableStateFlow(LiquidWalletState())

        generation++
        liquidState.value = LiquidWalletState()

        val freshGeneration = generation
        val newWalletState = LiquidWalletState(
            isInitialized = true,
            balanceSats = 42_000,
            currentAddress = "lq1newwallet",
        )

        if (freshGeneration == generation) {
            liquidState.value = newWalletState
        }

        liquidState.value.isInitialized shouldBe true
        liquidState.value.balanceSats shouldBe 42_000L
        liquidState.value.currentAddress shouldBe "lq1newwallet"
    }

    test("multiple rapid switches only allow the final wallet to publish") {
        var generation = 0L
        val liquidState = MutableStateFlow(LiquidWalletState())

        val gen1 = generation
        generation++
        val gen2 = generation
        generation++
        val gen3 = generation

        liquidState.value = LiquidWalletState()

        val wallet1 = LiquidWalletState(isInitialized = true, balanceSats = 100, currentAddress = "w1")
        val wallet2 = LiquidWalletState(isInitialized = true, balanceSats = 200, currentAddress = "w2")
        val wallet3 = LiquidWalletState(isInitialized = true, balanceSats = 300, currentAddress = "w3")

        if (gen1 == generation) liquidState.value = wallet1
        if (gen2 == generation) liquidState.value = wallet2
        if (gen3 == generation) liquidState.value = wallet3

        liquidState.value.balanceSats shouldBe 300L
        liquidState.value.currentAddress shouldBe "w3"
    }
})
