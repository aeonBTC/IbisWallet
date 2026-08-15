package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.WalletState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WalletListMetadataPolicyTest : FunSpec({
    fun wallet(id: String, name: String) =
        StoredWallet(
            id = id,
            name = name,
            addressType = AddressType.SEGWIT,
            derivationPath = AddressType.SEGWIT.defaultPath,
        )

    test("updates wallet list and active wallet without touching history") {
        val previous =
            WalletState(
                isInitialized = true,
                wallets = listOf(wallet("a", "Old")),
                activeWallet = wallet("a", "Old"),
                balanceSats = 12_345UL,
                transactions = emptyList(),
            )
        val updated = wallet("a", "New")

        val next =
            WalletListMetadataPolicy.apply(
                previous = previous,
                allWallets = listOf(updated),
                activeWallet = updated,
                loadedWalletId = "a",
                activeWalletId = "a",
            )

        next.wallets.single().name shouldBe "New"
        next.activeWallet?.name shouldBe "New"
        next.balanceSats shouldBe 12_345UL
    }

    test("does not swap active wallet while a load is in flight") {
        val previous =
            WalletState(
                isInitialized = true,
                wallets = listOf(wallet("a", "A")),
                activeWallet = wallet("a", "A"),
                balanceSats = 1UL,
            )

        val next =
            WalletListMetadataPolicy.apply(
                previous = previous,
                allWallets = listOf(wallet("b", "B")),
                activeWallet = wallet("b", "B"),
                loadedWalletId = "a",
                activeWalletId = "b",
            )

        next.activeWallet?.id shouldBe "a"
        next.wallets.single().id shouldBe "b"
        next.balanceSats shouldBe 1UL
    }
})
