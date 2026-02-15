package github.aeonbtc.ibiswallet.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    // Main screens (bottom navigation)
    data object Receive : Screen("receive")

    data object Balance : Screen("balance")

    data object Send : Screen("send")

    // Drawer screens
    data object ManageWallets : Screen("manage_wallets")

    data object ImportWallet : Screen("import_wallet")

    data object GenerateWallet : Screen("generate_wallet")

    data object ElectrumConfig : Screen("electrum_config")

    data object Settings : Screen("settings")

    data object Security : Screen("security")

    data object About : Screen("about")

    // Address and UTXO screens
    data object AllAddresses : Screen("all_addresses")

    data object AllUtxos : Screen("all_utxos")

    // PSBT signing flow (watch-only wallets)
    data object PsbtExport : Screen("psbt_export")

    // Sweep private key
    data object SweepPrivateKey : Screen("sweep_private_key")

    // Manual transaction broadcast
    data object BroadcastTransaction : Screen("broadcast_transaction")
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

// Bottom navigation items in order: Receive (left), Balance (center), Send (right)
val bottomNavItems =
    listOf(
        BottomNavItem(
            screen = Screen.Receive,
            title = "Receive",
            selectedIcon = Icons.AutoMirrored.Filled.CallReceived,
            unselectedIcon = Icons.AutoMirrored.Outlined.CallReceived,
        ),
        BottomNavItem(
            screen = Screen.Balance,
            title = "Balance",
            selectedIcon = Icons.Filled.AccountBalanceWallet,
            unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        ),
        BottomNavItem(
            screen = Screen.Send,
            title = "Send",
            selectedIcon = Icons.AutoMirrored.Filled.CallMade,
            unselectedIcon = Icons.AutoMirrored.Outlined.CallMade,
        ),
    )
