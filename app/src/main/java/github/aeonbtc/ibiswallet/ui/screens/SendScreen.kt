package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import kotlin.math.roundToLong
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.IbisWalletTheme
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import github.aeonbtc.ibiswallet.viewmodel.WalletUiState
import java.util.Locale


@Composable
fun SendScreen(
    walletState: WalletState = WalletState(),
    uiState: WalletUiState = WalletUiState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    utxos: List<UtxoInfo> = emptyList(),
    walletAddresses: Set<String> = emptySet(),
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    preSelectedUtxo: UtxoInfo? = null,
    spendUnconfirmed: Boolean = true,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    isWatchOnly: Boolean = false,
    draft: SendScreenDraft = SendScreenDraft(),
    onRefreshFees: () -> Unit = {},
    onClearPreSelectedUtxo: () -> Unit = {},
    onUpdateDraft: (SendScreenDraft) -> Unit = {},
    dryRunResult: DryRunResult? = null,
    onEstimateFee: (address: String, amountSats: ULong, feeRate: Float, selectedUtxos: List<UtxoInfo>?, isMaxSend: Boolean) -> Unit = { _, _, _, _, _ -> },
    onEstimateFeeMulti: (recipients: List<Recipient>, feeRate: Float, selectedUtxos: List<UtxoInfo>?) -> Unit = { _, _, _ -> },
    onClearDryRun: () -> Unit = {},
    onSend: (address: String, amountSats: ULong, feeRate: Float, selectedUtxos: List<UtxoInfo>?, label: String?, isMaxSend: Boolean, precomputedFeeSats: Long?) -> Unit = { _, _, _, _, _, _, _ -> },
    onSendMulti: (recipients: List<Recipient>, feeRate: Float, selectedUtxos: List<UtxoInfo>?, label: String?, precomputedFeeSats: Long?) -> Unit = { _, _, _, _, _ -> },
    onCreatePsbt: (address: String, amountSats: ULong, feeRate: Float, selectedUtxos: List<UtxoInfo>?, label: String?, isMaxSend: Boolean, precomputedFeeSats: Long?) -> Unit = { _, _, _, _, _, _, _ -> },
    onCreatePsbtMulti: (recipients: List<Recipient>, feeRate: Float, selectedUtxos: List<UtxoInfo>?, label: String?, precomputedFeeSats: Long?) -> Unit = { _, _, _, _, _ -> }
) {
    // Initialize state from draft
    var recipientAddress by remember { mutableStateOf(draft.recipientAddress) }
    var amountInput by remember { mutableStateOf(draft.amountInput) }
    var feeRate by remember { mutableFloatStateOf(draft.feeRate) }
    
    // USD input mode state (only available when btcPrice is available)
    var isUsdMode by remember { mutableStateOf(false) }
    
    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }
    
    // Coin control state
    var showCoinControl by remember { mutableStateOf(false) }
    val selectedUtxos = remember { mutableStateListOf<UtxoInfo>() }
    
    // Handle pre-selected UTXO from AllUtxosScreen
    LaunchedEffect(preSelectedUtxo) {
        if (preSelectedUtxo != null) {
            selectedUtxos.clear()
            selectedUtxos.add(preSelectedUtxo)
            onClearPreSelectedUtxo()
        }
    }
    
    // Multi-recipient mode
    var isMultiMode by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }
    // Each entry is (address, amountInput) pair — mutable state list for dynamic rows
    val multiRecipients = remember { mutableStateListOf<Pair<String, String>>() }
    
    // Max mode state (disabled in multi-recipient mode)
    var isMaxMode by remember { mutableStateOf(draft.isMaxSend) }
    
    // Label state
    var showLabelField by remember { mutableStateOf(draft.label.isNotEmpty()) }
    var labelText by remember { mutableStateOf(draft.label) }
    
    // Restore selected UTXOs from draft
    LaunchedEffect(draft.selectedUtxoOutpoints, utxos) {
        if (draft.selectedUtxoOutpoints.isNotEmpty() && selectedUtxos.isEmpty()) {
            val restoredUtxos = utxos.filter { it.outpoint in draft.selectedUtxoOutpoints }
            selectedUtxos.addAll(restoredUtxos)
        }
    }
    
    // Clear fields when draft is reset (after successful transaction)
    LaunchedEffect(draft) {
        if (draft.recipientAddress.isEmpty() && draft.amountInput.isEmpty() && 
            recipientAddress.isNotEmpty()) {
            // Draft was cleared, reset local state
            recipientAddress = ""
            amountInput = ""
            feeRate = 1.0f
            isUsdMode = false
            isMaxMode = false
            showLabelField = false
            labelText = ""
            selectedUtxos.clear()
        }
    }
    
    // Confirmation dialog state
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    // Address validation
    val addressValidationError = remember(recipientAddress) {
        validateBitcoinAddress(recipientAddress)
    }
    val isAddressValid = recipientAddress.isNotBlank() && addressValidationError == null
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    
    // Save draft whenever relevant state changes
    LaunchedEffect(recipientAddress, amountInput, labelText, feeRate, isMaxMode, selectedUtxos.toList()) {
        onUpdateDraft(
            SendScreenDraft(
                recipientAddress = recipientAddress,
                amountInput = amountInput,
                label = labelText,
                feeRate = feeRate,
                isMaxSend = isMaxMode,
                selectedUtxoOutpoints = selectedUtxos.map { it.outpoint }
            )
        )
    }
    
    // Filter out frozen UTXOs for coin control
    val spendableUtxos = remember(utxos) { utxos.filter { !it.isFrozen } }
    
    // Convert input to sats based on denomination or USD mode
    val amountSats = try {
        when {
            isUsdMode && btcPrice != null && btcPrice > 0 -> {
                // Input is in USD, convert to sats: USD / price * 100_000_000
                val usdAmount = amountInput.toDoubleOrNull() ?: 0.0
                (usdAmount / btcPrice) * 100_000_000
            }
            useSats -> {
                // Input is already in sats
                amountInput.replace(",", "").toLongOrNull()?.toDouble() ?: 0.0
            }
            else -> {
                // Input is in BTC, convert to sats
                (amountInput.toDoubleOrNull() ?: 0.0) * 100_000_000
            }
        }
    } catch (e: Exception) {
        0.0
    }
    
    // Calculate available balance based on coin control selection
    val availableSats = if (selectedUtxos.isNotEmpty()) {
        selectedUtxos.sumOf { it.amountSats.toLong() }
    } else {
        walletState.balanceSats.toLong()
    }
    
    // Build multi-recipient list for fee estimation
    val multiRecipientList = remember(multiRecipients.toList(), useSats, isUsdMode, btcPrice) {
        multiRecipients.mapNotNull { (addr, amt) ->
            val addrErr = validateBitcoinAddress(addr)
            if (addr.isNotBlank() && addrErr == null && amt.isNotBlank()) {
                val sats = try {
                    when {
                        isUsdMode && btcPrice != null && btcPrice > 0 ->
                            ((amt.toDoubleOrNull() ?: 0.0) / btcPrice * 100_000_000).roundToLong().toULong()
                        useSats -> amt.replace(",", "").toLongOrNull()?.toULong() ?: 0UL
                        else -> ((amt.toDoubleOrNull() ?: 0.0) * 100_000_000).roundToLong().toULong()
                    }
                } catch (_: Exception) { 0UL }
                if (sats > 0UL) Recipient(addr, sats) else null
            } else null
        }
    }
    val multiTotalSats = multiRecipientList.sumOf { it.amountSats.toLong() }
    
    // Fee estimation from BDK dry-run (sole source of truth)
    // The dry-run builds a real PSBT via TxBuilder.finish(), giving exact fees,
    // vBytes, change, and input count. No heuristic guessing needed.
    val dryRunOk = dryRunResult != null && !dryRunResult.isError
    val dryRunError = dryRunResult?.error
    val estimatedFeeSats = if (dryRunOk) dryRunResult!!.feeSats else null
    val estimatedVBytes = if (dryRunOk) dryRunResult!!.txVBytes else null
    
    // --- Single-mode: trigger BDK dry-run when address, amount, or fee rate changes ---
    val selectedUtxoSnapshot = selectedUtxos.toList()
    LaunchedEffect(recipientAddress, amountSats, feeRate, selectedUtxoSnapshot, isMaxMode, isMultiMode) {
        if (isMultiMode) return@LaunchedEffect  // handled by the multi-mode effect below
        if (!walletState.isInitialized) { onClearDryRun(); return@LaunchedEffect }
        kotlinx.coroutines.delay(150)
        if (isAddressValid && (amountSats > 0 || isMaxMode)) {
            onEstimateFee(
                recipientAddress,
                amountSats.roundToLong().toULong(),
                feeRate,
                if (selectedUtxoSnapshot.isNotEmpty()) selectedUtxoSnapshot else null,
                isMaxMode
            )
        } else {
            onClearDryRun()
        }
    }
    
    // --- Multi-mode: trigger BDK dry-run when recipients or fee rate changes ---
    LaunchedEffect(multiRecipientList, feeRate, selectedUtxoSnapshot, isMultiMode) {
        if (!isMultiMode) return@LaunchedEffect  // handled by the single-mode effect above
        if (!walletState.isInitialized) { onClearDryRun(); return@LaunchedEffect }
        kotlinx.coroutines.delay(150)
        if (multiRecipientList.isNotEmpty()) {
            onEstimateFeeMulti(
                multiRecipientList,
                feeRate,
                if (selectedUtxoSnapshot.isNotEmpty()) selectedUtxoSnapshot else null
            )
        } else {
            onClearDryRun()
        }
    }
    
    // Clear dry-run when switching modes so stale results don't linger.
    // Use a ref to skip the initial composition; only clear on actual mode switches.
    val prevMultiMode = remember { mutableStateOf(isMultiMode) }
    LaunchedEffect(isMultiMode) {
        if (prevMultiMode.value != isMultiMode) {
            prevMultiMode.value = isMultiMode
            onClearDryRun()
        }
    }
    
    val totalSpend = amountSats.roundToLong() + (estimatedFeeSats ?: 0L)
    val remainingAfterSend = if (amountSats > 0 && estimatedFeeSats != null) {
        maxOf(0L, availableSats - totalSpend)
    } else {
        availableSats
    }
    
    // Dry-run is the authority on whether funds are sufficient.
    val hasEnoughFunds = if (isMultiMode) {
        multiRecipientList.isNotEmpty() && dryRunOk
    } else {
        amountSats > 0 && dryRunOk
    }
    val canSend = walletState.isInitialized && 
                  (if (isMultiMode) multiRecipientList.size >= 2 else isAddressValid) &&
                  hasEnoughFunds && 
                  !uiState.isSending
    
    // --- Max mode: update displayed amount when dry-run returns exact recipient amount ---
    LaunchedEffect(feeRate, availableSats, isMaxMode, isUsdMode, dryRunResult, selectedUtxoSnapshot) {
        if (isMaxMode && !isMultiMode) {
            val maxSats = if (dryRunOk) {
                dryRunResult!!.recipientAmountSats
            } else {
                // Rough estimate while dry-run is pending
                maxOf(0L, availableSats - (feeRate.toLong() * 150))
            }
            amountInput = when {
                isUsdMode && btcPrice != null && btcPrice > 0 -> {
                    val usdValue = (maxSats.toDouble() / 100_000_000.0) * btcPrice
                    String.format(java.util.Locale.US, "%.2f", usdValue)
                }
                useSats -> maxSats.toString()
                else -> formatBtc(maxSats.toULong())
            }
        }
    }
    
    // Coin Control Dialog
    if (showCoinControl) {
        CoinControlDialog(
            utxos = spendableUtxos,
            selectedUtxos = selectedUtxos,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            spendUnconfirmed = spendUnconfirmed,
            onUtxoToggle = { utxo ->
                if (selectedUtxos.contains(utxo)) {
                    selectedUtxos.remove(utxo)
                } else {
                    selectedUtxos.add(utxo)
                }
            },
            onSelectAll = {
                selectedUtxos.clear()
                val selectableUtxos = if (spendUnconfirmed) spendableUtxos else spendableUtxos.filter { it.isConfirmed }
                selectedUtxos.addAll(selectableUtxos)
            },
            onClearAll = {
                selectedUtxos.clear()
            },
            onDismiss = { showCoinControl = false }
        )
    }
    
    // Multi-recipient editing dialog
    if (showMultiDialog) {
        MultiRecipientDialog(
            recipients = multiRecipients,
            useSats = useSats,
            isUsdMode = isUsdMode,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            availableSats = availableSats,
            estimatedFeeSats = estimatedFeeSats,
            dryRunError = dryRunError,
            validRecipientCount = multiRecipientList.size,
            totalSendingSats = multiTotalSats,
            onDone = { showMultiDialog = false },
            onDismiss = { showMultiDialog = false }
        )
    }
    
    // QR Scanner Dialog
    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                val bip21 = parseBip21Uri(code)
                recipientAddress = bip21.address
                
                // Set amount if present in URI
                bip21.amount?.let { btcAmount ->
                    isMaxMode = false
                    if (isUsdMode && btcPrice != null && btcPrice > 0) {
                        // Convert BTC to USD
                        val usdAmount = btcAmount * btcPrice
                        amountInput = String.format(Locale.US, "%.2f", usdAmount)
                    } else if (useSats) {
                        // Convert BTC to sats
                        amountInput = (btcAmount * 100_000_000).toLong().toString()
                    } else {
                        // Keep as BTC
                        amountInput = String.format(Locale.US, "%.8f", btcAmount).trimEnd('0').trimEnd('.')
                    }
                }
                
                // Set label if present in URI
                bip21.label?.let { label ->
                    labelText = label
                    showLabelField = true
                }
                
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }
    
    // Send Confirmation Dialog
    // Auto-close confirmation dialog when send completes (isSending goes false)
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isSending) {
        if (wasSending && !uiState.isSending) {
            showConfirmDialog = false
        }
        wasSending = uiState.isSending
    }
    
    if (showConfirmDialog) {
        val utxoSelection = if (selectedUtxos.isNotEmpty()) selectedUtxos.toList() else null
        val txLabel = if (showLabelField && labelText.isNotBlank()) labelText else null
        
        if (isMultiMode) {
            // Multi-recipient confirmation
            MultiRecipientConfirmationDialog(
                recipients = multiRecipientList,
                feeRate = feeRate,
                useSats = useSats,
                selectedUtxos = utxoSelection,
                privacyMode = privacyMode,
                isWatchOnly = isWatchOnly,
                dryRunResult = dryRunResult,
                label = txLabel,
                isSending = uiState.isSending,
                sendStatus = uiState.sendStatus,
                onConfirm = {
                    val fee = dryRunResult?.feeSats
                    if (isWatchOnly) {
                        showConfirmDialog = false
                        onCreatePsbtMulti(multiRecipientList, feeRate, utxoSelection, txLabel, fee)
                    } else {
                        onSendMulti(multiRecipientList, feeRate, utxoSelection, txLabel, fee)
                    }
                },
                onDismiss = { if (!uiState.isSending) showConfirmDialog = false }
            )
        } else {
            // Single-recipient confirmation
            val isSelfTransfer = recipientAddress in walletAddresses
            
            SendConfirmationDialog(
                recipientAddress = recipientAddress,
                amountSats = amountSats.roundToLong().toULong(),
                feeRate = feeRate,
                selectedUtxos = utxoSelection,
                useSats = useSats,
                privacyMode = privacyMode,
                label = txLabel,
                isSelfTransfer = isSelfTransfer,
                isWatchOnly = isWatchOnly,
                dryRunResult = dryRunResult,
                isSending = uiState.isSending,
                sendStatus = uiState.sendStatus,
                onConfirm = {
                    val address = recipientAddress
                    val amount = amountSats.roundToLong().toULong()
                    val fee = dryRunResult?.feeSats
                    if (isWatchOnly) {
                        showConfirmDialog = false
                        onCreatePsbt(address, amount, feeRate, utxoSelection, txLabel, isMaxMode, fee)
                    } else {
                        onSend(address, amount, feeRate, utxoSelection, txLabel, isMaxMode, fee)
                    }
                },
                onDismiss = { if (!uiState.isSending) showConfirmDialog = false }
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Send Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with title and coin control button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Send Bitcoin",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    // Coin Control button in header
                    if (spendableUtxos.isNotEmpty()) {
                        val isActive = selectedUtxos.isNotEmpty()
                        Card(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = walletState.isInitialized) { showCoinControl = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                            ),
                            border = BorderStroke(1.dp, if (isActive) BitcoinOrange else BorderColor)
                        ) {
                            Text(
                                text = if (isActive)
                                    "UTXOs (${selectedUtxos.size})"
                                else
                                    "Coin Control",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) BitcoinOrange else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Recipient Address header with Multiple toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recipient Address",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = walletState.isInitialized) {
                                if (!isMultiMode) {
                                    // Entering multi mode — seed from current single fields
                                    isMultiMode = true
                                    isMaxMode = false
                                    multiRecipients.clear()
                                    if (recipientAddress.isNotBlank() || amountInput.isNotBlank()) {
                                        multiRecipients.add(Pair(recipientAddress, amountInput))
                                    }
                                    multiRecipients.add(Pair("", ""))
                                    if (multiRecipients.size < 2) multiRecipients.add(Pair("", ""))
                                    showMultiDialog = true
                                } else {
                                    // Exiting multi mode
                                    isMultiMode = false
                                    multiRecipients.clear()
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMultiMode) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = BorderStroke(1.dp, if (isMultiMode) BitcoinOrange else BorderColor)
                    ) {
                        Text(
                            text = if (isMultiMode) "Multiple (${multiRecipientList.size})" else "Multiple",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMultiMode) BitcoinOrange else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                if (isMultiMode) {
                    // Summary of recipients — tap to edit
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMultiDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (multiRecipientList.isEmpty()) {
                                Text(
                                    text = "Tap to add recipients",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            } else {
                                multiRecipientList.forEachIndexed { i, r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = r.address.take(10) + "..." + r.address.takeLast(6),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(r.amountSats, useSats) + if (useSats) " sats" else " BTC",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange
                                        )
                                    }
                                    if (i < multiRecipientList.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total (${multiRecipientList.size} recipients)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(multiTotalSats.toULong(), useSats) + if (useSats) " sats" else " BTC",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to edit recipients",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                    
                    // --- Multi-recipient balance & fee summary ---
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Show dry-run error (insufficient funds, dust, etc.)
                    if (multiRecipientList.isNotEmpty() && dryRunError != null) {
                        Text(
                            text = dryRunError,
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    val multiFeeSats = estimatedFeeSats ?: 0L
                    val multiTotalWithFee = multiTotalSats + multiFeeSats
                    val multiRemaining = availableSats - multiTotalWithFee
                    val multiOverBudget = multiRemaining < 0 && multiRecipientList.isNotEmpty()
                    
                    // Available row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Available",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (privacyMode) HIDDEN_AMOUNT else "${formatAmount(availableSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                Text(
                                    text = " · ${formatUsd((availableSats.toDouble() / 100_000_000.0) * btcPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary.copy(alpha = 0.7f)
                                )
                            }
                            if (selectedUtxos.isNotEmpty()) {
                                Text(
                                    text = " (${selectedUtxos.size} UTXO${if (selectedUtxos.size > 1) "s" else ""})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    
                    // Fee row (only when dry-run has run)
                    if (estimatedFeeSats != null && multiRecipientList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Est. fee",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (privacyMode) HIDDEN_AMOUNT else "${formatAmount(estimatedFeeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange
                                )
                                if (estimatedVBytes != null) {
                                    Text(
                                        text = " (${formatVBytes(estimatedVBytes)} vB)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Remaining row
                    if (multiRecipientList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = if (privacyMode) HIDDEN_AMOUNT else "${formatAmount(maxOf(0L, multiRemaining).toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (multiOverBudget) AccentRed else TextSecondary
                            )
                        }
                    }
                } else {
                    // Single recipient
                    OutlinedTextField(
                        value = recipientAddress,
                        onValueChange = { recipientAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Enter Bitcoin address",
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showQrScanner = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR Code",
                                    tint = BitcoinOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        supportingText = if (addressValidationError != null && recipientAddress.isNotBlank()) {
                            {
                                Text(
                                    text = addressValidationError,
                                    color = AccentRed
                                )
                            }
                        } else null,
                        isError = addressValidationError != null && recipientAddress.isNotBlank(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (addressValidationError != null && recipientAddress.isNotBlank()) AccentRed else BitcoinOrange,
                            unfocusedBorderColor = if (addressValidationError != null && recipientAddress.isNotBlank()) AccentRed else BorderColor,
                            errorBorderColor = AccentRed,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange
                        ),
                        enabled = walletState.isInitialized
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Amount section (hidden in multi-recipient mode — each row has its own amount)
                if (!isMultiMode) {
                // Amount label with USD toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isUsdMode -> "Amount (USD)"
                            useSats -> "Amount (sats)"
                            else -> "Amount (BTC)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    // USD toggle button (only show if price is available)
                    if (btcPrice != null && btcPrice > 0) {
                        Card(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { 
                                    isUsdMode = !isUsdMode
                                    amountInput = "" // Clear input when switching modes
                                    isMaxMode = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUsdMode) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                            ),
                            border = BorderStroke(1.dp, if (isUsdMode) BitcoinOrange else BorderColor)
                        ) {
                            Text(
                                text = if (isUsdMode) "USD" else "USD",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) BitcoinOrange else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Calculate conversion text for display inside field
                val conversionText = if (amountInput.isNotEmpty() && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                    if (privacyMode) {
                        "≈ $HIDDEN_AMOUNT"
                    } else if (isUsdMode) {
                        "≈ ${formatAmount(amountSats.roundToLong().toULong(), useSats)} ${if (useSats) "sats" else "BTC"}"
                    } else {
                        val usdValue = (amountSats / 100_000_000.0) * btcPrice
                        "≈ ${formatUsd(usdValue)}"
                    }
                } else null
                
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { value ->
                        isMaxMode = false
                        when {
                            isUsdMode -> {
                                // USD input: allow decimals up to 2 places
                                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    amountInput = value
                                }
                            }
                            useSats -> {
                                if (value.isEmpty() || value.matches(Regex("^\\d*$"))) {
                                    amountInput = value
                                }
                            }
                            else -> {
                                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,8}$"))) {
                                    amountInput = value
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            when {
                                isUsdMode -> "0.00"
                                useSats -> "0"
                                else -> "0.00000000"
                            },
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = if (isUsdMode) {
                        { Text("$", color = TextSecondary) }
                    } else null,
                    suffix = if (conversionText != null) {
                        {
                            Text(
                                text = conversionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = BitcoinOrange
                            )
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isMaxMode) BitcoinOrange else BorderColor,
                        unfocusedBorderColor = if (isMaxMode) BitcoinOrange else BorderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = BitcoinOrange
                    ),
                    enabled = walletState.isInitialized
                )

                // Show dry-run error (insufficient funds, dust limit, etc.)
                if (amountInput.isNotBlank() && dryRunError != null && amountSats > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dryRunError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))

                // Available balance row with Max button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "${formatAmount(remainingAfterSend.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    // USD conversion
                    if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                        val usdValue = (remainingAfterSend.toDouble() / 100_000_000.0) * btcPrice
                        Text(
                            text = " · ${formatUsd(usdValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    if (selectedUtxos.isNotEmpty()) {
                        Text(
                            text = " (${selectedUtxos.size} UTXO${if (selectedUtxos.size > 1) "s" else ""})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val maxEnabled = walletState.isInitialized && availableSats > 0
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = maxEnabled) {
                                if (isMaxMode) {
                                    // Unclick max - clear amount
                                    isMaxMode = false
                                    amountInput = ""
                                } else {
                                    // Click max — sets the flag; the max-mode LaunchedEffect
                                    // will fill amountInput with the exact value from dry-run.
                                    // Use rough estimate as an immediate placeholder.
                                    isMaxMode = true
                                    val roughMaxSats = maxOf(0L, availableSats - (feeRate.toLong() * 150))
                                    amountInput = when {
                                        isUsdMode && btcPrice != null && btcPrice > 0 -> {
                                            val usdValue = (roughMaxSats.toDouble() / 100_000_000.0) * btcPrice
                                            String.format(java.util.Locale.US, "%.2f", usdValue)
                                        }
                                        useSats -> roughMaxSats.toString()
                                        else -> formatBtc(roughMaxSats.toULong())
                                    }
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMaxMode) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = BorderStroke(1.dp, if (isMaxMode) BitcoinOrange else BorderColor)
                    ) {
                        Text(
                            text = "Max",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMaxMode) BitcoinOrange else if (maxEnabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                } // end if (!isMultiMode)


                // Label row with button and inline field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = walletState.isInitialized) { showLabelField = !showLabelField },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (showLabelField || labelText.isNotBlank()) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                        ),
                        border = BorderStroke(1.dp, if (showLabelField || labelText.isNotBlank()) BitcoinOrange else BorderColor)
                    ) {
                        Text(
                            text = "Label",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showLabelField || labelText.isNotBlank()) BitcoinOrange else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    if (showLabelField) {
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            placeholder = {
                                Text(
                                    "e.g. Payment to Alice",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange
                            ),
                            enabled = walletState.isInitialized,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                val showFeeEstimate = if (isMultiMode) multiRecipientList.isNotEmpty() else (amountSats > 0 || isMaxMode)
                FeeRateSection(
                    feeEstimationState = feeEstimationState,
                    currentFeeRate = feeRate,
                    minFeeRate = minFeeRate,
                    onFeeRateChange = { feeRate = it },
                    onRefreshFees = onRefreshFees,
                    enabled = walletState.isInitialized,
                    estimatedFeeSats = if (showFeeEstimate) estimatedFeeSats else null,
                    estimatedVBytes = if (showFeeEstimate) estimatedVBytes else null,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    privacyMode = privacyMode
                )
            }
        }

                Spacer(modifier = Modifier.height(12.dp))

        if (walletState.isInitialized && !uiState.isConnected && !isWatchOnly) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WarningYellow.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = "Connect to an Electrum server to send transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        if (!walletState.isInitialized) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = WarningYellow.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = "Import a wallet first to send Bitcoin",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Watch-only wallets can create PSBTs without Electrum connection
        val isButtonEnabled = if (isWatchOnly) {
            canSend && walletState.isInitialized && !uiState.isSending
        } else {
            canSend && uiState.isConnected && !uiState.isSending
        }

        OutlinedButton(
            onClick = { showConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = isButtonEnabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BitcoinOrange,
                disabledContentColor = TextSecondary.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                1.dp,
                if (isButtonEnabled) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
            )
        ) {
            if (uiState.isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = BitcoinOrange,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isWatchOnly) "Review PSBT" else "Review Transaction")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Coin Control Dialog - allows user to select specific UTXOs to spend
 */
@Composable
private fun CoinControlDialog(
    utxos: List<UtxoInfo>,
    selectedUtxos: List<UtxoInfo>,
    useSats: Boolean,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    spendUnconfirmed: Boolean = true,
    onUtxoToggle: (UtxoInfo) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalSelected = selectedUtxos.sumOf { it.amountSats.toLong() }.toULong()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Coin Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val totalSelectedText = if (selectedUtxos.isNotEmpty()) {
                    if (privacyMode) {
                        "${selectedUtxos.size} selected • $HIDDEN_AMOUNT"
                    } else {
                        val baseText = "${selectedUtxos.size} selected • ${formatAmount(totalSelected, useSats)} ${if (useSats) "sats" else "BTC"}"
                        if (btcPrice != null && btcPrice > 0) {
                            val usdValue = (totalSelected.toDouble() / 100_000_000.0) * btcPrice
                            "$baseText · ${formatUsd(usdValue)}"
                        } else {
                            baseText
                        }
                    }
                } else {
                    "Select UTXOs to spend from"
                }
                Text(
                    text = totalSelectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedUtxos.isNotEmpty()) BitcoinOrange else TextSecondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select All", color = BitcoinOrange)
                    }
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        enabled = selectedUtxos.isNotEmpty()
                    ) {
                        Text(
                            "Clear All",
                            color = if (selectedUtxos.isNotEmpty()) TextSecondary else TextSecondary.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(utxos, key = { it.outpoint }) { utxo ->
                        val isSelected = selectedUtxos.contains(utxo)
                        val isDisabled = !utxo.isConfirmed && !spendUnconfirmed
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isDisabled) Modifier
                                    else Modifier.clickable { onUtxoToggle(utxo) }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isDisabled -> DarkCard.copy(alpha = 0.4f)
                                    isSelected -> BitcoinOrange.copy(alpha = 0.15f)
                                    else -> DarkCard
                                }
                            ),
                            border = if (isSelected && !isDisabled) 
                                BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f))
                            else 
                                null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected && !isDisabled) 
                                        Icons.Default.CheckCircle 
                                    else 
                                        Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = when {
                                        isDisabled -> TextSecondary.copy(alpha = 0.3f)
                                        isSelected -> BitcoinOrange
                                        else -> TextSecondary
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(utxo.amountSats, useSats) + if (useSats) " sats" else " BTC",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDisabled) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onBackground
                                        )
                                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                            val usdValue = (utxo.amountSats.toDouble() / 100_000_000.0) * btcPrice
                                            Text(
                                                text = " · ${formatUsd(usdValue)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${utxo.address.take(12)}...${utxo.address.takeLast(8)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isDisabled) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    if (!utxo.label.isNullOrEmpty()) {
                                        Text(
                                            text = "Label: ${utxo.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDisabled) AccentTeal.copy(alpha = 0.4f) else AccentTeal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (utxo.isConfirmed) 
                                                SuccessGreen.copy(alpha = if (isDisabled) 0.1f else 0.2f)
                                            else 
                                                BitcoinOrange.copy(alpha = if (isDisabled) 0.1f else 0.2f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (utxo.isConfirmed) "Confirmed" else "Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (utxo.isConfirmed) 
                                            SuccessGreen.copy(alpha = if (isDisabled) 0.4f else 1f)
                                        else 
                                            BitcoinOrange.copy(alpha = if (isDisabled) 0.4f else 1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange
                    )
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Send Confirmation Dialog - shows transaction details before sending
 */
@Composable
private fun SendConfirmationDialog(
    recipientAddress: String,
    amountSats: ULong,
    feeRate: Float,
    selectedUtxos: List<UtxoInfo>?,
    useSats: Boolean,
    privacyMode: Boolean = false,
    label: String? = null,
    isSelfTransfer: Boolean = false,
    isWatchOnly: Boolean = false,
    dryRunResult: DryRunResult? = null,
    isSending: Boolean = false,
    sendStatus: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Dry-run result is the sole source of truth for fee/change/vBytes.
    // The confirmation dialog is only reachable when canSend==true, which requires dryRunOk.
    val estimatedFeeSats = dryRunResult?.feeSats ?: 0L
    val estimatedVBytes = dryRunResult?.txVBytes ?: 0.0
    val actualChangeSats = dryRunResult?.changeSats ?: 0L
    val hasChange = dryRunResult?.hasChange ?: false
    val totalSats = amountSats.toLong() + estimatedFeeSats
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isWatchOnly) "Confirm PSBT" else "Confirm Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Recipient
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSelfTransfer) "Destination" else "Sending To",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    if (isSelfTransfer) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Self-transfer)",
                            style = MaterialTheme.typography.labelMedium,
                            color = BorderColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recipientAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Label (if provided)
                if (!label.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Label",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentTeal
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sending:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = recipientAddress.take(8)+ "..." + recipientAddress.takeLast(8),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "${if (isSelfTransfer) "+" else "-"}${formatAmount(amountSats, useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelfTransfer) SuccessGreen else AccentRed
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Change (if applicable)
                if (hasChange && actualChangeSats > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Change:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Returned to your wallet",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else "+${formatAmount(actualChangeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SuccessGreen
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Network Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "${formatFeeRate(feeRate)} sat/vB • ${formatVBytes(estimatedVBytes)} vB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(estimatedFeeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BitcoinOrange
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isSelfTransfer) "Total" else "Total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(if (isSelfTransfer) estimatedFeeSats.toULong() else totalSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                }
                
                // Selected UTXOs info
                if (selectedUtxos != null && selectedUtxos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Spending from ${selectedUtxos.size} selected UTXO${if (selectedUtxos.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Confirm button
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        contentColor = DarkBackground,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.5f),
                        disabledContentColor = DarkBackground.copy(alpha = 0.7f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWatchOnly) "Create PSBT" else "Send Bitcoin",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                // Send status text
                if (isSending && sendStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sendStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending
                ) {
                    Text(
                        text = "Cancel",
                        color = if (isSending) TextSecondary.copy(alpha = 0.3f) else TextSecondary
                    )
                }
            }
        }
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatBtc(sats: ULong): String {
    val btc = sats.toDouble() / 100_000_000.0
    return String.format(Locale.US, "%.8f", btc)
}

private fun formatSats(sats: ULong): String {
    return java.text.NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())
}

private fun formatAmount(sats: ULong, useSats: Boolean): String {
    return if (useSats) {
        formatSats(sats)
    } else {
        formatBtc(sats)
    }
}

private fun formatUsd(amount: Double): String {
    val format = java.text.NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

private fun formatFeeRate(rate: Double): String {
    // Format up to 2 decimal places, trim trailing zeros
    val formatted = String.format(Locale.US, "%.2f", rate)
    return formatted.trimEnd('0').trimEnd('.')
}

private fun formatFeeRate(rate: Float): String = formatFeeRate(rate.toDouble())

private fun formatVBytes(vBytes: Double): String {
    // Show up to 2 decimal places, trim trailing zeros: 256.75, 140.5, 154
    val formatted = String.format(Locale.US, "%.2f", vBytes)
    return formatted.trimEnd('0').trimEnd('.')
}

private enum class FeeRateOption {
    FASTEST,
    HALF_HOUR,
    HOUR,
    CUSTOM
}

@Composable
private fun FeeRateSection(
    feeEstimationState: FeeEstimationResult,
    currentFeeRate: Float,
    minFeeRate: Double,
    onFeeRateChange: (Float) -> Unit,
    onRefreshFees: () -> Unit,
    enabled: Boolean,
    estimatedFeeSats: Long? = null,
    estimatedVBytes: Double? = null,
    useSats: Boolean = true,
    btcPrice: Double? = null,
    privacyMode: Boolean = false
) {
    var selectedOption by remember { mutableStateOf(FeeRateOption.HALF_HOUR) }
    var customFeeInput by remember { mutableStateOf<String?>(null) }
    
    val minFeeFloat = minFeeRate.toFloat()
    
    val estimates = (feeEstimationState as? FeeEstimationResult.Success)?.estimates
    
    androidx.compose.runtime.LaunchedEffect(estimates) {
        if (estimates != null && selectedOption != FeeRateOption.CUSTOM) {
            val newRate = when (selectedOption) {
                FeeRateOption.FASTEST -> estimates.fastestFee.toFloat()
                FeeRateOption.HALF_HOUR -> estimates.halfHourFee.toFloat()
                FeeRateOption.HOUR -> estimates.hourFee.toFloat()
                FeeRateOption.CUSTOM -> currentFeeRate
            }
            onFeeRateChange(newRate.coerceAtLeast(minFeeFloat))
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fee Rate",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            
            if (feeEstimationState !is FeeEstimationResult.Disabled) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable(enabled = enabled && feeEstimationState !is FeeEstimationResult.Loading) {
                            onRefreshFees()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh fees",
                        tint = if (feeEstimationState is FeeEstimationResult.Loading) 
                            TextSecondary.copy(alpha = 0.5f) 
                        else 
                            BitcoinOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // Show estimated fee when amount is entered
        if (estimatedFeeSats != null && estimatedVBytes != null) {
            val vBytesDisplay = formatVBytes(estimatedVBytes)
            val usdFee = if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                " · ${formatUsd((estimatedFeeSats.toDouble() / 100_000_000.0) * btcPrice)}"
            } else ""
            Text(
                text = if (privacyMode) "Est. fee: $HIDDEN_AMOUNT ($vBytesDisplay vB)" else "Est. fee: ${formatAmount(estimatedFeeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}$usdFee ($vBytesDisplay vB)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        when (feeEstimationState) {
            is FeeEstimationResult.Disabled -> {
                if (customFeeInput == null) {
                    customFeeInput = formatFeeRate(currentFeeRate)
                }
                ManualFeeInput(
                    value = customFeeInput ?: "",
                    onValueChange = { input ->
                        customFeeInput = input
                        input.toFloatOrNull()?.let { onFeeRateChange(it.coerceAtLeast(minFeeFloat)) }
                    },
                    enabled = enabled,
                    minFeeRate = minFeeRate
                )
            }
            
            is FeeEstimationResult.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BitcoinOrange,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading fee estimates...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            
            is FeeEstimationResult.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = WarningYellow.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Failed to load fee estimates",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow
                        )
                        Text(
                            text = feeEstimationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))

                if (customFeeInput == null) {
                    customFeeInput = formatFeeRate(currentFeeRate)
                }
                ManualFeeInput(
                    value = customFeeInput ?: "",
                    onValueChange = { input ->
                        customFeeInput = input
                        input.toFloatOrNull()?.let { onFeeRateChange(it.coerceAtLeast(minFeeFloat)) }
                    },
                    enabled = enabled,
                    minFeeRate = minFeeRate
                )
            }
            
            is FeeEstimationResult.Success -> {
                val feeEstimates = feeEstimationState.estimates
                val isElectrum = feeEstimates.source == FeeEstimateSource.ELECTRUM_SERVER
                
                // Electrum targets are wider (2/6/12 blocks) vs mempool.space (1/3/6)
                val fastLabel = if (isElectrum) "~2 blocks" else "~1 block"
                val medLabel = if (isElectrum) "~6 blocks" else "~3 blocks"
                val slowLabel = if (isElectrum) "~12 blocks" else "~6 blocks"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeeTargetButton(
                        label = fastLabel,
                        feeRate = feeEstimates.fastestFee,
                        isSelected = selectedOption == FeeRateOption.FASTEST,
                        onClick = {
                            selectedOption = FeeRateOption.FASTEST
                            customFeeInput = null
                            onFeeRateChange(feeEstimates.fastestFee.toFloat().coerceAtLeast(minFeeFloat))
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                    
                    FeeTargetButton(
                        label = medLabel,
                        feeRate = feeEstimates.halfHourFee,
                        isSelected = selectedOption == FeeRateOption.HALF_HOUR,
                        onClick = {
                            selectedOption = FeeRateOption.HALF_HOUR
                            customFeeInput = null
                            onFeeRateChange(feeEstimates.halfHourFee.toFloat().coerceAtLeast(minFeeFloat))
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                    
                    FeeTargetButton(
                        label = slowLabel,
                        feeRate = feeEstimates.hourFee,
                        isSelected = selectedOption == FeeRateOption.HOUR,
                        onClick = {
                            selectedOption = FeeRateOption.HOUR
                            customFeeInput = null
                            onFeeRateChange(feeEstimates.hourFee.toFloat().coerceAtLeast(minFeeFloat))
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Hint when Electrum estimates are uniform (low-fee mempool)
                if (isElectrum && feeEstimates.isUniform) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Server reports same rate for all targets (low fee environment)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))

                if (selectedOption == FeeRateOption.CUSTOM) {
                    if (customFeeInput == null) {
                        customFeeInput = formatFeeRate(currentFeeRate)
                    }
                    ManualFeeInput(
                        value = customFeeInput ?: "",
                        onValueChange = { input ->
                            customFeeInput = input
                            input.toFloatOrNull()?.let { onFeeRateChange(it.coerceAtLeast(minFeeFloat)) }
                        },
                        enabled = enabled,
                        minFeeRate = minFeeRate
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) {
                                selectedOption = FeeRateOption.CUSTOM
                                customFeeInput = formatFeeRate(currentFeeRate)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeTargetButton(
    label: String,
    feeRate: Double,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
    val borderColor = if (isSelected) BitcoinOrange else BorderColor
    val textColor = if (isSelected) BitcoinOrange else TextSecondary
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else TextSecondary
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = formatFeeRate(feeRate),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = "sat/vB",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ManualFeeInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    minFeeRate: Double = 1.0
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty()) {
                onValueChange(input)
                return@OutlinedTextField
            }
            
            val isValidFormat = input.matches(Regex("^\\d*\\.?\\d{0,2}$"))
            val hasInvalidLeadingZeros = input.length > 1 && 
                input.startsWith("0") && 
                !input.startsWith("0.")
            
            if (isValidFormat && !hasInvalidLeadingZeros) {
                onValueChange(input)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Fee rate (sat/vB)") },
        placeholder = { Text(formatFeeRate(minFeeRate), color = TextSecondary.copy(alpha = 0.5f)) },
        supportingText = {
            Text(
                text = "Minimum: ${formatFeeRate(minFeeRate)} sat/vB",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.7f)
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BitcoinOrange,
            unfocusedBorderColor = BorderColor,
            focusedLabelColor = BitcoinOrange,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = BitcoinOrange
        )
    )
}

/**
 * Parsed BIP21 URI data
 */
private data class Bip21Uri(
    val address: String,
    val amount: Double? = null,  // Amount in BTC
    val label: String? = null,
    val message: String? = null
)

/**
 * Parse a BIP21 URI or plain Bitcoin address
 * Format: bitcoin:<address>[?amount=<amount>][&label=<label>][&message=<message>]
 */
private fun parseBip21Uri(input: String): Bip21Uri {
    val trimmed = input.trim()
    
    // Check if it's a BIP21 URI
    if (!trimmed.lowercase().startsWith("bitcoin:")) {
        // Plain address
        return Bip21Uri(address = trimmed)
    }
    
    // Remove "bitcoin:" prefix (case-insensitive)
    val withoutPrefix = trimmed.substring(8)
    
    // Split address and query parameters
    val parts = withoutPrefix.split("?", limit = 2)
    val address = parts[0]
    
    if (parts.size == 1) {
        // No query parameters
        return Bip21Uri(address = address)
    }
    
    // Parse query parameters
    val queryString = parts[1]
    val params = queryString.split("&").associate { param ->
        val keyValue = param.split("=", limit = 2)
        val key = keyValue[0].lowercase()
        val value = if (keyValue.size > 1) {
            java.net.URLDecoder.decode(keyValue[1], "UTF-8")
        } else {
            ""
        }
        key to value
    }
    
    return Bip21Uri(
        address = address,
        amount = params["amount"]?.toDoubleOrNull(),
        label = params["label"]?.takeIf { it.isNotBlank() },
        message = params["message"]?.takeIf { it.isNotBlank() }
    )
}

/**
 * Validate a Bitcoin address checksum
 * Returns null if valid, or an error message if invalid
 */
private fun validateBitcoinAddress(address: String): String? {
    if (address.isBlank()) return null // Empty is not an error yet
    
    val trimmed = address.trim()
    
    return when {
        // Legacy P2PKH (starts with 1)
        trimmed.startsWith("1") -> validateBase58CheckAddress(trimmed)
        // Legacy P2SH (starts with 3)
        trimmed.startsWith("3") -> validateBase58CheckAddress(trimmed)
        // SegWit (starts with bc1q)
        trimmed.lowercase().startsWith("bc1q") -> validateBech32Address(trimmed, false)
        // Taproot (starts with bc1p)
        trimmed.lowercase().startsWith("bc1p") -> validateBech32Address(trimmed, true)
        // Testnet addresses
        trimmed.startsWith("m") || trimmed.startsWith("n") -> validateBase58CheckAddress(trimmed)
        trimmed.startsWith("2") -> validateBase58CheckAddress(trimmed)
        trimmed.lowercase().startsWith("tb1") -> validateBech32Address(trimmed, trimmed.lowercase().startsWith("tb1p"))
        // Unknown format
        else -> "Invalid address format"
    }
}

/**
 * Validate Base58Check encoded address (Legacy P2PKH and P2SH)
 */
private fun validateBase58CheckAddress(address: String): String? {
    val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    
    // Check length
    if (address.length < 25 || address.length > 35) {
        return "Invalid address length"
    }
    
    // Check for invalid characters
    for (char in address) {
        if (char !in base58Alphabet) {
            return "Invalid character: $char"
        }
    }
    
    // Decode Base58
    var num = java.math.BigInteger.ZERO
    for (char in address) {
        val digit = base58Alphabet.indexOf(char)
        num = num.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit.toLong()))
    }
    
    // Convert to bytes (25 bytes for standard address)
    val decoded = num.toByteArray()
    
    // Handle leading zeros in Base58
    val leadingZeros = address.takeWhile { it == '1' }.length
    val fullDecoded = ByteArray(leadingZeros) + if (decoded[0] == 0.toByte() && decoded.size > 25) {
        decoded.drop(1).toByteArray()
    } else {
        decoded
    }
    
    if (fullDecoded.size < 25) {
        return "Invalid address encoding"
    }
    
    // Split into payload and checksum
    val payload = fullDecoded.takeLast(25).take(21).toByteArray()
    val checksum = fullDecoded.takeLast(4).toByteArray()
    
    // Calculate expected checksum (double SHA256)
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash1 = digest.digest(payload)
    val hash2 = digest.digest(hash1)
    val expectedChecksum = hash2.take(4).toByteArray()
    
    return if (checksum.contentEquals(expectedChecksum)) {
        null // Valid
    } else {
        "Invalid checksum"
    }
}

/**
 * Validate Bech32/Bech32m encoded address (SegWit and Taproot)
 */
private fun validateBech32Address(address: String, isBech32m: Boolean): String? {
    val bech32Charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    val bech32mConst = 0x2bc830a3
    val bech32Const = 1
    
    val lower = address.lowercase()
    
    // Check for mixed case
    if (address != lower && address != address.uppercase()) {
        return "Mixed case not allowed"
    }
    
    // Find separator
    val separatorPos = lower.lastIndexOf('1')
    if (separatorPos < 1 || separatorPos + 7 > lower.length || lower.length > 90) {
        return "Invalid format"
    }
    
    val hrp = lower.substring(0, separatorPos)
    val data = lower.substring(separatorPos + 1)
    
    // Check data characters
    val values = mutableListOf<Int>()
    for (char in data) {
        val index = bech32Charset.indexOf(char)
        if (index == -1) {
            return "Invalid character: $char"
        }
        values.add(index)
    }
    
    // Verify checksum using polymod
    fun polymod(values: List<Int>): Int {
        val generator = listOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) {
                if ((top shr i) and 1 == 1) {
                    chk = chk xor generator[i]
                }
            }
        }
        return chk
    }
    
    fun hrpExpand(hrp: String): List<Int> {
        val result = mutableListOf<Int>()
        for (char in hrp) {
            result.add(char.code shr 5)
        }
        result.add(0)
        for (char in hrp) {
            result.add(char.code and 31)
        }
        return result
    }
    
    val checksum = polymod(hrpExpand(hrp) + values)
    val expectedConst = if (isBech32m) bech32mConst else bech32Const
    
    return if (checksum == expectedConst) {
        null // Valid
    } else {
        "Invalid checksum"
    }
}

/**
 * Full-screen dialog for editing multiple recipients.
 * Scrollable list of address+amount rows with add/remove.
 */
@Composable
private fun MultiRecipientDialog(
    recipients: MutableList<Pair<String, String>>,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    availableSats: Long = 0L,
    estimatedFeeSats: Long? = null,
    dryRunError: String? = null,
    validRecipientCount: Int = 0,
    totalSendingSats: Long = 0L,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recipients (${recipients.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onDone) {
                        Text("Done", color = BitcoinOrange)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable list of recipient rows
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    recipients.forEachIndexed { index, (addr, amt) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    if (recipients.size > 2) {
                                        TextButton(
                                            onClick = { recipients.removeAt(index) },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                "Remove",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentRed
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = addr,
                                    onValueChange = { recipients[index] = Pair(it, amt) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Bitcoin address", color = TextSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        cursorColor = BitcoinOrange
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = amt,
                                    onValueChange = { value ->
                                        val filtered = when {
                                            isUsdMode -> if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) value else amt
                                            useSats -> if (value.isEmpty() || value.matches(Regex("^\\d*$"))) value else amt
                                            else -> if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,8}$"))) value else amt
                                        }
                                        recipients[index] = Pair(addr, filtered)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            when {
                                                isUsdMode -> "Amount (USD)"
                                                useSats -> "Amount (sats)"
                                                else -> "Amount (BTC)"
                                            },
                                            color = TextSecondary.copy(alpha = 0.5f)
                                        )
                                    },
                                    leadingIcon = if (isUsdMode) {{ Text("$", color = TextSecondary) }} else null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        cursorColor = BitcoinOrange
                                    )
                                )

                                // Show validation error for address
                                val addrErr = if (addr.isNotBlank()) validateBitcoinAddress(addr) else null
                                if (addrErr != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = addrErr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentRed
                                    )
                                }
                            }
                        }

                        if (index < recipients.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add Recipient button
                OutlinedButton(
                    onClick = { recipients.add(Pair("", "")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "+ Add Recipient",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                }
                
                // --- Balance / Fee footer ---
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Show dry-run error
                if (dryRunError != null && validRecipientCount > 0) {
                    Text(
                        text = dryRunError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                val feeSats = estimatedFeeSats ?: 0L
                val totalWithFee = totalSendingSats + feeSats
                val remaining = availableSats - totalWithFee
                val overBudget = remaining < 0 && validRecipientCount > 0
                
                // Sending total
                if (validRecipientCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sending ($validRecipientCount recipients)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(totalSendingSats.toULong(), useSats) + if (useSats) " sats" else " BTC",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                
                // Fee
                if (estimatedFeeSats != null && validRecipientCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est. fee",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(estimatedFeeSats.toULong(), useSats) + if (useSats) " sats" else " BTC",
                            style = MaterialTheme.typography.bodySmall,
                            color = BitcoinOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                
                // Available
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(availableSats.toULong(), useSats) + if (useSats) " sats" else " BTC",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                
                // Remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(maxOf(0L, remaining).toULong(), useSats) + if (useSats) " sats" else " BTC",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (overBudget) AccentRed else SuccessGreen
                    )
                }
                
                // Over-budget warning
                if (overBudget) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total exceeds available balance by ${formatAmount((-remaining).toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed
                    )
                }
            }
        }
    }
}

/**
 * Confirmation dialog for multi-recipient transactions.
 */
@Composable
private fun MultiRecipientConfirmationDialog(
    recipients: List<Recipient>,
    feeRate: Float,
    useSats: Boolean,
    selectedUtxos: List<UtxoInfo>?,
    privacyMode: Boolean = false,
    isWatchOnly: Boolean = false,
    dryRunResult: DryRunResult? = null,
    label: String? = null,
    isSending: Boolean = false,
    sendStatus: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val feeSats = dryRunResult?.feeSats ?: 0L
    val estimatedVBytes = dryRunResult?.txVBytes ?: 0.0
    val changeSats = dryRunResult?.changeSats ?: 0L
    val hasChange = dryRunResult?.hasChange ?: false
    val totalRecipientSats = recipients.sumOf { it.amountSats.toLong() }
    val totalSats = totalRecipientSats + feeSats

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isWatchOnly) "Confirm PSBT" else "Confirm Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sending To
                Text(
                    text = "Sending To (${recipients.size} recipients)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Recipient addresses (scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    recipients.forEach { r ->
                        Text(
                            text = r.address,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }

                // Label (if provided)
                if (!label.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Label",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentTeal
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Per-recipient amounts
                recipients.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sending:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = r.address.take(8) + "..." + r.address.takeLast(8),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(r.amountSats, useSats)} ${if (useSats) "sats" else "BTC"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = AccentRed
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Change (if applicable)
                if (hasChange && changeSats > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Change:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Returned to your wallet",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else "+${formatAmount(changeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SuccessGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Network Fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Network Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "${formatFeeRate(feeRate)} sat/vB • ${formatVBytes(estimatedVBytes)} vB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(feeSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BitcoinOrange
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(totalSats.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                }

                // Selected UTXOs info
                if (selectedUtxos != null && selectedUtxos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Spending from ${selectedUtxos.size} selected UTXO${if (selectedUtxos.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        contentColor = DarkBackground,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.5f),
                        disabledContentColor = DarkBackground.copy(alpha = 0.7f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWatchOnly) "Create PSBT" else "Send Bitcoin",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Send status text
                if (isSending && sendStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sendStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending
                ) {
                    Text(
                        text = "Cancel",
                        color = if (isSending) TextSecondary.copy(alpha = 0.3f) else TextSecondary
                    )
                }
            }
        }
    }
}
