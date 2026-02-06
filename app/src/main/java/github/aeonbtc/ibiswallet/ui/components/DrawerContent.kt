package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

sealed class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val route: String
) {
    data object ManageWallets : DrawerItem(
        title = "Manage Wallets",
        icon = Icons.Default.AccountBalanceWallet,
        route = "manage_wallets"
    )
    
    data object ElectrumServer : DrawerItem(
        title = "Electrum Server",
        icon = Icons.Default.Cloud,
        route = "electrum_config"
    )
    
    data object Settings : DrawerItem(
        title = "Settings",
        icon = Icons.Default.Settings,
        route = "settings"
    )
    
    data object Security : DrawerItem(
        title = "Security",
        icon = Icons.Default.Lock,
        route = "security"
    )
    
    data object About : DrawerItem(
        title = "About",
        icon = Icons.Default.Info,
        route = "about"
    )
}

val drawerItems = listOf(
    DrawerItem.ManageWallets,
    DrawerItem.ElectrumServer,
    DrawerItem.Settings,
    DrawerItem.Security,
    DrawerItem.About
)

@Composable
fun DrawerContent(
    onItemClick: (DrawerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Ibis Wallet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HorizontalDivider(
                color = BorderColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Menu items
            drawerItems.forEach { item ->
                DrawerMenuItem(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Version info at bottom
            HorizontalDivider(
                color = BorderColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = BitcoinOrange,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
