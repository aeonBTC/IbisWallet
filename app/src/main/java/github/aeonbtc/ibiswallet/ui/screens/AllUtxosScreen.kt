package github.aeonbtc.ibiswallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AllUtxosScreen(
    utxos: List<UtxoInfo>,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onFreezeUtxo: (String, Boolean) -> Unit = { _, _ -> },
    onSendFromUtxo: (UtxoInfo) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    
    // Local mutable state for immediate UI updates
    val localUtxos = remember(utxos) { mutableStateListOf(*utxos.toTypedArray()) }
    
    val totalBalance = localUtxos.filter { !it.isFrozen }.sumOf { it.amountSats.toLong() }.toULong()
    val frozenBalance = localUtxos.filter { it.isFrozen }.sumOf { it.amountSats.toLong() }.toULong()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Spendable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(totalBalance, useSats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Text(
                                text = formatUsd((totalBalance.toDouble() / 100_000_000.0) * btcPrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Frozen",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(frozenBalance, useSats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (frozenBalance > 0UL) AccentBlue else TextSecondary
                        )
                        if (btcPrice != null && btcPrice > 0 && frozenBalance > 0UL && !privacyMode) {
                            Text(
                                text = formatUsd((frozenBalance.toDouble() / 100_000_000.0) * btcPrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${localUtxos.size} UTXOs (${localUtxos.count { it.isFrozen }} frozen)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (localUtxos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No UTXOs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localUtxos, key = { "${it.outpoint}_${it.isFrozen}" }) { utxo ->
                    UtxoCard(
                        utxo = utxo,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
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
                        onSend = { onSendFromUtxo(utxo) }
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
    onFreeze: () -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedOutpoint by remember { mutableStateOf(false) }
    
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
        colors = CardDefaults.cardColors(
            containerColor = if (utxo.isFrozen) DarkCard.copy(alpha = 0.6f) else DarkCard
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Amount and frozen status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (utxo.isFrozen) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = "Frozen",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(utxo.amountSats, useSats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (utxo.isFrozen) AccentBlue else AccentGreen
                    )
                    if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                        Text(
                            text = " · ${formatUsd((utxo.amountSats.toDouble() / 100_000_000.0) * btcPrice)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                
                // Confirmation status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (utxo.isConfirmed) AccentGreen.copy(alpha = 0.2f)
                            else BitcoinOrange.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (utxo.isConfirmed) "Confirmed" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (utxo.isConfirmed) AccentGreen else BitcoinOrange
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Address
            Text(
                text = "Address:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = utxo.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Address", utxo.address)
                        clipboardManager.setPrimaryClip(clip)
                        showCopiedAddress = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopiedAddress) BitcoinOrange else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (showCopiedAddress) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange
                )
            }
            
            // Label if exists
            if (!utxo.label.isNullOrEmpty()) {
                Text(
                    text = "Label: ${utxo.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentTeal
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Outpoint (txid:vout)
            Text(
                text = "Outpoint:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${utxo.txid.take(16)}...${utxo.txid.takeLast(8)}:${utxo.vout}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Outpoint", utxo.outpoint)
                        clipboardManager.setPrimaryClip(clip)
                        showCopiedOutpoint = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopiedOutpoint) BitcoinOrange else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (showCopiedOutpoint) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onFreeze,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (utxo.isFrozen) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (utxo.isFrozen) AccentGreen else AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (utxo.isFrozen) "Unfreeze" else "Freeze",
                        color = if (utxo.isFrozen) AccentGreen else AccentBlue
                    )
                }
                
                TextButton(
                    onClick = onSend,
                    enabled = !utxo.isFrozen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (utxo.isFrozen) TextSecondary else AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Send",
                        color = if (utxo.isFrozen) TextSecondary else AccentGreen
                    )
                }
            }
        }
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatAmount(sats: ULong, useSats: Boolean): String {
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
