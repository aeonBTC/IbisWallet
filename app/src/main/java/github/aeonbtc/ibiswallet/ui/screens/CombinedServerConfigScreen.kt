package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

enum class ServerConfigSection {
    BITCOIN,
    LIQUID,
}

@Composable
fun CombinedServerConfigScreen(
    onBack: () -> Unit,
    initialSection: ServerConfigSection,
    showLiquidSection: Boolean,
    bitcoinContent: @Composable (Modifier) -> Unit,
    liquidContent: @Composable (Modifier) -> Unit,
) {
    var selectedSection by rememberSaveable(initialSection, showLiquidSection) {
        mutableStateOf(
            if (initialSection == ServerConfigSection.LIQUID && showLiquidSection) {
                ServerConfigSection.LIQUID
            } else {
                ServerConfigSection.BITCOIN
            },
        )
    }

    LaunchedEffect(showLiquidSection) {
        if (!showLiquidSection && selectedSection == ServerConfigSection.LIQUID) {
            selectedSection = ServerConfigSection.BITCOIN
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loc_dc73d3bf),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showLiquidSection) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkSurfaceVariant)
                        .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ServerTypeButton(
                    label = "Bitcoin",
                    accentColor = BitcoinOrange,
                    selected = selectedSection == ServerConfigSection.BITCOIN,
                    onClick = { selectedSection = ServerConfigSection.BITCOIN },
                    modifier = Modifier.weight(1f),
                )
                ServerTypeButton(
                    label = "Liquid",
                    accentColor = LiquidTeal,
                    selected = selectedSection == ServerConfigSection.LIQUID,
                    onClick = { selectedSection = ServerConfigSection.LIQUID },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedSection == ServerConfigSection.LIQUID && showLiquidSection) {
                liquidContent(Modifier.fillMaxSize())
            } else {
                bitcoinContent(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ServerTypeButton(
    label: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) accentColor else DarkSurfaceVariant,
        label = "serverSelectorBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) TextPrimary else TextSecondary,
        label = "serverSelectorContent",
    )
    Row(
        modifier =
            modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
