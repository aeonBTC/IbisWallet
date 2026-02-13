package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary

/**
 * Wallet name + chevron that sits inside the TopAppBar title slot.
 * The actual panel is rendered separately via [WalletSelectorPanel].
 */
@Composable
fun WalletSelectorDropdown(
    activeWallet: StoredWallet?,
    wallets: List<StoredWallet>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelectWallet: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(
            enabled = wallets.size > 1,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        )
    ) {
        Text(
            text = activeWallet?.name ?: "Ibis Wallet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (wallets.size > 1) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select wallet",
                tint = TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }
    }
}

/**
 * Full-width panel that slides down below the top bar.
 * Uses the same [DarkBackground] so it feels like the header expanding.
 * Place this in a Box that overlays the screen content.
 */
@Composable
fun WalletSelectorPanel(
    activeWallet: StoredWallet?,
    wallets: List<StoredWallet>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectWallet: (String) -> Unit,
    onManageWallets: () -> Unit = {}
) {
    // Scrim + panel
    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            // Tap-to-dismiss scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            )

            // Panel at the top
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(200)
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(150)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {} // consume clicks so they don't hit scrim
                        )
                ) {
                    wallets.forEach { wallet ->
                        val isActive = wallet.id == activeWallet?.id
                        WalletPanelItem(
                            wallet = wallet,
                            isActive = isActive,
                            onClick = {
                                if (!isActive) {
                                    onSelectWallet(wallet.id)
                                }
                                onDismiss()
                            }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.06f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Manage Wallets option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onManageWallets()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Manage Wallets",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BitcoinOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletPanelItem(
    wallet: StoredWallet,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Check indicator for active wallet
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isActive) BitcoinOrange else Color.Transparent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = wallet.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isActive) BitcoinOrange
                            else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (wallet.isWatchOnly) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Watch-only",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = wallet.addressType.displayName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }
    }
}
