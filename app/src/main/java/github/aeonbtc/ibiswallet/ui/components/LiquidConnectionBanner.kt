package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

/**
 * Shown on Liquid Balance / Send / Receive / Swap when the Liquid Electrum
 * server is not connected. Connects the active server when configured, otherwise
 * opens Liquid Electrum settings.
 */
@Composable
fun LiquidConnectionBanner(
    isConnecting: Boolean,
    hasServerConfigured: Boolean,
    onConnect: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text =
                    if (isConnecting) {
                        stringResource(R.string.liquid_status_connecting)
                    } else {
                        stringResource(R.string.liquid_not_connected)
                    },
                color = if (isConnecting) BitcoinOrange else ErrorRed,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    if (isConnecting) {
                        stringResource(R.string.liquid_connecting_hint)
                    } else {
                        stringResource(R.string.liquid_not_connected_hint)
                    },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isConnecting) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        if (hasServerConfigured) {
                            onConnect()
                        } else {
                            onOpenServerSettings()
                        }
                    },
                ) {
                    Text(
                        text =
                            if (hasServerConfigured) {
                                stringResource(R.string.liquid_connect_server)
                            } else {
                                stringResource(R.string.liquid_setup_server)
                            },
                        color = LiquidTeal,
                    )
                }
            }
        }
    }
}
