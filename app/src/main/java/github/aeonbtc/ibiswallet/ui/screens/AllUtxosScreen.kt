package github.aeonbtc.ibiswallet.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AllUtxosScreen(
    utxos: List<UtxoInfo>,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    spendUnconfirmed: Boolean = true,
    onFreezeUtxo: (String, Boolean) -> Unit = { _, _ -> },
    onSendFromUtxo: (UtxoInfo) -> Unit = {},
    onSaveLabel: (address: String, label: String) -> Unit = { _, _ -> },
    onDeleteLabel: (address: String) -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS

    // Local mutable state for immediate UI updates
    val localUtxos = remember(utxos) { mutableStateListOf(*utxos.toTypedArray()) }

    val totalBalance = localUtxos.filter { !it.isFrozen }.sumOf { it.amountSats.toLong() }.toULong()
    val frozenBalance = localUtxos.filter { it.isFrozen }.sumOf { it.amountSats.toLong() }.toULong()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 16.dp),
    ) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Spendable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(totalBalance, useSats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Text(
                                text = formatUsd((totalBalance.toDouble() / 100_000_000.0) * btcPrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Frozen",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(frozenBalance, useSats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (frozenBalance > 0UL) AccentBlue else TextSecondary,
                        )
                        if (btcPrice != null && btcPrice > 0 && frozenBalance > 0UL && !privacyMode) {
                            Text(
                                text = formatUsd((frozenBalance.toDouble() / 100_000_000.0) * btcPrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${localUtxos.size} UTXOs (${localUtxos.count { it.isFrozen }} frozen)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (localUtxos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No UTXOs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(localUtxos, key = { "${it.outpoint}_${it.isFrozen}" }) { utxo ->
                    UtxoCard(
                        utxo = utxo,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        spendUnconfirmed = spendUnconfirmed,
                        onSaveLabel = { label ->
                            onSaveLabel(utxo.address, label)
                            // Update all UTXOs sharing this address for immediate UI feedback
                            localUtxos.forEachIndexed { i, u ->
                                if (u.address == utxo.address) {
                                    localUtxos[i] = u.copy(label = label)
                                }
                            }
                        },
                        onDeleteLabel = {
                            onDeleteLabel(utxo.address)
                            localUtxos.forEachIndexed { i, u ->
                                if (u.address == utxo.address) {
                                    localUtxos[i] = u.copy(label = null)
                                }
                            }
                        },
                        onFreeze = {
                            // Update local state immediately for responsive UI
                            val index = localUtxos.indexOfFirst { it.outpoint == utxo.outpoint }
                            if (index >= 0) {
                                val newFrozenState = !utxo.isFrozen
                                localUtxos[index] = utxo.copy(isFrozen = newFrozenState)
                                // Persist the change
                                onFreezeUtxo(utxo.outpoint, newFrozenState)
                                // Show feedback
                                val message = if (newFrozenState) "UTXO frozen" else "UTXO unfrozen"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSend = { onSendFromUtxo(utxo) },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun UtxoCard(
    utxo: UtxoInfo,
    useSats: Boolean,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    spendUnconfirmed: Boolean = true,
    onSaveLabel: (String) -> Unit = {},
    onDeleteLabel: () -> Unit = {},
    onFreeze: () -> Unit,
    onSend: () -> Unit,
) {
    val context = LocalContext.current

    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedOutpoint by remember { mutableStateOf(false) }
    var isEditingLabel by remember(utxo.address) { mutableStateOf(false) }
    var labelDraft by remember(utxo.address, utxo.label) { mutableStateOf(utxo.label ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingLabel) {
        if (isEditingLabel) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Auto-dismiss copy notifications after 3 seconds
    LaunchedEffect(showCopiedAddress) {
        if (showCopiedAddress) {
            kotlinx.coroutines.delay(3000)
            showCopiedAddress = false
        }
    }
    LaunchedEffect(showCopiedOutpoint) {
        if (showCopiedOutpoint) {
            kotlinx.coroutines.delay(3000)
            showCopiedOutpoint = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (utxo.isFrozen) DarkCard.copy(alpha = 0.6f) else DarkCard,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Amount and frozen status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (utxo.isFrozen) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = "Frozen",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(utxo.amountSats, useSats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (utxo.isFrozen) AccentBlue else AccentGreen,
                    )
                    if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                        Text(
                            text = " · ${formatUsd((utxo.amountSats.toDouble() / 100_000_000.0) * btcPrice)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }

                // Confirmation status
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (utxo.isConfirmed) {
                                    AccentGreen.copy(alpha = 0.2f)
                                } else {
                                    BitcoinOrange.copy(alpha = 0.2f)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (utxo.isConfirmed) "Confirmed" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (utxo.isConfirmed) AccentGreen else BitcoinOrange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address
            Text(
                text = "Address:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = utxo.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                SecureClipboard.copyAndScheduleClear(context, "Address", utxo.address)
                                showCopiedAddress = true
                            },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopiedAddress) BitcoinOrange else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (showCopiedAddress) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Outpoint (txid:vout)
            Text(
                text = "Outpoint:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${utxo.txid.take(16)}...${utxo.txid.takeLast(8)}:${utxo.vout}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                SecureClipboard.copyAndScheduleClear(context, "Outpoint", utxo.outpoint)
                                showCopiedOutpoint = true
                            },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopiedOutpoint) BitcoinOrange else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (showCopiedOutpoint) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }

            // Label pill with inline editing
            Spacer(modifier = Modifier.height(4.dp))
            if (isEditingLabel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = labelDraft,
                        onValueChange = { if (it.length <= 50) labelDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val trimmed = labelDraft.trim()
                                if (trimmed.isNotEmpty()) onSaveLabel(trimmed)
                                isEditingLabel = false
                            },
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        BorderColor.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                if (labelDraft.isEmpty()) {
                                    Text(
                                        text = "Enter label",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary.copy(alpha = 0.5f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .focusRequester(focusRequester),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val trimmed = labelDraft.trim()
                                if (trimmed.isNotEmpty()) onSaveLabel(trimmed)
                                isEditingLabel = false
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasLabel = !utxo.label.isNullOrEmpty()
                    Card(
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .clickable {
                                labelDraft = utxo.label ?: ""
                                isEditingLabel = true
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasLabel) AccentTeal.copy(alpha = 0.15f) else DarkSurface,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasLabel) AccentTeal else BorderColor,
                        ),
                    ) {
                        Text(
                            text = utxo.label ?: "+ Label",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasLabel) AccentTeal else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (hasLabel) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onDeleteLabel() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove label",
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onFreeze,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (utxo.isFrozen) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (utxo.isFrozen) AccentGreen else AccentBlue,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (utxo.isFrozen) "Unfreeze" else "Freeze",
                        color = if (utxo.isFrozen) AccentGreen else AccentBlue,
                    )
                }

                val canSend = !utxo.isFrozen && (utxo.isConfirmed || spendUnconfirmed)
                TextButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (canSend) AccentGreen else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Send",
                        color = if (canSend) AccentGreen else TextSecondary,
                    )
                }
            }
        }
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatAmount(
    sats: ULong,
    useSats: Boolean,
): String {
    return if (useSats) {
        "${NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())} sats"
    } else {
        val btc = sats.toDouble() / 100_000_000.0
        "${String.format(Locale.US, "%.8f", btc)} BTC"
    }
}

private fun formatUsd(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}
