package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

/**
 * Shown on Bitcoin Balance / Send when the Electrum server is not connected.
 * Connects the active server when configured, otherwise opens Electrum settings.
 * Dismiss via Work offline stays until the next successful connection.
 */
@Composable
fun ElectrumConnectionBanner(
    isConnecting: Boolean,
    hasServerConfigured: Boolean,
    onConnect: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onWorkOffline: () -> Unit,
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
                        stringResource(R.string.electrum_status_connecting)
                    } else {
                        stringResource(R.string.electrum_not_connected)
                    },
                color = if (isConnecting) BitcoinOrange else ErrorRed,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    if (isConnecting) {
                        stringResource(R.string.electrum_connecting_hint)
                    } else {
                        stringResource(R.string.electrum_not_connected_hint)
                    },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isConnecting) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                                    stringResource(R.string.electrum_connect_server)
                                } else {
                                    stringResource(R.string.electrum_setup_server)
                                },
                            color = BitcoinOrange,
                        )
                    }
                    TextButton(onClick = onWorkOffline) {
                        Text(
                            text = stringResource(R.string.electrum_work_offline),
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
