package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DrawerIconColor
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.viewmodel.AppUpdateStatus
import androidx.compose.material3.Text

sealed class DrawerItem(
    val titleRes: Int,
    val icon: ImageVector,
) {
    data object ManageWallets : DrawerItem(
        titleRes = R.string.loc_bcb6fe62,
        icon = Icons.Default.AccountBalanceWallet,
    )

    data object ElectrumServer : DrawerItem(
        titleRes = R.string.loc_dc73d3bf,
        icon = Icons.Default.Dns,
    )

    data object Settings : DrawerItem(
        titleRes = R.string.loc_1c33c293,
        icon = Icons.Default.Settings,
    )

    data object Layer2Options : DrawerItem(
        titleRes = R.string.loc_2f73501f,
        icon = Icons.Default.Layers,
    )

    data object Security : DrawerItem(
        titleRes = R.string.loc_3cedb797,
        icon = Icons.Default.Lock,
    )

    data object BackupRestore : DrawerItem(
        titleRes = R.string.loc_db888836,
        icon = Icons.Default.SaveAlt,
    )

    data object About : DrawerItem(
        titleRes = R.string.loc_74350de7,
        icon = Icons.Default.Info,
    )
}

/** Base drawer items (always shown) */
private val baseDrawerItems =
    listOf(
        DrawerItem.ManageWallets,
        DrawerItem.ElectrumServer,
    )

/** Drawer items shown in the main menu */
fun getDrawerItems(): List<DrawerItem> = buildList {
    addAll(baseDrawerItems)
    add(DrawerItem.Security)
    add(DrawerItem.Settings)
    add(DrawerItem.Layer2Options)
    add(DrawerItem.BackupRestore)
    add(DrawerItem.About)
}

@Composable
fun DrawerContent(
    onItemClick: (DrawerItem) -> Unit,
    appUpdateStatus: AppUpdateStatus,
    onDownloadUpdateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val footerTextColor = TextSecondary.copy(alpha = 0.6f)

    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = DarkSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 24.dp),
        ) {
            // Header
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.loc_6bf5da9c),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                color = BorderColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Menu items
            getDrawerItems().forEach { item ->
                DrawerMenuItem(
                    item = item,
                    onClick = { onItemClick(item) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version info at bottom
            HorizontalDivider(
                color = BorderColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.drawer_version_short_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = footerTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (appUpdateStatus is AppUpdateStatus.UpdateAvailable) {
                    Spacer(modifier = Modifier.width(12.dp))

                    DrawerUpdateLabel(
                        appUpdateStatus = appUpdateStatus,
                        onDownloadUpdateClick = onDownloadUpdateClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerUpdateLabel(
    appUpdateStatus: AppUpdateStatus.UpdateAvailable,
    onDownloadUpdateClick: (String) -> Unit,
) {
    val contentDescription =
        stringResource(
            R.string.drawer_update_available,
            appUpdateStatus.latestVersionName,
        )
    val updateAvailableText =
        stringResource(
            R.string.drawer_update_available,
            appUpdateStatus.latestVersionName,
        )

    Text(
        text = updateAvailableText,
        style = MaterialTheme.typography.bodyMedium,
        color = BitcoinOrange,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDownloadUpdateClick(appUpdateStatus.releaseUrl) }
                .semantics {
                    this.contentDescription = contentDescription
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    onClick: () -> Unit,
) {
    val title = stringResource(item.titleRes)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = title,
            tint = DrawerIconColor,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
