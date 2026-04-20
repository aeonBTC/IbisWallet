@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.data.model.TransactionSearchResult
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.components.BoltzRescueKeyButton
import github.aeonbtc.ibiswallet.ui.components.BoltzRescueMnemonicDialog
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.MAX_FEE_RATE_SAT_VB
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.StatusBadge
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.components.shouldShowBoltzRescueKey
import github.aeonbtc.ibiswallet.ui.theme.AccentBlue
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
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.nfc.NfcReaderUiState
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Method for speeding up a transaction
 */
enum class SpeedUpMethod {
    RBF,
    CPFP,
    REDIRECT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(
    walletState: WalletState = WalletState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    onTogglePrivacy: () -> Unit = {},
    addressLabels: Map<String, String> = emptyMap(),
    transactionLabels: Map<String, String> = emptyMap(),
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    canBumpFee: (String) -> Boolean = { false },
    canCpfp: (String) -> Boolean = { false },
    onBumpFee: (String, Double) -> Unit = { _, _ -> },
    onCpfp: (String, Double) -> Unit = { _, _ -> },
    onRedirectTransaction: (String, Double, String?) -> Unit = { _, _, _ -> },
    onSaveTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onDeleteTransactionLabel: (String) -> Unit = {},
    onSaveAddressLabelFromTransaction: (String, String) -> Unit = { _, _ -> },
    onDeleteAddressLabelFromTransaction: (String) -> Unit = {},
    searchTransactions: suspend (String, Boolean, Int) -> TransactionSearchResult =
        { _, _, _ -> TransactionSearchResult(emptyList(), 0) },
    onFetchTxVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSync: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
    onManageWallets: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
    boltzRescueMnemonic: String? = null,
    showLayer2RequiredPlaceholder: Boolean = false,
    onOpenSettings: () -> Unit = {},
) {
    if (showLayer2RequiredPlaceholder) {
        Layer2RequiredPlaceholder(
            walletName = walletState.activeWallet?.name,
            onOpenSettings = onOpenSettings,
        )
        return
    }

    // State for selected transaction dialog
    var selectedTransaction by remember { mutableStateOf<TransactionDetails?>(null) }

    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }

    // Quick receive dialog state
    var showQuickReceive by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSwapTransactions by remember { mutableStateOf(false) }

    // Transaction display limit (progressive loading)
    var displayLimit by remember { mutableIntStateOf(25) }

    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val hasSwapTransactions = walletState.transactions.any { it.swapDetails != null }

    // NFC reader mode: tapping an NFC tag with a bitcoin address or URI
    // routes through pendingSendInput -> Send screen.
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val nfcReaderOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canRead
    DisposableEffect(mainActivity, nfcAvailable) {
        if (mainActivity != null && nfcAvailable) {
            mainActivity.requestNfcReaderMode(nfcReaderOwner)
        }
        onDispose {
            mainActivity?.releaseNfcReaderMode(nfcReaderOwner)
        }
    }
    val isNfcReaderActive = nfcAvailable && mainActivity?.isNfcReaderModeActive == true
    val nfcReaderState by NfcRuntimeStatus.readerState.collectAsState()

    // Show transaction detail dialog when a transaction is selected
    selectedTransaction?.let { tx ->
        val txCanRbf = canBumpFee(tx.txid)
        val txCanCpfp = canCpfp(tx.txid)
        val explicitTxLabel = transactionLabels[tx.txid]
        val txLabel = explicitTxLabel ?: tx.address?.let { addressLabels[it] }

        TransactionDetailDialog(
            transaction = tx,
            currentBlockHeight = walletState.blockHeight,
            useSats = useSats,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            label = txLabel,
            canRbf = txCanRbf,
            canCpfp = txCanCpfp,
            availableBalance = walletState.balanceSats,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            onFetchVsize = onFetchTxVsize,
            onRefreshFees = onRefreshFees,
            onSpeedUp = { method, feeRate, destinationAddress ->
                when (method) {
                    SpeedUpMethod.RBF -> onBumpFee(tx.txid, feeRate)
                    SpeedUpMethod.CPFP -> onCpfp(tx.txid, feeRate)
                    SpeedUpMethod.REDIRECT -> onRedirectTransaction(tx.txid, feeRate, destinationAddress)
                }
            },
            onSaveLabel = { label ->
                onSaveTransactionLabel(tx.txid, label)
                tx.address
                    ?.takeIf { it.isNotBlank() }
                    ?.let { onSaveAddressLabelFromTransaction(it, label) }
            },
            onDeleteLabel =
                explicitTxLabel?.let {
                    {
                        onDeleteTransactionLabel(tx.txid)
                        tx.address
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onDeleteAddressLabelFromTransaction)
                    }
                },
            boltzRescueMnemonic = boltzRescueMnemonic,
            onDismiss = { selectedTransaction = null },
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

    LaunchedEffect(hasSwapTransactions) {
        if (!hasSwapTransactions) {
            showSwapTransactions = false
        }
    }

    val sourceFilteredTransactions =
        remember(walletState.transactions, showSwapTransactions) {
            if (showSwapTransactions) {
                walletState.transactions.filter { it.swapDetails != null }
            } else {
                walletState.transactions
            }
        }
    val transactionsById = remember(walletState.transactions) { walletState.transactions.associateBy { it.txid } }
    var searchResultTxids by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchTotalCount by remember { mutableIntStateOf(0) }
    var isSearchFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchActive, searchQuery.trim(), showSwapTransactions) {
        displayLimit = 25
    }

