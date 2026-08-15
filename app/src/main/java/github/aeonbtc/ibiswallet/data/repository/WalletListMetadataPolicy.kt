package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.WalletState

internal object WalletListMetadataPolicy {
    fun apply(
        previous: WalletState,
        allWallets: List<StoredWallet>,
        activeWallet: StoredWallet?,
        loadedWalletId: String?,
        activeWalletId: String?,
    ): WalletState {
        if (loadedWalletId != null && loadedWalletId != activeWalletId) {
            return previous.copy(wallets = allWallets)
        }
        return previous.copy(
            isInitialized = allWallets.isNotEmpty() || previous.isInitialized,
            wallets = allWallets,
            activeWallet = activeWallet,
        )
    }
}
