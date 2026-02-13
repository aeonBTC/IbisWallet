package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
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
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.viewmodel.WalletUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onFetchTxVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSync: () -> Unit = {},
    onManageWallets: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {}
) {
    // State for selected transaction dialog
    var selectedTransaction by remember { mutableStateOf<TransactionDetails?>(null) }
    
    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }
    
    // Quick receive dialog state
    var showQuickReceive by remember { mutableStateOf(false) }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Transaction display limit (progressive loading)
    var displayLimit by remember { mutableIntStateOf(25) }
    
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
    
    // Track pull-to-refresh separately from sync state so background/auto syncs
    // don't cause the page to stretch — only user-initiated pull gestures do.
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    
    // Clear pull-refreshing state when sync finishes
    LaunchedEffect(walletState.isSyncing) {
        if (!walletState.isSyncing) {
            isPullRefreshing = false
        }
    }
    
    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            if (walletState.isInitialized) {
                isPullRefreshing = true
                onSync()
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {}
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    // Add stretch effect only when user is actively pulling
                    val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                    translationY = progress * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkCard
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Top row — privacy toggle (left) + sync button (right)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(bottom = 4.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        // Privacy toggle — pinned to the left
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { onTogglePrivacy() }
                        ) {
                            Icon(
                                imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle privacy",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Sync button — pinned to the right
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable(enabled = walletState.isInitialized && !walletState.isSyncing) { onSync() }
                        ) {
                            if (walletState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = if (walletState.isInitialized) TextSecondary else TextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    // Balance content — vertically centered in the card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
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
                                style = MaterialTheme.typography.titleLarge,
                                color = TextSecondary
                            )
                        }
                        
                        // Pending incoming (green)
                        if (walletState.pendingIncomingSats > 0UL) {
                            Text(
                                text = if (privacyMode) "$HIDDEN_AMOUNT pending" else "+${formatAmount(walletState.pendingIncomingSats, useSats)} pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SuccessGreen,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Pending outgoing (red)
                        if (walletState.pendingOutgoingSats > 0UL) {
                            Text(
                                text = if (privacyMode) "$HIDDEN_AMOUNT pending" else "-${formatAmount(walletState.pendingOutgoingSats, useSats)} pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Quick receive button — pinned to bottom left
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = walletState.currentAddress != null) {
                                showQuickReceive = true
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Quick receive",
                            tint = BitcoinOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // QR scan button — pinned to bottom right
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = walletState.isInitialized) {
                                showQrScanner = true
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            }

            item {
            Spacer(modifier = Modifier.height(4.dp))
            
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
                            onClick = onManageWallets,
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
            }
                Spacer(modifier = Modifier.height(6.dp))
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }
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
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // Filter transactions based on search query (always searches ALL transactions)
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

        // When searching, show all results; otherwise apply display limit
        val isSearching = searchQuery.isNotBlank()
        val visibleTransactions = if (isSearching) {
            filteredTransactions
        } else {
            filteredTransactions.take(displayLimit)
        }
        val totalCount = filteredTransactions.size
        val visibleCount = visibleTransactions.size
        val hasMore = !isSearching && visibleCount < totalCount
        
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
            items(visibleTransactions) { tx ->
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
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Progressive "Show More" / "Show All" buttons
            if (hasMore) {
                item {
                    val remaining = totalCount - visibleCount
                    TextButton(
                        onClick = {
                            if (displayLimit <= 25) {
                                displayLimit = 100
                            } else {
                                displayLimit = Int.MAX_VALUE
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (displayLimit <= 25)
                                "Show More"
                            else
                                "Show All ($remaining remaining)",
                            color = TextSecondary
                        )
                    }
                }
            }
        }
        
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // QR Scanner Dialog — must be outside LazyColumn
    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                onScanQrResult(code)
            },
            onDismiss = { showQrScanner = false }
        )
    }

    // Quick Receive Dialog — must be outside LazyColumn
    if (showQuickReceive && walletState.currentAddress != null) {
        val address = walletState.currentAddress
        val qrBitmap = remember(address) { generateQrCode(address) }
        val context = LocalContext.current
        var showCopied by remember { mutableStateOf(false) }

        LaunchedEffect(showCopied) {
            if (showCopied) {
                kotlinx.coroutines.delay(3000)
                showCopied = false
            }
        }

        Dialog(
            onDismissRequest = { showQuickReceive = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // QR Code
                    qrBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Receive address QR",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Address + copy icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${address.take(8)}...${address.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy address",
                            tint = if (showCopied) BitcoinOrange else TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    SecureClipboard.copyAndScheduleClear(
                                        context,
                                        "Address",
                                        address
                                    )
                                    showCopied = true
                                }
                        )
                    }

                    if (showCopied) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Copied to clipboard!",
                            style = MaterialTheme.typography.bodySmall,
                            color = BitcoinOrange
                        )
                    }
                }
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
                // Date and time
                Text(
                    text = transaction.timestamp?.let { formatDateTime(it) } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                val amountText = if (privacyMode) {
                    HIDDEN_AMOUNT
                } else {
                    val sign = if (isReceived) "+" else "-"
                    val amount = formatAmount(absSats, useSats)
                    if (useSats) "$sign$amount sats" else "$sign$amount"
                }
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isReceived) AccentGreen else AccentRed,
                    textAlign = TextAlign.End
                )
                // USD value
                if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                    val usdValue = (absSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = formatUsd(usdValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.End
                    )
                }
                // Pending / Confirmed status
                Text(
                    text = if (transaction.isConfirmed) "Confirmed" else "Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (transaction.isConfirmed) TextSecondary else BitcoinOrange,
                    textAlign = TextAlign.End
                )
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

