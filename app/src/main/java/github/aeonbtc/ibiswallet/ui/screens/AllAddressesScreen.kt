package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import java.util.Locale
import kotlin.math.pow

private const val ADDRESS_DISPLAY_LIMIT = 20

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAddressesScreen(
    receiveAddresses: List<WalletAddress>,
    changeAddresses: List<WalletAddress>,
    usedAddresses: List<WalletAddress>,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    privacyMode: Boolean = false,
    accentColor: Color = BitcoinOrange,
    labelAccentColor: Color = AccentTeal,
    addressEdgeCharacters: Int? = null,
    addressMaxLines: Int = 3,
    useMultilineTruncatedAddress: Boolean = false,
    changeTabHelperText: String? = null,
    emptyChangeMessage: String? = null,
    assetUtxos: List<UtxoInfo> = emptyList(),
    onSaveLabel: (address: String, label: String) -> Unit = { _, _ -> },
    onDeleteLabel: (address: String) -> Unit = { },
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAllUsed by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var receiveDisplayCount by remember { mutableIntStateOf(ADDRESS_DISPLAY_LIMIT) }
    var changeDisplayCount by remember { mutableIntStateOf(ADDRESS_DISPLAY_LIMIT) }
    val tabs = listOf("Receive", "Change", "Used")
    val useSats = denomination == SecureStorage.DENOMINATION_SATS

    val assetBalancesByAddress = remember(assetUtxos) {
        val result = mutableMapOf<String, MutableMap<String, Long>>()
        assetUtxos.forEach { utxo ->
            val aid = utxo.assetId ?: return@forEach
            if (LiquidAsset.isPolicyAsset(aid)) return@forEach
            val addrMap = result.getOrPut(utxo.address) { mutableMapOf() }
            addrMap[aid] = (addrMap[aid] ?: 0L) + utxo.amountSats.toLong()
        }
        result
    }

    val receiveListState = rememberLazyListState()
    var scrollToAddress by remember { mutableStateOf<String?>(null) }
    var showQrForAddress by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Generate QR code when showing
    LaunchedEffect(showQrForAddress) {
        showQrForAddress?.let { address ->
            qrBitmap = generateQrBitmap(address)
        }
    }

    // QR Code Dialog
    if (showQrForAddress != null && qrBitmap != null) {
        ScrollableAlertDialog(
            onDismissRequest = {
                showQrForAddress = null
                qrBitmap = null
            },
            confirmButton = {
                TextButton(onClick = {
                    showQrForAddress = null
                    qrBitmap = null
                }) {
                    Text("Close", color = accentColor)
                }
            },
            title = {
                Text(
                    text = "Address QR Code",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(250.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = showQrForAddress ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = TextSecondary,
                    )
                }
            },
            containerColor = DarkCard,
        )
    }

    // Scroll to newly generated address when list updates
    LaunchedEffect(receiveAddresses, scrollToAddress) {
        scrollToAddress?.let { targetAddress ->
            val index = receiveAddresses.indexOfFirst { it.address == targetAddress }
            if (index >= 0) {
                receiveListState.animateScrollToItem(index)
                scrollToAddress = null
            }
        }
    }

    // Receive/Change: oldest-first, capped at displayCount; search bypasses the cap
    val displayedReceiveAddresses =
        remember(receiveAddresses, searchQuery, receiveDisplayCount) {
            if (searchQuery.isBlank()) {
                receiveAddresses.take(receiveDisplayCount)
            } else {
                val query = searchQuery.lowercase()
                receiveAddresses.filter { addr ->
                    addr.address.lowercase().contains(query) ||
                        addr.label?.lowercase()?.contains(query) == true
                }
            }
        }
    val displayedChangeAddresses =
        remember(changeAddresses, searchQuery, changeDisplayCount) {
            if (searchQuery.isBlank()) {
                changeAddresses.take(changeDisplayCount)
            } else {
                val query = searchQuery.lowercase()
                changeAddresses.filter { addr ->
                    addr.address.lowercase().contains(query) ||
                        addr.label?.lowercase()?.contains(query) == true
                }
            }
        }

    // Used: sort funded addresses to top (L-BTC or any asset), then empty
    val sortedUsedAddresses =
        remember(usedAddresses, assetBalancesByAddress) {
            usedAddresses.sortedWith(
                compareByDescending<WalletAddress> {
                    it.balanceSats > 0UL || assetBalancesByAddress.containsKey(it.address)
                }.thenByDescending { it.balanceSats },
            )
        }
    val filteredUsedAddresses =
        remember(sortedUsedAddresses, searchQuery) {
            if (searchQuery.isBlank()) {
                sortedUsedAddresses
            } else {
                val query = searchQuery.lowercase()
                sortedUsedAddresses.filter { addr ->
                    addr.address.lowercase().contains(query) ||
                        addr.label?.lowercase()?.contains(query) == true
                }
            }
        }

    // Apply limit on Used tab (only when not searching)
    val displayedUsedAddresses =
        remember(filteredUsedAddresses, showAllUsed, searchQuery) {
            if (searchQuery.isNotBlank() || showAllUsed) {
                filteredUsedAddresses
            } else {
                filteredUsedAddresses.take(ADDRESS_DISPLAY_LIMIT)
            }
        }

    val hasMoreUsedAddresses =
        searchQuery.isBlank() &&
            !showAllUsed &&
            filteredUsedAddresses.size > ADDRESS_DISPLAY_LIMIT

    val currentAddresses =
        when (selectedTab) {
            0 -> displayedReceiveAddresses
            1 -> displayedChangeAddresses
            2 -> displayedUsedAddresses
            else -> displayedReceiveAddresses
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 16.dp),
    ) {
        // Tab selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        showAllUsed = false
                        searchQuery = ""
                        receiveDisplayCount = ADDRESS_DISPLAY_LIMIT
                        changeDisplayCount = ADDRESS_DISPLAY_LIMIT
                    },
                    label = {
                        Text(
                            text = title,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search address or label") },
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
                            contentDescription = "Clear",
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
                    focusedBorderColor = accentColor,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 1 && !changeTabHelperText.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Text(
                    text = changeTabHelperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (currentAddresses.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        when {
                            searchQuery.isNotBlank() -> "No matching addresses"
                            selectedTab == 1 && !emptyChangeMessage.isNullOrBlank() -> emptyChangeMessage
                            else -> "No addresses"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = if (selectedTab == 0) receiveListState else rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(currentAddresses, key = { it.address }) { address ->
                    AddressCard(
                        address = address,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        accentColor = accentColor,
                        labelAccentColor = labelAccentColor,
                        addressEdgeCharacters = addressEdgeCharacters,
                        addressMaxLines = addressMaxLines,
                        useMultilineTruncatedAddress = useMultilineTruncatedAddress,
                        isUsedTab = selectedTab == 2,
                        addressAssetBalances = assetBalancesByAddress[address.address],
                        onShowQr = { showQrForAddress = address.address },
                        onCopy = { /* Handled inside AddressCard */ },
                        onSaveLabel = { label -> onSaveLabel(address.address, label) },
                        onDeleteLabel = { onDeleteLabel(address.address) },
                    )
                }

                // Show 20 more button for Receive tab
                if (selectedTab == 0 && searchQuery.isBlank() && receiveAddresses.size > receiveDisplayCount) {
                    item {
                        TextButton(
                            onClick = { receiveDisplayCount += ADDRESS_DISPLAY_LIMIT },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 1.dp),
                            contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
                        ) {
                            Text(
                                text = "Show 20 more",
                                color = accentColor,
                            )
                        }
                    }
                }

                // Show 20 more button for Change tab
                if (selectedTab == 1 && searchQuery.isBlank() && changeAddresses.size > changeDisplayCount) {
                    item {
                        TextButton(
                            onClick = { changeDisplayCount += ADDRESS_DISPLAY_LIMIT },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 1.dp),
                            contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
                        ) {
                            Text(
                                text = "Show 20 more",
                                color = accentColor,
                            )
                        }
                    }
                }

                // Show All button for Used tab
                if (selectedTab == 2 && hasMoreUsedAddresses) {
                    item {
                        TextButton(
                            onClick = { showAllUsed = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 1.dp),
                            contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
                        ) {
                            Text(
                                text = "Show All (${filteredUsedAddresses.size - ADDRESS_DISPLAY_LIMIT} more)",
                                color = accentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressCard(
    address: WalletAddress,
    useSats: Boolean,
    privacyMode: Boolean = false,
    accentColor: Color = BitcoinOrange,
    labelAccentColor: Color = AccentTeal,
    addressEdgeCharacters: Int? = null,
    addressMaxLines: Int = 3,
    useMultilineTruncatedAddress: Boolean = false,
    isUsedTab: Boolean = false,
    addressAssetBalances: Map<String, Long>? = null,
    onShowQr: () -> Unit,
    onCopy: () -> Unit,
    onSaveLabel: (String) -> Unit = {},
    onDeleteLabel: () -> Unit = {},
) {
    val context = LocalContext.current

    var showCopied by remember(address.address) { mutableStateOf(false) }
    var isEditingLabel by remember(address.address) { mutableStateOf(false) }
    var labelDraft by remember(address.address, address.label) { mutableStateOf(address.label ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Auto-dismiss copy notification after 3 seconds
    LaunchedEffect(showCopied, address.address) {
        if (showCopied) {
            kotlinx.coroutines.delay(3000)
            showCopied = false
        }
    }

    // Auto-focus the label field when editing starts
    LaunchedEffect(isEditingLabel) {
        if (isEditingLabel) focusRequester.requestFocus()
    }

    val typeName = if (address.keychain == KeychainType.EXTERNAL) "Receive" else "Change"
    val hasAnyBalance = address.balanceSats > 0UL || !addressAssetBalances.isNullOrEmpty()
    val displayAddress = remember(address.address, addressEdgeCharacters, useMultilineTruncatedAddress) {
        formatDisplayedAddress(
            address = address.address,
            edgeCharacters = addressEdgeCharacters,
            multiline = useMultilineTruncatedAddress,
        )
    }

    // Determine card background color
    val cardColor =
        if (isUsedTab) {
            if (hasAnyBalance) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
        } else {
            DarkCard
        }

    // Determine border color for used tab
    val borderColor =
        if (isUsedTab) {
            if (hasAnyBalance) SuccessGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
        } else {
            null
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header row: index + asset badges left, label right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$typeName #${address.index + 1u}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                    )
                    if (!addressAssetBalances.isNullOrEmpty()) {
                        addressAssetBalances.keys.forEach { assetId ->
                            val asset = LiquidAsset.resolve(assetId)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentTeal.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = asset.ticker,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = AccentTeal,
                                )
                            }
                        }
                    }
                }

                if (isEditingLabel) {
                    // Inline editor in the header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = labelDraft,
                            onValueChange = { if (it.length <= 50) labelDraft = it },
                            singleLine = true,
                            textStyle =
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        val trimmed = labelDraft.trim()
                                        if (trimmed.isNotEmpty()) onSaveLabel(trimmed)
                                        isEditingLabel = false
                                    },
                                ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier =
                                        Modifier
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
                            modifier =
                                Modifier
                                    .widthIn(max = 140.dp)
                                    .focusRequester(focusRequester),
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
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
                                contentDescription = "Save",
                                tint = SuccessGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    EditableLabelChip(
                        label = address.label,
                        accentColor = labelAccentColor,
                        onClick = {
                            labelDraft = address.label ?: ""
                            isEditingLabel = true
                        },
                        onDelete = onDeleteLabel,
                        maxWidth = 160.dp,
                        verticalPadding = 6.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address with copy and QR buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = addressMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { onShowQr() },
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Show QR",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                SecureClipboard.copyAndScheduleClear(context, "Address", address.address)
                                showCopied = true
                                onCopy()
                            },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopied) accentColor else TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (showCopied) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balance and transaction count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(address.balanceSats, useSats, includeUnit = true),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!addressAssetBalances.isNullOrEmpty() && !privacyMode) {
                        addressAssetBalances.forEach { (assetId, rawAmount) ->
                            val asset = LiquidAsset.resolve(assetId)
                            val formatted = formatLiquidAssetAmount(asset, rawAmount)
                            Text(
                                text = "$formatted ${asset.ticker}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = address.transactionCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

private fun formatDisplayedAddress(
    address: String,
    edgeCharacters: Int?,
    multiline: Boolean = false,
): String {
    if (edgeCharacters == null || edgeCharacters <= 0) return address
    val minimumLengthToShorten = edgeCharacters * 2
    if (address.length <= minimumLengthToShorten) return address
    return if (multiline) {
        "${address.take(edgeCharacters)}...\n${address.takeLast(edgeCharacters)}"
    } else {
        "${address.take(edgeCharacters)}...${address.takeLast(edgeCharacters)}"
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatLiquidAssetAmount(asset: LiquidAsset, rawAmount: Long): String {
    val divisor = 10.0.pow(asset.precision.toDouble())
    val full = String.format(Locale.US, "%.${asset.precision}f", rawAmount.toDouble() / divisor)
    val trimmed = full.trimEnd('0').let { if (it.endsWith('.')) "${it}00" else it }
    return if (trimmed.contains('.') && trimmed.substringAfter('.').length < 2) {
        trimmed + "0".repeat(2 - trimmed.substringAfter('.').length)
    } else {
        trimmed
    }
}

