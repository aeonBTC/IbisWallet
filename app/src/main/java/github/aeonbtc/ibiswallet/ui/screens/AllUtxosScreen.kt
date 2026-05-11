package github.aeonbtc.ibiswallet.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import androidx.compose.material3.Text

@Composable
fun AllUtxosScreen(
    utxos: List<UtxoInfo>,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    spendUnconfirmed: Boolean = true,
    addressEdgeCharacters: Int? = null,
    onFreezeUtxo: (String, Boolean) -> Unit = { _, _ -> },
    onSendFromUtxo: (UtxoInfo) -> Unit = {},
    onSaveLabel: (address: String, label: String) -> Unit = { _, _ -> },
    onDeleteLabel: (address: String) -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var searchQuery by remember { mutableStateOf("") }

    // Local mutable state for immediate UI updates
    val localUtxos = remember { mutableStateListOf<UtxoInfo>() }

    LaunchedEffect(utxos) {
        localUtxos.clear()
        localUtxos.addAll(utxos)
    }

    val totalBalance by remember {
        derivedStateOf {
            localUtxos.filter { !it.isFrozen && it.isLbtcOrUnknownAsset() }.sumOf { it.amountSats.toLong() }.toULong()
        }
    }
    val frozenBalance by remember {
        derivedStateOf {
            localUtxos.filter { it.isFrozen && it.isLbtcOrUnknownAsset() }.sumOf { it.amountSats.toLong() }.toULong()
        }
    }
    val filteredUtxos by remember {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isBlank()) {
                localUtxos.toList()
            } else {
                localUtxos.filter { utxo ->
                    utxoMatchesSearch(utxo, query)
                }
            }
        }
    }

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
                        .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.loc_ba1d1a3c),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(totalBalance, useSats, includeUnit = true),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Text(
                                text = formatFiat((totalBalance.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.loc_63722213),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(frozenBalance, useSats, includeUnit = true),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (frozenBalance > 0UL) AccentBlue else TextSecondary,
                        )
                        if (btcPrice != null && btcPrice > 0 && frozenBalance > 0UL && !privacyMode) {
                            Text(
                                text = formatFiat((frozenBalance.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text =
                        if (searchQuery.isBlank()) {
                            stringResource(
                                R.string.utxo_list_summary_with_frozen,
                                localUtxos.size,
                                localUtxos.count { it.isFrozen },
                            )
                        } else {
                            stringResource(
                                R.string.utxo_list_search_matches,
                                filteredUtxos.size,
                                localUtxos.size,
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.loc_ff1e339d),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.loc_2470de02),
                            tint = TextSecondary,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = DarkCard,
                    focusedContainerColor = DarkCard,
                    unfocusedBorderColor = DarkCard,
                    focusedBorderColor = BitcoinOrange,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredUtxos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (searchQuery.isBlank()) {
                            stringResource(R.string.loc_4bb9e286)
                        } else {
                            stringResource(R.string.loc_3a296c42)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredUtxos, key = { it.outpoint }) { utxo ->
                    UtxoCard(
                        utxo = utxo,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        fiatCurrency = fiatCurrency,
                        privacyMode = privacyMode,
                        spendUnconfirmed = spendUnconfirmed,
                        addressEdgeCharacters = addressEdgeCharacters,
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
                                val currentUtxo = localUtxos[index]
                                val newFrozenState = !currentUtxo.isFrozen
                                localUtxos[index] = currentUtxo.copy(isFrozen = newFrozenState)
                                // Persist the change
                                onFreezeUtxo(currentUtxo.outpoint, newFrozenState)
                                // Show feedback
                                val message =
                                    if (newFrozenState) {
                                        context.getString(R.string.loc_f1076a8d)
                                    } else {
                                        context.getString(R.string.loc_e1b46fb6)
                                    }
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
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    spendUnconfirmed: Boolean = true,
    addressEdgeCharacters: Int? = null,
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

    val displayAddress = remember(utxo.address, addressEdgeCharacters) {
        formatDisplayedAddress(utxo.address, addressEdgeCharacters)
    }
    val displayOutpoint = remember(utxo.txid, utxo.vout) {
        "${utxo.txid.take(16)}...${utxo.txid.takeLast(8)}:${utxo.vout}"
    }
    val copyStatusText =
        when {
            showCopiedAddress -> stringResource(R.string.loc_b6b10bfe)
            showCopiedOutpoint -> stringResource(R.string.loc_0fddb7b5)
            else -> null
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val assetId = utxo.assetId
            val isNonLbtcAsset = assetId != null && !LiquidAsset.isPolicyAsset(assetId)
            val resolvedAsset = utxo.assetId?.let { LiquidAsset.resolve(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (utxo.isFrozen) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = stringResource(R.string.loc_63722213),
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (isNonLbtcAsset && resolvedAsset != null) {
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAssetAmount(resolvedAsset, utxo.amountSats.toLong()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (utxo.isFrozen) AccentBlue else AccentGreen,
                        )
                    } else {
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(utxo.amountSats, useSats, includeUnit = true),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (utxo.isFrozen) AccentBlue else AccentGreen,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Text(
                                text = " · ${formatFiat((utxo.amountSats.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isNonLbtcAsset && resolvedAsset != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentTeal.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = resolvedAsset.ticker,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentTeal,
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
                        text = if (utxo.isConfirmed) stringResource(R.string.loc_4ab75d7f) else stringResource(R.string.loc_1b684325),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (utxo.isConfirmed) AccentGreen else BitcoinOrange,
                    )
                }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompactInfoRow(
                    label = stringResource(R.string.loc_c2f3561d),
                    value = displayAddress,
                    copied = showCopiedAddress,
                    modifier = Modifier.weight(1f),
                    onCopy = {
                        SecureClipboard.copyAndScheduleClear(
                            context,
                            utxo.address,
                        )
                        showCopiedAddress = true
                    },
                )

                CompactInfoRow(
                    label = stringResource(R.string.loc_cafbbb4a),
                    value = displayOutpoint,
                    copied = showCopiedOutpoint,
                    valueColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                    onCopy = {
                        SecureClipboard.copyAndScheduleClear(
                            context,
                            utxo.outpoint,
                        )
                        showCopiedOutpoint = true
                    },
                )
            }

            if (copyStatusText != null) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = copyStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
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
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        if (labelDraft.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.loc_822c6f45),
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val trimmed = labelDraft.trim()
                                        if (trimmed.isNotEmpty()) onSaveLabel(trimmed)
                                        isEditingLabel = false
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.loc_f55495e0),
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        EditableLabelChip(
                            label = utxo.label,
                            accentColor = AccentTeal,
                            onClick = {
                                labelDraft = utxo.label ?: ""
                                isEditingLabel = true
                            },
                            onDelete = onDeleteLabel,
                            maxWidth = 200.dp,
                            verticalPadding = 5.dp,
                        )
                    }
                }

                val canSend = !utxo.isFrozen && (utxo.isConfirmed || spendUnconfirmed)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onFreeze,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = if (utxo.isFrozen) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (utxo.isFrozen) AccentGreen else AccentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (utxo.isFrozen) stringResource(R.string.loc_543e40ca) else stringResource(R.string.loc_413df12c),
                            color = if (utxo.isFrozen) AccentGreen else AccentBlue,
                        )
                    }

                    TextButton(
                        onClick = onSend,
                        enabled = canSend,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (canSend) AccentGreen else TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.loc_074195f3),
                            color = if (canSend) AccentGreen else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInfoRow(
    label: String,
    value: String,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable(onClick = onCopy),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.common_copy_with_label, label),
                    tint = if (copied) BitcoinOrange else TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatDisplayedAddress(address: String, edgeCharacters: Int?): String {
    if (edgeCharacters == null || edgeCharacters <= 0) return address
    val minimumLengthToShorten = edgeCharacters * 2
    if (address.length <= minimumLengthToShorten) return address
    return "${address.take(edgeCharacters)}...${address.takeLast(edgeCharacters)}"
}

private fun utxoMatchesSearch(
    utxo: UtxoInfo,
    query: String,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true
    val normalizedAmountQuery = normalizeAmountSearchValue(query)
    val assetId = utxo.assetId
    val isNonLbtc = assetId != null && !LiquidAsset.isPolicyAsset(assetId)
    val amountTerms = buildList {
        add(utxo.amountSats.toString())
        add(NumberFormat.getNumberInstance(Locale.US).format(utxo.amountSats.toLong()))
        if (isNonLbtc) {
            val asset = LiquidAsset.resolve(assetId)
            add(formatAssetAmount(asset, utxo.amountSats.toLong()))
            add(asset.ticker)
        } else {
            add("${utxo.amountSats} sats")
            add("${NumberFormat.getNumberInstance(Locale.US).format(utxo.amountSats.toLong())} sats")
            add(String.format(Locale.US, "%.8f", utxo.amountSats.toDouble() / 100_000_000.0))
            add("${String.format(Locale.US, "%.8f", utxo.amountSats.toDouble() / 100_000_000.0)} btc")
        }
    }

    return utxo.address.lowercase().contains(normalizedQuery) ||
        utxo.label?.lowercase()?.contains(normalizedQuery) == true ||
        utxo.txid.lowercase().contains(normalizedQuery) ||
        utxo.outpoint.lowercase().contains(normalizedQuery) ||
        utxo.vout.toString().contains(normalizedQuery) ||
        amountTerms.any { normalizeAmountSearchValue(it).contains(normalizedAmountQuery) } ||
        (if (utxo.isConfirmed) "confirmed" else "pending").contains(normalizedQuery) ||
        (if (utxo.isFrozen) "frozen" else "spendable").contains(normalizedQuery) ||
        (if (isNonLbtc) "usdt" else "").contains(normalizedQuery)
}

private fun normalizeAmountSearchValue(value: String): String =
    value
        .trim()
        .lowercase()
        .replace(",", "")
        .replace(" ", "")

private fun UtxoInfo.isLbtcOrUnknownAsset(): Boolean =
    assetId?.let(LiquidAsset::isPolicyAsset) != false

private fun formatAssetAmount(asset: LiquidAsset, rawAmount: Long): String {
    val divisor = 10.0.pow(asset.precision.toDouble())
    val full = String.format(Locale.US, "%.${asset.precision}f", rawAmount.toDouble() / divisor)
    val trimmed = full.trimEnd('0').let { if (it.endsWith('.')) "${it}00" else it }
    val minDecimals = if (trimmed.contains('.') && trimmed.substringAfter('.').length < 2) {
        trimmed + "0".repeat(2 - trimmed.substringAfter('.').length)
    } else {
        trimmed
    }
    return "$minDecimals ${asset.ticker}"
}

