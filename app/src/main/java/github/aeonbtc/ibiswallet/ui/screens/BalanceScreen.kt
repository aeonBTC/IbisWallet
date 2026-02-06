package github.aeonbtc.ibiswallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.viewmodel.WalletUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.ui.theme.BorderColor

/**
 * Method for speeding up a transaction
 */
enum class SpeedUpMethod {
    RBF,  // Replace-By-Fee (for sent transactions)
    CPFP  // Child-Pays-For-Parent (for received transactions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(
    walletState: WalletState = WalletState(),
    uiState: WalletUiState = WalletUiState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onTogglePrivacy: () -> Unit = {},
    addressLabels: Map<String, String> = emptyMap(),
    transactionLabels: Map<String, String> = emptyMap(),
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    canBumpFee: (String) -> Boolean = { false },
    canCpfp: (String) -> Boolean = { false },
    onBumpFee: (String, Float) -> Unit = { _, _ -> },
    onCpfp: (String, Float) -> Unit = { _, _ -> },
    onSaveTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onFetchTxVsize: suspend (String) -> Int? = { null },
    onRefreshFees: () -> Unit = {},
    onSync: () -> Unit = {},
    onImportWallet: () -> Unit = {}
) {
    // State for selected transaction dialog
    var selectedTransaction by remember { mutableStateOf<TransactionDetails?>(null) }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    
    // Show transaction detail dialog when a transaction is selected
    selectedTransaction?.let { tx ->
        val txCanRbf = canBumpFee(tx.txid)
        val txCanCpfp = canCpfp(tx.txid)
        val txLabel = transactionLabels[tx.txid] ?: tx.address?.let { addressLabels[it] }
        
        TransactionDetailDialog(
            transaction = tx,
            useSats = useSats,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            label = txLabel,
            canRbf = txCanRbf,
            canCpfp = txCanCpfp,
            availableBalance = walletState.balanceSats,
            feeEstimationState = feeEstimationState,
            onFetchVsize = onFetchTxVsize,
            onRefreshFees = onRefreshFees,
            onSpeedUp = { method, feeRate ->
                when (method) {
                    SpeedUpMethod.RBF -> onBumpFee(tx.txid, feeRate)
                    SpeedUpMethod.CPFP -> onCpfp(tx.txid, feeRate)
                }
            },
            onSaveLabel = { label -> onSaveTransactionLabel(tx.txid, label) },
            onDismiss = { selectedTransaction = null }
        )
    }
    
    val pullToRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = walletState.isSyncing,
        onRefresh = { if (walletState.isInitialized) onSync() },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {}
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    // Add stretch effect when pulling
                    val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                    translationY = progress * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            
            // Balance Card
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
                        .padding(20.dp)
                ) {
                    // Header row: label + sync button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        IconButton(
                            onClick = onSync,
                            enabled = walletState.isInitialized && !walletState.isSyncing,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (walletState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = if (walletState.isInitialized) TextSecondary else TextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Balance content - tap to toggle privacy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTogglePrivacy),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Main balance with inline denomination
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else if (useSats) {
                                "${formatAmount(walletState.balanceSats, useSats)} sats"
                            } else {
                                "\u20BF ${formatAmount(walletState.balanceSats, useSats)}"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        // USD value
                        if (btcPrice != null && btcPrice > 0) {
                            val usdValue = (walletState.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                            Text(
                                text = if (privacyMode) HIDDEN_AMOUNT else formatUsd(usdValue),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        // Pending (only if there is pending balance)
                        if (walletState.pendingBalanceSats > 0UL) {
                            Text(
                                text = if (privacyMode) "$HIDDEN_AMOUNT pending" else "+${formatAmount(walletState.pendingBalanceSats, useSats)} pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BitcoinOrange,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Import wallet prompt if not initialized
            if (!walletState.isInitialized) {
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
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Wallet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Import a wallet to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onImportWallet,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BitcoinOrangeLight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Wallet")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Recent Transactions Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = { 
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearchActive) "Close search" else "Search",
                        tint = if (isSearchActive) BitcoinOrange else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Search field
            if (isSearchActive) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search labels or addresses...", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = BitcoinOrange
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Filter transactions based on search query
        val filteredTransactions = if (searchQuery.isBlank()) {
            walletState.transactions
        } else {
            val query = searchQuery.lowercase()
            walletState.transactions.filter { tx ->
                val txLabel = transactionLabels[tx.txid]
                val addrLabel = tx.address?.let { addressLabels[it] }
                val address = tx.address ?: ""
                
                txLabel?.lowercase()?.contains(query) == true ||
                addrLabel?.lowercase()?.contains(query) == true ||
                address.lowercase().contains(query)
            }
        }
        
        if (filteredTransactions.isEmpty()) {
            item {
                // Empty state for transactions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkCard
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching transactions" else "No transactions yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank())
                                    "Try a different search term"
                                else if (walletState.isInitialized) 
                                    "Transactions will appear here after syncing"
                                else 
                                    "Import a wallet to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredTransactions) { tx ->
                // Look up label: first check transaction label, then address label
                val label = transactionLabels[tx.txid] 
                    ?: tx.address?.let { addressLabels[it] }
                
                TransactionItem(
                    transaction = tx,
                    useSats = useSats,
                    label = label,
                    btcPrice = btcPrice,
                    privacyMode = privacyMode,
                    onClick = { selectedTransaction = tx }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: TransactionDetails,
    useSats: Boolean = false,
    label: String? = null,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isReceived = transaction.amountSats > 0
    val absSats = kotlin.math.abs(transaction.amountSats).toULong()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isReceived) AccentGreen.copy(alpha = 0.1f)
                        else AccentRed.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isReceived) 
                        Icons.AutoMirrored.Filled.CallReceived
                    else 
                        Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = if (isReceived) "Received" else "Sent",
                    tint = if (isReceived) AccentGreen else AccentRed,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReceived) "Received" else "Sent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (label != null) {
                    Text(
                        text = "$label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Date and time (or Pending if no timestamp)
                Text(
                    text = transaction.timestamp?.let { formatDateTime(it) } ?: "Pending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (transaction.timestamp != null) TextSecondary else BitcoinOrange
                )
            }
            
            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (privacyMode) HIDDEN_AMOUNT else "${if (isReceived) "+" else "-"}${formatAmount(absSats, useSats)} ${if (useSats) "sats" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isReceived) AccentGreen else AccentRed
                )
                // USD value
                if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                    val usdValue = (absSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = formatUsd(usdValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                // Confirmed status (only show when confirmed, pending is shown on left)
                if (transaction.isConfirmed) {
                    Text(
                        text = "Confirmed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun formatBtc(sats: ULong): String {
    val btc = sats.toDouble() / 100_000_000.0
    return String.format(Locale.US, "%.8f", btc)
}

private fun formatDateTime(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000) // Convert seconds to milliseconds
    val format = java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun formatUsd(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

private fun formatSatsAmount(sats: ULong): String {
    return NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())
}

private fun formatSats(sats: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(kotlin.math.abs(sats))
}

private const val HIDDEN_AMOUNT = "****"

/**
 * Format amount based on denomination preference
 */
private fun formatAmount(sats: ULong, useSats: Boolean): String {
    return if (useSats) {
        formatSatsAmount(sats)
    } else {
        formatBtc(sats)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Format fee rate - up to 2 decimal places, removes trailing zeros
 */
private fun formatFeeRate(rate: Double): String {
    val formatted = String.format(Locale.US, "%.2f", rate)
    return formatted.trimEnd('0').trimEnd('.')
}

/**
 * Transaction Detail Dialog
 * Shows complete transaction details when user taps on a transaction
 */
@Composable
fun TransactionDetailDialog(
    transaction: TransactionDetails,
    useSats: Boolean = false,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    label: String? = null,
    canRbf: Boolean = false,
    canCpfp: Boolean = false,
    availableBalance: ULong = 0UL,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    onFetchVsize: suspend (String) -> Int? = { null },
    onRefreshFees: () -> Unit = {},
    onSpeedUp: ((SpeedUpMethod, Float) -> Unit)? = null,
    onSaveLabel: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isReceived = transaction.amountSats > 0
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    // State for showing copy confirmation
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedChangeAddress by remember { mutableStateOf(false) }
    
    // Auto-dismiss copy notifications after 3 seconds
    LaunchedEffect(showCopiedTxid) {
        if (showCopiedTxid) {
            kotlinx.coroutines.delay(3000)
            showCopiedTxid = false
        }
    }
    LaunchedEffect(showCopiedAddress) {
        if (showCopiedAddress) {
            kotlinx.coroutines.delay(3000)
            showCopiedAddress = false
        }
    }
    LaunchedEffect(showCopiedChangeAddress) {
        if (showCopiedChangeAddress) {
            kotlinx.coroutines.delay(3000)
            showCopiedChangeAddress = false
        }
    }
    
    // State for showing Tor Browser error dialog
    var showTorBrowserError by remember { mutableStateOf(false) }
    
    // State for Speed Up dialog
    var showSpeedUpDialog by remember { mutableStateOf(false) }
    
    // State for label editing
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember { mutableStateOf(label ?: "") }
    
    // State for fetched vsize from Electrum (more accurate than BDK's vsize)
    var fetchedVsize by remember { mutableStateOf<Int?>(null) }
    
    // Fetch actual vsize from Electrum when dialog opens (for sent transactions with fee info)
    LaunchedEffect(transaction.txid) {
        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
            fetchedVsize = onFetchVsize(transaction.txid)
        }
    }
    
    // Check if mempool URL is an onion address
    val isOnionAddress = mempoolUrl.contains(".onion")
    
    // Determine if Speed Up is available
    val canSpeedUp = !transaction.isConfirmed && (canRbf || canCpfp)
    val speedUpMethod = when {
        canRbf && !isReceived -> SpeedUpMethod.RBF
        canCpfp -> SpeedUpMethod.CPFP
        else -> null
    }
    
    val amountAbs = kotlin.math.abs(transaction.amountSats).toULong()
    val denominationLabel = if (useSats) "sats" else "BTC"
    
    // Tor Browser error dialog
    if (showTorBrowserError) {
        TorBrowserErrorDialog(
            onDismiss = { showTorBrowserError = false }
        )
    }
    
    // Speed Up dialog
    if (showSpeedUpDialog && speedUpMethod != null && onSpeedUp != null) {
        val dialogVsize = fetchedVsize?.toLong() ?: transaction.vsize?.toLong()
        // For CPFP, the spendable output is either the received amount or the change amount
        val isReceived = transaction.amountSats > 0
        val cpfpSpendableOutput = if (isReceived) {
            transaction.amountSats.toULong()
        } else {
            transaction.changeAmount ?: 0UL
        }
        SpeedUpDialog(
            method = speedUpMethod,
            currentFee = transaction.fee,
            currentFeeRate = transaction.feeRate,
            vsize = dialogVsize,
            availableBalance = availableBalance,
            cpfpSpendableOutput = cpfpSpendableOutput,
            feeEstimationState = feeEstimationState,
            useSats = useSats,
            btcPrice = btcPrice,
            privacyMode = privacyMode,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate ->
                onSpeedUp(speedUpMethod, feeRate)
                showSpeedUpDialog = false
                onDismiss()
            },
            onDismiss = { showSpeedUpDialog = false }
        )
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
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
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Amount display (large, centered)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isReceived) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Direction icon
                        Icon(
                            imageVector = when {
                                transaction.isSelfTransfer -> Icons.AutoMirrored.Filled.CallMade
                                isReceived -> Icons.AutoMirrored.Filled.CallReceived
                                else -> Icons.AutoMirrored.Filled.CallMade
                            },
                            contentDescription = null,
                            tint = when {
                                transaction.isSelfTransfer -> BitcoinOrange
                                isReceived -> AccentGreen
                                else -> AccentRed
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Direction text
                        Text(
                            text = when {
                                transaction.isSelfTransfer -> "Self-Transfer"
                                isReceived -> "Received"
                                else -> "Sent"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                transaction.isSelfTransfer -> BitcoinOrange
                                isReceived -> AccentGreen
                                else -> AccentRed
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Amount (based on denomination setting)
                        Text(
                            text = if (privacyMode) HIDDEN_AMOUNT else "${if (isReceived) "+" else "-"}${formatAmount(amountAbs, useSats)} $denominationLabel",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceived) AccentGreen else AccentRed
                        )
                        
                        // USD amount (if price is available)
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (amountAbs.toDouble() / 100_000_000.0) * btcPrice
                            Text(
                                text = "${formatUsd(usdValue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Transaction details
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
                        // Status section (includes confirmation status, block height, date/time)
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (transaction.isConfirmed) 
                                        Icons.Default.CheckCircle 
                                    else 
                                        Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (transaction.isConfirmed) "Confirmed" else "Pending",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (transaction.isConfirmed) AccentGreen else BitcoinOrange
                                )
                            }
                            // Block height (left) and Date/Time (right)
                            if (transaction.isConfirmed && transaction.confirmationTime != null && transaction.timestamp != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Block ${transaction.confirmationTime.height}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = formatFullTimestamp(transaction.timestamp * 1000),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            } else if (transaction.timestamp != null) {
                                // Just date/time for pending (aligned right)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatFullTimestamp(transaction.timestamp * 1000),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = TextSecondary.copy(alpha = 0.1f)
                        )
                        
                        // Transaction ID
                        Column {
                            Text(
                                text = "Transaction ID",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = transaction.txid.take(20) + "..." + transaction.txid.takeLast(8),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clip = ClipData.newPlainText("Transaction ID", transaction.txid)
                                        clipboardManager.setPrimaryClip(clip)
                                        showCopiedTxid = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (showCopiedTxid) BitcoinOrange else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // Show "Copied!" text
                            if (showCopiedTxid) {
                                Text(
                                    text = "Copied to clipboard!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = TextSecondary.copy(alpha = 0.1f)
                        )
                        
                        // Address (recipient for sent, receiving address for received, or destination for self-transfer)
                        if (transaction.address != null) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isReceived && !transaction.isSelfTransfer) "Received at" else "Recipient",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    if (transaction.isSelfTransfer) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Self-transfer)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BorderColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transaction.address.take(14) + "..." + transaction.address.takeLast(8),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        transaction.addressAmount?.let { amount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (privacyMode) HIDDEN_AMOUNT else "${if (isReceived || transaction.isSelfTransfer) "+" else "-"}${formatAmount(amount, useSats)} $denominationLabel",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isReceived || transaction.isSelfTransfer) AccentGreen else AccentRed
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val clip = ClipData.newPlainText("Address", transaction.address)
                                            clipboardManager.setPrimaryClip(clip)
                                            showCopiedAddress = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Address",
                                            tint = if (showCopiedAddress) BitcoinOrange else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                // Show "Copied!" text
                                if (showCopiedAddress) {
                                    Text(
                                        text = "Copied to clipboard!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BitcoinOrange
                                    )
                                }
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = TextSecondary.copy(alpha = 0.1f)
                            )
                            
                            // Change Address (for sent transactions and self-transfers)
                            if ((!isReceived || transaction.isSelfTransfer) && transaction.changeAddress != null && transaction.changeAddress != transaction.address) {
                                Column {
                                    Text(
                                        text = "Change returned to",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = transaction.changeAddress.take(14) + "..." + transaction.changeAddress.takeLast(8),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            transaction.changeAmount?.let { amount ->
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = if (privacyMode) HIDDEN_AMOUNT else "+${formatAmount(amount, useSats)} $denominationLabel",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = AccentGreen
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val clip = ClipData.newPlainText("Change Address", transaction.changeAddress)
                                                clipboardManager.setPrimaryClip(clip)
                                                showCopiedChangeAddress = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Change Address",
                                                tint = if (showCopiedChangeAddress) BitcoinOrange else TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    // Show "Copied!" text
                                    if (showCopiedChangeAddress) {
                                        Text(
                                            text = "Copied to clipboard!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange
                                        )
                                    }
                                }
                                
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = TextSecondary.copy(alpha = 0.1f)
                                )
                            }
                        }
                        
                        // Fee (if available and it's a sent transaction)
                        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
                            Column {
                                Text(
                                    text = "Network Fee",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Use fetched vsize from Electrum if available, otherwise fall back to BDK's vsize
                                val displayVsize: Long? = fetchedVsize?.toLong() ?: transaction.vsize?.toLong()
                                // Recalculate fee rate using the actual vsize
                                val displayFeeRate = if (displayVsize != null && displayVsize > 0L) {
                                    transaction.fee!!.toDouble() / displayVsize.toDouble()
                                } else {
                                    transaction.feeRate
                                }
                                if (displayFeeRate != null && displayVsize != null) {
                                    Text(
                                        text = "${formatFeeRate(displayFeeRate)} sat/vB • $displayVsize vB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }
                                Text(
                                    text = if (privacyMode) HIDDEN_AMOUNT else "-${formatAmount(transaction.fee, useSats)} $denominationLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BitcoinOrange
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = TextSecondary.copy(alpha = 0.1f)
                            )
                        }
                        
                        // Label section
                        Column {
                            Text(
                                text = "Label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isEditingLabel) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = { labelText = it },
                                    placeholder = { Text("Enter label", color = TextSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = BitcoinOrange
                                    ),
                                    trailingIcon = {
                                        TextButton(
                                            onClick = {
                                                onSaveLabel(labelText)
                                                isEditingLabel = false
                                            }
                                        ) {
                                            Text("Save", color = BitcoinOrange)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (labelText.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AccentTeal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Card(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isEditingLabel = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        border = BorderStroke(1.dp, BorderColor)
                                    ) {
                                        Text(
                                            text = "Edit",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isEditingLabel = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Speed Up button (for pending transactions)
                if (canSpeedUp && speedUpMethod != null && onSpeedUp != null) {
                    OutlinedButton(
                        onClick = { showSpeedUpDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BitcoinOrange
                        ),
                        border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Speed Up Transaction", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                // View on Block Explorer button (only shown when not disabled)
                if (mempoolServer != SecureStorage.MEMPOOL_DISABLED) {
                    OutlinedButton(
                        onClick = {
                            val url = "$mempoolUrl/tx/${transaction.txid}"
                            if (isOnionAddress) {
                                // Try to open Tor Browser automatically for onion addresses
                                val torBrowserPackage = "org.torproject.torbrowser"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    setPackage(torBrowserPackage)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Tor Browser not installed, show error
                                    showTorBrowserError = true
                                }
                            } else {
                                // Open directly for clearnet
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BitcoinOrange
                        ),
                        border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(getMempoolButtonText(mempoolServer), style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Close",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Tor Browser Error Dialog
 * Shows when Tor Browser is not installed but needed for onion addresses
 */
@Composable
private fun TorBrowserErrorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = AccentRed,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Header
                Text(
                    text = "Tor Browser Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Tor Browser is required to view onion links. Please install it from the Play Store or F-Droid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Close",
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Speed Up Transaction Dialog
 * Allows user to set a new fee rate for RBF or CPFP
 */
@Composable
private fun SpeedUpDialog(
    method: SpeedUpMethod,
    currentFee: ULong?,
    currentFeeRate: Double?,
    vsize: Long?,
    availableBalance: ULong = 0UL,
    cpfpSpendableOutput: ULong = 0UL,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    useSats: Boolean = true,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onRefreshFees: () -> Unit = {},
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // Start with empty field - user enters their desired fee rate
    var feeRateInput by remember { mutableStateOf("") }
    val feeRate = feeRateInput.toFloatOrNull() ?: 0f
    val isValidFeeRate = feeRate >= 1f && (currentFeeRate == null || feeRate > currentFeeRate)
    
    // Track selected fee estimate option (null = custom/manual input)
    var selectedFeeOption by remember { mutableStateOf<String?>(null) }
    
    // Estimated child tx vsize for CPFP (1 input + 1 output, conservative estimate)
    // P2WPKH: ~110 vB, P2TR: ~111 vB, adding buffer for safety
    val estimatedChildVsize = 150L
    
    // Dust limit - use 546 sats (Bitcoin Core default, covers all output types)
    // P2TR actual dust is ~387, P2WPKH is ~294
    val dustLimit = 546L
    
    // Calculate costs differently for RBF vs CPFP
    val additionalCost: Long? = when (method) {
        SpeedUpMethod.RBF -> {
            // RBF: Pay the difference between new total fee and old fee
            if (vsize != null && feeRate > 0f && currentFee != null) {
                val newTotalFee = (feeRate * vsize).toLong()
                (newTotalFee - currentFee.toLong()).coerceAtLeast(0)
            } else null
        }
        SpeedUpMethod.CPFP -> {
            // CPFP: Child tx fee = ceil(fee_rate) × child_vsize
            if (feeRate > 0f) {
                val effectiveFeeRate = kotlin.math.ceil(feeRate.toDouble()).toLong()
                (effectiveFeeRate * estimatedChildVsize)
            } else null
        }
    }
    
    // For CPFP, we can use the parent output PLUS confirmed wallet balance
    // BDK will add more UTXOs if needed to cover the fee
    // For RBF, we need confirmed balance to cover the fee bump
    val fundsForFee = when (method) {
        SpeedUpMethod.RBF -> availableBalance
        SpeedUpMethod.CPFP -> cpfpSpendableOutput + availableBalance // Parent output + confirmed balance
    }
    
    // Check affordability - need funds >= fee + dust (for change output)
    val canAfford = when (method) {
        SpeedUpMethod.RBF -> additionalCost == null || additionalCost <= 0 || additionalCost.toULong() <= fundsForFee
        SpeedUpMethod.CPFP -> {
            if (additionalCost == null || additionalCost <= 0) true
            else {
                // Need: totalFunds >= fee + dustLimit (to have valid change output)
                fundsForFee.toLong() >= additionalCost + dustLimit
            }
        }
    }
    
    // Check if parent output alone can cover fee + dust
    val parentCanCoverAlone = method == SpeedUpMethod.CPFP &&
        additionalCost != null &&
        cpfpSpendableOutput.toLong() > additionalCost + dustLimit
    
    // If parent can't cover alone, wallet UTXOs will be consolidated
    val willConsolidateWallet = method == SpeedUpMethod.CPFP && 
        additionalCost != null && 
        !parentCanCoverAlone && 
        canAfford
    
    // Get fee estimates if available
    val estimates = (feeEstimationState as? FeeEstimationResult.Success)?.estimates
    
    // Fetch fee estimates when dialog opens
    LaunchedEffect(Unit) {
        onRefreshFees()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Speed Up Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Method description
                Text(
                    text = when (method) {
                        SpeedUpMethod.RBF -> "Replace this transaction with a higher fee (RBF)"
                        SpeedUpMethod.CPFP -> "Create a child transaction with high fee to speed up confirmation (CPFP)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Current fee info
                if (currentFeeRate != null || (currentFee != null && currentFee > 0UL)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Current Transaction:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFeeRate != null) {
                                Text(
                                    text = "Fee rate: ${formatFeeRate(currentFeeRate)} sat/vB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFee != null && currentFee > 0UL) {
                                Text(
                                    text = if (privacyMode) "Fee: $HIDDEN_AMOUNT" else "Fee: ${formatAmount(currentFee, useSats)} ${if (useSats) "sats" else "BTC"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Fee rate input
                Text(
                    text = "New Fee Rate (sat/vB)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = feeRateInput,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                            feeRateInput = input
                            selectedFeeOption = null // Clear selection when manually typing
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. 6.50", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        cursorColor = BitcoinOrange
                    )
                )
                
                // Validation message
                if (currentFeeRate != null && feeRate > 0f && feeRate <= currentFeeRate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "New fee rate must be higher than current (${formatFeeRate(currentFeeRate)} sat/vB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // New Transaction card (shows estimated fee info)
                if (feeRate > 0f && isValidFeeRate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (method == SpeedUpMethod.RBF) "Replacement Transaction:" else "Child Transaction (CPFP):",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Target fee rate: ${formatFeeRate(feeRate.toDouble())} sat/vB",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            if (additionalCost != null && additionalCost > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val costLabel = if (method == SpeedUpMethod.CPFP) "Child tx fee" else "Additional cost"
                                Text(
                                    text = if (privacyMode) "$costLabel: $HIDDEN_AMOUNT" else "$costLabel: ${formatAmount(additionalCost.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canAfford) BitcoinOrange else ErrorRed
                                )
                                if (method == SpeedUpMethod.CPFP) {
                                    Text(
                                        text = "(~${estimatedChildVsize}vB × ${kotlin.math.ceil(feeRate.toDouble()).toLong()} sat/vB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    if (willConsolidateWallet) {
                                        Text(
                                            text = "Will consolidate all wallet UTXOs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BitcoinOrange
                                        )
                                    }
                                    val changeBack = fundsForFee.toLong() - additionalCost
                                    if (canAfford && changeBack > dustLimit) {
                                        Text(
                                            text = if (privacyMode) "You'll receive back: $HIDDEN_AMOUNT" else "You'll receive back: ~${formatAmount(changeBack.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AccentGreen
                                        )
                                    }
                                }
                                if (!canAfford) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (method == SpeedUpMethod.CPFP) {
                                        val needed = additionalCost + dustLimit
                                        Text(
                                            text = "Insufficient funds",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed
                                        )
                                        Text(
                                            text = if (privacyMode) "Need: $HIDDEN_AMOUNT (fee + dust)" else "Need: ~${formatAmount(needed.toULong(), useSats)} ${if (useSats) "sats" else "BTC"} (fee + dust)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErrorRed
                                        )
                                        Text(
                                            text = if (privacyMode) "Have: $HIDDEN_AMOUNT (output + wallet)" else "Have: ${formatAmount(fundsForFee, useSats)} ${if (useSats) "sats" else "BTC"} (output + wallet)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    } else {
                                        Text(
                                            text = if (privacyMode) "Insufficient funds" else "Insufficient funds (Available: ${formatAmount(fundsForFee, useSats)} ${if (useSats) "sats" else "BTC"})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Fee estimates (if enabled)
                if (estimates != null || feeEstimationState is FeeEstimationResult.Loading) {
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Fee Estimates",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        IconButton(
                            onClick = onRefreshFees,
                            enabled = feeEstimationState !is FeeEstimationResult.Loading,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (feeEstimationState is FeeEstimationResult.Loading) 
                                    TextSecondary.copy(alpha = 0.5f) else BitcoinOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    if (feeEstimationState is FeeEstimationResult.Loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading fee estimates...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    } else if (estimates != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ~1 block card
                            // ~1 block card
                            val isSelected1 = selectedFeeOption == "fastest"
                            Card(
                                onClick = { 
                                    feeRateInput = formatFeeRate(estimates.fastestFee)
                                    selectedFeeOption = "fastest"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected1) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected1) BitcoinOrange else BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "~1 block",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected1) MaterialTheme.colorScheme.onBackground else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = formatFeeRate(estimates.fastestFee),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected1) BitcoinOrange else TextSecondary
                                    )
                                    Text(
                                        text = "sat/vB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            // ~3 blocks card
                            val isSelected3 = selectedFeeOption == "halfHour"
                            Card(
                                onClick = { 
                                    feeRateInput = formatFeeRate(estimates.halfHourFee)
                                    selectedFeeOption = "halfHour"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected3) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected3) BitcoinOrange else BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "~3 blocks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected3) MaterialTheme.colorScheme.onBackground else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = formatFeeRate(estimates.halfHourFee),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected3) BitcoinOrange else TextSecondary
                                    )
                                    Text(
                                        text = "sat/vB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            // ~6 blocks card
                            val isSelected6 = selectedFeeOption == "hour"
                            Card(
                                onClick = { 
                                    feeRateInput = formatFeeRate(estimates.hourFee)
                                    selectedFeeOption = "hour"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected6) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected6) BitcoinOrange else BorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "~6 blocks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected6) MaterialTheme.colorScheme.onBackground else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = formatFeeRate(estimates.hourFee),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected6) BitcoinOrange else TextSecondary
                                    )
                                    Text(
                                        text = "sat/vB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Confirm button
                val canConfirm = isValidFeeRate && canAfford
                OutlinedButton(
                    onClick = { onConfirm(feeRate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = canConfirm,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BitcoinOrange,
                        disabledContentColor = TextSecondary.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (canConfirm) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = when (method) {
                            SpeedUpMethod.RBF -> "Bump Fee"
                            SpeedUpMethod.CPFP -> "Bump Fee"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Helper composable for detail rows
 */
@Composable
private fun DetailRow(
    label: String,
    value: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (content != null) {
            content()
        } else if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Get the appropriate button text for the block explorer button
 * based on the selected mempool server
 */
private fun getMempoolButtonText(mempoolServer: String): String {
    return when (mempoolServer) {
        SecureStorage.MEMPOOL_SPACE -> "View on mempool.space"
        SecureStorage.MEMPOOL_ONION -> "View on mempool.space (onion)"
        SecureStorage.MEMPOOL_CUSTOM -> "View on custom mempool server"
        else -> "View in block explorer"
    }
}
