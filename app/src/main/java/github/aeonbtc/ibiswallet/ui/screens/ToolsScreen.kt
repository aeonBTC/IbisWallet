package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenChecksumHelper: () -> Unit,
    onOpenBroadcast: () -> Unit,
    onOpenVerifyMessage: () -> Unit,
    onOpenDecoder: () -> Unit,
    onOpenSweepPrivateKey: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.tools_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        ToolCard(
            icon = Icons.Default.QrCode2,
            title = stringResource(R.string.tools_decoder_title),
            description = stringResource(R.string.tools_decoder_desc),
            onClick = onOpenDecoder,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ToolCard(
            icon = Icons.AutoMirrored.Filled.FactCheck,
            title = stringResource(R.string.tools_verify_message_title),
            description = stringResource(R.string.tools_verify_message_desc),
            onClick = onOpenVerifyMessage,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ToolCard(
            icon = Icons.Default.Password,
            title = stringResource(R.string.loc_380cec3b),
            description = stringResource(R.string.tools_sweep_desc),
            onClick = onOpenSweepPrivateKey,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ToolCard(
            icon = Icons.Default.Campaign,
            title = stringResource(R.string.loc_89bab973),
            description = stringResource(R.string.tools_broadcast_desc),
            onClick = onOpenBroadcast,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ToolCard(
            icon = Icons.Default.Key,
            title = stringResource(R.string.loc_7f2c91aa),
            description = stringResource(R.string.loc_3d8b44f1),
            onClick = onOpenChecksumHelper,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BitcoinOrange,
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