    LaunchedEffect(
        walletState.transactions,
        transactionLabels,
        addressLabels,
        searchQuery,
        showSwapTransactions,
        displayLimit,
    ) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            searchResultTxids = emptyList()
            searchTotalCount = sourceFilteredTransactions.size
            isSearchFiltering = false
            return@LaunchedEffect
        }

        isSearchFiltering = true
        searchResultTxids = emptyList()
        searchTotalCount = 0
        kotlinx.coroutines.delay(150)
        val result = searchTransactions(trimmedQuery, showSwapTransactions, displayLimit)
        searchResultTxids = result.txids
        searchTotalCount = result.totalCount
        isSearchFiltering = false
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
        indicator = {},
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        // Add stretch effect only when user is actively pulling
                        val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                        translationY = progress * 40f
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val cardAccentColor = BitcoinOrange

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = DarkCard,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                    ) {
                        // Top row — privacy toggle (left) + sync button (right)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .padding(bottom = 4.dp)
                                    .align(Alignment.TopCenter),
                        ) {
                            // Privacy toggle — pinned to the left
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .pointerInput(privacyMode) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                if (privacyMode) {
                                                    val releasedBeforeReveal =
                                                        withTimeoutOrNull(350L) {
                                                            waitForUpOrCancellation()
                                                        } != null
                                                    if (!releasedBeforeReveal) {
                                                        onTogglePrivacy()
                                                        waitForUpOrCancellation()
                                                    }
                                                } else {
                                                    waitForUpOrCancellation()?.let {
                                                        onTogglePrivacy()
                                                    }
                                                }
                                            }
                                        },
                            ) {
                                Icon(
                                    imageVector = if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle privacy",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            // Sync button — pinned to the right
                            val isSyncing = walletState.isSyncing
                            val syncEnabled = walletState.isInitialized && !walletState.isSyncing
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable(enabled = syncEnabled) {
                                            onSync()
                                        },
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = cardAccentColor,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync",
                                        tint =
                                            if (walletState.isInitialized) {
                                                TextSecondary
                                            } else {
                                                TextSecondary.copy(
                                                    alpha = 0.3f,
                                                )
                                            },
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        // Balance content — vertically centered in the card
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Main balance with inline denomination — tap to toggle BTC/sats
                            Text(
                                text =
                                    if (privacyMode) {
                                        HIDDEN_AMOUNT
                                    } else if (useSats) {
                                        "${formatAmount(walletState.balanceSats, true)} sats"
                                    } else {
                                        "\u20BF ${formatAmount(walletState.balanceSats, false)}"
                                    },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleDenomination,
                                ),
                            )

                            // USD value
                            if (btcPrice != null && btcPrice > 0) {
                                val usdValue = (walletState.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                                Text(
                                    text = if (privacyMode) HIDDEN_AMOUNT else formatFiat(usdValue, fiatCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                )
                            }

                        }

                        // Quick receive button — pinned to bottom left
                        val quickReceiveEnabled = walletState.currentAddress != null
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = quickReceiveEnabled) {
                                        showQuickReceive = true
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Quick receive",
                                tint = cardAccentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // QR scan button — pinned to bottom right
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = walletState.isInitialized) {
                                        showQrScanner = true
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = cardAccentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        if (isNfcReaderActive) {
                            val nfcStatusLabel =
                                when (nfcReaderState) {
                                    NfcReaderUiState.Inactive,
                                    NfcReaderUiState.Ready,
                                    -> stringResource(R.string.nfc_status_ready)
                                    NfcReaderUiState.Detecting -> stringResource(R.string.nfc_status_detecting)
                                    NfcReaderUiState.Received -> stringResource(R.string.nfc_status_received)
                                }
                            val nfcStatusColor =
                                if (nfcReaderState == NfcReaderUiState.Detecting) {
                                    BitcoinOrange
                                } else {
                                    SuccessGreen
                                }
                            NfcStatusIndicator(
                                label = nfcStatusLabel,
                                contentDescription = nfcStatusLabel,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp),
                                color = nfcStatusColor,
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
                        colors =
                            CardDefaults.cardColors(
                                containerColor = DarkCard,
                            ),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No Wallet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add a wallet to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onManageWallets,
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, BitcoinOrangeLight),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = BitcoinOrange,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (hasSwapTransactions) {
                            TransactionFilterButton(
                                icon = Icons.Default.SwapHoriz,
                                contentDescription = "Filter swaps",
                                tint = BitcoinOrange,
                                isSelected = showSwapTransactions,
                                onClick = { showSwapTransactions = !showSwapTransactions },
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable {
                                        isSearchActive = !isSearchActive
                                        if (!isSearchActive) searchQuery = ""
                                    },
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close search" else "Search",
                                tint = if (isSearchActive) {
                                    BitcoinOrange
                                } else {
                                    TextSecondary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Search field
                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search labels, addresses, txid, or date...",
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange,
                            ),
                        trailingIcon = {
                            if (isSearchFiltering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            val isSearching = searchQuery.trim().isNotBlank()
            val visibleTransactions =
                if (isSearching) {
                    searchResultTxids.mapNotNull(transactionsById::get)
                } else {
                    sourceFilteredTransactions.take(displayLimit)
                }
            val totalCount = if (isSearching) searchTotalCount else sourceFilteredTransactions.size
            val visibleCount = visibleTransactions.size
            val hasMore = visibleCount < totalCount
            val showTransactionLoadingState =
                totalCount == 0 &&
                    (
                        walletState.isTransactionHistoryLoading ||
                            (walletState.isSyncing && walletState.transactions.isEmpty())
                    )

            if (showTransactionLoadingState) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = DarkCard,
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        walletState.syncProgress?.status
                                            ?: if (walletState.isTransactionHistoryLoading) {
                                                "Loading transaction history..."
                                            } else {
                                                "Syncing wallet..."
                                            },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text =
                                        if (walletState.isTransactionHistoryLoading) {
                                            "Balance is ready. Transactions will appear shortly."
                                        } else {
                                            "Recent activity will appear when sync completes."
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            } else if (totalCount == 0 && !isSearchFiltering) {
                item {
                    // Empty state for transactions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = DarkCard,
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = if (isSearching) "No matching transactions" else "No transactions yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (isSearching) {
                                            "Try a different search term"
                                        } else if (walletState.isInitialized) {
                                            "Transactions will appear here after syncing"
                                        } else {
                                            "Add a wallet to get started"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            } else {
                items(visibleTransactions, key = { it.txid }) { tx ->
                    // Look up label: first check transaction label, then address label
                    val label =
                        transactionLabels[tx.txid]
                            ?: tx.address?.let { addressLabels[it] }

                    TransactionItem(
                        transaction = tx,
                        useSats = useSats,
                        label = label,
                        btcPrice = btcPrice,
                        fiatCurrency = fiatCurrency,
                        privacyMode = privacyMode,
                        onClick = { selectedTransaction = tx },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!isSearching && walletState.isTransactionHistoryLoading) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = DarkCard,
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 2.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Loading more transactions...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Showing the newest activity first while older history finishes loading.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Progressive "Show More" / "Show All" buttons
                if (hasMore) {
                    item {
                        val remaining = totalCount - visibleCount
                        TextButton(
                            onClick = {
                                displayLimit = if (displayLimit <= 25) {
                                    100
                                } else {
                                    Int.MAX_VALUE
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text =
                                    if (displayLimit <= 25) {
                                        "Show More"
                                    } else {
                                        "Show All ($remaining remaining)"
                                    },
                                color = TextSecondary,
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
            onDismiss = { showQrScanner = false },
        )
    }

    // Quick Receive Dialog — must be outside LazyColumn
    val quickReceiveAddress = walletState.currentAddress
    if (showQuickReceive && quickReceiveAddress != null) {
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(quickReceiveAddress) {
            qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                generateQrBitmap(quickReceiveAddress)
            }
        }
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
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // QR Code
                    qrBitmap?.let { bitmap ->
                        Box(
                            modifier =
                                Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Receive address QR",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Address + copy icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "${quickReceiveAddress.take(9)}...${quickReceiveAddress.takeLast(9)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy address",
                            tint = if (showCopied) BitcoinOrange else TextSecondary,
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .clickable {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            "Address",
                                            quickReceiveAddress,
                                        )
                                        showCopied = true
                                    },
                        )
                    }

                    if (showCopied) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Copied to clipboard!",
                            style = MaterialTheme.typography.bodySmall,
                            color = BitcoinOrange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Layer2RequiredPlaceholder(
    walletName: String?,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = DarkCard,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = walletName ?: "Layer 2 required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Enable Layer 2 to view this Liquid watch-only wallet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    IbisButton(
                        onClick = onOpenSettings,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text("Open Settings", style = MaterialTheme.typography.titleMedium)
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
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    onClick: () -> Unit = {},
) {
    val swapDetails = transaction.swapDetails
    val isReceived =
        when (swapDetails?.role) {
            LiquidSwapTxRole.FUNDING -> false
            LiquidSwapTxRole.SETTLEMENT -> true
            null -> transaction.amountSats > 0
        }
    val absSats = swapDisplayAmountSats(transaction).toULong()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = DarkCard,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isReceived) {
                                AccentGreen.copy(alpha = 0.1f)
                            } else {
                                AccentRed.copy(alpha = 0.1f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (isReceived) {
                            Icons.AutoMirrored.Filled.CallReceived
                        } else {
                            Icons.AutoMirrored.Filled.CallMade
                        },
                    contentDescription = if (isReceived) "Received" else "Sent",
                    tint = if (isReceived) AccentGreen else AccentRed,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isReceived) "Received" else "Sent",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (swapDetails != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BitcoinOrange.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Swap",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = BitcoinOrange,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Date and time
                Text(
                    text = transaction.timestamp?.let { formatDateTime(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                val amountText =
                    if (privacyMode) {
                        HIDDEN_AMOUNT
                    } else {
                        val sign = if (isReceived) "+" else "-"
                        val amount = formatAmount(absSats, useSats)
                        if (useSats) "$sign$amount sats" else "$sign$amount"
                    }
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isReceived) AccentGreen else AccentRed,
                    textAlign = TextAlign.End,
                )
                // USD value
                if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                    val usdValue = (absSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = formatFiat(usdValue, fiatCurrency),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                if (!transaction.isConfirmed) {
                    StatusBadge(
                        label = "Pending",
                        color = BitcoinOrange,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val date = Date(timestamp * 1000) // Convert seconds to milliseconds
    val format = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    return format.format(date)
}

private fun TransactionDetails.confirmationProgressText(currentBlockHeight: UInt?): String? {
    if (!isConfirmed) return "0/6"
    val confirmationHeight = confirmationTime?.height ?: return null
    val blockHeight = currentBlockHeight ?: return null
    val confirmations =
        (blockHeight.toLong() - confirmationHeight.toLong() + 1L)
            .coerceAtLeast(1L)
            .coerceAtMost(6L)
            .toInt()
    return if (confirmations >= 6) null else "$confirmations/6"
}

private fun swapFundingAmountSats(
    transaction: TransactionDetails,
    details: LiquidSwapDetails,
): Long? {
    val netWithoutFee =
        transaction.fee?.toLong()?.let { fee ->
            (kotlin.math.abs(transaction.amountSats) - fee).takeIf { it > 0L }
        }
    return transaction.addressAmount?.toLong()?.takeIf { it > 0L }
        ?: netWithoutFee
        ?: details.sendAmountSats.takeIf { it > 0L }
}

private fun swapSettlementAmountSats(
    transaction: TransactionDetails,
    details: LiquidSwapDetails,
): Long? {
    return transaction.addressAmount?.toLong()?.takeIf { it > 0L }
        ?: kotlin.math.abs(transaction.amountSats).takeIf { it > 0L }
        ?: details.expectedReceiveAmountSats.takeIf { it > 0L }
}

private fun swapDisplayAmountSats(transaction: TransactionDetails): Long {
    val details = transaction.swapDetails ?: return kotlin.math.abs(transaction.amountSats)
    return when (details.role) {
        LiquidSwapTxRole.FUNDING ->
            swapFundingAmountSats(transaction, details) ?: kotlin.math.abs(transaction.amountSats)
        LiquidSwapTxRole.SETTLEMENT ->
            swapSettlementAmountSats(transaction, details) ?: kotlin.math.abs(transaction.amountSats)
    }
}

private fun swapProviderLabel(service: SwapService): String =
    when (service) {
        SwapService.BOLTZ -> "Boltz"
        SwapService.SIDESWAP -> "SideSwap"
    }

private fun swapProviderColor(service: SwapService): Color =
    when (service) {
        SwapService.BOLTZ -> AccentBlue
        SwapService.SIDESWAP -> AccentTeal
    }

private fun swapDepositLabel(details: LiquidSwapDetails): String =
    when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "Bitcoin Deposit Address"
        SwapDirection.LBTC_TO_BTC -> "Liquid Deposit Address"
    }

private fun swapReceiveLabel(details: LiquidSwapDetails): String =
    when (details.direction) {
        SwapDirection.BTC_TO_LBTC -> "Liquid Destination Address"
        SwapDirection.LBTC_TO_BTC -> "Bitcoin Destination Address"
    }

private fun swapPrimaryAmountColor(direction: SwapDirection): Color =
    when (direction) {
        SwapDirection.BTC_TO_LBTC -> BitcoinOrange
        SwapDirection.LBTC_TO_BTC -> AccentTeal
    }

private fun swapExpectedReceiveColor(direction: SwapDirection): Color =
    when (direction) {
        SwapDirection.BTC_TO_LBTC -> AccentTeal
        SwapDirection.LBTC_TO_BTC -> BitcoinOrange
    }

private fun swapDetailPrimaryAmountText(
    transaction: TransactionDetails,
    details: LiquidSwapDetails,
    useSats: Boolean,
    privacyMode: Boolean,
): String? {
    val asset = if (details.direction == SwapDirection.BTC_TO_LBTC) "BTC" else "L-BTC"
    val amountSats =
        when (details.role) {
            LiquidSwapTxRole.FUNDING -> swapFundingAmountSats(transaction, details)
            LiquidSwapTxRole.SETTLEMENT -> details.sendAmountSats.takeIf { it > 0L }
        } ?: return null
    return formatSwapAssetAmount(amountSats, asset, useSats, privacyMode)
}

private fun swapDetailExpectedReceiveText(
    transaction: TransactionDetails,
    details: LiquidSwapDetails,
    useSats: Boolean,
    privacyMode: Boolean,
): String? {
    val asset = if (details.direction == SwapDirection.BTC_TO_LBTC) "L-BTC" else "BTC"
    val amountSats =
        when (details.role) {
            LiquidSwapTxRole.FUNDING -> details.expectedReceiveAmountSats.takeIf { it > 0L }
            LiquidSwapTxRole.SETTLEMENT -> swapSettlementAmountSats(transaction, details)
        } ?: return null
    return formatSwapAssetAmount(amountSats, asset, useSats, privacyMode)
}

private fun formatSwapAssetAmount(
    amountSats: Long,
    asset: String,
    useSats: Boolean,
    privacyMode: Boolean,
): String {
    if (privacyMode) return HIDDEN_AMOUNT
    val unit = if (useSats) "sats" else "BTC"
    return "${formatAmount(amountSats.toULong(), useSats)} $unit $asset"
}

private fun truncateSwapDetailValue(
    value: String,
    leadingChars: Int = 16,
    trailingChars: Int = 8,
): String {
    if (value.length <= leadingChars + trailingChars + 3) return value
    return "${value.take(leadingChars)}...${value.takeLast(trailingChars)}"
}

@Composable
private fun SwapDetailCopyRow(
    label: String,
    value: String,
    copyLabel: String,
    amountText: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onBackground,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = truncateSwapDetailValue(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                amountText?.let {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val liftPx = 4.dp.roundToPx()
                            layout(placeable.width, (placeable.height - liftPx).coerceAtLeast(0)) {
                                placeable.placeRelative(0, -liftPx)
                            }
                        }
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable(onClick = onCopy),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $copyLabel",
                    tint = if (copied) BitcoinOrange else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (copied) {
            Text(
                text = "Copied to clipboard!",
                style = MaterialTheme.typography.bodySmall,
                color = BitcoinOrange,
            )
        }
    }
}

@Composable
private fun SwapDetailBadge(
    text: String,
    accentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwapDetailDirectionBadge(
    direction: SwapDirection,
) {
    val fromText = if (direction == SwapDirection.BTC_TO_LBTC) "BTC" else "L-BTC"
    val toText = if (direction == SwapDirection.BTC_TO_LBTC) "L-BTC" else "BTC"
    val fromColor = if (direction == SwapDirection.BTC_TO_LBTC) BitcoinOrange else AccentTeal
    val toColor = if (direction == SwapDirection.BTC_TO_LBTC) AccentTeal else BitcoinOrange

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = fromText,
            style = MaterialTheme.typography.labelMedium,
            color = fromColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "->",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = toText,
            style = MaterialTheme.typography.labelMedium,
            color = toColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TransactionFilterButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) tint.copy(alpha = 0.16f) else DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) tint else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val HIDDEN_AMOUNT = "****"

/**
 * Transaction Detail Dialog
 * Shows complete transaction details when user taps on a transaction
 */
@Composable
fun TransactionDetailDialog(
    transaction: TransactionDetails,
    currentBlockHeight: UInt? = null,
    useSats: Boolean = false,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    label: String? = null,
    canRbf: Boolean = false,
    canCpfp: Boolean = false,
    availableBalance: ULong = 0UL,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    onFetchVsize: suspend (String) -> Double? = { null },
    onRefreshFees: () -> Unit = {},
    onSpeedUp: ((SpeedUpMethod, Double, String?) -> Unit)? = null,
    onSaveLabel: (String) -> Unit = {},
    onDeleteLabel: (() -> Unit)? = null,
    boltzRescueMnemonic: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val swapDetails = transaction.swapDetails
    val isReceived =
        when (swapDetails?.role) {
            LiquidSwapTxRole.FUNDING -> false
            LiquidSwapTxRole.SETTLEMENT -> true
            null -> transaction.amountSats > 0
        }
    val amountAbs = swapDisplayAmountSats(transaction).toULong()
    val scrollState = rememberScrollState()

    // State for showing copy confirmation
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedChangeAddress by remember { mutableStateOf(false) }
    var copiedSwapField by remember { mutableStateOf<String?>(null) }
    var swapDetailsExpanded by remember { mutableStateOf(false) }
    var showRescueKeyDialog by remember { mutableStateOf(false) }

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
    LaunchedEffect(copiedSwapField) {
        if (copiedSwapField != null) {
            kotlinx.coroutines.delay(3000)
            copiedSwapField = null
        }
    }

    // State for showing Tor Browser error dialog
    var showTorBrowserError by remember { mutableStateOf(false) }

    // State for Speed Up dialog
    var showSpeedUpDialog by remember { mutableStateOf(false) }

    // State for Redirect (cancel) dialog
    var showRedirectDialog by remember { mutableStateOf(false) }
    val canRedirect = canRbf && !isReceived && !transaction.isConfirmed && !transaction.isSelfTransfer

    // State for label editing
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember(label) { mutableStateOf(label ?: "") }

    // State for fetched vsize from Electrum (fractional: weight/4.0)
    var fetchedVsize by remember { mutableStateOf<Double?>(null) }

    // Fetch actual vsize from Electrum when dialog opens (for sent transactions with fee info)
    LaunchedEffect(transaction.txid) {
        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
            fetchedVsize = onFetchVsize(transaction.txid)
        }
    }

    // Check if mempool URL is an onion address
    val isOnionAddress =
        try {
            java.net.URI(mempoolUrl).host?.endsWith(".onion") == true
        } catch (_: Exception) {
            mempoolUrl.endsWith(".onion")
        }
    val canOpenBlockExplorer = mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank()
    val openBlockExplorer = {
        val url = "$mempoolUrl/tx/${transaction.txid}"
        if (isOnionAddress) {
            val torBrowserPackage = "org.torproject.torbrowser"
            val intent =
                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    setPackage(torBrowserPackage)
                }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                showTorBrowserError = true
            }
        } else {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        }
    }

    // Determine if Speed Up is available
    val canSpeedUp = !transaction.isConfirmed && (canRbf || canCpfp)
    val speedUpMethod =
        when {
            canRbf && !isReceived -> SpeedUpMethod.RBF
            canCpfp -> SpeedUpMethod.CPFP
            else -> null
        }

    val denominationLabel = if (useSats) "sats" else "BTC"
    val confirmationProgressText = transaction.confirmationProgressText(currentBlockHeight)

    // Tor Browser error dialog
    if (showTorBrowserError) {
        TorBrowserErrorDialog(
            onDismiss = { showTorBrowserError = false },
        )
    }

    if (
        showRescueKeyDialog &&
        swapDetails != null &&
        shouldShowBoltzRescueKey(swapDetails.service, boltzRescueMnemonic)
    ) {
        BoltzRescueMnemonicDialog(
            mnemonic = boltzRescueMnemonic.orEmpty(),
            accentColor = BitcoinOrange,
            onDismiss = { showRescueKeyDialog = false },
        )
    }

    // Speed Up dialog
    if (showSpeedUpDialog && speedUpMethod != null && onSpeedUp != null) {
        val dialogVsize = fetchedVsize ?: transaction.vsize
        // For CPFP, the spendable output is either the received amount or the change amount
        val isReceived = transaction.amountSats > 0
        val cpfpSpendableOutput =
            if (isReceived) {
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
            minFeeRate = minFeeRate,
            useSats = useSats,
            privacyMode = privacyMode,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate, _ ->
                onSpeedUp(speedUpMethod, feeRate, null)
                showSpeedUpDialog = false
                onDismiss()
            },
            onDismiss = { showSpeedUpDialog = false },
        )
    }

    // Redirect (cancel transaction) dialog
    if (showRedirectDialog && canRedirect && onSpeedUp != null) {
        val dialogVsize = fetchedVsize ?: transaction.vsize
        SpeedUpDialog(
            method = SpeedUpMethod.REDIRECT,
            currentFee = transaction.fee,
            currentFeeRate = transaction.feeRate,
            vsize = dialogVsize,
            availableBalance = availableBalance,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            useSats = useSats,
            privacyMode = privacyMode,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate, destinationAddress ->
                onSpeedUp(SpeedUpMethod.REDIRECT, feeRate, destinationAddress)
                showRedirectDialog = false
                onDismiss()
            },
            onDismiss = { showRedirectDialog = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkCard)
                                .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount display (large, centered)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isReceived) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f),
                            )
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Direction icon
                        Icon(
                            imageVector =
                                when {
                                    swapDetails != null -> Icons.Default.SwapHoriz
                                    transaction.isSelfTransfer -> Icons.AutoMirrored.Filled.CallMade
                                    isReceived -> Icons.AutoMirrored.Filled.CallReceived
                                    else -> Icons.AutoMirrored.Filled.CallMade
                                },
                            contentDescription = null,
                            tint =
                                when {
                                    transaction.isSelfTransfer -> BitcoinOrange
                                    isReceived -> AccentGreen
                                    else -> AccentRed
                                },
                            modifier = Modifier.size(28.dp),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Direction text
                        Text(
                            text =
                                when {
                                    swapDetails != null -> "Swap"
                                    transaction.isSelfTransfer -> "Self-Transfer"
                                    isReceived -> "Received"
                                    else -> "Sent"
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color =
                                when {
                                    transaction.isSelfTransfer -> BitcoinOrange
                                    isReceived -> AccentGreen
                                    else -> AccentRed
                                },
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Amount (based on denomination setting)
                        Text(
                            text =
                                if (privacyMode) {
                                    HIDDEN_AMOUNT
                                } else {
                                    "${if (isReceived) "+" else "-"}${formatAmount(
                                        amountAbs,
                                        useSats,
                                    )} $denominationLabel"
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceived) AccentGreen else AccentRed,
                        )

                        // USD amount (if price is available)
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (amountAbs.toDouble() / 100_000_000.0) * btcPrice
                            Text(
                                text = formatFiat(usdValue, fiatCurrency),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speed Up button (for pending transactions)
                if (canSpeedUp && speedUpMethod != null && onSpeedUp != null) {
                    OutlinedButton(
                        onClick = { showSpeedUpDialog = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = BitcoinOrange,
                            ),
                        border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (speedUpMethod) {
                                SpeedUpMethod.RBF -> "Bump Fee (RBF)"
                                SpeedUpMethod.CPFP -> "Bump Fee (CPFP)"
                                SpeedUpMethod.REDIRECT -> "Bump Fee (RBF)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cancel Transaction button (redirect to self via RBF)
                if (canRedirect && onSpeedUp != null) {
                    OutlinedButton(
                        onClick = { showRedirectDialog = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorRed,
                            ),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cancel Transaction (RBF)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Transaction details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                    ) {
                        // Status section
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                if (canOpenBlockExplorer) {
                                    TransactionExplorerBadge(
                                        tint = BitcoinOrange,
                                        onClick = openBlockExplorer,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector =
                                            if (transaction.isConfirmed) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Schedule
                                            },
                                        contentDescription = null,
                                        tint = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (transaction.isConfirmed) "Confirmed" else "Pending",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                    )
                                    confirmationProgressText?.let { progress ->
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = progress,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (transaction.isConfirmed) AccentGreen else BitcoinOrange,
                                        )
                                    }
                                }
                                if (transaction.timestamp != null) {
                                    Text(
                                        text = formatFullTimestamp(transaction.timestamp * 1000),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        // Transaction ID
                        Column {
                            Text(
                                text = "Transaction ID",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = transaction.txid,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val liftPx = 4.dp.roundToPx()
                                                layout(placeable.width, (placeable.height - liftPx).coerceAtLeast(0)) {
                                                    placeable.placeRelative(0, -liftPx)
                                                }
                                            }
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .clickable {
                                                SecureClipboard.copyAndScheduleClear(
                                                    context,
                                                    "Transaction ID",
                                                    transaction.txid,
                                                )
                                                showCopiedTxid = true
                                            },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (showCopiedTxid) BitcoinOrange else TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            // Show "Copied!" text
                            if (showCopiedTxid) {
                                Text(
                                    text = "Copied to clipboard!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        // Address (recipient for sent, receiving address for received, or destination for self-transfer)
                        if (transaction.address != null) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isReceived && !transaction.isSelfTransfer) "Received at" else "Recipient",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    if (transaction.isSelfTransfer) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Self-transfer)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BorderColor,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = truncateSwapDetailValue(transaction.address),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        transaction.addressAmount?.let { amount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text =
                                                    if (privacyMode) {
                                                        HIDDEN_AMOUNT
                                                    } else {
                                                        "${if (isReceived || transaction.isSelfTransfer) "+" else "-"}${formatAmount(
                                                            amount,
                                                            useSats,
                                                        )} $denominationLabel"
                                                    },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isReceived || transaction.isSelfTransfer) AccentGreen else AccentRed,
                                            )
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .layout { measurable, constraints ->
                                                    val placeable = measurable.measure(constraints)
                                                    val liftPx = 4.dp.roundToPx()
                                                    layout(placeable.width, (placeable.height - liftPx).coerceAtLeast(0)) {
                                                        placeable.placeRelative(0, -liftPx)
                                                    }
                                                }
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurfaceVariant)
                                                .clickable {
                                                    SecureClipboard.copyAndScheduleClear(
                                                        context,
                                                        "Address",
                                                        transaction.address,
                                                    )
                                                    showCopiedAddress = true
                                                },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Address",
                                            tint = if (showCopiedAddress) BitcoinOrange else TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                // Show "Copied!" text
                                if (showCopiedAddress) {
                                    Text(
                                        text = "Copied to clipboard!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BitcoinOrange,
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )

                            // Change Address (for sent transactions and self-transfers)
                            if ((!isReceived || transaction.isSelfTransfer) && transaction.changeAddress != null && transaction.changeAddress != transaction.address) {
                                Column {
                                    Text(
                                        text = "Change returned to",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = truncateSwapDetailValue(transaction.changeAddress),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            transaction.changeAmount?.let { amount ->
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text =
                                                        if (privacyMode) {
                                                            HIDDEN_AMOUNT
                                                        } else {
                                                            "+${formatAmount(
                                                                amount,
                                                                useSats,
                                                            )} $denominationLabel"
                                                        },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = AccentGreen,
                                                )
                                            }
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier =
                                                Modifier
                                                    .layout { measurable, constraints ->
                                                        val placeable = measurable.measure(constraints)
                                                        val liftPx = 4.dp.roundToPx()
                                                        layout(placeable.width, (placeable.height - liftPx).coerceAtLeast(0)) {
                                                            placeable.placeRelative(0, -liftPx)
                                                        }
                                                    }
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(DarkSurfaceVariant)
                                                    .clickable {
                                                        SecureClipboard.copyAndScheduleClear(
                                                            context,
                                                            "Change Address",
                                                            transaction.changeAddress,
                                                        )
                                                        showCopiedChangeAddress = true
                                                    },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Change Address",
                                                tint = if (showCopiedChangeAddress) BitcoinOrange else TextSecondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    // Show "Copied!" text
                                    if (showCopiedChangeAddress) {
                                        Text(
                                            text = "Copied to clipboard!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                            }
                        }

                        // Fee (if available and it's a sent transaction)
                        if (!isReceived && transaction.fee != null && transaction.fee > 0UL) {
                            Column {
                                Text(
                                    text = "Network Fee",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Use fetched vsize from Electrum if available, otherwise fall back to BDK's weight/4.0
                                val displayVsize: Double? = fetchedVsize ?: transaction.vsize
                                // Recalculate fee rate using the fractional vsize
                                val displayFeeRate =
                                    if (displayVsize != null && displayVsize > 0.0) {
                                        transaction.fee.toDouble() / displayVsize
                                    } else {
                                        transaction.feeRate
                                    }
                                if (displayFeeRate != null && displayVsize != null) {
                                    Text(
                                        text = "${formatFeeRate(
                                            displayFeeRate,
                                        )} sat/vB • ${formatVBytes(displayVsize)} vB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }
                                Text(
                                    text =
                                        if (privacyMode) {
                                            HIDDEN_AMOUNT
                                        } else {
                                            "-${formatAmount(
                                                transaction.fee,
                                                useSats,
                                            )} $denominationLabel"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BitcoinOrange,
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                        }

                        // Label section
                        Column {
                            Text(
                                text = "Label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isEditingLabel) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = { labelText = it },
                                    placeholder = { Text("Enter label", color = TextSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            cursorColor = BitcoinOrange,
                                        ),
                                    trailingIcon = {
                                        TextButton(
                                            onClick = {
                                                onSaveLabel(labelText)
                                                isEditingLabel = false
                                            },
                                        ) {
                                            Text("Save", color = BitcoinOrange)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                EditableLabelChip(
                                    label = labelText.takeIf { it.isNotBlank() },
                                    accentColor = AccentTeal,
                                    onClick = { isEditingLabel = true },
                                    onDelete =
                                        onDeleteLabel?.let {
                                            {
                                                labelText = ""
                                                it()
                                            }
                                        },
                                )
                            }
                        }
                        swapDetails?.let { details ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { swapDetailsExpanded = !swapDetailsExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Swap Details",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                SwapDetailBadge(
                                    text = swapProviderLabel(details.service),
                                    accentColor = swapProviderColor(details.service),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (swapDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (swapDetailsExpanded) "Collapse swap details" else "Expand swap details",
                                    tint = BitcoinOrange,
                                )
                            }

                            if (swapDetailsExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SwapDetailDirectionBadge(direction = details.direction)
                                        if (shouldShowBoltzRescueKey(details.service, boltzRescueMnemonic)) {
                                            BoltzRescueKeyButton(
                                                onClick = { showRescueKeyDialog = true },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SwapDetailCopyRow(
                                        label = if (details.service == SwapService.BOLTZ) "Boltz Swap ID" else "SideSwap Order ID",
                                        value = details.swapId,
                                        copyLabel = "Swap ID",
                                        copied = copiedSwapField == "swap_id",
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(context, "Swap ID", details.swapId)
                                            copiedSwapField = "swap_id"
                                        },
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SwapDetailCopyRow(
                                        label = swapDepositLabel(details),
                                        value = details.depositAddress,
                                        copyLabel = "Swap Deposit Address",
                                        amountText =
                                            swapDetailPrimaryAmountText(
                                                transaction = transaction,
                                                details = details,
                                                useSats = useSats,
                                                privacyMode = privacyMode,
                                            ),
                                        amountColor = swapPrimaryAmountColor(details.direction),
                                        copied = copiedSwapField == "deposit_address",
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(
                                                context,
                                                "Swap Deposit Address",
                                                details.depositAddress,
                                            )
                                            copiedSwapField = "deposit_address"
                                        },
                                    )
                                    details.receiveAddress?.let { receiveAddress ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        SwapDetailCopyRow(
                                            label = swapReceiveLabel(details),
                                            value = receiveAddress,
                                            copyLabel = "Swap Destination Address",
                                            amountText =
                                                swapDetailExpectedReceiveText(
                                                    transaction = transaction,
                                                    details = details,
                                                    useSats = useSats,
                                                    privacyMode = privacyMode,
                                                ),
                                            amountColor = swapExpectedReceiveColor(details.direction),
                                            copied = copiedSwapField == "receive_address",
                                            onCopy = {
                                                SecureClipboard.copyAndScheduleClear(
                                                    context,
                                                    "Swap Destination Address",
                                                    receiveAddress,
                                                )
                                                copiedSwapField = "receive_address"
                                            },
                                        )
                                    }
                                    details.refundAddress?.let { refundAddress ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        SwapDetailCopyRow(
                                            label = "Refund Address",
                                            value = refundAddress,
                                            copyLabel = "Refund Address",
                                            copied = copiedSwapField == "refund_address",
                                            onCopy = {
                                                SecureClipboard.copyAndScheduleClear(
                                                    context,
                                                    "Refund Address",
                                                    refundAddress,
                                                )
                                                copiedSwapField = "refund_address"
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
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
private fun TorBrowserErrorDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = AccentRed,
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Text(
                    text = "Tor Browser Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tor Browser is required to view onion links. Please install it from the Play Store or F-Droid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Speed Up Transaction Dialog
 * Allows user to set a new fee rate for RBF, CPFP, or redirect replacement
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
    minFeeRate: Double = 1.0,
    useSats: Boolean = true,
    privacyMode: Boolean = false,
    onRefreshFees: () -> Unit = {},
    onConfirm: (Double, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogTitle =
        when (method) {
            SpeedUpMethod.RBF -> "Bump Fee (RBF)"
            SpeedUpMethod.CPFP -> "Bump Fee (CPFP)"
            SpeedUpMethod.REDIRECT -> "Cancel Transaction"
        }
    val dialogDescription =
        when (method) {
            SpeedUpMethod.RBF -> "Replace this transaction with a higher fee (RBF)"
            SpeedUpMethod.CPFP -> "Create a child transaction with high fee to speed up confirmation (CPFP)"
            SpeedUpMethod.REDIRECT -> "Redirect all funds back to your wallet or a custom address. Requires a higher fee than the original."
        }
    var appliedFeeRate by remember { mutableDoubleStateOf(0.0) }
    var rawCustomFeeRate by remember { mutableStateOf<Double?>(null) }
    var useCustomDestinationAddress by remember(method) { mutableStateOf(false) }
    var customDestinationInput by remember(method) { mutableStateOf("") }
    var showCustomDestinationQrScanner by remember(method) { mutableStateOf(false) }
    val enteredFeeRate = rawCustomFeeRate ?: appliedFeeRate
    val isBelowMinFeeRate = enteredFeeRate > 0.0 && enteredFeeRate < minFeeRate
    val isBelowCurrentFeeRate =
        currentFeeRate != null &&
            enteredFeeRate > 0.0 &&
            enteredFeeRate <= currentFeeRate
    val isValidFeeRate =
        appliedFeeRate > 0.0 &&
            enteredFeeRate <= MAX_FEE_RATE_SAT_VB &&
            !isBelowMinFeeRate &&
            !isBelowCurrentFeeRate
    val parsedCustomDestination =
        remember(useCustomDestinationAddress, customDestinationInput) {
            if (!useCustomDestinationAddress) {
                null
            } else {
                parseSendRecipient(customDestinationInput)
            }
        }
    val customDestinationHelperText =
        when {
            method != SpeedUpMethod.REDIRECT || !useCustomDestinationAddress -> null
            customDestinationInput.isBlank() -> "Enter a Bitcoin address"
            else -> null
        }
    val customDestinationError =
        when {
            method != SpeedUpMethod.REDIRECT || !useCustomDestinationAddress -> null
            customDestinationInput.isBlank() -> null
            parsedCustomDestination is ParsedSendRecipient.Bitcoin -> null
            parsedCustomDestination is ParsedSendRecipient.Unknown -> parsedCustomDestination.errorMessage
            else -> "Enter a Bitcoin address"
        }
    val resolvedCustomDestination =
        (parsedCustomDestination as? ParsedSendRecipient.Bitcoin)?.address
    val hasValidCustomDestination =
        method != SpeedUpMethod.REDIRECT ||
            !useCustomDestinationAddress ||
            resolvedCustomDestination != null

    // Estimated child tx vsize for CPFP (1 input + 1 output, conservative estimate)
    // P2WPKH: ~110 vB, P2TR: ~111 vB, adding buffer for safety
    val estimatedChildVsize = 150L

    // Dust limit - use 546 sats (Bitcoin Core default, covers all output types)
    // P2TR actual dust is ~387, P2WPKH is ~294
    val dustLimit = 546L

    // Calculate costs differently for RBF vs CPFP vs REDIRECT
    val additionalCost: Long? =
        when (method) {
            SpeedUpMethod.RBF, SpeedUpMethod.REDIRECT -> {
                if (vsize != null && appliedFeeRate > 0.0 && currentFee != null) {
                    val newTotalFee = (appliedFeeRate * vsize).toLong()
                    (newTotalFee - currentFee.toLong()).coerceAtLeast(0)
                } else {
                    null
                }
            }
            SpeedUpMethod.CPFP -> {
                if (appliedFeeRate > 0.0) {
                    val effectiveFeeRate = kotlin.math.ceil(appliedFeeRate).toLong()
                    (effectiveFeeRate * estimatedChildVsize)
                } else {
                    null
                }
            }
        }

    // For CPFP, we can use the parent output PLUS confirmed wallet balance
    // BDK will add more UTXOs if needed to cover the fee
    // For RBF, we need confirmed balance to cover the fee bump
    val fundsForFee =
        when (method) {
            SpeedUpMethod.RBF -> availableBalance
            SpeedUpMethod.REDIRECT -> availableBalance
            SpeedUpMethod.CPFP -> cpfpSpendableOutput + availableBalance
        }

    // Check affordability - need funds >= fee + dust (for change output)
    val canAfford =
        when (method) {
            SpeedUpMethod.RBF -> additionalCost == null || additionalCost <= 0 || additionalCost.toULong() <= fundsForFee
            SpeedUpMethod.REDIRECT -> true // Fee comes from the original inputs, not wallet balance
            SpeedUpMethod.CPFP -> {
                if (additionalCost == null || additionalCost <= 0) {
                    true
                } else {
                    // Need: totalFunds >= fee + dustLimit (to have valid change output)
                    fundsForFee.toLong() >= additionalCost + dustLimit
                }
            }
        }

    // Check if parent output alone can cover fee + dust
    val parentCanCoverAlone =
        method == SpeedUpMethod.CPFP &&
            additionalCost != null &&
            cpfpSpendableOutput.toLong() > additionalCost + dustLimit

    // If parent can't cover alone, wallet UTXOs will be consolidated
    val willConsolidateWallet =
        method == SpeedUpMethod.CPFP &&
            additionalCost != null &&
            !parentCanCoverAlone &&
            canAfford

    // Fetch fee estimates when dialog opens
    LaunchedEffect(Unit) {
        onRefreshFees()
    }

    if (showCustomDestinationQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showCustomDestinationQrScanner = false
                customDestinationInput =
                    when (val parsed = parseSendRecipient(code.trim())) {
                        is ParsedSendRecipient.Bitcoin -> parsed.address
                        else -> code.trim()
                    }
            },
            onDismiss = { showCustomDestinationQrScanner = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
            ) {
                // Header
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Method description
                Text(
                    text = dialogDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current fee info
                if (currentFeeRate != null || (currentFee != null && currentFee > 0UL)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            Text(
                                text = "Current Transaction:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFeeRate != null) {
                                Text(
                                    text = "Fee rate: ${formatFeeRate(currentFeeRate)} sat/vB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            if (currentFee != null && currentFee > 0UL) {
                                Text(
                                    text =
                                        if (privacyMode) {
                                            "Fee: $HIDDEN_AMOUNT"
                                        } else {
                                            "Fee: ${formatAmount(
                                                currentFee,
                                                useSats,
                                            )} ${if (useSats) "sats" else "BTC"}"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                FeeRateSection(
                    feeEstimationState = feeEstimationState,
                    currentFeeRate = appliedFeeRate,
                    minFeeRate = minFeeRate,
                    onFeeRateChange = { appliedFeeRate = it },
                    onRawCustomFeeRateChange = { rawCustomFeeRate = it },
                    onRefreshFees = onRefreshFees,
                    enabled = true,
                )

                if (isBelowMinFeeRate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fee rate is below the minimum (${formatFeeRate(minFeeRate)} sat/vB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                currentFeeRate?.takeIf { isBelowCurrentFeeRate }?.let { currentRate ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fee rate must be higher than current (${formatFeeRate(currentRate)} sat/vB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Transaction card (shows estimated fee info)
                if (appliedFeeRate > 0.0 && isValidFeeRate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            Text(
                                text = when (method) {
                                    SpeedUpMethod.RBF -> "Replacement Transaction:"
                                    SpeedUpMethod.REDIRECT -> "Redirect Transaction:"
                                    SpeedUpMethod.CPFP -> "Child Transaction (CPFP):"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Target fee rate: ${formatFeeRate(appliedFeeRate)} sat/vB",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                            )
                            if (additionalCost != null && additionalCost > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val costLabel = if (method == SpeedUpMethod.CPFP) "Child tx fee" else "Additional cost"
                                Text(
                                    text =
                                        if (privacyMode) {
                                            "$costLabel: $HIDDEN_AMOUNT"
                                        } else {
                                            "$costLabel: ${formatAmount(
                                                additionalCost.toULong(),
                                                useSats,
                                            )} ${if (useSats) "sats" else "BTC"}"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canAfford) BitcoinOrange else ErrorRed,
                                )
                                if (method == SpeedUpMethod.CPFP) {
                                    Text(
                                        text = "($estimatedChildVsize vB × ${kotlin.math.ceil(
                                            appliedFeeRate,
                                        ).toLong()} sat/vB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                    if (willConsolidateWallet) {
                                        Text(
                                            text = "Will consolidate all wallet UTXOs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BitcoinOrange,
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
                                            color = ErrorRed,
                                        )
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Need: $HIDDEN_AMOUNT (fee + dust)"
                                                } else {
                                                    "Need: ${formatAmount(
                                                        needed.toULong(),
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"} (fee + dust)"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErrorRed,
                                        )
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Have: $HIDDEN_AMOUNT (output + wallet)"
                                                } else {
                                                    "Have: ${formatAmount(
                                                        fundsForFee,
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"} (output + wallet)"
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                        )
                                    } else {
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "Insufficient funds"
                                                } else {
                                                    "Insufficient funds (Available: ${formatAmount(
                                                        fundsForFee,
                                                        useSats,
                                                    )} ${if (useSats) "sats" else "BTC"})"
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (method == SpeedUpMethod.REDIRECT) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    useCustomDestinationAddress = !useCustomDestinationAddress
                                    if (!useCustomDestinationAddress) {
                                        customDestinationInput = ""
                                    }
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = useCustomDestinationAddress,
                            onCheckedChange = {
                                useCustomDestinationAddress = it
                                if (!it) {
                                    customDestinationInput = ""
                                }
                            },
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = BitcoinOrange,
                                    uncheckedColor = TextSecondary,
                                ),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Custom destination address",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = useCustomDestinationAddress,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customDestinationInput,
                                onValueChange = { customDestinationInput = it.trim() },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                placeholder = {
                                    Text(
                                        text = "Bitcoin address",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showCustomDestinationQrScanner = true }) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "Scan QR Code",
                                            tint = BitcoinOrange,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                isError = customDestinationError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (customDestinationError != null) ErrorRed else BitcoinOrange,
                                        unfocusedBorderColor = if (customDestinationError != null) ErrorRed else BorderColor,
                                        cursorColor = BitcoinOrange,
                                    ),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = customDestinationError ?: (customDestinationHelperText ?: "Funds will be redirected to this Bitcoin address."),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customDestinationError != null) ErrorRed else TextSecondary,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button
                val canConfirm = isValidFeeRate && canAfford && customDestinationError == null && hasValidCustomDestination
                IbisButton(
                    onClick = {
                        onConfirm(
                            appliedFeeRate,
                            if (method == SpeedUpMethod.REDIRECT && useCustomDestinationAddress) {
                                resolvedCustomDestination
                            } else {
                                null
                            },
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    enabled = canConfirm,
                ) {
                    Text(
                        text =
                            when (method) {
                                SpeedUpMethod.RBF -> "Bump Fee"
                                SpeedUpMethod.CPFP -> "Bump Fee"
                                SpeedUpMethod.REDIRECT -> "Cancel Transaction"
                            },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TransactionExplorerBadge(
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tint.copy(alpha = 0.16f))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
    ) {
        Text(
            text = "Explorer",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open in block explorer",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
