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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.clickable(
                enabled = wallets.size > 1,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            ),
    ) {
        Text(
            text = activeWallet?.name ?: "Ibis Wallet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (wallets.size > 1) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select wallet",
                tint = TextSecondary,
                modifier =
                    Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
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
    onManageWallets: () -> Unit = {},
    onFullSync: (StoredWallet) -> Unit = {},
    syncingWalletId: String? = null,
    lastFullSyncTimes: Map<String, Long?> = emptyMap(),
) {
    // Scrim + panel
    if (expanded) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(10f),
        ) {
            // Tap-to-dismiss scrim
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
            )

            // Panel at the top
            AnimatedVisibility(
                visible = true,
                enter =
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(200),
                    ) + fadeIn(animationSpec = tween(200)),
                exit =
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(150),
                    ) + fadeOut(animationSpec = tween(150)),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(DarkBackground)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {}, // consume clicks so they don't hit scrim
                            ),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                    ) {
                        items(wallets, key = { it.id }) { wallet ->
                            val isActive = wallet.id == activeWallet?.id
                            WalletPanelItem(
                                wallet = wallet,
                                isActive = isActive,
                                isSyncing = syncingWalletId == wallet.id,
                                lastFullSyncTime = lastFullSyncTimes[wallet.id],
                                onClick = {
                                    if (!isActive) {
                                        onSelectWallet(wallet.id)
                                    }
                                    onDismiss()
                                },
                                onSync = { onFullSync(wallet) },
                            )
                            HorizontalDivider(
                                color = BorderColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }

                    // Manage Wallets option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onManageWallets()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manage Wallets",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BitcoinOrange,
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
    isSyncing: Boolean = false,
    lastFullSyncTime: Long? = null,
    onClick: () -> Unit,
    onSync: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        // Check indicator for active wallet
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isActive) BitcoinOrange else Color.Transparent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = wallet.name,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color =
                        if (isActive) {
                            BitcoinOrange
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                val isWatchAddress = wallet.derivationPath == "single" && wallet.isWatchOnly
                val isPrivateKey = wallet.derivationPath == "single" && !wallet.isWatchOnly
                when {
                    isWatchAddress || wallet.isWatchOnly -> Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = if (isWatchAddress) "Watch Address" else "Watch Only",
                        tint =
                            if (isActive) {
                                BitcoinOrange
                            } else {
                                TextSecondary
                            },
                        modifier = Modifier.size(14.dp),
                    )
                    else -> Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = if (isPrivateKey) "Private Key" else "Seed Phrase",
                        tint =
                            if (isActive) {
                                BitcoinOrange
                            } else {
                                TextSecondary
                            },
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            val walletKind = when {
                wallet.derivationPath == "single" && wallet.isWatchOnly -> "Watch Address"
                wallet.derivationPath == "single" && !wallet.isWatchOnly -> "Private Key"
                wallet.isWatchOnly -> "Watch Only"
                else -> "Seed Phrase"
            }
            Text(
                text = "${wallet.addressType.displayName}  -  $walletKind",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = if (isActive) BitcoinOrange else TextSecondary,
            )
            if (wallet.derivationPath != "single" && wallet.masterFingerprint != null) {
                Text(
                    text = "Fingerprint: ${wallet.masterFingerprint}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = if (isActive) BitcoinOrange.copy(alpha = 0.8f) else TextTertiary.copy(alpha = 0.9f),
                )
            }
            val syncText =
                if (lastFullSyncTime != null) {
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    "Last full sync: ${formatter.format(Date(lastFullSyncTime))}"
                } else {
                    "Never fully synced"
                }
            Text(
                text = syncText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = if (isActive) BitcoinOrange.copy(alpha = 0.8f) else TextTertiary.copy(alpha = 0.9f),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Full sync button
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBackground.copy(alpha = 0.6f))
                    .clickable(enabled = !isSyncing) { onSync() },
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = BitcoinOrange,
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Full Sync",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
