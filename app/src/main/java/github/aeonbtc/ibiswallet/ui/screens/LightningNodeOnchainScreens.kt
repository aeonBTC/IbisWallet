package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.ui.components.StatusBadge
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainTransaction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainSendState
import github.aeonbtc.ibiswallet.data.model.LightningNodeOnchainState
import github.aeonbtc.ibiswallet.data.model.Recipient
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.nfc.NfcReaderUiState
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.NfcShareUiState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.AvailableBalanceMaxRow
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ReceiveActionButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightningNodeOnchainBalanceScreen(
    state: LightningNodeOnchainState,
    denomination: String,
    privacyMode: Boolean,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    historicalBtcPrices: Map<String, Double> = emptyMap(),
    showHistoricalTxPrices: Boolean = false,
    onShowHistoricalTxPricesChange: (Boolean) -> Unit = {},
    dateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    isNodeConnected: Boolean = false,
    isNodeConnecting: Boolean = false,
    connectionTarget: String? = null,
    onRefresh: () -> Unit,
    onTogglePrivacy: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
    onQuickReceive: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit = {},
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    onRefreshFees: () -> Unit = {},
    onBumpFee: (String, Double) -> Unit = { _, _ -> },
    canBumpFee: (txid: String, confirmations: Int) -> Boolean = { _, conf -> conf <= 0 },
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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

    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(state.isSyncing) {
        if (!state.isSyncing) isPullRefreshing = false
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }
    var showQrScanner by remember { mutableStateOf(false) }
    var selectedTx by remember { mutableStateOf<LightningNodeOnchainTransaction?>(null) }

    val filteredTransactions =
        remember(state.transactions, searchQuery.trim()) {
            val q = searchQuery.trim()
            val base =
                if (q.isBlank()) {
                    state.transactions
                } else {
                    state.transactions.filter { tx ->
                        tx.txid.contains(q, ignoreCase = true) ||
                            (tx.address?.contains(q, ignoreCase = true) == true)
                    }
                }
            // Pending first, then newest. Keeps unconfirmed channel/spend txs at the top.
            base.sortedWith(
                compareByDescending<LightningNodeOnchainTransaction> { it.confirmations <= 0 }
                    .thenByDescending { it.timestamp }
                    .thenByDescending { it.txid },
            )
        }
    val totalTransactionCount = filteredTransactions.size
    val visibleTransactions = remember(filteredTransactions, displayLimit) {
        filteredTransactions.take(displayLimit)
    }
    // Progressive reveal matches L1 / Liquid / Spark (25 → 100 → all loaded).
    LaunchedEffect(searchQuery, state.transactions.size) {
        displayLimit = 25
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = {
                onScanQrResult(it)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    selectedTx?.let { tx ->
        LnOnchainTransactionDetailDialog(
            transaction = tx,
            utxos = state.utxos,
            availableBalanceSats = state.balanceSats,
            useSats = useSats,
            privacyMode = privacyMode,
            btcPrice = btcPrice,
            // Details always show historical line when available (same as BalanceScreen).
            historicalBtcPrice = historicalBtcPrices[tx.txid],
            fiatCurrency = fiatCurrency,
            dateFormat = dateFormat,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            canBumpFee = canBumpFee(tx.txid, tx.confirmations),
            onRefreshFees = onRefreshFees,
            onBumpFee = onBumpFee,
            onDismiss = { selectedTx = null },
        )
    }

    // Always keep L1 balance chrome (card + history) for disconnected / on-chain
    // unavailable; inline banners carry connection/capability status (matches L2 LN).
    val nodeOffline = !isNodeConnected
    val onchainUnavailable =
        isNodeConnected && !state.isAvailable && !state.isSyncing && !isNodeConnecting

    val emptyHistoryMessage =
        when {
            !isNodeConnected && isNodeConnecting ->
                stringResource(R.string.ln_node_onchain_no_transactions)
            !isNodeConnected ->
                stringResource(R.string.ln_node_onchain_waiting_connection)
            state.isSyncing || !state.isAvailable ->
                stringResource(R.string.ln_node_onchain_loading_history)
            searchQuery.isNotBlank() ->
                stringResource(R.string.loc_a317d3d8)
            else ->
                stringResource(R.string.ln_node_onchain_no_transactions)
        }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            onRefresh()
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
                        val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                        translationY = progress * 40f
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val cardAccentColor = BitcoinOrange
                val balanceLines =
                    listOfNotNull(
                        state.reservedSats.takeIf { it > 0L },
                        state.immatureSats.takeIf { it > 0L },
                    ).size
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((140 + balanceLines * 16).dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .padding(bottom = 4.dp)
                                    .align(Alignment.TopCenter),
                        ) {
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
                                    imageVector =
                                        if (privacyMode) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                    contentDescription = stringResource(R.string.loc_990bb023),
                                    tint = if (privacyMode) BitcoinOrange else TextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            val isSyncing = state.isSyncing || isNodeConnecting
                            val syncEnabled =
                                (isNodeConnected || isNodeConnecting) && !state.isSyncing
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable(enabled = syncEnabled) { onRefresh() },
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
                                        contentDescription = stringResource(R.string.loc_8c195a44),
                                        tint =
                                            if (syncEnabled) {
                                                TextSecondary
                                            } else {
                                                TextSecondary.copy(alpha = 0.3f)
                                            },
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    if (privacyMode) {
                                        "****"
                                    } else if (useSats) {
                                        "${formatAmount(state.balanceSats.toULong(), true)} sats"
                                    } else {
                                        "\u20BF ${formatAmount(state.balanceSats.toULong(), false)}"
                                    },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier =
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onToggleDenomination,
                                    ),
                            )
                            if (btcPrice != null && btcPrice > 0) {
                                val usdValue = (state.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                                Text(
                                    text = if (privacyMode) "****" else formatFiat(usdValue, fiatCurrency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                )
                            }
                            if (state.reservedSats > 0L || state.immatureSats > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                if (state.reservedSats > 0L) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.ln_node_onchain_reserved_format,
                                                if (privacyMode) {
                                                    "****"
                                                } else {
                                                    formatAmount(
                                                        state.reservedSats.toULong(),
                                                        useSats,
                                                        includeUnit = true,
                                                    )
                                                },
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningYellow,
                                    )
                                }
                                if (state.immatureSats > 0L) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.ln_node_onchain_immature_format,
                                                if (privacyMode) {
                                                    "****"
                                                } else {
                                                    formatAmount(
                                                        state.immatureSats.toULong(),
                                                        useSats,
                                                        includeUnit = true,
                                                    )
                                                },
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = state.isAvailable) { onQuickReceive() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = stringResource(R.string.loc_a397da3c),
                                tint = cardAccentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable(enabled = state.isAvailable) { showQrScanner = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.loc_60129540),
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
                                    NfcReaderUiState.Detecting ->
                                        stringResource(R.string.nfc_status_detecting)
                                    NfcReaderUiState.Received ->
                                        stringResource(R.string.nfc_status_received)
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
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    nodeOffline && !isNodeConnecting -> {
                        OnchainConnectionBanner(
                            isConnecting = false,
                            detail =
                                state.error?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.ln_node_not_connected_hint),
                            onOpenConnectionSettings = onOpenConnectionSettings,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    onchainUnavailable -> {
                        OnchainStatusBanner(
                            message =
                                state.error
                                    ?: stringResource(R.string.ln_node_onchain_unavailable),
                            isConnecting = false,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    state.error != null && state.isAvailable -> {
                        OnchainAvailabilityCard(state.error)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
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
                            text = stringResource(R.string.loc_f61cc0f6),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (historicalBtcPrices.isNotEmpty()) {
                            LnOnchainHistoryFilterButton(
                                icon = Icons.Default.Schedule,
                                contentDescription =
                                    if (showHistoricalTxPrices) {
                                        stringResource(R.string.tx_history_show_current_prices)
                                    } else {
                                        stringResource(R.string.tx_history_show_historical_prices)
                                    },
                                tint = BitcoinOrange,
                                isSelected = showHistoricalTxPrices,
                                onClick = { onShowHistoricalTxPricesChange(!showHistoricalTxPrices) },
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) searchQuery = ""
                                },
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription =
                                if (isSearchActive) {
                                    stringResource(R.string.loc_dda0ea3a)
                                } else {
                                    stringResource(R.string.loc_b35cde91)
                                },
                            tint = if (isSearchActive) BitcoinOrange else TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(R.string.loc_0177e398),
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
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (visibleTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Text(
                            text = emptyHistoryMessage,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(visibleTransactions, key = { it.txid + it.timestamp }) { tx ->
                    LnOnchainTransactionRow(
                        transaction = tx,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        btcPrice = btcPrice,
                        historicalBtcPrice =
                            if (showHistoricalTxPrices) {
                                historicalBtcPrices[tx.txid]
                            } else {
                                null
                            },
                        fiatCurrency = fiatCurrency,
                        dateFormat = dateFormat,
                        onClick = { selectedTx = tx },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (visibleTransactions.size < totalTransactionCount) {
                    item {
                        val remaining = totalTransactionCount - visibleTransactions.size
                        TextButton(
                            onClick = {
                                displayLimit =
                                    if (displayLimit <= 25) {
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
                                        stringResource(R.string.loc_0ee47e3c)
                                    } else {
                                        stringResource(
                                            R.string.common_show_all_remaining_format,
                                            remaining,
                                        )
                                    },
                                color = TextSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LnOnchainTransactionRow(
    transaction: LightningNodeOnchainTransaction,
    useSats: Boolean,
    privacyMode: Boolean,
    btcPrice: Double?,
    historicalBtcPrice: Double? = null,
    fiatCurrency: String,
    dateFormat: String,
    onClick: () -> Unit,
) {
    val isReceived = transaction.amountSats >= 0
    val absSats = kotlin.math.abs(transaction.amountSats).toULong()
    val amountColor = if (isReceived) AccentGreen else AccentRed
    val formattedTimestamp =
        remember(transaction.timestamp, dateFormat) {
            if (transaction.timestamp > 0) {
                formatBalanceTimestamp(transaction.timestamp, dateFormat)
            } else {
                ""
            }
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(amountColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (isReceived) {
                            Icons.AutoMirrored.Filled.CallReceived
                        } else {
                            Icons.AutoMirrored.Filled.CallMade
                        },
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            if (isReceived) {
                                stringResource(R.string.loc_301a5b91)
                            } else {
                                stringResource(R.string.loc_1af68597)
                            },
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (transaction.isChannelOpen || transaction.isChannelClose) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge(
                            label = stringResource(R.string.ln_node_onchain_channel_badge),
                            color =
                                if (transaction.isChannelOpen) {
                                    LightningYellow
                                } else {
                                    AccentRed
                                },
                        )
                    }
                }
                if (formattedTimestamp.isNotBlank()) {
                    Text(
                        text = formattedTimestamp,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                        color = TextSecondary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val amountText =
                    if (privacyMode) {
                        "****"
                    } else {
                        val sign = if (isReceived) "+" else "-"
                        "$sign${formatAmount(absSats, useSats, includeUnit = true)}"
                    }
                Text(
                    text = amountText,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                        ),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    textAlign = TextAlign.End,
                )
                val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                if (effectiveBtcPrice != null && effectiveBtcPrice > 0 && !privacyMode) {
                    val usdValue = (absSats.toDouble() / 100_000_000.0) * effectiveBtcPrice
                    LnOnchainHistoricalFiatText(
                        text = formatFiat(usdValue, fiatCurrency),
                        isHistorical = historicalBtcPrice != null && historicalBtcPrice > 0,
                    )
                }
                if (transaction.confirmations <= 0) {
                    StatusBadge(
                        label = stringResource(R.string.loc_1b684325),
                        color = BitcoinOrange,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LnOnchainHistoryFilterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) tint.copy(alpha = 0.16f) else DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) tint else TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LnOnchainHistoricalFiatText(
    text: String,
    isHistorical: Boolean,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val style =
        if (large) {
            MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            )
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp)
        }
    val iconSize = if (large) 18.dp else 14.dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (isHistorical) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = BitcoinOrange,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = text,
            color = TextSecondary,
            style = style,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LnOnchainTransactionDetailDialog(
    transaction: LightningNodeOnchainTransaction,
    utxos: List<UtxoInfo>,
    availableBalanceSats: Long,
    useSats: Boolean,
    privacyMode: Boolean,
    btcPrice: Double?,
    historicalBtcPrice: Double? = null,
    fiatCurrency: String,
    dateFormat: String,
    mempoolUrl: String,
    mempoolServer: String,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    canBumpFee: Boolean = false,
    onRefreshFees: () -> Unit = {},
    onBumpFee: (String, Double) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dialogUriHandler = LocalUriHandler.current
    val isReceived = transaction.amountSats >= 0
    val absSats = kotlin.math.abs(transaction.amountSats).toULong()
    val amountColor = if (isReceived) AccentGreen else AccentRed
    val confirmed = transaction.confirmations > 0
    val statusColor = if (confirmed) AccentGreen else BitcoinOrange
    val statusLabel =
        if (confirmed) {
            stringResource(R.string.loc_4ab75d7f)
        } else {
            stringResource(R.string.loc_1b684325)
        }
    val confirmationProgressText =
        when {
            !confirmed -> "0/6"
            transaction.confirmations in 1..5 -> "${transaction.confirmations.coerceAtMost(6)}/6"
            else -> null
        }
    val formattedTimestamp =
        if (transaction.timestamp > 0) {
            formatFullTimestamp(transaction.timestamp, dateFormat)
        } else {
            ""
        }
    val denominationLabel =
        if (useSats) stringResource(R.string.loc_9384ed0d) else "BTC"
    val amountText =
        if (privacyMode) {
            "****"
        } else {
            val sign = if (isReceived) "+" else "-"
            "$sign${formatAmount(absSats, useSats)} $denominationLabel"
        }
    val canOpenExplorer =
        mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank()
    val parentUtxos =
        remember(utxos, transaction.txid) {
            utxos.filter { it.txid.equals(transaction.txid, ignoreCase = true) }
        }
    // Receive with owned UTXOs -> CPFP; send with change UTXO -> RBF path (LND only when allowed).
    val speedUpMethod =
        when {
            !canBumpFee || transaction.confirmations > 0 || parentUtxos.isEmpty() -> null
            isReceived -> SpeedUpMethod.CPFP
            else -> SpeedUpMethod.RBF
        }
    var showSpeedUpDialog by remember { mutableStateOf(false) }
    var showCopiedAmount by remember { mutableStateOf(false) }
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var showCopiedChangeAddress by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val currentFeeRate =
        remember(transaction.feeSats, transaction.vsize) {
            if (transaction.feeSats > 0 && transaction.vsize != null && transaction.vsize > 0) {
                transaction.feeSats / transaction.vsize
            } else {
                null
            }
        }
    val cpfpSpendable = parentUtxos.sumOf { it.amountSats.toLong() }.toULong()
    val feeBumpSubmittedMessage = stringResource(R.string.ln_node_onchain_bump_submitted)

    LaunchedEffect(showCopiedAmount) {
        if (showCopiedAmount) {
            kotlinx.coroutines.delay(3000)
            showCopiedAmount = false
        }
    }
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

    if (showSpeedUpDialog && speedUpMethod != null) {
        SpeedUpDialog(
            method = speedUpMethod,
            currentFee = transaction.feeSats.toULong().takeIf { it > 0u },
            currentFeeRate = currentFeeRate,
            vsize = transaction.vsize,
            availableBalance = availableBalanceSats.toULong(),
            cpfpSpendableOutput = cpfpSpendable,
            feeEstimationState = feeEstimationState,
            minFeeRate = minFeeRate,
            useSats = useSats,
            onRefreshFees = onRefreshFees,
            onConfirm = { feeRate, _ ->
                showSpeedUpDialog = false
                onBumpFee(transaction.txid, feeRate)
                Toast
                    .makeText(
                        context,
                        feeBumpSubmittedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
            },
            onDismiss = { showSpeedUpDialog = false },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_98c5fbdc),
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
                            contentDescription = stringResource(R.string.loc_d2c0aec0),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(amountColor.copy(alpha = 0.1f))
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector =
                                if (isReceived) {
                                    Icons.AutoMirrored.Filled.CallReceived
                                } else {
                                    Icons.AutoMirrored.Filled.CallMade
                                },
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (isReceived) {
                                    stringResource(R.string.loc_301a5b91)
                                } else {
                                    stringResource(R.string.loc_1af68597)
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color = amountColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = amountColor,
                            modifier =
                                if (privacyMode) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        SecureClipboard.copyAndScheduleClear(context, amountText)
                                        showCopiedAmount = true
                                    }
                                },
                        )
                        if (showCopiedAmount) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange,
                            )
                        }
                        if (!privacyMode) {
                            if (btcPrice != null && btcPrice > 0) {
                                LnOnchainHistoricalFiatText(
                                    text =
                                        formatFiat(
                                            (absSats.toDouble() / 100_000_000.0) * btcPrice,
                                            fiatCurrency,
                                        ),
                                    isHistorical = false,
                                    large = true,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (historicalBtcPrice != null && historicalBtcPrice > 0) {
                                LnOnchainHistoricalFiatText(
                                    text =
                                        formatFiat(
                                            (absSats.toDouble() / 100_000_000.0) * historicalBtcPrice,
                                            fiatCurrency,
                                        ),
                                    isHistorical = true,
                                    large = true,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                        if (transaction.isChannelOpen || transaction.isChannelClose) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusBadge(
                                label =
                                    stringResource(
                                        if (transaction.isChannelOpen) {
                                            R.string.ln_node_onchain_channel_open_badge
                                        } else {
                                            R.string.ln_node_onchain_channel_close_badge
                                        },
                                    ),
                                color =
                                    if (transaction.isChannelOpen) {
                                        LightningYellow
                                    } else {
                                        AccentRed
                                    },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (speedUpMethod != null) {
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
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                when (speedUpMethod) {
                                    SpeedUpMethod.RBF -> stringResource(R.string.loc_3dae8142)
                                    SpeedUpMethod.CPFP -> stringResource(R.string.loc_51321a45)
                                    SpeedUpMethod.REDIRECT -> stringResource(R.string.loc_3dae8142)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_7cac602a),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                if (canOpenExplorer) {
                                    LnOnchainExplorerBadge(
                                        tint = BitcoinOrange,
                                        onClick = {
                                            runCatching {
                                                dialogUriHandler.openUri(
                                                    "$mempoolUrl/tx/${transaction.txid}",
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector =
                                            if (confirmed) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Schedule
                                            },
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = statusColor,
                                    )
                                    confirmationProgressText?.let { progress ->
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = progress,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = statusColor,
                                        )
                                    }
                                }
                                if (formattedTimestamp.isNotBlank()) {
                                    Text(
                                        text = formattedTimestamp,
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

                        Column {
                            Text(
                                text = stringResource(R.string.loc_13e398d0),
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
                                LnOnchainCopyChip(
                                    copied = showCopiedTxid,
                                    contentDescription = stringResource(R.string.loc_ed8814bc),
                                    onClick = {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            transaction.txid,
                                        )
                                        showCopiedTxid = true
                                    },
                                )
                            }
                            if (showCopiedTxid) {
                                Text(
                                    text = stringResource(R.string.loc_e287255d),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }

                        transaction.address?.takeIf { it.isNotBlank() }?.let { address ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text(
                                    text =
                                        if (isReceived) {
                                            stringResource(R.string.loc_b47edf23)
                                        } else {
                                            stringResource(R.string.loc_eaf579ea)
                                        },
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
                                            text = truncateLnOnchainDetailValue(address),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        transaction.addressAmountSats?.let { addressAmount ->
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text =
                                                    if (privacyMode) {
                                                        "****"
                                                    } else {
                                                        "${if (isReceived) "+" else "-"}${formatAmount(
                                                            addressAmount.toULong(),
                                                            useSats,
                                                        )} $denominationLabel"
                                                    },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isReceived) AccentGreen else AccentRed,
                                            )
                                        }
                                    }
                                    LnOnchainCopyChip(
                                        copied = showCopiedAddress,
                                        contentDescription = stringResource(R.string.loc_f3a4dab2),
                                        onClick = {
                                            SecureClipboard.copyAndScheduleClear(context, address)
                                            showCopiedAddress = true
                                        },
                                    )
                                }
                                if (showCopiedAddress) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BitcoinOrange,
                                    )
                                }
                            }
                        }

                        transaction.changeAddress
                            ?.takeIf { !isReceived && it != transaction.address }
                            ?.let { changeAddress ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.loc_a28f34b2),
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
                                                text = truncateLnOnchainDetailValue(changeAddress),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            transaction.changeAmountSats?.let { changeAmount ->
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text =
                                                        if (privacyMode) {
                                                            "****"
                                                        } else {
                                                            "+${formatAmount(changeAmount.toULong(), useSats)} $denominationLabel"
                                                        },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = AccentGreen,
                                                )
                                            }
                                        }
                                        LnOnchainCopyChip(
                                            copied = showCopiedChangeAddress,
                                            contentDescription = stringResource(R.string.loc_90d5f46b),
                                            onClick = {
                                                SecureClipboard.copyAndScheduleClear(context, changeAddress)
                                                showCopiedChangeAddress = true
                                            },
                                        )
                                    }
                                    if (showCopiedChangeAddress) {
                                        Text(
                                            text = stringResource(R.string.loc_e287255d),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }
                            }

if (!isReceived && transaction.feeSats > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.loc_f72cc482),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (currentFeeRate != null && transaction.vsize != null) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.liquid_fee_rate_vbytes_format,
                                                formatFeeRate(currentFeeRate),
                                                formatVBytes(transaction.vsize),
                                            ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }
                                Text(
                                    text =
                                        if (privacyMode) {
                                            "****"
                                        } else {
                                            "-${formatAmount(transaction.feeSats.toULong(), useSats)} $denominationLabel"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BitcoinOrange,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))
                IbisButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.loc_d2c0aec0),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LnOnchainExplorerBadge(
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
            text = stringResource(R.string.loc_1e89ceb8),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.loc_f07d5799),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun LnOnchainCopyChip(
    copied: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
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
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = contentDescription,
            tint = if (copied) BitcoinOrange else TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun truncateLnOnchainDetailValue(value: String): String {
    if (value.length <= 27) return value
    return "${value.take(16)}...${value.takeLast(8)}"
}

@Composable
fun LightningNodeOnchainReceiveScreen(
    state: LightningNodeOnchainState,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    isNodeConnected: Boolean = false,
    isNodeConnecting: Boolean = false,
    connectionTarget: String? = null,
    onGenerateAddress: () -> Unit = {},
    onShowAllAddresses: () -> Unit = {},
    onShowAllUtxos: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val copyReceiveToast = stringResource(R.string.loc_b6b10bfe)
    val receiveShareRequestTitle = stringResource(R.string.receive_share_request_title)
    val receiveNoShareAppMessage = stringResource(R.string.receive_no_share_app)
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val address = state.currentAddress

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showAmountField by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    val amountBringIntoViewRequester =
        rememberBringIntoViewRequesterOnExpand(showAmountField, "ln_onchain_receive_amount")

    val amountInSats =
        remember(amountText, useSats, isUsdMode, btcPrice) {
            if (amountText.isEmpty()) {
                null
            } else if (isUsdMode && btcPrice != null && btcPrice > 0) {
                amountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).roundToLong()
                }
            } else if (useSats) {
                amountText.toLongOrNull()
            } else {
                amountText.toDoubleOrNull()?.let { btc ->
                    (btc * 100_000_000).roundToLong()
                }
            }
        }

    val qrContent =
        remember(address, amountInSats, showAmountField) {
            address?.let { addr ->
                val bitcoinAmountSats = amountInSats?.takeIf { showAmountField && it > 0 }
                if (bitcoinAmountSats != null) {
                    val btcAmount = bitcoinAmountSats.toDouble() / 100_000_000.0
                    "bitcoin:$addr?amount=${String.format(Locale.US, "%.8f", btcAmount)}"
                } else {
                    addr
                }
            }
        }

    val copyReceiveRequest: () -> Unit = {
        qrContent?.let { content ->
            SecureClipboard.copyAndScheduleClear(context, content)
            Toast.makeText(context, copyReceiveToast, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(qrContent) {
        qrContent?.let { content ->
            qrBitmap =
                withContext(Dispatchers.Default) {
                    generateQrBitmap(content)
                }
        } ?: run { qrBitmap = null }
    }

    LaunchedEffect(state.isAvailable, address) {
        if (state.isAvailable && address == null && !state.isSyncing) {
            onGenerateAddress()
        }
    }

    val mainActivity = context as? MainActivity
    val nfcShareOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canBroadcast
    val hasNfcSharePayload = nfcAvailable && qrContent != null
    val nfcShareState by NfcRuntimeStatus.shareState.collectAsState()
    DisposableEffect(mainActivity, hasNfcSharePayload) {
        if (mainActivity != null && hasNfcSharePayload) {
            mainActivity.requestPreferredHceService(nfcShareOwner)
        }
        onDispose {
            mainActivity?.releasePreferredHceService(nfcShareOwner)
        }
    }
    val isNfcBroadcasting = hasNfcSharePayload && mainActivity?.isPreferredHceServiceActive == true
    DisposableEffect(qrContent, nfcAvailable) {
        if (nfcAvailable && qrContent != null) {
            NdefHostApduService.setNdefPayload(qrContent)
        }
        onDispose {
            NdefHostApduService.setNdefPayload(null)
        }
    }

    if (showEnlargedQr && qrBitmap != null) {
        Dialog(onDismissRequest = { showEnlargedQr = false }) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { showEnlargedQr = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(16.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_ef73e5ab),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.loc_e1041b50),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Connection status is shown in the top-right pill; only surface offline/unavailable.
        val statusMessage =
            when {
                isNodeConnecting -> null
                !isNodeConnected ->
                    stringResource(R.string.ln_node_onchain_waiting_connection)
                !state.isAvailable ->
                    state.error ?: stringResource(R.string.ln_node_onchain_unavailable)
                else -> state.error?.takeIf { it.isNotBlank() }
            }
        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OnchainStatusBanner(
                message = statusMessage,
                isConnecting = false,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = stringResource(R.string.loc_4126d5db),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isNfcBroadcasting) {
                    val nfcStatusLabel =
                        when (nfcShareState) {
                            NfcShareUiState.Inactive,
                            NfcShareUiState.Ready,
                            -> stringResource(R.string.nfc_status_share_ready)
                            NfcShareUiState.Sharing -> stringResource(R.string.nfc_status_sharing)
                        }
                    val nfcStatusColor =
                        if (nfcShareState == NfcShareUiState.Sharing) {
                            BitcoinOrange
                        } else {
                            SuccessGreen
                        }
                    NfcStatusIndicator(
                        label = nfcStatusLabel,
                        contentDescription = nfcStatusLabel,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        color = nfcStatusColor,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .combinedClickable(
                                enabled = address != null,
                                onClick = { showEnlargedQr = true },
                                onLongClick = copyReceiveRequest,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null && address != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_416323aa),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text =
                                if (state.isSyncing) {
                                    stringResource(R.string.loc_da2e3020)
                                } else {
                                    stringResource(R.string.loc_fb85740c)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = formatOnchainReceiveAddress(address),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color =
                        if (address != null) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            TextSecondary
                        },
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = qrContent != null, onClick = copyReceiveRequest)
                            .padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_ed8814bc),
                        icon = Icons.Default.ContentCopy,
                        tint = BitcoinOrange,
                        onClick = copyReceiveRequest,
                        enabled = address != null,
                        iconSize = 17.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_53ae02a5),
                        icon = Icons.Default.Refresh,
                        tint = BitcoinOrange,
                        onClick = onGenerateAddress,
                        enabled = state.isAvailable && !state.isSyncing,
                        iconSize = 20.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_2ec7b25e),
                        icon = Icons.Default.Share,
                        tint = BitcoinOrange,
                        onClick = {
                            qrContent?.let { content ->
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, content)
                                    }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            receiveShareRequestTitle,
                                        ),
                                    )
                                }.onFailure {
                                    Toast
                                        .makeText(
                                            context,
                                            receiveNoShareAppMessage,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        },
                        enabled = qrContent != null,
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAmountField = !showAmountField }
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_890d7574),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        SquareToggle(
                            checked = showAmountField,
                            onCheckedChange = { showAmountField = it },
                        )
                    }

                    AnimatedVisibility(visible = showAmountField) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(amountBringIntoViewRequester),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AmountLabel(
                                    useSats = useSats,
                                    isUsdMode = isUsdMode,
                                    fiatCurrency = fiatCurrency,
                                    onToggleDenomination = onToggleDenomination,
                                )
                                if (btcPrice != null && btcPrice > 0) {
                                    Card(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    isUsdMode = !isUsdMode
                                                    amountText = ""
                                                },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    if (isUsdMode) {
                                                        BitcoinOrange.copy(alpha = 0.15f)
                                                    } else {
                                                        DarkSurface
                                                    },
                                            ),
                                        border =
                                            BorderStroke(
                                                1.dp,
                                                if (isUsdMode) BitcoinOrange else BorderColor,
                                            ),
                                    ) {
                                        Text(
                                            text = fiatCurrency,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isUsdMode) BitcoinOrange else TextSecondary,
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 6.dp,
                                                ),
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    when {
                                        isUsdMode -> {
                                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                amountText = input
                                            }
                                        }
                                        useSats -> {
                                            amountText = input.filter { c -> c.isDigit() }
                                        }
                                        else -> {
                                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,8}$"))) {
                                                amountText = input
                                            }
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        when {
                                            isUsdMode -> "0.00"
                                            useSats -> "0"
                                            else -> "0.00000000"
                                        },
                                        color = TextSecondary.copy(alpha = 0.5f),
                                    )
                                },
                                leadingIcon =
                                    if (isUsdMode) {
                                        { Text(fiatCurrency, color = TextSecondary) }
                                    } else {
                                        null
                                    },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType =
                                            if (useSats && !isUsdMode) {
                                                KeyboardType.Number
                                            } else {
                                                KeyboardType.Decimal
                                            },
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = BitcoinOrange,
                                    ),
                                enabled = address != null,
                            )

                            if (
                                amountText.isNotEmpty() &&
                                amountInSats != null &&
                                amountInSats > 0 &&
                                btcPrice != null &&
                                btcPrice > 0
                            ) {
                                val conversionText =
                                    if (privacyMode) {
                                        "≈ ****"
                                    } else if (isUsdMode) {
                                        "≈ ${formatAmount(amountInSats.toULong(), useSats, includeUnit = true)}"
                                    } else {
                                        val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                                        "≈ ${formatFiat(usdValue, fiatCurrency)}"
                                    }
                                Text(
                                    text = conversionText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IbisButton(
                onClick = onShowAllAddresses,
                modifier = Modifier.weight(1f),
                enabled = state.isAvailable,
            ) {
                Text(
                    text = stringResource(R.string.loc_5c96cb11),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            IbisButton(
                onClick = onShowAllUtxos,
                modifier = Modifier.weight(1f),
                enabled = state.isAvailable,
            ) {
                Text(
                    text = stringResource(R.string.loc_8bc041b3),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun formatOnchainReceiveAddress(address: String?): String {
    if (address == null) return "No wallet"
    val chunks = address.chunked(7)
    val numLines = if (address.length > 50) 3 else 2
    val perLine = (chunks.size + numLines - 1) / numLines
    return chunks
        .chunked(perLine)
        .joinToString("\n") { it.joinToString(" ") }
}

@Composable
fun LightningNodeOnchainSendScreen(
    state: LightningNodeOnchainState,
    sendState: LightningNodeOnchainSendState = LightningNodeOnchainSendState.Idle,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    preSelectedUtxo: UtxoInfo? = null,
    spendUnconfirmed: Boolean = true,
    isNodeConnected: Boolean = false,
    isNodeConnecting: Boolean = false,
    connectionTarget: String? = null,
    onRefreshFees: () -> Unit = {},
    onClearPreSelectedUtxo: () -> Unit = {},
    onSend: (
        address: String,
        amountSats: Long?,
        satPerVbyte: Double?,
        sendAll: Boolean,
        label: String?,
        selectedOutpoints: List<UtxoInfo>?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onSendMany: (
        addrToAmountSats: Map<String, Long>,
        satPerVbyte: Double?,
        label: String?,
        selectedOutpoints: List<UtxoInfo>?,
    ) -> Unit = { _, _, _, _ -> },
    onResetSend: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val isSending = sendState is LightningNodeOnchainSendState.Sending
    val sendError = (sendState as? LightningNodeOnchainSendState.Error)?.message
    val sendSuccessTxid = (sendState as? LightningNodeOnchainSendState.Success)?.txid
    // Stay interactive during background history refresh — isSyncing only loads,
        // it is not a reconnect. Gating would flash "Connecting…" and disable Send.
        val gatewayReady = state.isAvailable && isNodeConnected && !isNodeConnecting

    var recipientAddress by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    // Honour app min (may be sub-sat). Do not clamp to 1.0 — that hid the fee widget/
    // blocked decimal rates when minFeeRate is e.g. 0.5.
    var feeRate by remember { mutableDoubleStateOf(minFeeRate.coerceAtLeast(0.0)) }
    var isUsdMode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showCoinControl by remember { mutableStateOf(false) }
    var isMaxMode by remember { mutableStateOf(false) }
    var showLabelField by remember { mutableStateOf(false) }
    var labelText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isMultiMode by remember { mutableStateOf(false) }
    var showMultiDialog by remember { mutableStateOf(false) }
    val multiRecipients = remember { mutableStateListOf<Pair<String, String>>() }
    val selectedUtxos = remember { mutableStateListOf<UtxoInfo>() }
    val spendableUtxos =
        remember(state.utxos, spendUnconfirmed) {
            state.utxos.filter { !it.isFrozen && (spendUnconfirmed || it.isConfirmed) }
        }

    fun amountInputToSats(input: String): Long {
        return try {
            when {
                isUsdMode && btcPrice != null && btcPrice > 0 -> {
                    val usdAmount = input.toDoubleOrNull() ?: 0.0
                    ((usdAmount / btcPrice) * 100_000_000).roundToLong()
                }
                useSats -> input.replace(",", "").toLongOrNull() ?: 0L
                else -> ((input.toDoubleOrNull() ?: 0.0) * 100_000_000).roundToLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    val multiRecipientList =
        remember(multiRecipients.toList(), isUsdMode, btcPrice, useSats) {
            multiRecipients.mapNotNull { (addr, amt) ->
                val amount = amountInputToSats(amt)
                val err = validateBitcoinAddress(addr)
                if (addr.isNotBlank() && err == null && amount > 0) {
                    Recipient(address = addr.trim(), amountSats = amount.toULong())
                } else {
                    null
                }
            }
        }
    val multiTotalSats = remember(multiRecipientList) { multiRecipientList.sumOf { it.amountSats.toLong() } }

    LaunchedEffect(preSelectedUtxo) {
        if (preSelectedUtxo != null) {
            selectedUtxos.clear()
            selectedUtxos.add(preSelectedUtxo)
            onClearPreSelectedUtxo()
        }
    }

    LaunchedEffect(spendableUtxos) {
        reconcileCoinControlSelection(selectedUtxos, spendableUtxos)
    }

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

    val addressValidationError = remember(recipientAddress) { validateBitcoinAddress(recipientAddress) }
    val isAddressValid = recipientAddress.isNotBlank() && addressValidationError == null

    val amountSats =
        remember(amountInput, isUsdMode, btcPrice, useSats) {
            amountInputToSats(amountInput).toDouble()
        }
    val sendSuccessMessage =
        sendSuccessTxid?.let { stringResource(R.string.ln_node_onchain_sent_format, it.take(16)) }

    // Available to spend: coin-control selection, else node spendable.
    // Prefer balanceSats (pending outs deducted) over raw UTXO sum — ListUnspent can
    // still list coins that SendCoins/SendOutputs will not use yet.
    val selectedUtxoSnapshot = selectedUtxos.toList()
    val availableSats =
        remember(selectedUtxoSnapshot, spendableUtxos, state.balanceSats) {
            if (selectedUtxoSnapshot.isNotEmpty()) {
                selectedUtxoSnapshot.sumOf { it.amountSats.toLong() }
            } else {
                val utxoSum = spendableUtxos.sumOf { it.amountSats.toLong() }
                when {
                    utxoSum <= 0L -> state.balanceSats
                    state.balanceSats <= 0L -> utxoSum
                    else -> minOf(utxoSum, state.balanceSats)
                }
            }
        }
    val feeInputUtxos =
        remember(selectedUtxoSnapshot, spendableUtxos) {
            selectedUtxoSnapshot.ifEmpty { spendableUtxos }
        }
    val feeInputAddresses = remember(feeInputUtxos) { feeInputUtxos.map { it.address } }

    // Single fee/vsize for Max + review: sweep has no change; otherwise one change out.
    val maxSweepFeeSats =
        remember(feeRate, feeInputAddresses, recipientAddress, isMultiMode, multiRecipientList) {
            if (feeRate <= 0.0) {
                0L
            } else {
                val outs =
                    if (isMultiMode) {
                        multiRecipientList.map { it.address }.ifEmpty { listOf(recipientAddress) }
                    } else {
                        listOf(recipientAddress)
                    }
                BitcoinUtils.estimateOnchainSendFeeSats(
                    satPerVb = feeRate,
                    inputAddresses = feeInputAddresses,
                    outputAddresses = outs,
                    includeChange = false,
                )
            }
        }
    val maxRecipientSats =
        remember(availableSats, maxSweepFeeSats) {
            maxOf(0L, availableSats - maxSweepFeeSats)
        }

    val estimatedVBytes =
        remember(
            feeInputAddresses,
            isMultiMode,
            multiRecipientList,
            multiTotalSats,
            recipientAddress,
            isMaxMode,
            amountSats,
            availableSats,
            feeRate,
            maxSweepFeeSats,
        ) {
            val outputs =
                if (isMultiMode) {
                    multiRecipientList.map { it.address }.ifEmpty { listOf(recipientAddress) }
                } else {
                    listOf(recipientAddress)
                }
            val recipientTotal =
                if (isMultiMode) multiTotalSats else amountSats.roundToLong().coerceAtLeast(0L)
            // Sweep (max or amount+fee covers all inputs) has no change output.
            val looksLikeSweep =
                isMaxMode ||
                    (
                        !isMultiMode &&
                            amountSats > 0 &&
                            availableSats > 0 &&
                            recipientTotal + maxSweepFeeSats >= availableSats
                    )
            BitcoinUtils.estimateOnchainSendVsize(
                inputAddresses = feeInputAddresses,
                outputAddresses = outputs,
                includeChange = !looksLikeSweep && !isMaxMode,
            )
        }
    val roughFeeSats =
        remember(feeRate, estimatedVBytes, isMaxMode, maxSweepFeeSats) {
            if (feeRate <= 0.0) {
                0L
            } else if (isMaxMode) {
                // Keep max amount + fee tied to the same sweep estimate.
                maxSweepFeeSats
            } else {
                BitcoinUtils.computeExactFeeSats(feeRate, estimatedVBytes)?.toLong()
                    ?: ceil(feeRate * estimatedVBytes).toLong().coerceAtLeast(0L)
            }
        }
    // Max send display: pin recipient so amount + fee never exceeds available.
    val displayAmountSats =
        if (isMaxMode && !isMultiMode) {
            maxRecipientSats
        } else {
            amountSats.roundToLong().coerceAtLeast(0L)
        }
    val remainingAfterSend =
        if (isMultiMode) {
            maxOf(0L, availableSats - multiTotalSats - roughFeeSats)
        } else if (isMaxMode) {
            0L
        } else if (displayAmountSats > 0) {
            maxOf(0L, availableSats - displayAmountSats - roughFeeSats)
        } else {
            availableSats
        }

    LaunchedEffect(
        isMaxMode,
        maxRecipientSats,
        useSats,
        isMultiMode,
    ) {
        if (isMaxMode && !isMultiMode) {
            amountInput =
                if (useSats) {
                    maxRecipientSats.toString()
                } else {
                    formatBtc(maxRecipientSats.toULong())
                }
        }
    }

    LaunchedEffect(sendSuccessTxid, sendSuccessMessage) {
        if (sendSuccessTxid != null && sendSuccessMessage != null) {
            showConfirmDialog = false
            showMultiDialog = false
            recipientAddress = ""
            amountInput = ""
            isMaxMode = false
            isUsdMode = false
            isMultiMode = false
            multiRecipients.clear()
            showLabelField = false
            labelText = ""
            selectedUtxos.clear()
            feeRate = minFeeRate.coerceAtLeast(0.0)
            Toast
                .makeText(
                        context,
                        sendSuccessMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            onResetSend()
        }
    }

    fun applyBip21(code: String) {
        val bip21 =
            try {
                parseBip21Uri(code.trim())
            } catch (_: IllegalArgumentException) {
                recipientAddress = code.trim()
                return
            }
        recipientAddress = bip21.address
        bip21.amount?.let { btcAmount ->
            isMaxMode = false
            amountInput =
                when {
                    isUsdMode && btcPrice != null && btcPrice > 0 ->
                        String.format(Locale.US, "%.2f", btcAmount * btcPrice)
                    useSats -> (btcAmount * 100_000_000).roundToLong().toString()
                    else ->
                        String.format(Locale.US, "%.8f", btcAmount).trimEnd('0').trimEnd('.')
                }
        }
        bip21.label?.let {
            labelText = it
            showLabelField = true
        }
    }

    if (showCoinControl) {
        CoinControlDialog(
            utxos = spendableUtxos,
            selectedUtxos = selectedUtxos,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            spendUnconfirmed = spendUnconfirmed,
            onUtxoToggle = { utxo -> toggleCoinControlSelection(selectedUtxos, utxo) },
            onSelectAll = {
                selectedUtxos.clear()
                selectedUtxos.addAll(spendableUtxos)
            },
            onClearAll = { selectedUtxos.clear() },
            onDismiss = { showCoinControl = false },
        )
    }

    if (showMultiDialog) {
        MultiRecipientDialog(
            recipients = multiRecipients,
            useSats = useSats,
            isUsdMode = isUsdMode,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            availableSats = availableSats,
            estimatedFeeSats = roughFeeSats,
            dryRunError = null,
            validRecipientCount = multiRecipientList.size,
            totalSendingSats = multiTotalSats,
            onDone = { showMultiDialog = false },
            onDismiss = { showMultiDialog = false },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = {
                applyBip21(it)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    if (showConfirmDialog) {
        val txLabel = labelText.trim().takeIf { showLabelField && it.isNotBlank() }
        val amountForSend = displayAmountSats
        // Max review total is always the spendable pool (fee carved out of amount).
        val reviewFeeSats =
            if (isMaxMode && !isMultiMode) {
                maxOf(0L, availableSats - amountForSend)
            } else {
                roughFeeSats
            }
        LnOnchainSendConfirmationDialog(
            recipients = if (isMultiMode) multiRecipientList else listOf(Recipient(recipientAddress, amountForSend.toULong())),
            isMaxMode = isMaxMode,
            recipientTotalSats = if (isMultiMode) multiTotalSats else amountForSend,
            estimatedFeeSats = reviewFeeSats,
            changeSats = remainingAfterSend.takeIf { !isMaxMode && it > 0L },
            feeRate = feeRate,
            label = txLabel,
            selectedUtxos = selectedUtxos.toList(),
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            isSending = isSending,
            error = sendError,
            onConfirm = {
                val fee = feeRate.takeIf { it > 0.0 }
                val outs = selectedUtxos.toList().ifEmpty { null }
                if (isMultiMode) {
                    onSendMany(multiRecipientList.associate { it.address to it.amountSats.toLong() }, fee, txLabel, outs)
                } else {
                    // Max: amount omitted — node send_all / fractional recreate from spendable.
                    onSend(
                        recipientAddress.trim(),
                        if (isMaxMode) null else amountForSend,
                        fee,
                        isMaxMode,
                        txLabel,
                        outs,
                    )
                }
            },
            onDismiss = { if (!isSending) showConfirmDialog = false },
        )
    }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Connection status is shown in the top-right pill; only surface offline/unavailable.
        val statusMessage =
            when {
                isNodeConnecting -> null
                !isNodeConnected ->
                    stringResource(R.string.ln_node_onchain_waiting_connection)
                !state.isAvailable ->
                    state.error ?: stringResource(R.string.ln_node_onchain_unavailable)
                else -> state.error?.takeIf { it.isNotBlank() }
            }
        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OnchainStatusBanner(
                message = statusMessage,
                isConnecting = false,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.loc_a274c658),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (isNfcReaderActive) {
                            val nfcStatusLabel =
                                when (nfcReaderState) {
                                    NfcReaderUiState.Inactive,
                                    NfcReaderUiState.Ready,
                                    -> stringResource(R.string.nfc_status_receive_ready)
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
                                modifier = Modifier.padding(top = 2.dp),
                                color = nfcStatusColor,
                            )
                        }
                    }

                    val hasUtxos = spendableUtxos.isNotEmpty()
                    val isActive = selectedUtxos.isNotEmpty()
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = gatewayReady && hasUtxos) {
                                    showCoinControl = true
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isActive) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface,
                            ),
                        border = BorderStroke(1.dp, if (isActive) BitcoinOrange else BorderColor),
                    ) {
                        Text(
                            text =
                                if (isActive) {
                                    "${stringResource(R.string.send_utxos_selected_prefix)} (${selectedUtxos.size})"
                                } else {
                          stringResource(R.string.loc_002b1ce2)
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                when {
                                    isActive -> BitcoinOrange
                                    hasUtxos -> TextSecondary
                                    else -> TextSecondary.copy(alpha = 0.5f)
                                },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_eaf579ea),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = gatewayReady && !isSending) {
                                    if (!isMultiMode) {
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
                                        isMultiMode = false
                                        multiRecipients.clear()
                                    }
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isMultiMode) BitcoinOrange.copy(alpha = 0.15f) else DarkSurface,
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                if (isMultiMode) BitcoinOrange else BorderColor,
                            ),
                    ) {
                        Text(
                            text =
                                if (isMultiMode) {
                                    "${stringResource(R.string.loc_fcc11f52)} (${multiRecipientList.size})"
                                } else {
                                    stringResource(R.string.loc_fcc11f52)
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMultiMode) BitcoinOrange else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isMultiMode) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMultiDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                        ) {
                            if (multiRecipientList.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.loc_d98e9517),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            } else {
                                multiRecipientList.forEachIndexed { i, r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = r.address.take(10) + "..." + r.address.takeLast(6),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text =
                                                if (privacyMode) {
                                                    "****"
                                                } else {
                                                    formatAmount(r.amountSats, useSats, includeUnit = true)
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                    if (i < multiRecipientList.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.send_total_recipients_format,
                                                multiRecipientList.size,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                    )
                                    Text(
                                        text =
                                            if (privacyMode) {
                                                "****"
                                            } else {
                                                formatAmount(
                                                    multiTotalSats.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                )
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AvailableBalanceMaxRow(
                        amountText =
                            if (privacyMode) {
                                "****"
                            } else {
                                formatAmount(remainingAfterSend.toULong(), useSats, includeUnit = true)
                            },
                        fiatText =
                            if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                                val usdValue = (remainingAfterSend.toDouble() / 100_000_000.0) * btcPrice
                                " · ${formatFiat(usdValue, fiatCurrency)}"
                            } else {
                                null
                            },
                        accentColor = BitcoinOrange,
                        isMaxMode = false,
                        maxEnabled = false,
                        onMaxClick = {},
                    )
                } else {
                OutlinedTextField(
                    value = recipientAddress,
                    onValueChange = { input ->
                        if (input.contains("bitcoin:", ignoreCase = true) || input.contains("?")) {
                            applyBip21(input)
                        } else {
                            recipientAddress = input
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_a18fd453),
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showQrScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.loc_59b2cdc5),
                                tint = BitcoinOrange,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    supportingText =
                        if (addressValidationError != null && recipientAddress.isNotBlank()) {
                            { Text(text = addressValidationError, color = AccentRed) }
                        } else {
                            null
                        },
                    isError = addressValidationError != null && recipientAddress.isNotBlank(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor =
                                if (addressValidationError != null && recipientAddress.isNotBlank()) {
                                    AccentRed
                                } else {
                                    BitcoinOrange
                                },
                            unfocusedBorderColor =
                                if (addressValidationError != null && recipientAddress.isNotBlank()) {
                                    AccentRed
                                } else {
                                    BorderColor
                                },
                            errorBorderColor = AccentRed,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                    enabled = gatewayReady && !isSending,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmountLabel(
                        useSats = useSats,
                        isUsdMode = isUsdMode,
                        fiatCurrency = fiatCurrency,
                        onToggleDenomination = onToggleDenomination,
                    )
                    if (btcPrice != null && btcPrice > 0) {
                        Card(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (amountInput.isNotEmpty()) {
                                            val currentSats =
                                                when {
                                                    isUsdMode -> {
                                                        val usd = amountInput.toDoubleOrNull() ?: 0.0
                                                        (usd / btcPrice * 100_000_000).roundToLong()
                                                    }
                                                    useSats ->
                                                        amountInput.replace(",", "").toLongOrNull() ?: 0L
                                                    else -> {
                                                        val btc = amountInput.toDoubleOrNull() ?: 0.0
                                                        (btc * 100_000_000).roundToLong()
                                                    }
                                                }
                                            amountInput =
                                                if (!isUsdMode) {
                                                    String.format(
                                                        Locale.US,
                                                        "%.2f",
                                                        (currentSats / 100_000_000.0) * btcPrice,
                                                    )
                                                } else if (useSats) {
                                                    currentSats.toString()
                                                } else {
                                                    String.format(Locale.US, "%.8f", currentSats / 100_000_000.0)
                                                }
                                        }
                                        isUsdMode = !isUsdMode
                                        isMaxMode = false
                                    },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isUsdMode) {
                                            BitcoinOrange.copy(alpha = 0.15f)
                                        } else {
                                            DarkSurface
                                        },
                                ),
                            border = BorderStroke(1.dp, if (isUsdMode) BitcoinOrange else BorderColor),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) BitcoinOrange else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val conversionText =
                    if (amountInput.isNotEmpty() && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                        if (privacyMode) {
                            "≈ ****"
                        } else if (isUsdMode) {
                            "≈ ${formatAmount(amountSats.roundToLong().toULong(), useSats, includeUnit = true)}"
                        } else {
                            val usdValue = (amountSats / 100_000_000.0) * btcPrice
                            "≈ ${formatFiat(usdValue, fiatCurrency)}"
                        }
                    } else {
                        null
                    }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { value ->
                        isMaxMode = false
                        when {
                            isUsdMode -> {
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
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon =
                        if (isUsdMode) {
                            { Text(fiatCurrency, color = TextSecondary) }
                        } else {
                            null
                        },
                    suffix =
                        if (conversionText != null) {
                            {
                                Text(
                                    text = conversionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = BitcoinOrange,
                                )
                            }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isMaxMode) BitcoinOrange else BorderColor,
                            unfocusedBorderColor = if (isMaxMode) BitcoinOrange else BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                    enabled = gatewayReady && !isSending,
                )

                Spacer(modifier = Modifier.height(6.dp))

                AvailableBalanceMaxRow(
                    amountText =
                        if (privacyMode) {
                            "****"
                        } else {
                            formatAmount(remainingAfterSend.toULong(), useSats, includeUnit = true)
                        },
                    fiatText =
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (remainingAfterSend.toDouble() / 100_000_000.0) * btcPrice
                            " · ${formatFiat(usdValue, fiatCurrency)}"
                        } else {
                            null
                        },
                    accentColor = BitcoinOrange,
                    isMaxMode = isMaxMode,
                    maxEnabled = gatewayReady && availableSats > 0 && !isSending,
                    onMaxClick = {
                        if (isMaxMode) {
                            isMaxMode = false
                            amountInput = ""
                        } else {
                            isMaxMode = true
                            isUsdMode = false
                            amountInput =
                                if (useSats) {
                                    maxRecipientSats.toString()
                                } else {
                                    formatBtc(maxRecipientSats.toULong())
                                }
                        }
                    },
                )
                } // end !isMultiMode single-recipient fields

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = gatewayReady) {
                                    showLabelField = !showLabelField
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (showLabelField || labelText.isNotBlank()) {
                                        BitcoinOrange.copy(alpha = 0.15f)
                                    } else {
                                        DarkSurface
                                    },
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                if (showLabelField || labelText.isNotBlank()) BitcoinOrange else BorderColor,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (showLabelField || labelText.isNotBlank()) BitcoinOrange else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    if (showLabelField) {
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.loc_642fdbfc),
                                    style = MaterialTheme.typography.bodyMedium,
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
                            enabled = gatewayReady && !isSending,
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                FeeRateSection(
                    feeEstimationState = feeEstimationState,
                    currentFeeRate = feeRate,
                    minFeeRate = minFeeRate,
                    onFeeRateChange = { feeRate = it },
                    onRefreshFees = onRefreshFees,
                    enabled = gatewayReady && !isSending,
                    estimatedFeeSats = if ((amountSats > 0 || isMaxMode) || (isMultiMode && multiRecipientList.isNotEmpty())) roughFeeSats else null,
                    estimatedVBytes = if ((amountSats > 0 || isMaxMode) || (isMultiMode && multiRecipientList.isNotEmpty())) estimatedVBytes else null,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    privacyMode = privacyMode,
                    formatAmount = ::formatAmount,
                    formatUsd = ::formatUsd,
                    formatVBytesDisplay = { v -> String.format(Locale.US, "%.0f", v) },
                    hiddenAmount = "****",
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (sendError != null && !showConfirmDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WarningYellow.copy(alpha = 0.1f)),
            ) {
                Text(
                    text = sendError,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningYellow,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val canReview =
            gatewayReady &&
                !isSending &&
                if (isMultiMode) {
                    multiRecipientList.size >= 2
                } else {
                    isAddressValid && (isMaxMode || amountSats.roundToLong() > 0)
                }

        Button(
            onClick = {
                onResetSend()
                showConfirmDialog = true
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canReview,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                ),
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.loc_81f5c0cf),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LnOnchainSendConfirmationDialog(
    recipients: List<Recipient>,
    isMaxMode: Boolean,
    recipientTotalSats: Long,
    estimatedFeeSats: Long,
    changeSats: Long?,
    feeRate: Double,
    label: String?,
    selectedUtxos: List<UtxoInfo>,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    isSending: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalSats = recipientTotalSats + estimatedFeeSats
    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = DarkSurface,
        actions = {
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isSending,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        contentColor = DarkBackground,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.5f),
                        disabledContentColor = DarkBackground.copy(alpha = 0.7f),
                    ),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DarkBackground,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.loc_a274c658),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(8.dp))
            IbisButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = !isSending,
            ) {
                Text(
                    text = stringResource(R.string.loc_51bac044),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.loc_81f5c0cf),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                if (recipients.size == 1) {
                    stringResource(R.string.loc_eaf579ea)
                } else {
                    stringResource(R.string.send_sending_to_recipients_format, recipients.size)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        recipients.forEach { recipient ->
            Text(
                text = recipient.address,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (recipients.size > 1) Spacer(modifier = Modifier.height(2.dp))
        }

        label?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.loc_cf667fec),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = AccentGreen,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = BorderColor)
        Spacer(modifier = Modifier.height(16.dp))

        recipients.forEach { recipient ->
            LnOnchainReviewAmountRow(
                label = stringResource(R.string.loc_d19e8dd8),
                detail = abbreviateLnOnchainReviewAddress(recipient.address),
                amountSats = recipient.amountSats.toLong(),
                sign = "-",
                color = AccentRed,
                useSats = useSats,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        changeSats?.let { amount ->
            LnOnchainReviewAmountRow(
                label = stringResource(R.string.loc_e09d7895),
                detail = stringResource(R.string.ln_node_onchain_change_assigned_on_send),
                amountSats = amount,
                sign = "+",
                color = SuccessGreen,
                useSats = useSats,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        LnOnchainReviewAmountRow(
            label = stringResource(R.string.loc_a870ad41),
            detail = stringResource(R.string.ln_node_onchain_fee_rate_format, formatFeeRate(feeRate)),
            amountSats = estimatedFeeSats,
            sign = "-",
            color = BitcoinOrange,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BorderColor)
        Spacer(modifier = Modifier.height(16.dp))

        LnOnchainReviewAmountRow(
            label = stringResource(R.string.loc_03eece5a),
            detail = null,
            amountSats = totalSats,
            sign = "-",
            color = AccentRed,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            emphasize = true,
        )

        if (selectedUtxos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    stringResource(
                        R.string.loc_485306ed,
                        selectedUtxos.size,
                        if (selectedUtxos.size > 1) "s" else "",
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = AccentRed,
            )
        }
    }
}

@Composable
private fun LnOnchainReviewAmountRow(
    label: String,
    detail: String?,
    amountSats: Long,
    sign: String,
    color: Color,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
                color = if (emphasize) MaterialTheme.colorScheme.onBackground else TextSecondary,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text =
                    if (privacyMode) {
                        "****"
                    } else {
                        "$sign${formatAmount(amountSats.toULong(), useSats, includeUnit = true)}"
                    },
                style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
                color = color,
            )
            if (!privacyMode && btcPrice != null && btcPrice > 0) {
                Text(
                    text = formatFiat((amountSats.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun abbreviateLnOnchainReviewAddress(address: String): String =
    if (address.length <= 19) address else "${address.take(8)}...${address.takeLast(8)}"

@Composable
private fun OnchainAvailabilityCard(error: String?) {
    if (error == null) return
    OnchainStatusBanner(message = error, isConnecting = false)
}

@Composable
private fun OnchainConnectionBanner(
    isConnecting: Boolean,
    detail: String,
    onOpenConnectionSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text =
                    if (isConnecting) {
                        stringResource(R.string.ln_node_status_connecting)
                    } else {
                        stringResource(R.string.ln_node_not_connected)
                    },
                color = if (isConnecting) BitcoinOrange else ErrorRed,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isConnecting) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onOpenConnectionSettings) {
                    Text(
                        text = stringResource(R.string.ln_node_setup_connection),
                        color = LightningYellow,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnchainStatusBanner(
    message: String,
    isConnecting: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text =
                    if (isConnecting) {
                        stringResource(R.string.ln_node_status_connecting)
                    } else {
                        stringResource(R.string.ln_node_onchain_unavailable)
                    },
                color = if (isConnecting) BitcoinOrange else ErrorRed,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
