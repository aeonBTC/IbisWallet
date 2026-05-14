package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
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
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.startActivityWithTaskFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
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
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrices: Map<String, Double> = emptyMap(),
    showHistoricalTxPrices: Boolean = false,
    onShowHistoricalTxPricesChange: (Boolean) -> Unit = {},
    privacyMode: Boolean,
    sparkTransactionLabels: Map<String, String> = emptyMap(),
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDenomination: () -> Unit,
    onQuickReceive: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
    onSaveSparkTransactionLabel: (String, String) -> Unit = { _, _ -> },
    onDeleteSparkTransactionLabel: (String) -> Unit = {},
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val showQrScanner = remember { mutableStateOf(false) }
    val showQuickReceive = remember { mutableStateOf(false) }
    val selectedSparkPayment = remember { mutableStateOf<SparkPayment?>(null) }
    val selectedSparkDeposit = remember { mutableStateOf<SparkUnclaimedDeposit?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSparkTransactions by remember { mutableStateOf(false) }
    var showLightningTransactions by remember { mutableStateOf(false) }
    var showSwapTransactions by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }
    val isPullRefreshing = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(sparkState.isSyncing) {
        if (!sparkState.isSyncing) {
            isPullRefreshing.value = false
        }
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
        remember(railFilteredPayments, railFilteredDeposits, sparkTransactionLabels, trimmedSearchQuery) {
            val paymentItems =
                if (trimmedSearchQuery.isBlank()) {
                    railFilteredPayments.map(SparkHistoryItem::Payment)
                } else {
                    val query = trimmedSearchQuery.lowercase(Locale.US)
                    railFilteredPayments.filter { payment ->
                        val label = sparkPaymentLabel(payment, sparkTransactionLabels).orEmpty()
                        val badge = sparkRailBadge(payment)
                        val date = formatSparkTimestamp(payment.timestamp)
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
                    railFilteredDeposits.map(SparkHistoryItem::Deposit)
                } else {
                    val query = trimmedSearchQuery.lowercase(Locale.US)
                    railFilteredDeposits.filter { deposit ->
                        listOf(
                            deposit.txid,
                            deposit.address.orEmpty(),
                            deposit.vout.toString(),
                            deposit.amountSats.toString(),
                            "Received",
                            "Bitcoin",
                            "On-chain",
                            if (deposit.isMature) "Ready to claim" else "Waiting for confirmations",
                            deposit.claimError.orEmpty(),
                        ).any { it.lowercase(Locale.US).contains(query) }
                    }.map(SparkHistoryItem::Deposit)
                }
            depositItems + paymentItems
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
        val paymentLabel = sparkPaymentLabel(payment, sparkTransactionLabels)
        val linkedLayer1Transaction = remember(payment, layer1Transactions) { sparkResolveLayer1Transaction(payment, layer1Transactions) }
        SparkPaymentDetailDialog(
            payment = payment,
            layer1Transaction = linkedLayer1Transaction,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            historicalBtcPrice = historicalBtcPrices[payment.id],
            privacyMode = privacyMode,
            label = paymentLabel,
            onSaveLabel = { label ->
                onSaveSparkTransactionLabel(payment.id, label)
                onSaveSparkTransactionLabel(sparkPaymentLabelKey(payment), label)
            },
            onDeleteLabel = paymentLabel?.let {
                {
                    onDeleteSparkTransactionLabel(payment.id)
                    onDeleteSparkTransactionLabel(sparkPaymentLabelKey(payment))
                }
            },
            onDismiss = { selectedSparkPayment.value = null },
        )
    }
    selectedSparkDeposit.value?.let { deposit ->
        SparkPendingDepositDetailDialog(
            deposit = deposit,
            layer1Transaction = layer1Transactions.firstOrNull { it.txid.equals(deposit.txid, ignoreCase = true) },
            layer1BlockHeight = layer1BlockHeight,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            historicalBtcPrice = historicalBtcPrices[deposit.txid],
            privacyMode = privacyMode,
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
                            val paymentLabel = sparkPaymentLabel(item.payment, sparkTransactionLabels)
                            SparkTransactionRow(
                                payment = item.payment,
                                useSats = useSats,
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
                                onClick = { selectedSparkPayment.value = item.payment },
                            )
                        }
                        is SparkHistoryItem.Deposit ->
                            SparkPendingDepositRow(
                                deposit = item.deposit,
                                layer1Timestamp =
                                    layer1Transactions.firstOrNull {
                                        it.txid.equals(item.deposit.txid, ignoreCase = true)
                                    }?.timestamp,
                                useSats = useSats,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                historicalBtcPrice =
                                    if (showHistoricalTxPrices) {
                                        historicalBtcPrices[item.deposit.txid]
                                    } else {
                                        null
                                    },
                                privacyMode = privacyMode,
                                onClick = { selectedSparkDeposit.value = item.deposit },
                            )
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
                Text(
                    text =
                        if (privacyMode) {
                            SPARK_HIDDEN_AMOUNT
                        } else if (useSats) {
                            "${formatAmount(sparkState.balanceSats.toULong(), true)} sats"
                        } else {
                            "\u20BF ${formatAmount(sparkState.balanceSats.toULong(), false)}"
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
                if (btcPrice != null && btcPrice > 0) {
                    val fiatValue = (sparkState.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text = if (privacyMode) SPARK_HIDDEN_AMOUNT else formatFiat(fiatValue, fiatCurrency),
                        style = MaterialTheme.typography.titleMedium,
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
        }
    }
}

private sealed interface SparkHistoryItem {
    val id: String

    data class Payment(val payment: SparkPayment) : SparkHistoryItem {
        override val id: String = payment.id
    }

    data class Deposit(val deposit: SparkUnclaimedDeposit) : SparkHistoryItem {
        override val id: String = "deposit:${deposit.txid}:${deposit.vout}"
    }
}

@Composable
private fun SparkPendingDepositRow(
    deposit: SparkUnclaimedDeposit,
    layer1Timestamp: Long?,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    onClick: () -> Unit,
) {
    val formattedTimestamp = (deposit.timestamp ?: layer1Timestamp)?.let(::formatSparkTimestamp).orEmpty()
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
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = stringResource(R.string.loc_301a5b91),
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.loc_301a5b91),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BitcoinOrange.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_197cebf2),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = BitcoinOrange,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text =
                        formattedTimestamp.ifBlank {
                            if (deposit.isMature) {
                                stringResource(R.string.spark_deposit_status_ready_to_claim)
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
                if (!deposit.isMature) {
                    SparkPendingBadge(text = stringResource(R.string.loc_1b684325))
                }
            }
        }
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
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val depositTxidCopyLabel = stringResource(R.string.spark_deposit_txid_copy_label)
    val depositAddressCopyLabel = stringResource(R.string.spark_deposit_address_copy_label)
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedAddress by remember { mutableStateOf(false) }
    val showTorBrowserError = remember { mutableStateOf(false) }
    val confirmationProgress = sparkDepositConfirmationProgress(deposit, layer1Transaction, layer1BlockHeight)
    val formattedTimestamp = (deposit.timestamp ?: layer1Transaction?.timestamp)?.let(::formatSparkFullTimestamp).orEmpty()
    val explorerTxid = deposit.txid.takeIf { mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank() }

    if (showTorBrowserError.value) {
        SparkTorBrowserErrorDialog(onDismiss = { showTorBrowserError.value = false })
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.spark_deposit_title_received_bitcoin),
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentGreen,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (privacyMode) {
                                    SPARK_HIDDEN_AMOUNT
                                } else {
                                    "+${formatAmount(deposit.amountSats.toULong(), useSats, includeUnit = true)}"
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                        )
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
                                    imageVector = if (deposit.isMature) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (deposit.isMature) AccentGreen else BitcoinOrange,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text =
                                        if (deposit.isMature) {
                                            stringResource(R.string.spark_deposit_status_ready_to_claim)
                                        } else {
                                            stringResource(
                                                R.string.spark_deposit_status_confirmations_format,
                                                confirmationProgress,
                                            )
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (deposit.isMature) AccentGreen else BitcoinOrange,
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
                                    if (deposit.isMature) {
                                        stringResource(R.string.spark_deposit_status_awaiting_claim)
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
                            text = stringResource(R.string.spark_deposit_note_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.spark_deposit_note_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        deposit.claimError?.let {
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
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    onClick: () -> Unit,
) {
    val isReceive = payment.type.equals("RECEIVE", ignoreCase = true)
    val isPending = !payment.status.equals("COMPLETE", ignoreCase = true) &&
        !payment.status.equals("SUCCEEDED", ignoreCase = true) &&
        !payment.status.equals("SUCCESS", ignoreCase = true) &&
        !payment.status.equals("COMPLETED", ignoreCase = true)
    val icon = if (isReceive) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade
    val iconTint = if (isReceive) AccentGreen else AccentRed
    val iconBackground = if (isReceive) AccentGreen.copy(alpha = 0.1f) else AccentRed.copy(alpha = 0.1f)
    val amountColor = if (isReceive) AccentGreen else AccentRed
    val formattedTimestamp = remember(payment.timestamp) { formatSparkTimestamp(payment.timestamp) }
    val displayLabel = label?.takeIf { it.isNotBlank() }
    val railBadge = sparkRailBadge(payment)

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
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
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
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            if (isReceive) {
                                stringResource(R.string.loc_301a5b91)
                            } else {
                                stringResource(R.string.loc_1af68597)
                            },
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
                        } else {
                            "${if (isReceive) "+" else "-"}${formatAmount(payment.amountSats.toULong(), useSats, includeUnit = true)}"
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    textAlign = TextAlign.End,
                )
                val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                if (!privacyMode && effectiveBtcPrice != null && effectiveBtcPrice > 0) {
                    SparkHistoricalFiatText(
                        text = formatFiat((payment.amountSats / 100_000_000.0) * effectiveBtcPrice, fiatCurrency),
                        isHistorical = historicalBtcPrice != null,
                    )
                }
                if (isPending) {
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
private fun SparkPendingBadge(text: String) {
    Box(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BitcoinOrange.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = BitcoinOrange,
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
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double?,
    privacyMode: Boolean,
    label: String?,
    onSaveLabel: (String) -> Unit,
    onDeleteLabel: (() -> Unit)?,
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
    val isConfirmed = payment.status.equals("COMPLETE", ignoreCase = true) ||
        payment.status.equals("SUCCEEDED", ignoreCase = true) ||
        payment.status.equals("SUCCESS", ignoreCase = true) ||
        payment.status.equals("COMPLETED", ignoreCase = true)
    val statusLabel =
        if (isConfirmed) {
            stringResource(R.string.loc_4ab75d7f)
        } else {
            payment.status.lowercase(Locale.US).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
        }
    val formattedTimestamp = formatSparkFullTimestamp(payment.timestamp)
    val recipient = payment.recipient?.takeIf { it.isNotBlank() }
    val feeColor = if (railBadge.rail == SparkRail.LIGHTNING) LightningYellow else BitcoinOrange
    val scrollState = rememberScrollState()
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
                                    if (isReceive) {
                                        stringResource(R.string.loc_301a5b91)
                                    } else {
                                        stringResource(R.string.loc_1af68597)
                                    },
                                    stringResource(railBadge.labelRes),
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            color = accentColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (privacyMode) {
                                    SPARK_HIDDEN_AMOUNT
                                } else {
                                    "${if (isReceive) "+" else "-"}${formatAmount(payment.amountSats.toULong(), useSats, includeUnit = true)}"
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            SparkHistoricalFiatText(
                                text = formatFiat((payment.amountSats / 100_000_000.0) * btcPrice, fiatCurrency),
                                isHistorical = false,
                                large = true,
                            )
                        }
                        if (historicalBtcPrice != null && historicalBtcPrice > 0 && !privacyMode) {
                            SparkHistoricalFiatText(
                                text =
                                    formatFiat(
                                        (payment.amountSats / 100_000_000.0) * historicalBtcPrice,
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
                                        imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (isConfirmed) AccentGreen else BitcoinOrange,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
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
                                        "${if (isReceive) "+" else "-"}${formatAmount(payment.amountSats.toULong(), useSats, includeUnit = true)}"
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
) {
    val rowAlignment = if (amountText == null) Alignment.CenterVertically else Alignment.Top
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = rowAlignment,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sparkTruncateDetailValue(value),
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
                contentDescription = stringResource(railBadge.labelRes),
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

private fun sparkPaymentLabelKey(payment: SparkPayment): String =
    listOf(
        payment.type,
        payment.status,
        payment.amountSats.toString(),
        payment.timestamp.toString(),
        payment.method,
    ).joinToString(separator = "|")

private fun sparkPaymentLabel(
    payment: SparkPayment,
    labels: Map<String, String>,
): String? =
    labels[sparkPaymentLabelKey(payment)] ?: labels[payment.id]

private enum class SparkRail {
    SPARK,
    LIGHTNING,
    SWAP,
}

private data class SparkRailBadge(
    val labelRes: Int,
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

private fun sparkRailBadge(payment: SparkPayment): SparkRailBadge {
    val method = "${payment.method} ${payment.methodDetails} ${payment.type}".lowercase(Locale.US)
    return when {
        method.contains("bolt") || method.contains("lightning") || method.contains("lnurl") ->
            SparkRailBadge(R.string.loc_03b82433, LightningYellow, SparkRail.LIGHTNING)
        method.contains("bitcoin") ||
            method.contains("onchain") ||
            method.contains("on-chain") ||
            method.contains("on_chain") ||
            method.contains("deposit") ||
            method.contains("withdraw") ->
            SparkRailBadge(R.string.loc_197cebf2, BitcoinOrange, SparkRail.SWAP)
        method.contains("spark") ->
            SparkRailBadge(R.string.loc_85f5955f, SparkPurple, SparkRail.SPARK)
        else -> SparkRailBadge(R.string.loc_85f5955f, SparkPurple, SparkRail.SPARK)
    }
}

private fun sparkExtractLayer1Txid(payment: SparkPayment): String? {
    if (sparkRailBadge(payment).rail != SparkRail.SWAP) return null

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

private fun formatSparkTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val millis = if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
    return sparkDateTimeFormatter.get()?.format(Date(millis)).orEmpty()
}

private val sparkDateTimeFormatter: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    }

private fun formatSparkFullTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val millis = if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L
    return formatFullTimestamp(millis)
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
    var qrBitmap by remember(ready?.paymentRequest, privacyMode) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(ready?.paymentRequest, privacyMode) {
        qrBitmap =
            ready?.paymentRequest
                ?.takeUnless { privacyMode }
                ?.let { content ->
                    withContext(Dispatchers.Default) {
                        generateQrBitmap(content)
                    }
                }
    }

    Dialog(onDismissRequest = onDismiss) {
        ProvideLocalizedResources {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (receiveState) {
                    SparkReceiveState.Loading -> CircularProgressIndicator(color = SparkPurple)
                    is SparkReceiveState.Error -> Text(receiveState.message, color = ErrorRed)
                    is SparkReceiveState.Ready -> {
                        qrBitmap?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.loc_6e2afb3f),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (privacyMode) SPARK_HIDDEN_AMOUNT else receiveState.paymentRequest,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SparkReceiveState.Idle ->
                        Text(
                            text = stringResource(R.string.spark_preparing_address),
                            color = TextSecondary,
                        )
                }
            }
            }
        }
    }
}