private fun formatFullTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Format vBytes - up to 2 decimal places, removes trailing zeros: 109.25, 140.5, 154
 */
private fun formatVBytes(vBytes: Double): String {
    val formatted = String.format(Locale.US, "%.2f", vBytes)
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
    onFetchVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSpeedUp: ((SpeedUpMethod, Float) -> Unit)? = null,
    onSaveLabel: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isReceived = transaction.amountSats > 0
    
    
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
    
    // State for fetched vsize from Electrum (fractional: weight/4.0)
    var fetchedVsize by remember { mutableStateOf<Double?>(null) }
    
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
        val dialogVsize = fetchedVsize ?: transaction.vsize
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                        // Status section
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                if (transaction.timestamp != null) {
                                    Text(
                                        text = formatFullTimestamp(transaction.timestamp * 1000),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable {
                                            SecureClipboard.copyAndScheduleClear(context, "Transaction ID", transaction.txid)
                                            showCopiedTxid = true
                                        }
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
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .clickable {
                                                SecureClipboard.copyAndScheduleClear(context, "Address", transaction.address)
                                                showCopiedAddress = true
                                            }
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
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurfaceVariant)
                                                .clickable {
                                                    SecureClipboard.copyAndScheduleClear(context, "Change Address", transaction.changeAddress)
                                                    showCopiedChangeAddress = true
                                                }
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
                                // Use fetched vsize from Electrum if available, otherwise fall back to BDK's weight/4.0
                                val displayVsize: Double? = fetchedVsize ?: transaction.vsize
                                // Recalculate fee rate using the fractional vsize
                                val displayFeeRate = if (displayVsize != null && displayVsize > 0.0) {
                                    transaction.fee!!.toDouble() / displayVsize
                                } else {
                                    transaction.feeRate
                                }
                                if (displayFeeRate != null && displayVsize != null) {
                                    Text(
                                        text = "${formatFeeRate(displayFeeRate)} sat/vB • ${formatVBytes(displayVsize)} vB",
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
    vsize: Double?,
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
                                        text = "(${estimatedChildVsize} vB × ${kotlin.math.ceil(feeRate.toDouble()).toLong()} sat/vB)",
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
                                            text = if (privacyMode) "You'll receive back: $HIDDEN_AMOUNT" else "You'll receive back: ${formatAmount(changeBack.toULong(), useSats)} ${if (useSats) "sats" else "BTC"}",
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
                                            text = if (privacyMode) "Need: $HIDDEN_AMOUNT (fee + dust)" else "Need: ${formatAmount(needed.toULong(), useSats)} ${if (useSats) "sats" else "BTC"} (fee + dust)",
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
                if (feeEstimationState !is FeeEstimationResult.Disabled) {
                    val isLoading = feeEstimationState is FeeEstimationResult.Loading
                    val isElectrum = estimates?.source == FeeEstimateSource.ELECTRUM_SERVER
                    val fastLabel = if (isElectrum) "~2 blocks" else "~1 block"
                    val medLabel = if (isElectrum) "~6 blocks" else "~3 blocks"
                    val slowLabel = if (isElectrum) "~12 blocks" else "~6 blocks"

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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable(enabled = !isLoading) { onRefreshFees() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isLoading) TextSecondary.copy(alpha = 0.5f) else BitcoinOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        github.aeonbtc.ibiswallet.ui.components.FeeTargetButton(
                            label = fastLabel,
                            feeRate = estimates?.fastestFee,
                            isSelected = selectedFeeOption == "fastest",
                            onClick = {
                                if (estimates != null) {
                                    feeRateInput = formatFeeRate(estimates.fastestFee)
                                    selectedFeeOption = "fastest"
                                }
                            },
                            enabled = true,
                            isLoading = isLoading,
                            modifier = Modifier.weight(1f)
                        )
                        github.aeonbtc.ibiswallet.ui.components.FeeTargetButton(
                            label = medLabel,
                            feeRate = estimates?.halfHourFee,
                            isSelected = selectedFeeOption == "halfHour",
                            onClick = {
                                if (estimates != null) {
                                    feeRateInput = formatFeeRate(estimates.halfHourFee)
                                    selectedFeeOption = "halfHour"
                                }
                            },
                            enabled = true,
                            isLoading = isLoading,
                            modifier = Modifier.weight(1f)
                        )
                        github.aeonbtc.ibiswallet.ui.components.FeeTargetButton(
                            label = slowLabel,
                            feeRate = estimates?.hourFee,
                            isSelected = selectedFeeOption == "hour",
                            onClick = {
                                if (estimates != null) {
                                    feeRateInput = formatFeeRate(estimates.hourFee)
                                    selectedFeeOption = "hour"
                                }
                            },
                            enabled = true,
                            isLoading = isLoading,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (estimates != null && isElectrum && estimates.isUniform) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Server reports same rate for all targets (low fee environment)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Confirm button
                val canConfirm = isValidFeeRate && canAfford
                IbisButton(
                    onClick = { onConfirm(feeRate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canConfirm,
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
 * Get the appropriate button text for the block explorer button
 * based on the selected mempool server
 */
private fun generateQrCode(content: String): Bitmap? {
    return try {
        val size = 512
        val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val bitMatrix = qrCodeWriter.encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun getMempoolButtonText(mempoolServer: String): String {
    return when (mempoolServer) {
        SecureStorage.MEMPOOL_SPACE -> "View on mempool.space"
        SecureStorage.MEMPOOL_ONION -> "View on mempool.space (onion)"
        SecureStorage.MEMPOOL_CUSTOM -> "View on custom mempool server"
        else -> "View in block explorer"
    }
}
