package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import github.aeonbtc.ibiswallet.data.model.BitcoinTxSource
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkDepositClaimQuote
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.NfcReaderUiState
import github.aeonbtc.ibiswallet.ui.components.BalanceAmountText
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.QuickReceiveDialog
import github.aeonbtc.ibiswallet.ui.components.TransactionHistoryHideAllDialog
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.normalizeSparkAddressLabelRef
import github.aeonbtc.ibiswallet.util.startActivityWithTaskFallback
import java.util.Locale

private const val SPARK_HIDDEN_AMOUNT = "****"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkBalanceScreen(
    sparkState: SparkWalletState,
    receiveState: SparkReceiveState = SparkReceiveState.Idle,
    layer1Transactions: List<TransactionDetails> = emptyList(),
    layer1BlockHeight: UInt? = null,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_DISABLED,
    denomination: String,
    dateFormat: String = SecureStorage.DATE_FORMAT_MM_DD_YY,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrices: Map<String, Double> = emptyMap(),
    showHistoricalTxPrices: Boolean = false,
    onShowHistoricalTxPricesChange: (Boolean) -> Unit = {},
    privacyMode: Boolean,
    sparkAddressLabels: Map<String, String> = emptyMap(),
    sparkTransactionLabels: Map<String, String> = emptyMap(),
    sparkTransactionSources: Map<String, String> = emptyMap(),
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDenomination: () -> Unit,
    onQuickReceive: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
    onSaveSparkTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onSaveSparkAddressLabel: (String, String) -> Unit = { _, _ -> },
    onDeleteSparkTransactionLabel: (String) -> Unit = {},
    onDeleteSparkAddressLabel: (String) -> Unit = {},
    onDeleteSparkHistoryItem: (String) -> Unit = {},
    onDeleteAllSparkHistory: () -> Unit = {},
    onOpenExit: () -> Unit = {},
    onFetchInstantClaimQuote: suspend (String, UInt) -> SparkDepositClaimQuote? = { _, _ -> null },
    onClaimDepositNow: (SparkDepositClaimQuote) -> Unit = {},
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val showQrScanner = remember { mutableStateOf(false) }
    val showQuickReceive = remember { mutableStateOf(false) }
    val selectedSparkPayment = remember { mutableStateOf<SparkPayment?>(null) }
    val selectedSparkDeposit = remember { mutableStateOf<SparkUnclaimedDeposit?>(null) }
    // Instant-claim quote for the open deposit dialog. Fetched once per
    // dialog open for immature deposits; absent (null, not loading) means the
    // provider offers no early claim and the dialog shows nothing extra.
    var instantClaimQuote by remember { mutableStateOf<SparkDepositClaimQuote?>(null) }
    var isQuotingClaim by remember { mutableStateOf(false) }
    var historyItemPendingDelete by remember { mutableStateOf<SparkHistoryItem?>(null) }
    var showDeleteAllHistoryDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSparkTransactions by remember { mutableStateOf(false) }
    var showLightningTransactions by remember { mutableStateOf(false) }
    var showSwapTransactions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }
    val isPullRefreshing = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // NFC reader mode: tapping a tag routes through pendingSendInput.
    val balanceNfcContext = LocalContext.current
    val mainActivity = balanceNfcContext as? MainActivity
    val nfcReaderOwner = remember { Any() }
    val nfcAvailable = balanceNfcContext.getNfcAvailability().canRead
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

    LaunchedEffect(sparkState.isSyncing) {
        if (!sparkState.isSyncing) {
            isPullRefreshing.value = false
        }
    }

    historyItemPendingDelete?.let { item ->
        IbisConfirmDialog(
            onDismissRequest = { historyItemPendingDelete = null },
            title = stringResource(R.string.transaction_history_hide_title),
            message = stringResource(R.string.transaction_history_hide_message),
            confirmText = stringResource(R.string.transaction_history_hide_confirm),
            onConfirm = {
                onDeleteSparkHistoryItem(item.id)
                historyItemPendingDelete = null
                when (item) {
                    is SparkHistoryItem.Payment -> {
                        if (selectedSparkPayment.value?.id == item.payment.id) {
                            selectedSparkPayment.value = null
                        }
                    }
                    is SparkHistoryItem.Deposit -> {
                        if (
                            selectedSparkDeposit.value?.txid == item.deposit.txid &&
                            selectedSparkDeposit.value?.vout == item.deposit.vout
                        ) {
                            selectedSparkDeposit.value = null
                        }
                    }
                }
            },
        )
    }

    if (showDeleteAllHistoryDialog) {
        TransactionHistoryHideAllDialog(
            entryCount = sparkState.payments.size + sparkState.unclaimedDeposits.size,
            onDismissRequest = { showDeleteAllHistoryDialog = false },
            onConfirm = {
                onDeleteAllSparkHistory()
                showDeleteAllHistoryDialog = false
            },
        )
    }

    LaunchedEffect(
        isSearchActive,
        searchQuery.trim(),
        showSparkTransactions,
        showLightningTransactions,
        showSwapTransactions,
    ) {
        displayLimit = 25
    }
    val hasRailFilters = showSparkTransactions || showLightningTransactions || showSwapTransactions
    val railFilteredPayments =
        remember(
            sparkState.payments,
            sparkState.unclaimedDeposits,
            showSparkTransactions,
            showLightningTransactions,
            showSwapTransactions,
        ) {
            if (!hasRailFilters) {
                sparkState.payments
            } else {
                sparkState.payments.filter { payment ->
                    val rail = sparkRailBadge(payment).rail
                    (showSparkTransactions && rail == SparkRail.SPARK) ||
                        (showLightningTransactions && rail == SparkRail.LIGHTNING) ||
                        (showSwapTransactions && rail == SparkRail.SWAP)
                }
            }
        }
    val railFilteredDeposits =
        remember(
            sparkState.unclaimedDeposits,
            showSparkTransactions,
            showLightningTransactions,
            showSwapTransactions,
        ) {
            if (!hasRailFilters || showSwapTransactions) {
                sparkState.unclaimedDeposits
            } else {
                emptyList()
            }
        }
    val trimmedSearchQuery = searchQuery.trim()
    val searchedHistoryItems =
        remember(
            railFilteredPayments,
            railFilteredDeposits,
            sparkAddressLabels,
            sparkTransactionLabels,
            trimmedSearchQuery,
            layer1Transactions,
        ) {
            fun depositSortTimestamp(deposit: SparkUnclaimedDeposit): Long =
                deposit.timestamp?.let(::sparkNormalizeTimestampMillis)
                    ?: layer1Transactions
                        .firstOrNull { it.txid.equals(deposit.txid, ignoreCase = true) }
                        ?.timestamp
                        ?.let(::sparkNormalizeTimestampMillis)
                    ?: Long.MIN_VALUE

            val paymentItems =
                if (trimmedSearchQuery.isBlank()) {
                    railFilteredPayments.map(SparkHistoryItem::Payment)
                } else {
                    val query = trimmedSearchQuery.lowercase(Locale.US)
                    railFilteredPayments.filter { payment ->
                        val label =
                            sparkPaymentLabelWithOnchainFallback(
                                payment,
                                sparkAddressLabels,
                                sparkTransactionLabels,
                                layer1Transactions,
                            ).orEmpty()
                        val badge = sparkRailBadge(payment)
                        val date = formatSparkTimestamp(payment.timestamp, dateFormat)
                        listOf(
                            payment.id,
                            payment.type,
                            payment.status,
                            payment.method,
                            payment.amountSats.toString(),
                            payment.feeSats.toString(),
                            label,
                            badge.rail.name,
                            date,
                        ).any { it.lowercase(Locale.US).contains(query) }
                    }.map(SparkHistoryItem::Payment)
                }
            val depositItems =
                if (trimmedSearchQuery.isBlank()) {
                    railFilteredDeposits.map { deposit ->
                        SparkHistoryItem.Deposit(deposit, depositSortTimestamp(deposit))
                    }
                } else {
                    val query = trimmedSearchQuery.lowercase(Locale.US)
                    railFilteredDeposits.filter { deposit ->
                        val depositLayer1 =
                            layer1Transactions.firstOrNull { it.txid.equals(deposit.txid, ignoreCase = true) }
                        val depositLabel =
                            sparkDepositLabel(deposit, depositLayer1, sparkAddressLabels, sparkTransactionLabels).orEmpty()
                        listOf(
                            deposit.txid,
                            deposit.address.orEmpty(),
                            deposit.vout.toString(),
                            deposit.amountSats.toString(),
                            "Received",
                            "Bitcoin",
                            "On-chain",
                            if (deposit.isMature) "Confirmed" else "Waiting for confirmations",
                            deposit.claimError.orEmpty(),
                            depositLabel,
                        ).any { it.lowercase(Locale.US).contains(query) }
                    }.map { deposit ->
                        SparkHistoryItem.Deposit(deposit, depositSortTimestamp(deposit))
                    }
                }
            (depositItems + paymentItems).sortedByDescending { it.sortTimestampMillis }
        }
    val visibleHistoryItems = remember(searchedHistoryItems, displayLimit) { searchedHistoryItems.take(displayLimit) }
    val totalHistoryCount = searchedHistoryItems.size

    if (showQrScanner.value) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner.value = false
                onScanQrResult(code)
            },
            onDismiss = { showQrScanner.value = false },
        )
    }

    if (showQuickReceive.value) {
        SparkQuickReceiveDialog(
            receiveState = receiveState,
            privacyMode = privacyMode,
            onDismiss = { showQuickReceive.value = false },
        )
    }

    selectedSparkPayment.value?.let { payment ->
        val linkedPaymentRef = sparkPaymentLabelRef(payment)
        val explicitPaymentLabel = sparkPaymentTransactionLabel(payment, sparkTransactionLabels)
        val paymentLabel =
            sparkPaymentLabelWithOnchainFallback(
                payment,
                sparkAddressLabels,
                sparkTransactionLabels,
                layer1Transactions,
            )
        val linkedLayer1Transaction = remember(payment, layer1Transactions) { sparkResolveLayer1Transaction(payment, layer1Transactions) }
        val isCenterSwap =
            remember(payment, sparkTransactionSources, layer1Transactions) {
                sparkPaymentIsCenterSwap(
                    payment = payment,
                    sparkTransactionSources = sparkTransactionSources,
                    layer1Transactions = layer1Transactions,
                )
            }
        SparkPaymentDetailDialog(
            payment = payment,
            layer1Transaction = linkedLayer1Transaction,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            useSats = useSats,
            dateFormat = dateFormat,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            historicalBtcPrice = historicalBtcPrices[payment.id],
            privacyMode = privacyMode,
            label = paymentLabel,
            isCenterSwap = isCenterSwap,
            onSaveLabel = { label ->
                if (linkedPaymentRef != null) {
                    onSaveSparkAddressLabel(linkedPaymentRef, label)
                    if (explicitPaymentLabel != null) {
                        onDeleteSparkTransactionLabel(payment.id)
                        onDeleteSparkTransactionLabel(sparkPaymentLabelKey(payment))
                    }
                } else {
                    onSaveSparkTransactionLabel(payment.id, label)
                    onSaveSparkTransactionLabel(sparkPaymentLabelKey(payment), label)
                }
            },
            onDeleteLabel = paymentLabel?.let {
                {
                    linkedPaymentRef?.let(onDeleteSparkAddressLabel)
                        ?: run {
                            onDeleteSparkTransactionLabel(payment.id)
                            onDeleteSparkTransactionLabel(sparkPaymentLabelKey(payment))
                        }
                    if (explicitPaymentLabel != null) {
                        onDeleteSparkTransactionLabel(payment.id)
                        onDeleteSparkTransactionLabel(sparkPaymentLabelKey(payment))
                    }
                }
            },
            onHideFromHistory = {
                historyItemPendingDelete = SparkHistoryItem.Payment(payment)
            },
            onDismiss = { selectedSparkPayment.value = null },
        )
    }
    selectedSparkDeposit.value?.let { deposit ->
        val depositLayer1 =
            layer1Transactions.firstOrNull { it.txid.equals(deposit.txid, ignoreCase = true) }
        val depositLabel = sparkDepositLabel(deposit, depositLayer1, sparkAddressLabels, sparkTransactionLabels)
        val depositLabelRef = sparkDepositLabelRef(deposit, depositLayer1)
        // Quote once per dialog open; matured deposits never offer early claims.
        LaunchedEffect(deposit.txid, deposit.vout) {
            instantClaimQuote = null
            if (!deposit.isMature) {
                isQuotingClaim = true
                instantClaimQuote =
                    runCatching { onFetchInstantClaimQuote(deposit.txid, deposit.vout) }.getOrNull()
                isQuotingClaim = false
            }
        }
        SparkPendingDepositDetailDialog(
            deposit = deposit,
            layer1Transaction = depositLayer1,
            layer1BlockHeight = layer1BlockHeight,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            useSats = useSats,
            dateFormat = dateFormat,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            historicalBtcPrice = historicalBtcPrices[deposit.txid],
            privacyMode = privacyMode,
            label = depositLabel,
            onSaveLabel = { label ->
                if (depositLabelRef != null) {
                    onSaveSparkAddressLabel(depositLabelRef, label)
                    onDeleteSparkTransactionLabel(deposit.txid)
                } else {
                    onSaveSparkTransactionLabel(deposit.txid, label)
                }
            },
            onDeleteLabel = depositLabel?.let {
                {
                    depositLabelRef?.let(onDeleteSparkAddressLabel)
                        ?: onDeleteSparkTransactionLabel(deposit.txid)
                    onDeleteSparkTransactionLabel(deposit.txid)
                }
            },
            onHideFromHistory = {
                historyItemPendingDelete = SparkHistoryItem.Deposit(
                    deposit = deposit,
                    sortTimestampMillis = 0L,
                )
            },
            instantClaimQuote = instantClaimQuote,
            isQuotingClaim = isQuotingClaim,
            onClaimDeposit = { quote ->
                // Close first: the claim runs fire-and-forget behind spend
                // auth with snackbar reporting; the pending list refresh drops
                // the deposit on success.
                selectedSparkDeposit.value = null
                onClaimDepositNow(quote)
            },
            onDismiss = { selectedSparkDeposit.value = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing.value,
        onRefresh = {
            if (sparkState.isInitialized) {
                isPullRefreshing.value = true
                onRefresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {},
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                    translationY = progress * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SparkBalanceCard(
                    sparkState = sparkState,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    fiatCurrency = fiatCurrency,
                    privacyMode = privacyMode,
                    onTogglePrivacy = onTogglePrivacy,
                    onRefresh = onRefresh,
                    onToggleDenomination = onToggleDenomination,
                    onQuickReceive = {
                        showQuickReceive.value = true
                        onQuickReceive()
                    },
                    onScan = { showQrScanner.value = true },
                    isNfcReaderActive = isNfcReaderActive,
                    nfcReaderState = nfcReaderState,
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
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
                        if (sparkState.payments.isNotEmpty() || sparkState.unclaimedDeposits.isNotEmpty()) {
                            SparkTransactionFilterButton(
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.transaction_history_hide_all_content_description),
                                tint = TextSecondary,
                                isSelected = true,
                                onClick = { showDeleteAllHistoryDialog = true },
                                matchLayer1Style = true,
                            )
                        }
                        if (historicalBtcPrices.isNotEmpty()) {
                            SparkTransactionFilterButton(
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
                                matchLayer1Style = true,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = SparkPurple,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable(enabled = sparkState.isInitialized) {
                                    onOpenExit()
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.spark_exit_open),
                                tint = SparkPurple,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        SparkTransactionAsteriskFilterButton(
                            tint = SparkPurple,
                            isSelected = showSparkTransactions,
                            onClick = { showSparkTransactions = !showSparkTransactions },
                        )
                        SparkTransactionFilterButton(
                            icon = Icons.Default.Bolt,
                            contentDescription = stringResource(R.string.loc_ce31119b),
                            tint = LightningYellow,
                            isSelected = showLightningTransactions,
                            onClick = { showLightningTransactions = !showLightningTransactions },
                        )
                        SparkTransactionFilterButton(
                            icon = Icons.Default.CurrencyBitcoin,
                            contentDescription = stringResource(R.string.loc_ea6a9a53),
                            tint = BitcoinOrange,
                            isSelected = showSwapTransactions,
                            onClick = { showSwapTransactions = !showSwapTransactions },
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
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
                                tint = if (isSearchActive) SparkPurple else TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
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
                                text = stringResource(R.string.spark_search_history_placeholder),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SparkPurple,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = SparkPurple,
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            sparkState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }

            if (totalHistoryCount == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text =
                                        if (searchQuery.isNotBlank()) {
                                            stringResource(R.string.loc_167ce23f)
                                        } else {
                                            stringResource(R.string.loc_a317d3d8)
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (searchQuery.isNotBlank()) {
                                            stringResource(R.string.loc_9febfd40)
                                        } else if (sparkState.isInitialized) {
                                            stringResource(R.string.loc_2aebf14e)
                                        } else {
                                            stringResource(R.string.spark_loading_wallet_message)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            } else {
                items(visibleHistoryItems, key = { it.id }) { item ->
                    when (item) {
                        is SparkHistoryItem.Payment -> {
                            val paymentLabel =
                                sparkPaymentLabelWithOnchainFallback(
                                    item.payment,
                                    sparkAddressLabels,
                                    sparkTransactionLabels,
                                    layer1Transactions,
                                )
                            SparkTransactionRow(
                                payment = item.payment,
                                useSats = useSats,
                                dateFormat = dateFormat,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                historicalBtcPrice =
                                    if (showHistoricalTxPrices) {
                                        historicalBtcPrices[item.payment.id]
                                    } else {
                                        null
                                    },
                                privacyMode = privacyMode,
                                label = paymentLabel,
                                isCenterSwap =
                                    sparkPaymentIsCenterSwap(
                                        payment = item.payment,
                                        sparkTransactionSources = sparkTransactionSources,
                                        layer1Transactions = layer1Transactions,
                                    ),
                                onClick = { selectedSparkPayment.value = item.payment },
                            )
                        }
                        is SparkHistoryItem.Deposit -> {
                            val layer1Transaction =
                                layer1Transactions.firstOrNull {
                                    it.txid.equals(item.deposit.txid, ignoreCase = true)
                                }
                            val depositLabel =
                                sparkDepositLabel(
                                    item.deposit,
                                    layer1Transaction,
                                    sparkAddressLabels,
                                    sparkTransactionLabels,
                                )
                            SparkPendingDepositRow(
                                deposit = item.deposit,
                                layer1Transaction = layer1Transaction,
                                layer1BlockHeight = layer1BlockHeight,
                                useSats = useSats,
                                dateFormat = dateFormat,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                historicalBtcPrice =
                                    if (showHistoricalTxPrices) {
                                        historicalBtcPrices[item.deposit.txid]
                                    } else {
                                        null
                                },
                                privacyMode = privacyMode,
                                label = depositLabel,
                                onClick = { selectedSparkDeposit.value = item.deposit },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (visibleHistoryItems.size < totalHistoryCount) {
                    item {
                        val remaining = totalHistoryCount - visibleHistoryItems.size
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
                                        stringResource(R.string.loc_0ee47e3c)
                                    } else {
                                        stringResource(R.string.common_show_all_remaining_format, remaining)
                                    },
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkBalanceCard(
    sparkState: SparkWalletState,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDenomination: () -> Unit,
    onQuickReceive: () -> Unit,
    onScan: () -> Unit,
    isNfcReaderActive: Boolean = false,
    nfcReaderState: NfcReaderUiState = NfcReaderUiState.Inactive,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(bottom = 4.dp)
                    .align(Alignment.TopCenter),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
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
                        contentDescription = stringResource(R.string.loc_990bb023),
                        tint = if (privacyMode) SparkPurple else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                val syncEnabled = sparkState.isInitialized && !sparkState.isSyncing
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable(enabled = syncEnabled) { onRefresh() },
                ) {
                    if (sparkState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SparkPurple,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.loc_8c195a44),
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BalanceAmountText(
                    amountText =
                        if (privacyMode) {
                            SPARK_HIDDEN_AMOUNT
                        } else if (useSats) {
                            formatAmount(sparkState.balanceSats.toULong(), true)
                        } else {
                            formatAmount(sparkState.balanceSats.toULong(), false)
                        },
                    showBtcSymbol = !privacyMode && !useSats,
                    showSatsUnit = !privacyMode && useSats,
                    onClick = onToggleDenomination,
                )
                if (btcPrice != null && btcPrice > 0) {
                    val fiatValue = (sparkState.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = if (privacyMode) SPARK_HIDDEN_AMOUNT else formatFiat(fiatValue, fiatCurrency),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = TextSecondary,
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant)
                    .clickable(enabled = sparkState.isInitialized) { onQuickReceive() },
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = stringResource(R.string.loc_a397da3c),
                    tint = SparkPurple,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant)
                    .clickable(enabled = sparkState.isInitialized) { onScan() },
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(R.string.loc_60129540),
                    tint = SparkPurple,
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
                        SparkPurple
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

private sealed interface SparkHistoryItem {
    val id: String
    val sortTimestampMillis: Long

    data class Payment(val payment: SparkPayment) : SparkHistoryItem {
        override val id: String = payment.id
        override val sortTimestampMillis: Long = sparkNormalizeTimestampMillis(payment.timestamp)
    }

    data class Deposit(
        val deposit: SparkUnclaimedDeposit,
        override val sortTimestampMillis: Long,
    ) : SparkHistoryItem {
        override val id: String = "deposit:${deposit.txid}:${deposit.vout}"
    }
}

@Composable
private fun SparkPendingDepositRow(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
    useSats: Boolean,
    dateFormat: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val isConfirmed = sparkDepositHasRequiredConfirmations(deposit, layer1Transaction, layer1BlockHeight)
    val formattedTimestamp = (deposit.timestamp ?: layer1Transaction?.timestamp)?.let { formatSparkTimestamp(it, dateFormat) }.orEmpty()
    val displayLabel = label?.takeIf { it.isNotBlank() }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
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
                        .background(AccentGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = stringResource(R.string.loc_301a5b91),
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            if (deposit.isSwapDeposit || layer1Transaction?.isSwapHistory == true) {
                                stringResource(R.string.loc_85a12a5f)
                            } else {
                                stringResource(R.string.loc_301a5b91)
                            },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    SparkHistoryRailBadge(railBadge = SparkRailBadge(BitcoinOrange, SparkRail.SWAP))
                }
                if (!displayLabel.isNullOrBlank()) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = SparkPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                        formattedTimestamp.ifBlank {
                            if (isConfirmed) {
                                stringResource(R.string.loc_4ab75d7f)
                            } else {
                                ""
                            }
                        },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        if (privacyMode) {
                            SPARK_HIDDEN_AMOUNT
                        } else {
                            "+${formatAmount(deposit.amountSats.toULong(), useSats, includeUnit = true)}"
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen,
                    textAlign = TextAlign.End,
                )
                val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                if (!privacyMode && effectiveBtcPrice != null && effectiveBtcPrice > 0) {
                    SparkHistoricalFiatText(
                        text = formatFiat((deposit.amountSats / 100_000_000.0) * effectiveBtcPrice, fiatCurrency),
                        isHistorical = historicalBtcPrice != null,
                    )
                }
                if (!isConfirmed) {
                    SparkPendingBadge(text = stringResource(R.string.loc_1b684325))
                }
            }
        }
    }
}

@Composable
private fun InstantClaimSection(
    quote: SparkDepositClaimQuote?,
    quoting: Boolean,
    useSats: Boolean,
    privacyMode: Boolean,
    onClaim: (SparkDepositClaimQuote) -> Unit,
) {
    if (!quoting && quote == null) return
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = TextSecondary.copy(alpha = 0.1f),
    )
    Text(
        text = stringResource(R.string.spark_deposit_claim_section_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (quoting) {
        Text(
            text = stringResource(R.string.spark_deposit_claim_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        return
    }
    val current = quote ?: return
    val amountText: (Long) -> String = { sats ->
        if (privacyMode) SPARK_HIDDEN_AMOUNT else formatAmount(sats.toULong(), useSats, includeUnit = true)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.spark_deposit_claim_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Text(
                    text = amountText(current.creditSats),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.spark_deposit_claim_fee),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Text(
                    text = amountText(current.feeSats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            // The claim is its own on-chain transaction spending the deposit
            // output, so its fee necessarily comes out of the deposit itself —
            // "You receive" above is already net. Without this line the
            // deduction looks like a cut.
            Text(
                text = stringResource(R.string.spark_deposit_claim_fee_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (current.isEstimate) {
                Text(
                    text = stringResource(R.string.spark_deposit_claim_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    // Slim action row mirroring the tx-detail Speed Up button (36.dp
    // outlined, accent border) instead of a full 48.dp primary.
    OutlinedButton(
        onClick = { onClaim(current) },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = SparkPurple,
            ),
        border = BorderStroke(1.dp, SparkPurple.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.spark_deposit_claim_title),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SparkPendingDepositDetailDialog(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
    mempoolUrl: String,
    mempoolServer: String,
    useSats: Boolean,
    dateFormat: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    onSaveLabel: (String) -> Unit,
    onDeleteLabel: (() -> Unit)?,
    onHideFromHistory: () -> Unit = {},
    instantClaimQuote: SparkDepositClaimQuote? = null,
    isQuotingClaim: Boolean = false,
    onClaimDeposit: (SparkDepositClaimQuote) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val depositTxidCopyLabel = stringResource(R.string.spark_deposit_txid_copy_label)
    val depositAddressCopyLabel = stringResource(R.string.spark_deposit_address_copy_label)
    val depositAmountText =
        if (privacyMode) {
            SPARK_HIDDEN_AMOUNT
        } else {
            "+${formatAmount(deposit.amountSats.toULong(), useSats, includeUnit = true)}"
        }
    var showCopiedAmount by remember { mutableStateOf(false) }
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember(label) { mutableStateOf(label.orEmpty()) }
    val showTorBrowserError = remember { mutableStateOf(false) }
    val confirmationProgress = sparkDepositConfirmationProgress(deposit, layer1Transaction, layer1BlockHeight)
    val isConfirmed = sparkDepositHasRequiredConfirmations(deposit, layer1Transaction, layer1BlockHeight)
    val formattedTimestamp =
        (deposit.timestamp ?: layer1Transaction?.timestamp)?.let { formatSparkFullTimestamp(it, dateFormat) }.orEmpty()
    val explorerTxid = deposit.txid.takeIf { mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank() }

    if (showTorBrowserError.value) {
        SparkTorBrowserErrorDialog(onDismiss = { showTorBrowserError.value = false })
    }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideLocalizedResources {
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
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
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
                            .background(AccentGreen.copy(alpha = 0.1f))
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCard.copy(alpha = 0.72f))
                            .clickable(onClick = onHideFromHistory),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.transaction_history_hide_confirm),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (deposit.isSwapDeposit || layer1Transaction?.isSwapHistory == true) {
                                    stringResource(R.string.loc_85a12a5f)
                                } else {
                                    stringResource(R.string.spark_deposit_title_received_bitcoin)
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentGreen,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = depositAmountText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                            modifier =
                                if (privacyMode) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        SecureClipboard.copyAndScheduleClear(context, depositAmountText)
                                        showCopiedAmount = true
                                    }
                                },
                        )
                        if (showCopiedAmount) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = SparkPurple,
                            )
                        }
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatFiat((deposit.amountSats / 100_000_000.0) * btcPrice, fiatCurrency),
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                color = TextSecondary,
                            )
                        }
                        if (historicalBtcPrice != null && historicalBtcPrice > 0 && !privacyMode) {
                            SparkHistoricalFiatText(
                                text =
                                    formatFiat(
                                        (deposit.amountSats / 100_000_000.0) * historicalBtcPrice,
                                        fiatCurrency,
                                    ),
                                isHistorical = true,
                                large = true,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            explorerTxid?.let { txid ->
                                SparkExplorerBadge(
                                    tint = BitcoinOrange,
                                    onClick = {
                                        sparkOpenBitcoinExplorer(
                                            context = context,
                                            mempoolUrl = mempoolUrl,
                                            txid = txid,
                                            onTorBrowserMissing = { showTorBrowserError.value = true },
                                        )
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
                                    imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (isConfirmed) AccentGreen else BitcoinOrange,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text =
                                        if (isConfirmed) {
                                            stringResource(R.string.loc_4ab75d7f)
                                        } else {
                                            "${stringResource(R.string.loc_1b684325)} $confirmationProgress"
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isConfirmed) AccentGreen else BitcoinOrange,
                                )
                            }
                            if (formattedTimestamp.isNotBlank()) {
                                Text(
                                    text = formattedTimestamp,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                        }

                        if (!isConfirmed) {
                            InstantClaimSection(
                                quote = instantClaimQuote,
                                quoting = isQuotingClaim,
                                useSats = useSats,
                                privacyMode = privacyMode,
                                onClaim = { quote -> onClaimDeposit(quote) },
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.loc_13e398d0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SparkCopyRow(
                            value = deposit.txid,
                            copyLabel = depositTxidCopyLabel,
                            copied = showCopiedTxid,
                            accentColor = SparkPurple,
                            showFullValue = true,
                            onCopy = {
                                SecureClipboard.copyAndScheduleClear(
                                    context,
                                    deposit.txid,
                                )
                                showCopiedTxid = true
                            },
                        )
                        if (showCopiedTxid) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = SparkPurple,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.loc_b47edf23),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (deposit.address != null) {
                            SparkCopyRow(
                                value = deposit.address,
                                copyLabel = depositAddressCopyLabel,
                                copied = showCopiedAddress,
                                accentColor = SparkPurple,
                                amountText =
                                    if (privacyMode) {
                                        SPARK_HIDDEN_AMOUNT
                                    } else {
                                        "+${formatAmount(deposit.amountSats.toULong(), useSats, includeUnit = true)}"
                                    },
                                amountColor = AccentGreen,
                                onCopy = {
                                    SecureClipboard.copyAndScheduleClear(
                                        context,
                                        deposit.address,
                                    )
                                    showCopiedAddress = true
                                },
                            )
                        } else {
                            Text(
                                text =
                                    if (isConfirmed) {
                                        stringResource(R.string.loc_4ab75d7f)
                                    } else {
                                        stringResource(R.string.spark_deposit_status_awaiting_confirmations)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        if (showCopiedAddress) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = SparkPurple,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isEditingLabel) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.loc_822c6f45),
                                        color = TextSecondary.copy(alpha = 0.5f),
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SparkPurple,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = SparkPurple,
                                    ),
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            onSaveLabel(labelText)
                                            isEditingLabel = false
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.loc_f55495e0), color = SparkPurple)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            EditableLabelChip(
                                label = labelText.takeIf { it.isNotBlank() },
                                accentColor = SparkPurple,
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.spark_deposit_note_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (isConfirmed) {
                                    stringResource(R.string.spark_deposit_note_confirmed_message)
                                } else {
                                    stringResource(R.string.spark_deposit_note_message)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        if (!isConfirmed) deposit.claimError?.let {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text = stringResource(R.string.spark_deposit_claim_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.spark_deposit_claim_manual_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BitcoinOrange,
                            )
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
                    Text(text = stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        }
    }
}

@Composable
private fun SparkTransactionFilterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    matchLayer1Style: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) tint.copy(alpha = if (matchLayer1Style) 0.16f else 0.18f) else DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) tint else if (matchLayer1Style) TextSecondary else TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(if (matchLayer1Style) 20.dp else 18.dp),
        )
    }
}

@Composable
private fun SparkTransactionAsteriskFilterButton(
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
                .background(if (isSelected) tint.copy(alpha = 0.18f) else DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        val iconColor = if (isSelected) tint else TextSecondary.copy(alpha = 0.6f)
        SparkAsteriskIcon(
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SparkTransactionRow(
    payment: SparkPayment,
    useSats: Boolean,
    dateFormat: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    isCenterSwap: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val isReceive = payment.type.equals("RECEIVE", ignoreCase = true)
    val isFailed = sparkPaymentIsFailed(payment.status)
    val isPending = !sparkPaymentIsConfirmed(payment.status) && !isFailed
    val railBadge = sparkRailBadge(payment)
    val isFailedLightningPayment = isFailed && railBadge.rail == SparkRail.LIGHTNING
    // Sent total debited = invoice amount + fee (matches send review Total). Receives show amount only.
    val totalSats = sparkPaymentTotalSats(payment).toULong()
    val icon = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade
    val iconTint = if (isReceive) AccentGreen else AccentRed
    val iconBackground = if (isReceive) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f)
    val amountColor = if (isFailedLightningPayment) TextSecondary else if (isReceive) AccentGreen else AccentRed
    val formattedTimestamp = remember(payment.timestamp, dateFormat) { formatSparkTimestamp(payment.timestamp, dateFormat) }
    val displayLabel = label?.takeIf { it.isNotBlank() }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
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
                        .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription =
                        if (isReceive) {
                            stringResource(R.string.loc_301a5b91)
                        } else {
                            stringResource(R.string.loc_1af68597)
                        },
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            sparkPaymentDisplayTitle(
                                isReceive = isReceive,
                                isCenterSwap = isCenterSwap,
                            ),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    SparkHistoryRailBadge(railBadge = railBadge)
                }
                if (!displayLabel.isNullOrBlank()) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = SparkPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formattedTimestamp.ifBlank { payment.status },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        if (privacyMode) {
                            SPARK_HIDDEN_AMOUNT
                        } else if (isFailedLightningPayment) {
                            formatAmount(totalSats, useSats, includeUnit = true)
                        } else {
                            "${if (isReceive) "+" else "-"}${formatAmount(totalSats, useSats, includeUnit = true)}"
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    textAlign = TextAlign.End,
                )
                val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                if (!privacyMode && effectiveBtcPrice != null && effectiveBtcPrice > 0) {
                    SparkHistoricalFiatText(
                        text = formatFiat((totalSats.toDouble() / 100_000_000.0) * effectiveBtcPrice, fiatCurrency),
                        isHistorical = historicalBtcPrice != null,
                    )
                }
                if (isFailed) {
                    SparkPendingBadge(
                        text = sparkPaymentDisplayStatus(payment.status),
                        color = ErrorRed,
                    )
                } else if (isPending) {
                    SparkPendingBadge(text = stringResource(R.string.loc_1b684325))
                }
            }
        }
    }
}

@Composable
private fun SparkHistoricalFiatText(
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
private fun SparkPendingBadge(
    text: String,
    color: Color = BitcoinOrange,
) {
    Box(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SparkPaymentDetailDialog(
    payment: SparkPayment,
    layer1Transaction: TransactionDetails?,
    mempoolUrl: String,
    mempoolServer: String,
    useSats: Boolean,
    dateFormat: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    isCenterSwap: Boolean = false,
    onSaveLabel: (String) -> Unit,
    onDeleteLabel: (() -> Unit)?,
    onHideFromHistory: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isReceive = payment.type.equals("RECEIVE", ignoreCase = true)
    val accentColor = if (isReceive) AccentGreen else AccentRed
    val railBadge = sparkRailBadge(payment)
    val paymentIdCopyLabel = stringResource(R.string.spark_payment_id_copy_label)
    val bitcoinTxidCopyLabel = stringResource(R.string.loc_09d663eb)
    val receivedAtCopyLabel = stringResource(R.string.spark_received_at_copy_label)
    val recipientCopyLabel = stringResource(R.string.spark_recipient_copy_label)
    val recipientValueCopyLabel = if (isReceive) receivedAtCopyLabel else recipientCopyLabel
    val isConfirmed = sparkPaymentIsConfirmed(payment.status)
    val isFailed = sparkPaymentIsFailed(payment.status)
    val statusColor =
        when {
            isFailed -> ErrorRed
            isConfirmed -> AccentGreen
            else -> BitcoinOrange
        }
    val statusLabel =
        when {
            isFailed -> stringResource(R.string.ln_node_status_failed)
            isConfirmed -> stringResource(R.string.loc_4ab75d7f)
            else -> sparkPaymentDisplayStatus(payment.status)
        }
    val failureReasonText =
        payment.failureReason?.takeIf { it.isNotBlank() }
            ?: sparkPaymentFailureReasonFallback(payment.status).takeIf { isFailed }
    val formattedTimestamp = formatSparkFullTimestamp(payment.timestamp, dateFormat)
    val recipient = payment.recipient?.takeIf { it.isNotBlank() }
    val feeColor = if (railBadge.rail == SparkRail.LIGHTNING) LightningYellow else BitcoinOrange
    // Header shows full debited total on sends (invoice + fee, matches send review Total).
    // Breakdown below shows invoice amount (recipient row) + fee row separately.
    val totalSats = sparkPaymentTotalSats(payment).toULong()
    val invoiceSats = payment.amountSats.coerceAtLeast(0L).toULong()
    // Deposit receives: the header stays the exact net credit, while the
    // "Received at" row below shows the gross face value that arrived
    // on-chain — the fee row then nets the two out (10,000 − 198, not
    // 9,802 − 198).
    val isDepositReceive =
        isReceive && railBadge.rail == SparkRail.SWAP && payment.feeSats > 0L
    val paymentAmountText =
        if (privacyMode) {
            SPARK_HIDDEN_AMOUNT
        } else {
            "${if (isReceive) "+" else "-"}${formatAmount(totalSats, useSats, includeUnit = true)}"
        }
    val scrollState = rememberScrollState()
    var showCopiedAmount by remember { mutableStateOf(false) }
    var showCopiedId by remember { mutableStateOf(false) }
    var showCopiedRecipient by remember { mutableStateOf(false) }
    var showCopiedBitcoinTxid by remember { mutableStateOf(false) }
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember(label) { mutableStateOf(label.orEmpty()) }
    val showTorBrowserError = remember { mutableStateOf(false) }
    val bitcoinExplorerTxid =
        remember(payment, layer1Transaction, mempoolUrl, mempoolServer) {
            (sparkExtractLayer1Txid(payment) ?: layer1Transaction?.txid)
                ?.takeIf { mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank() }
        }

    if (showTorBrowserError.value) {
        SparkTorBrowserErrorDialog(onDismiss = { showTorBrowserError.value = false })
    }

    LaunchedEffect(showCopiedAmount) {
        if (showCopiedAmount) {
            kotlinx.coroutines.delay(3000)
            showCopiedAmount = false
        }
    }
    LaunchedEffect(showCopiedId) {
        if (showCopiedId) {
            kotlinx.coroutines.delay(3000)
            showCopiedId = false
        }
    }
    LaunchedEffect(showCopiedRecipient) {
        if (showCopiedRecipient) {
            kotlinx.coroutines.delay(3000)
            showCopiedRecipient = false
        }
    }
    LaunchedEffect(showCopiedBitcoinTxid) {
        if (showCopiedBitcoinTxid) {
            kotlinx.coroutines.delay(3000)
            showCopiedBitcoinTxid = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideLocalizedResources {
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
                            .background(accentColor.copy(alpha = 0.1f))
                            .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCard.copy(alpha = 0.72f))
                            .clickable(onClick = onHideFromHistory),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.transaction_history_hide_confirm),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector =
                                if (isReceive) {
                                    Icons.AutoMirrored.Filled.CallReceived
                                } else {
                                    Icons.AutoMirrored.Filled.CallMade
                                },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.spark_payment_direction_with_rail_format,
                                    sparkPaymentDisplayTitle(
                                        isReceive = isReceive,
                                        isCenterSwap = isCenterSwap,
                                    ),
                                    stringResource(sparkRailLabelRes(railBadge.rail)),
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            color = accentColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = paymentAmountText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier =
                                if (privacyMode) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        SecureClipboard.copyAndScheduleClear(context, paymentAmountText)
                                        showCopiedAmount = true
                                    }
                                },
                        )
                        if (showCopiedAmount) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = SparkPurple,
                            )
                        }
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            SparkHistoricalFiatText(
                                text = formatFiat((totalSats.toDouble() / 100_000_000.0) * btcPrice, fiatCurrency),
                                isHistorical = false,
                                large = true,
                            )
                        }
                        if (historicalBtcPrice != null && historicalBtcPrice > 0 && !privacyMode) {
                            SparkHistoricalFiatText(
                                text =
                                    formatFiat(
                                        (totalSats.toDouble() / 100_000_000.0) * historicalBtcPrice,
                                        fiatCurrency,
                                    ),
                                isHistorical = true,
                                large = true,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                                bitcoinExplorerTxid?.let { txid ->
                                    SparkExplorerBadge(
                                        tint = BitcoinOrange,
                                        onClick = {
                                            sparkOpenBitcoinExplorer(
                                                context = context,
                                                mempoolUrl = mempoolUrl,
                                                txid = txid,
                                                onTorBrowserMissing = { showTorBrowserError.value = true },
                                            )
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
                                            when {
                                                isFailed -> Icons.Default.Close
                                                isConfirmed -> Icons.Default.CheckCircle
                                                else -> Icons.Default.Schedule
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
                                }
                                if (formattedTimestamp.isNotBlank()) {
                                    Text(
                                        text = formattedTimestamp,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            failureReasonText?.let { reason ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Text(
                                    text = stringResource(R.string.ln_node_failure_reason),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.spark_payment_id_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SparkCopyRow(
                            value = payment.id,
                            copyLabel = paymentIdCopyLabel,
                            copied = showCopiedId,
                            accentColor = SparkPurple,
                            showFullValue = true,
                            onCopy = {
                                SecureClipboard.copyAndScheduleClear(
                                    context,
                                    payment.id,
                                )
                                showCopiedId = true
                            },
                        )
                        if (showCopiedId) {
                            Text(
                                text = stringResource(R.string.loc_e287255d),
                                style = MaterialTheme.typography.bodySmall,
                                color = SparkPurple,
                            )
                        }

                        bitcoinExplorerTxid?.let { txid ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text = stringResource(R.string.loc_13e398d0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SparkCopyRow(
                                value = txid,
                                copyLabel = bitcoinTxidCopyLabel,
                                copied = showCopiedBitcoinTxid,
                                accentColor = BitcoinOrange,
                                showFullValue = true,
                                onCopy = {
                                    SecureClipboard.copyAndScheduleClear(
                                        context,
                                        txid,
                                    )
                                    showCopiedBitcoinTxid = true
                                },
                            )
                            if (showCopiedBitcoinTxid) {
                                Text(
                                    text = stringResource(R.string.loc_e287255d),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                        }

                        recipient?.let { recipientValue ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text =
                                    if (isReceive) {
                                        stringResource(R.string.loc_b47edf23)
                                    } else {
                                        stringResource(R.string.loc_eaf579ea)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SparkCopyRow(
                                value = recipientValue,
                                copyLabel = recipientValueCopyLabel,
                                copied = showCopiedRecipient,
                                accentColor = SparkPurple,
                                amountText =
                                    if (privacyMode) {
                                        SPARK_HIDDEN_AMOUNT
                                    } else {
                                        // Deposit receives show the gross face value that
                                        // arrived on-chain here; the header above stays
                                        // the exact net credit and the fee row nets out.
                                        val recipientSats =
                                            if (isDepositReceive) {
                                                (payment.amountSats.coerceAtLeast(0L) + payment.feeSats.coerceAtLeast(0L)).toULong()
                                            } else {
                                                invoiceSats
                                            }
                                        "${if (isReceive) "+" else "-"}${formatAmount(recipientSats, useSats, includeUnit = true)}"
                                    },
                                amountColor = if (isReceive) AccentGreen else AccentRed,
                                onCopy = {
                                    SecureClipboard.copyAndScheduleClear(
                                        context,
                                        recipientValue,
                                    )
                                    showCopiedRecipient = true
                                },
                            )
                            if (showCopiedRecipient) {
                                Text(
                                    text = stringResource(R.string.loc_e287255d),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SparkPurple,
                                )
                            }
                        }

                        // Breakdown for sends without a recipient row (e.g. lightning with no stored invoice):
                        // show invoice amount separately so header total = amount + fee is explainable.
                        if (!isReceive && recipient == null && payment.feeSats > 0L) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text = stringResource(R.string.loc_890d7574),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    if (privacyMode) {
                                        SPARK_HIDDEN_AMOUNT
                                    } else {
                                        "-${formatAmount(invoiceSats, useSats, includeUnit = true)}"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = AccentRed,
                            )
                        }

                        if (!isReceive && payment.feeSats > 0L) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text = stringResource(R.string.loc_f72cc482),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    if (privacyMode) {
                                        SPARK_HIDDEN_AMOUNT
                                    } else {
                                        "-${formatAmount(payment.feeSats.toULong(), useSats, includeUnit = true)}"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = feeColor,
                            )
                        }

                        // Receives never showed fees, so a claimed deposit read
                        // as "10,000 became 9,802" with no explanation. The
                        // claim is an on-chain spend of the deposit output, so
                        // its mining fee comes out of the deposit itself.
                        if (isReceive && payment.feeSats > 0L) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Text(
                                text = stringResource(R.string.loc_f72cc482),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    if (privacyMode) {
                                        SPARK_HIDDEN_AMOUNT
                                    } else {
                                        "-${formatAmount(payment.feeSats.toULong(), useSats, includeUnit = true)}"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = feeColor,
                            )
                            // Claim-fee note only for on-chain deposit receives,
                            // where the deduction surprises (Lightning fee
                            // semantics differ).
                            if (railBadge.rail == SparkRail.SWAP) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.spark_deposit_claim_fee_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = TextSecondary.copy(alpha = 0.1f),
                        )

                        Text(
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isEditingLabel) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.loc_822c6f45),
                                        color = TextSecondary.copy(alpha = 0.5f),
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SparkPurple,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = SparkPurple,
                                    ),
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            onSaveLabel(labelText)
                                            isEditingLabel = false
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.loc_f55495e0), color = SparkPurple)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            EditableLabelChip(
                                label = labelText.takeIf { it.isNotBlank() },
                                accentColor = SparkPurple,
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
                    Text(text = stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        }
    }
}

@Composable
private fun SparkCopyRow(
    value: String,
    copyLabel: String,
    copied: Boolean,
    accentColor: Color,
    onCopy: () -> Unit,
    amountText: String? = null,
    amountColor: Color = TextSecondary,
    showFullValue: Boolean = false,
) {
    // Always top-aligned: centering single-line text against the 32.dp copy
    // button pushed it ~6.dp below its label, reading as a stray blank line.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showFullValue) value else sparkTruncateDetailValue(value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            amountText?.let {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = amountColor,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant)
                    .clickable(onClick = onCopy),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.common_copy_with_label, copyLabel),
                tint = if (copied) accentColor else TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SparkHistoryRailBadge(railBadge: SparkRailBadge) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(railBadge.color.copy(alpha = 0.16f)),
    ) {
        if (railBadge.rail == SparkRail.SPARK) {
            SparkAsteriskIcon(
                tint = railBadge.color,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector =
                    when (railBadge.rail) {
                        SparkRail.LIGHTNING -> Icons.Default.Bolt
                        SparkRail.SWAP -> Icons.Default.CurrencyBitcoin
                        SparkRail.SPARK -> Icons.Default.Bolt
                    },
                contentDescription = null,
                tint = railBadge.color,
                modifier =
                    Modifier.size(
                        when (railBadge.rail) {
                            SparkRail.LIGHTNING,
                            -> 20.dp
                            SparkRail.SWAP -> 21.dp
                            SparkRail.SPARK -> 18.dp
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SparkAsteriskIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.46f
        val innerRadius = size.minDimension * 0.18f
        val starPath =
            Path().apply {
                for (index in 0 until 12) {
                    val angle = Math.toRadians((index * 30 - 90).toDouble())
                    val radius = if (index % 2 == 0) outerRadius else innerRadius
                    val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                    val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) {
                        moveTo(x, y)
                    } else {
                        lineTo(x, y)
                    }
                }
                close()
            }
        drawPath(
            path = starPath,
            color = tint,
        )
    }
}

private fun sparkTruncateDetailValue(value: String): String {
    if (value.length <= 32) return value
    return "${value.take(18)}...${value.takeLast(12)}"
}

@Composable
private fun SparkExplorerBadge(
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

private fun sparkPaymentIsConfirmed(status: String): Boolean =
    status.equals("COMPLETE", ignoreCase = true) ||
        status.equals("SUCCEEDED", ignoreCase = true) ||
        status.equals("SUCCESS", ignoreCase = true) ||
        status.equals("COMPLETED", ignoreCase = true)

private fun sparkPaymentIsFailed(status: String): Boolean {
    val normalized = status.trim().lowercase(Locale.US)
    return normalized == "failed" ||
        normalized == "failure" ||
        normalized.contains("fail")
}

private fun sparkPaymentDisplayStatus(status: String): String =
    status.trim().lowercase(Locale.US).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }

/** Fallback when SDK only reports FAILED with no detail text. */
private fun sparkPaymentFailureReasonFallback(status: String): String? {
    val normalized = status.trim()
    if (normalized.isBlank()) return null
    if (normalized.equals("FAILED", ignoreCase = true) ||
        normalized.equals("FAILURE", ignoreCase = true)
    ) {
        return null
    }
    return sparkPaymentDisplayStatus(normalized)
}

private fun sparkPaymentLabelKey(payment: SparkPayment): String =
    listOf(
        payment.type,
        payment.status,
        payment.amountSats.toString(),
        payment.timestamp.toString(),
        payment.method,
    ).joinToString(separator = "|")

private fun sparkPaymentLabelRef(payment: SparkPayment): String? =
    payment.recipient
        ?.takeIf { it.isNotBlank() }
        ?.let(::normalizeSparkAddressLabelRef)
        ?.takeIf { it.isNotBlank() }

private fun sparkPaymentTransactionLabel(
    payment: SparkPayment,
    labels: Map<String, String>,
): String? =
    labels[sparkPaymentLabelKey(payment)] ?: labels[payment.id]

private fun sparkPaymentLabel(
    payment: SparkPayment,
    addressLabels: Map<String, String>,
    transactionLabels: Map<String, String>,
): String? =
    sparkPaymentLabelRef(payment)?.let { addressLabels[it] } ?: sparkPaymentTransactionLabel(payment, transactionLabels)

/**
 * Payment label with on-chain fallback for claimed deposits. Deposit-type payments carry no
 * invoice recipient, so also try the on-chain txid (transaction labels) and the linked Layer 1
 * output address (address labels saved from the receive screen).
 */
private fun sparkPaymentLabelWithOnchainFallback(
    payment: SparkPayment,
    addressLabels: Map<String, String>,
    transactionLabels: Map<String, String>,
    layer1Transactions: List<TransactionDetails>,
): String? {
    sparkPaymentLabel(payment, addressLabels, transactionLabels)?.let { return it }
    if (sparkRailBadge(payment).rail != SparkRail.SWAP) return null
    val txid =
        payment.onchainTxid?.trim()?.takeIf { it.isNotBlank() }
            ?: sparkExtractLayer1Txid(payment)
            ?: return null
    transactionLabels[txid]?.let { return it }
    transactionLabels.entries.firstOrNull { it.key.equals(txid, ignoreCase = true) }?.value?.let { return it }
    val layer1Address =
        layer1Transactions.firstOrNull { it.txid.equals(txid, ignoreCase = true) }?.address
            ?.takeIf { it.isNotBlank() } ?: return null
    return normalizeSparkAddressLabelRef(layer1Address).takeIf { it.isNotBlank() }?.let { addressLabels[it] }
}

/**
 * Pending (unclaimed) deposit label. The SDK deposit carries no address, so resolve via the
 * stored txid→address mapping, the linked Layer 1 output address, then txid transaction labels.
 */
private fun sparkDepositLabel(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
    addressLabels: Map<String, String>,
    transactionLabels: Map<String, String>,
): String? {
    deposit.address?.let(::normalizeSparkAddressLabelRef)
        ?.takeIf { it.isNotBlank() }?.let { addressLabels[it] }?.let { return it }
    layer1Transaction?.address?.takeIf { it.isNotBlank() }
        ?.let(::normalizeSparkAddressLabelRef)
        ?.takeIf { it.isNotBlank() }?.let { addressLabels[it] }?.let { return it }
    transactionLabels[deposit.txid]?.let { return it }
    return transactionLabels.entries.firstOrNull { it.key.equals(deposit.txid, ignoreCase = true) }?.value
}

/** Preferred address key when saving a label for a pending deposit (receive-screen round-trip). */
private fun sparkDepositLabelRef(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
): String? =
    deposit.address?.let(::normalizeSparkAddressLabelRef)?.takeIf { it.isNotBlank() }
        ?: layer1Transaction?.address?.takeIf { it.isNotBlank() }
            ?.let(::normalizeSparkAddressLabelRef)?.takeIf { it.isNotBlank() }

private enum class SparkRail {
    SPARK,
    LIGHTNING,
    SWAP,
}

private data class SparkRailBadge(
    val color: Color,
    val rail: SparkRail,
)

private fun sparkDepositConfirmationProgress(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
): String {
    if (deposit.isMature) return "3/3"
    val transaction = layer1Transaction ?: return "0/3"
    if (!transaction.isConfirmed) return "0/3"
    val confirmationHeight = transaction.confirmationTime?.height ?: return "1/3"
    val blockHeight = layer1BlockHeight ?: return "1/3"
    val confirmations =
        (blockHeight.toLong() - confirmationHeight.toLong() + 1L)
            .coerceAtLeast(1L)
            .coerceAtMost(3L)
            .toInt()
    return "$confirmations/3"
}

private fun sparkDepositHasRequiredConfirmations(
    deposit: SparkUnclaimedDeposit,
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
): Boolean {
    if (deposit.isMature) return true
    val transaction = layer1Transaction ?: return false
    if (!transaction.isConfirmed) return false
    val confirmationHeight = transaction.confirmationTime?.height ?: return false
    val blockHeight = layer1BlockHeight ?: return false
    return blockHeight.toLong() - confirmationHeight.toLong() + 1L >= 3L
}

private fun sparkRailBadge(payment: SparkPayment): SparkRailBadge {
    val method = "${payment.method} ${payment.methodDetails} ${payment.type}".lowercase(Locale.US)
    return when {
        method.contains("bolt") || method.contains("lightning") || method.contains("lnurl") ->
            SparkRailBadge(LightningYellow, SparkRail.LIGHTNING)
        method.contains("bitcoin") ||
            method.contains("onchain") ||
            method.contains("on-chain") ||
            method.contains("on_chain") ||
            method.contains("deposit") ||
            method.contains("withdraw") ->
            SparkRailBadge(BitcoinOrange, SparkRail.SWAP)
        method.contains("spark") ->
            SparkRailBadge(SparkPurple, SparkRail.SPARK)
        else -> SparkRailBadge(SparkPurple, SparkRail.SPARK)
    }
}

/** Full debited total for sends: invoice amount + fee. Receives show amount only. */
private fun sparkPaymentTotalSats(payment: SparkPayment): Long {
    val amount = payment.amountSats.coerceAtLeast(0L)
    if (payment.type.equals("RECEIVE", ignoreCase = true)) return amount
    return amount + payment.feeSats.coerceAtLeast(0L)
}

/** True for center Swap control L1↔Spark pegs (not peer Bitcoin/LN/Spark sends). */
private fun sparkPaymentIsCenterSwap(
    payment: SparkPayment,
    sparkTransactionSources: Map<String, String>,
    layer1Transactions: List<TransactionDetails>,
): Boolean {
    val stored = sparkTransactionSources[payment.id]
        ?: sparkTransactionSources.entries
            .firstOrNull { it.key.equals(payment.id, ignoreCase = true) }
            ?.value
    if (BitcoinTxSource.isSwapHistory(stored)) return true
    val linked = sparkResolveLayer1Transaction(payment, layer1Transactions)
    return linked?.isSwapHistory == true
}

@Composable
private fun sparkPaymentDisplayTitle(
    isReceive: Boolean,
    isCenterSwap: Boolean,
): String =
    // Center Swap control L1 pegs → Swap; peer pays → Received/Sent. LN never uses this path.
    when {
        isCenterSwap -> stringResource(R.string.loc_85a12a5f)
        isReceive -> stringResource(R.string.loc_301a5b91)
        else -> stringResource(R.string.loc_1af68597)
    }

private fun sparkRailLabelRes(rail: SparkRail): Int =
    when (rail) {
        SparkRail.SPARK -> R.string.loc_85f5955f
        SparkRail.LIGHTNING -> R.string.loc_03b82433
        SparkRail.SWAP -> R.string.loc_197cebf2
    }

private fun sparkExtractLayer1Txid(payment: SparkPayment): String? {
    if (sparkRailBadge(payment).rail != SparkRail.SWAP) return null

    payment.onchainTxid
        ?.trim()
        ?.takeIf { it.length == 64 && it.all { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' } }
        ?.let { return it.lowercase(Locale.US) }

    val txidPattern = Regex("""\b[0-9a-fA-F]{64}\b""")
    val candidates =
        buildList {
            add(payment.methodDetails)
            payment.recipient?.let(::add)
            add(payment.id)
        }

    return candidates
        .asSequence()
        .flatMap { candidate -> txidPattern.findAll(candidate).map { it.value.lowercase(Locale.US) } }
        .firstOrNull()
}

private fun sparkResolveLayer1Transaction(
    payment: SparkPayment,
    layer1Transactions: List<TransactionDetails>,
): TransactionDetails? {
    if (sparkRailBadge(payment).rail != SparkRail.SWAP) return null

    sparkExtractLayer1Txid(payment)?.let { txid ->
        layer1Transactions.firstOrNull { it.txid.equals(txid, ignoreCase = true) }?.let { return it }
    }

    val recipient = payment.recipient?.trim()?.takeIf { it.isNotBlank() }
    val paymentAmount = payment.amountSats.takeIf { it > 0L } ?: return null
    val paymentTimestampSeconds = sparkTimestampSeconds(payment.timestamp)
    val directionalMatches =
        layer1Transactions.filter { transaction ->
            // Spark withdrawals to one of our own Layer 1 addresses show up as receives on L1,
            // so use direction only as a fallback signal, not a hard requirement.
            val isSparkReceive = payment.type.equals("RECEIVE", ignoreCase = true)
            if (isSparkReceive) transaction.amountSats > 0 else transaction.amountSats < 0
        }

    val exactAnyDirectionAddressAndAmountMatches =
        layer1Transactions.filter { transaction ->
            recipient != null &&
                transaction.address.equals(recipient, ignoreCase = true) &&
                sparkTransactionMatchesAmount(transaction, paymentAmount)
        }
    if (exactAnyDirectionAddressAndAmountMatches.size == 1) {
        return exactAnyDirectionAddressAndAmountMatches.first()
    }
    val timeBoundAnyDirectionAddressAndAmountMatches =
        exactAnyDirectionAddressAndAmountMatches.filter { transaction ->
            sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds) <= 86_400L
        }
    if (timeBoundAnyDirectionAddressAndAmountMatches.isNotEmpty()) {
        return timeBoundAnyDirectionAddressAndAmountMatches.minByOrNull { transaction ->
            sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds)
        }
    }

    val exactAddressAndAmountMatches =
        directionalMatches.filter { transaction ->
            recipient != null &&
                transaction.address.equals(recipient, ignoreCase = true) &&
                sparkTransactionMatchesAmount(transaction, paymentAmount)
        }
    if (exactAddressAndAmountMatches.size == 1) return exactAddressAndAmountMatches.first()

    val timeBoundAddressAndAmountMatches =
        exactAddressAndAmountMatches.filter { transaction ->
            sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds) <= 86_400L
        }
    if (timeBoundAddressAndAmountMatches.isNotEmpty()) {
        return timeBoundAddressAndAmountMatches.minByOrNull { transaction ->
            sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds)
        }
    }

    val exactAddressMatches =
        layer1Transactions.filter { transaction ->
            recipient != null && transaction.address.equals(recipient, ignoreCase = true)
        }
    if (exactAddressMatches.size == 1) return exactAddressMatches.first()

    val amountAndTimeMatches =
        directionalMatches.filter { transaction ->
            sparkTransactionMatchesAmount(transaction, paymentAmount) &&
                sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds) <= 7_200L
        }
    if (amountAndTimeMatches.size == 1) return amountAndTimeMatches.first()

    val amountMatches =
        directionalMatches.filter { transaction ->
            sparkTransactionMatchesAmount(transaction, paymentAmount)
        }
    if (amountMatches.size == 1) return amountMatches.first()
    if (amountMatches.isNotEmpty()) {
        return amountMatches.minByOrNull { transaction ->
            sparkTimestampDistanceSeconds(transaction.timestamp, paymentTimestampSeconds)
        }
    }

    return null
}

private fun sparkTransactionMatchesAmount(
    transaction: TransactionDetails,
    paymentAmount: Long,
): Boolean {
    val transactionAddressAmount = transaction.addressAmount?.toLong()
    return transactionAddressAmount == paymentAmount || kotlin.math.abs(transaction.amountSats) == paymentAmount
}

private fun sparkTimestampSeconds(timestamp: Long?): Long? {
    val value = timestamp ?: return null
    if (value <= 0L) return null
    return if (value > 10_000_000_000L) value / 1000L else value
}

private fun sparkTimestampDistanceSeconds(
    transactionTimestamp: Long?,
    paymentTimestampSeconds: Long?,
): Long {
    val txTimestampSeconds = sparkTimestampSeconds(transactionTimestamp)
    return if (txTimestampSeconds == null || paymentTimestampSeconds == null) {
        Long.MAX_VALUE
    } else {
        kotlin.math.abs(txTimestampSeconds - paymentTimestampSeconds)
    }
}

private fun sparkOpenBitcoinExplorer(
    context: Context,
    mempoolUrl: String,
    txid: String,
    onTorBrowserMissing: () -> Unit,
) {
    val url = "$mempoolUrl/tx/$txid"
    val isOnionAddress =
        try {
            java.net.URI(mempoolUrl).host?.endsWith(".onion") == true
        } catch (_: Exception) {
            mempoolUrl.endsWith(".onion")
        }

    if (isOnionAddress) {
        val intent =
            Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                setPackage("org.torproject.torbrowser")
            }
        try {
            context.startActivityWithTaskFallback(intent)
        } catch (_: Exception) {
            onTorBrowserMissing()
        }
    } else {
        context.startActivityWithTaskFallback(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

private fun formatSparkTimestamp(
    timestamp: Long,
    dateFormat: String,
): String {
    if (timestamp <= 0L) return ""
    return formatBalanceTimestamp(timestamp, dateFormat)
}

private fun sparkNormalizeTimestampMillis(timestamp: Long): Long {
    return if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
}

private fun formatSparkFullTimestamp(
    timestamp: Long,
    dateFormat: String,
): String {
    if (timestamp <= 0L) return ""
    return formatFullTimestamp(timestamp, dateFormat)
}

@Composable
private fun SparkTorBrowserErrorDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideLocalizedResources {
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
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.loc_9c1c9375),
                        tint = AccentRed,
                        modifier = Modifier.size(48.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.loc_3a15e5cd),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.loc_71c1fcab),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )

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
                        Text(stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkQuickReceiveDialog(
    receiveState: SparkReceiveState,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
) {
    val ready = receiveState as? SparkReceiveState.Ready
    val paid = receiveState as? SparkReceiveState.Paid
    val err = receiveState as? SparkReceiveState.Error
    QuickReceiveDialog(
        payload = ready?.paymentRequest ?: paid?.paymentRequest,
        onDismiss = onDismiss,
        accentColor = SparkPurple,
        isLoading =
            receiveState is SparkReceiveState.Loading ||
                receiveState is SparkReceiveState.Idle,
        errorMessage = err?.message,
        hidePayload = privacyMode,
        hiddenPlaceholder = SPARK_HIDDEN_AMOUNT,
    )
}
