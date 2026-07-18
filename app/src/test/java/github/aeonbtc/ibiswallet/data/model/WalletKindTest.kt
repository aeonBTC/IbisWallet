package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WalletKindTest : StringSpec({
    "stored wallets default to Bitcoin kind" {
        StoredWallet(
            id = "wallet-id",
            name = "Wallet",
            addressType = AddressType.SEGWIT,
            derivationPath = "m/84'/0'/0'/0",
        ).walletKind shouldBe WalletKind.BITCOIN
    }

    "Lightning Node is a distinct wallet kind" {
        WalletKind.LIGHTNING_NODE.name shouldBe "LIGHTNING_NODE"
    }
})
