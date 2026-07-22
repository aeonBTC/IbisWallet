package github.aeonbtc.ibiswallet.ui.screens

import kotlinx.coroutines.withTimeoutOrNull
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentStatus
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.components.ReceiveActionButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.nfc.NfcShareUiState
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.MainActivity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.lightning.LightningNodeConnectionUriParser
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestPhase
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionTestResult
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import github.aeonbtc.ibiswallet.data.model.LightningNodeChannel
import github.aeonbtc.ibiswallet.data.model.LightningNodePayment
import github.aeonbtc.ibiswallet.data.model.LightningNodePaymentDirection
import github.aeonbtc.ibiswallet.data.model.LightningNodeReceiveState
import github.aeonbtc.ibiswallet.data.model.LightningNodeSendState
import github.aeonbtc.ibiswallet.data.model.LightningNodeWalletState
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.ui.components.AvailableBalanceMaxRow
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.StatusBadge
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.LightningKind
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.isLightningAddressPayment
import github.aeonbtc.ibiswallet.util.layer2RecipientValidationError
import github.aeonbtc.ibiswallet.util.lightningNodeRecipientPlaceholder
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import github.aeonbtc.ibiswallet.viewmodel.SendScreenDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.DialogProperties

private const val LN_HIDDEN = "****"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightningNodeBalanceScreen(
    state: LightningNodeWalletState,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrices: Map<String, Double> = emptyMap(),
    showHistoricalTxPrices: Boolean = false,
    onShowHistoricalTxPricesChange: (Boolean) -> Unit = {},
    privacyMode: Boolean,
    dateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDenomination: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onQuickReceive: () -> Unit = {},
    onScanQrResult: (String) -> Unit = {},
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val context = LocalContext.current
    val accent = LightningYellow

    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(state.isSyncing) {
        if (!state.isSyncing) isPullRefreshing = false
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }
    var showQrScanner by remember { mutableStateOf(false) }
    var selectedPayment by remember { mutableStateOf<LightningNodePayment?>(null) }

    val filteredPayments =
        remember(state.payments, searchQuery.trim()) {
            val q = searchQuery.trim()
            if (q.isBlank()) {
                state.payments
            } else {
                state.payments.filter { p ->
                    listOfNotNull(
                        p.id,
                        p.memo,
                        p.paymentHash,
                        p.destination,
                        p.destinationAlias,
                        p.failureReason,
                        p.amountSats.toString(),
                        p.status.name,
                    ).any { it.contains(q, ignoreCase = true) }
                }
            }
        }
    val totalPaymentCount = filteredPayments.size
    val visiblePayments = remember(filteredPayments, displayLimit) {
        filteredPayments.take(displayLimit)
    }
    // Progressive reveal matches L1 / Liquid / Spark (25 → 100 → all loaded).
    LaunchedEffect(searchQuery, state.payments.size) {
        displayLimit = 25
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = {
                showQrScanner = false
                onScanQrResult(it)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    selectedPayment?.let { payment ->
        LightningNodePaymentDetailDialog(
            payment = payment,
            useSats = useSats,
            privacyMode = privacyMode,
            btcPrice = btcPrice,
            // Details always show historical line when available (same as BalanceScreen/Spark).
            historicalBtcPrice =
                historicalBtcPrices[payment.id]
                    ?: payment.paymentHash?.let { historicalBtcPrices[it] },
            fiatCurrency = fiatCurrency,
            dateFormat = dateFormat,
            onDismiss = { selectedPayment = null },
        )
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
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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
                                    tint = if (privacyMode) accent else TextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            val syncEnabled = state.isInitialized && !state.isSyncing
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
                                if (state.isSyncing || state.isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = accent,
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    if (privacyMode) {
                                        LN_HIDDEN
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
                                val fiatValue = (state.balanceSats.toDouble() / 100_000_000.0) * btcPrice
                                Text(
                                    text =
                                        if (privacyMode) {
                                            LN_HIDDEN
                                        } else {
                                            formatFiat(fiatValue, fiatCurrency)
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary,
                                )
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
                                    .clickable(enabled = state.isConnected) { onQuickReceive() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = stringResource(R.string.loc_a397da3c),
                                tint = accent,
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
                                    .clickable(enabled = state.isConnected) { showQrScanner = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.loc_60129540),
                                tint = accent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            item {
                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
            }

            item {
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
                            LnHistoryFilterButton(
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
                            imageVector =
                                if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription =
                                if (isSearchActive) {
                                    stringResource(R.string.loc_dda0ea3a)
                                } else {
                                    stringResource(R.string.loc_b35cde91)
                                },
                            tint = if (isSearchActive) accent else TextSecondary,
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
                                focusedBorderColor = accent,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = accent,
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (visiblePayments.isEmpty()) {
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
                            Text(
                                text =
                                    if (searchQuery.isNotBlank()) {
                                        stringResource(R.string.loc_167ce23f)
                                    } else {
                                        stringResource(R.string.ln_node_no_payments)
                                    },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            } else {
                items(visiblePayments, key = { it.id + it.timestamp }) { payment ->
                    LightningPaymentRow(
                        payment = payment,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        btcPrice = btcPrice,
                        historicalBtcPrice =
                            if (showHistoricalTxPrices) {
                                historicalBtcPrices[payment.id]
                                    ?: historicalBtcPrices[payment.paymentHash.orEmpty()]
                            } else {
                                null
                            },
                        fiatCurrency = fiatCurrency,
                        dateFormat = dateFormat,
                        onClick = { selectedPayment = payment },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (visiblePayments.size < totalPaymentCount) {
                    item {
                        val remaining = totalPaymentCount - visiblePayments.size
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LightningPaymentRow(
    payment: LightningNodePayment,
    useSats: Boolean,
    privacyMode: Boolean,
    btcPrice: Double? = null,
    historicalBtcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    dateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    onClick: () -> Unit = {},
) {
    val isReceive = payment.direction == LightningNodePaymentDirection.INCOMING
    // History / detail: outgoing total = amount + fee.
    val absSats =
        if (isReceive) {
            payment.amountSats.coerceAtLeast(0L).toULong()
        } else {
            (
                payment.amountSats.coerceAtLeast(0L) +
                    payment.feeSats.coerceAtLeast(0L)
                ).toULong()
        }
    val formattedTimestamp =
        remember(payment.timestamp, dateFormat) {
            if (payment.timestamp > 0) {
                formatBalanceTimestamp(payment.timestamp, dateFormat)
            } else {
                ""
            }
        }
    val failed = payment.status == LightningNodePaymentStatus.FAILED
    val pending = payment.status == LightningNodePaymentStatus.PENDING
    val amountColor =
        when {
            failed -> TextSecondary
            isReceive -> AccentGreen
            else -> AccentRed
        }
    val iconTint = if (isReceive) AccentGreen else AccentRed

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
                        .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (isReceive) {
                            Icons.AutoMirrored.Filled.CallReceived
                        } else {
                            Icons.AutoMirrored.Filled.CallMade
                        },
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (isReceive) {
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
                payment.memo?.let { BitcoinUtils.sanitizeExternalLabel(it) }?.let { memo ->
                    Text(
                        text = memo,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                        color = AccentTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                        LN_HIDDEN
                    } else if (failed) {
                        formatAmount(absSats, useSats, includeUnit = true)
                    } else {
                        val sign = if (isReceive) "+" else "-"
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
                    val fiatValue = (absSats.toDouble() / 100_000_000.0) * effectiveBtcPrice
                    LnHistoricalFiatText(
                        text = formatFiat(fiatValue, fiatCurrency),
                        isHistorical = historicalBtcPrice != null && historicalBtcPrice > 0,
                    )
                }
                if (failed) {
                    StatusBadge(
                        label = stringResource(R.string.ln_node_status_failed),
                        color = ErrorRed,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (pending) {
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
private fun LnHistoryFilterButton(
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
private fun LnHistoricalFiatText(
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
private fun LightningNodePaymentDetailDialog(
    payment: LightningNodePayment,
    useSats: Boolean,
    privacyMode: Boolean,
    btcPrice: Double?,
    historicalBtcPrice: Double? = null,
    fiatCurrency: String,
    dateFormat: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isReceive = payment.direction == LightningNodePaymentDirection.INCOMING
    // Payment to recipient (excludes routing fee).
    val recipientSats = payment.amountSats.coerceAtLeast(0L).toULong()
    // Outgoing total spent = amount + fee; receive is amount only.
    val totalSats =
        if (isReceive) {
            recipientSats
        } else {
            (payment.amountSats.coerceAtLeast(0L) + payment.feeSats.coerceAtLeast(0L)).toULong()
        }
    val amountColor =
        when (payment.status) {
            LightningNodePaymentStatus.FAILED -> TextSecondary
            else -> if (isReceive) AccentGreen else AccentRed
        }
    val statusColor =
        when (payment.status) {
            LightningNodePaymentStatus.SUCCEEDED -> AccentGreen
            LightningNodePaymentStatus.FAILED -> ErrorRed
            LightningNodePaymentStatus.PENDING -> BitcoinOrange
            LightningNodePaymentStatus.UNKNOWN -> TextSecondary
        }
    val statusLabel =
        when (payment.status) {
            LightningNodePaymentStatus.SUCCEEDED -> stringResource(R.string.ln_node_status_succeeded)
            LightningNodePaymentStatus.FAILED -> stringResource(R.string.ln_node_status_failed)
            LightningNodePaymentStatus.PENDING -> stringResource(R.string.loc_1b684325)
            LightningNodePaymentStatus.UNKNOWN -> stringResource(R.string.loc_1b684325)
        }
    val denominationLabel =
        if (useSats) stringResource(R.string.loc_9384ed0d) else "BTC"
    val amountText =
        if (privacyMode) {
            LN_HIDDEN
        } else if (payment.status == LightningNodePaymentStatus.FAILED) {
            "${formatAmount(totalSats, useSats)} $denominationLabel"
        } else {
            val sign = if (isReceive) "+" else "-"
            "$sign${formatAmount(totalSats, useSats)} $denominationLabel"
        }
    val formattedTimestamp =
        if (payment.timestamp > 0) {
            formatFullTimestamp(payment.timestamp, dateFormat)
        } else {
            ""
        }
    val invoiceOrOffer = payment.paymentRequest?.takeIf { it.isNotBlank() }
    val invoiceKindLabelRes =
        when {
            invoiceOrOffer == null -> null
            invoiceOrOffer.startsWith("lno", ignoreCase = true) -> R.string.ln_node_detail_offer
            invoiceOrOffer.contains('@') &&
                !invoiceOrOffer.startsWith("ln", ignoreCase = true)
            -> R.string.ln_node_detail_ln_address
            else -> R.string.ln_node_detail_invoice
        }
    val invoiceCopyCdRes =
        when (invoiceKindLabelRes) {
            R.string.ln_node_detail_offer -> R.string.ln_node_copy_offer
            else -> R.string.ln_node_copy_invoice
        }
    // Recipient = node alias (preferred) or pubkey — not the invoice string.
    val recipientNode =
        payment.destinationAlias?.takeIf { it.isNotBlank() }
            ?: payment.destination?.takeIf { it.isNotBlank() }
    val recipientCopyValue =
        payment.destination?.takeIf { it.isNotBlank() } ?: recipientNode
    var showCopiedAmount by remember { mutableStateOf(false) }
    var showCopiedHash by remember { mutableStateOf(false) }
    var showCopiedInvoice by remember { mutableStateOf(false) }
    var showCopiedRecipient by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(showCopiedAmount) {
        if (showCopiedAmount) {
            kotlinx.coroutines.delay(3000)
            showCopiedAmount = false
        }
    }
    LaunchedEffect(showCopiedHash) {
        if (showCopiedHash) {
            kotlinx.coroutines.delay(3000)
            showCopiedHash = false
        }
    }
    LaunchedEffect(showCopiedInvoice) {
        if (showCopiedInvoice) {
            kotlinx.coroutines.delay(3000)
            showCopiedInvoice = false
        }
    }
    LaunchedEffect(showCopiedRecipient) {
        if (showCopiedRecipient) {
            kotlinx.coroutines.delay(3000)
            showCopiedRecipient = false
        }
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
                                if (isReceive) {
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
                                stringResource(
                                    R.string.spark_payment_direction_with_rail_format,
                                    if (isReceive) {
                                        stringResource(R.string.loc_301a5b91)
                                    } else {
                                        stringResource(R.string.loc_1af68597)
                                    },
                                    stringResource(R.string.ln_node_pill_label),
                                ),
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
                                color = LightningYellow,
                            )
                        }
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            LnHistoricalFiatText(
                                text =
                                    formatFiat(
                                        (totalSats.toDouble() / 100_000_000.0) * btcPrice,
                                        fiatCurrency,
                                    ),
                                isHistorical = false,
                                large = true,
                            )
                        }
                        if (historicalBtcPrice != null && historicalBtcPrice > 0 && !privacyMode) {
                            LnHistoricalFiatText(
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
                        Text(
                            text = stringResource(R.string.loc_7cac602a),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector =
                                        when (payment.status) {
                                            LightningNodePaymentStatus.SUCCEEDED ->
                                                Icons.Default.CheckCircle
                                            LightningNodePaymentStatus.FAILED ->
                                                Icons.Default.Close
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

                        payment.failureReason
                            ?.takeIf {
                                it.isNotBlank() &&
                                    payment.status == LightningNodePaymentStatus.FAILED
                            }
                            ?.let { reason ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Column {
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

                        // Receive with no node: amount lives under Invoice. Otherwise under Recipient.
                        val amountUnderInvoice =
                            isReceive &&
                                recipientNode == null &&
                                recipientSats > 0uL
                        val showRecipientSection =
                            recipientNode != null ||
                                (!isReceive && recipientSats > 0uL)
                        val recipientAmountText =
                            if (privacyMode) {
                                LN_HIDDEN
                            } else if (payment.status == LightningNodePaymentStatus.FAILED) {
                                "${formatAmount(recipientSats, useSats)} $denominationLabel"
                            } else {
                                val sign = if (isReceive) "+" else "-"
                                "$sign${formatAmount(recipientSats, useSats)} $denominationLabel"
                            }

                        if (invoiceOrOffer != null && invoiceKindLabelRes != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text(
                                    text = stringResource(invoiceKindLabelRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    // Top-align so a tall copy chip doesn't pad above/below the invoice line.
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = truncateLnDetailField(invoiceOrOffer),
                                            style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                                                ),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                        )
                                        if (amountUnderInvoice) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = recipientAmountText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = amountColor,
                                            )
                                        }
                                    }
                                    LnPaymentCopyChip(
                                        contentDescription = stringResource(invoiceCopyCdRes),
                                        onClick = {
                                            SecureClipboard.copyAndScheduleClear(
                                                context,
                                                invoiceOrOffer,
                                            )
                                            showCopiedInvoice = true
                                        },
                                    )
                                }
                                if (showCopiedInvoice) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LightningYellow,
                                    )
                                }
                            }
                        }

                        if (showRecipientSection) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        recipientNode?.let { nodeLabel ->
                                            Text(
                                                text =
                                                    if (payment.destinationAlias.isNullOrBlank()) {
                                                        truncateLnDetailField(nodeLabel)
                                                    } else {
                                                        nodeLabel
                                                    },
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily =
                                                            if (payment.destinationAlias.isNullOrBlank()) {
                                                                FontFamily.Monospace
                                                            } else {
                                                                FontFamily.Default
                                                            },
                                                    ),
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                        // Amount to recipient only (excludes routing fee).
                                        if (recipientSats > 0uL && !amountUnderInvoice) {
                                            if (recipientNode != null) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                            }
                                            Text(
                                                text = recipientAmountText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = amountColor,
                                            )
                                        }
                                    }
                                    recipientCopyValue?.let { copyValue ->
                                        LnPaymentCopyChip(
                                            contentDescription =
                                                stringResource(R.string.ln_node_copy_recipient),
                                            onClick = {
                                                SecureClipboard.copyAndScheduleClear(
                                                    context,
                                                    copyValue,
                                                )
                                                showCopiedRecipient = true
                                            },
                                        )
                                    }
                                }
                                if (showCopiedRecipient) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LightningYellow,
                                    )
                                }
                            }
                        }

                        payment.memo?.let { BitcoinUtils.sanitizeExternalLabel(it) }?.let { memo ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.ln_node_memo),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = memo,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        payment.paymentHash?.takeIf { it.isNotBlank() }?.let { hash ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = TextSecondary.copy(alpha = 0.1f),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.ln_node_payment_hash),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = hash,
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    LnPaymentCopyChip(
                                        contentDescription =
                                            stringResource(R.string.ln_node_copy_payment_hash),
                                        onClick = {
                                            SecureClipboard.copyAndScheduleClear(context, hash)
                                            showCopiedHash = true
                                        },
                                    )
                                }
                                if (showCopiedHash) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LightningYellow,
                                    )
                                }
                            }
                        }

                        if (!isReceive && payment.feeSats > 0) {
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
                                Text(
                                    text =
                                        if (privacyMode) {
                                            LN_HIDDEN
                                        } else {
                                            "-${formatAmount(payment.feeSats.toULong(), useSats)} $denominationLabel"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LightningYellow,
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
private fun LnPaymentCopyChip(
    contentDescription: String,
    onClick: () -> Unit,
) {
    // Match on-chain chip: slightly lifted so it doesn't force vertical padding around the value line.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val liftPx = 2.dp.roundToPx()
                    layout(placeable.width, (placeable.height - liftPx).coerceAtLeast(0)) {
                        placeable.placeRelative(0, -liftPx)
                    }
                }
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = contentDescription,
            tint = LightningYellow,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun truncateLnDetailField(
    value: String,
    keepStart: Int = 14,
    keepEnd: Int = 10,
): String {
    val singleLine = value.replace(Regex("\\s+"), "")
    if (singleLine.length <= keepStart + keepEnd + 1) return singleLine
    return "${singleLine.take(keepStart)}…${singleLine.takeLast(keepEnd)}"
}

@Composable
fun LightningNodeReceiveScreen(
    receiveState: LightningNodeReceiveState,
    isConnected: Boolean,
    isConnecting: Boolean = false,
    connectionTarget: String? = null,
    denomination: String,
    privacyMode: Boolean,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    lightningAddress: String = "",
    onSaveLightningAddress: (String) -> Boolean = { false },
    onClearLightningAddress: () -> Unit = {},
    onCreateInvoice: (amountSats: Long?, description: String?) -> Unit,
    onReset: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val copyToast = stringResource(R.string.ln_node_copied)
    val shareTitle = stringResource(R.string.receive_share_request_title)
    val shareNoApp = stringResource(R.string.receive_no_share_app)
    val addressSavedToast = stringResource(R.string.ln_node_lightning_address_saved)
    val addressInvalidToast = stringResource(R.string.ln_node_lightning_address_invalid)

    var receiveTab by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var addressQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    var addressDraft by remember(lightningAddress) { mutableStateOf(lightningAddress) }
    var editingAddress by remember(lightningAddress) {
        mutableStateOf(lightningAddress.isBlank())
    }
    val descBringIntoView = rememberBringIntoViewRequesterOnExpand(showDescription, "ln_node_receive_desc")
    val isAddressTab = receiveTab == 1

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

    val paid = receiveState as? LightningNodeReceiveState.Paid
    val ready = receiveState as? LightningNodeReceiveState.Ready
    val savedAddress = lightningAddress.trim()
    val invoicePayload =
        ready?.paymentRequest?.let { pr ->
            if (pr.startsWith("lightning:", ignoreCase = true)) {
                pr
            } else {
                "lightning:$pr"
            }
        }
    val addressSharePayload =
        savedAddress.takeIf { it.isNotBlank() }?.let { addr ->
            if (addr.startsWith("lightning:", ignoreCase = true)) {
                addr
            } else {
                "lightning:$addr"
            }
        }
    val activeSharePayload = if (isAddressTab) addressSharePayload else invoicePayload
    val activeEnlargedQr = if (isAddressTab) addressQrBitmap else qrBitmap

    LaunchedEffect(ready?.paymentRequest, privacyMode) {
        qrBitmap =
            if (privacyMode || ready?.paymentRequest.isNullOrBlank()) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    generateQrBitmap(ready!!.paymentRequest)
                }
            }
    }

    LaunchedEffect(savedAddress, privacyMode) {
        addressQrBitmap =
            if (privacyMode || savedAddress.isBlank()) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    generateQrBitmap(savedAddress)
                }
            }
    }

    val mainActivity = context as? MainActivity
    val nfcShareOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canBroadcast
    val hasNfcSharePayload = nfcAvailable && activeSharePayload != null && !privacyMode
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
    DisposableEffect(activeSharePayload, nfcAvailable, privacyMode) {
        if (nfcAvailable && activeSharePayload != null && !privacyMode) {
            NdefHostApduService.setNdefPayload(activeSharePayload)
        }
        onDispose {
            NdefHostApduService.setNdefPayload(null)
        }
    }

    fun copyInvoice() {
        ready?.paymentRequest?.let { pr ->
            SecureClipboard.copyAndScheduleClear(context, pr)
            Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareInvoice() {
        ready?.paymentRequest?.let { pr ->
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, pr)
                }
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, shareTitle))
            }.onFailure {
                Toast.makeText(context, shareNoApp, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyLightningAddress() {
        if (savedAddress.isBlank()) return
        SecureClipboard.copyAndScheduleClear(context, savedAddress)
        Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
    }

    fun shareLightningAddress() {
        if (savedAddress.isBlank()) return
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, savedAddress)
            }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, shareTitle))
        }.onFailure {
            Toast.makeText(context, shareNoApp, Toast.LENGTH_SHORT).show()
        }
    }

    if (showEnlargedQr && activeEnlargedQr != null) {
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
                            bitmap = activeEnlargedQr!!.asImageBitmap(),
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
        if (!isConnected && !isConnecting) {
            LnConnectionStatusBanner(
                isConnecting = false,
                nodeTarget = connectionTarget,
                onOpenConnectionSettings = onOpenConnectionSettings,
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
                Text(
                    text = stringResource(R.string.loc_869ffe29),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LnReceiveTab(
                        label = stringResource(R.string.ln_node_receive_tab_invoice),
                        selected = !isAddressTab,
                        modifier = Modifier.weight(1f),
                        onClick = { receiveTab = 0 },
                    )
                    LnReceiveTab(
                        label = stringResource(R.string.ln_node_receive_tab_address),
                        selected = isAddressTab,
                        modifier = Modifier.weight(1f),
                        onClick = { receiveTab = 1 },
                    )
                }
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
                            LightningYellow
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

                if (isAddressTab) {
                    LnLightningAddressReceiveContent(
                        savedAddress = savedAddress,
                        addressDraft = addressDraft,
                        editingAddress = editingAddress || savedAddress.isBlank(),
                        addressQrBitmap = addressQrBitmap,
                        privacyMode = privacyMode,
                        onDraftChange = { addressDraft = it },
                        onStartEdit = {
                            addressDraft = savedAddress
                            editingAddress = true
                        },
                        onSave = {
                            val ok = onSaveLightningAddress(addressDraft)
                            if (ok) {
                                editingAddress = addressDraft.trim().isBlank()
                                Toast.makeText(context, addressSavedToast, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, addressInvalidToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClear = {
                            onClearLightningAddress()
                            addressDraft = ""
                            editingAddress = true
                        },
                        onCopy = { copyLightningAddress() },
                        onShare = { shareLightningAddress() },
                        onEnlargeQr = { showEnlargedQr = true },
                    )
                } else {
                when {
                    receiveState is LightningNodeReceiveState.Generating -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = LightningYellow,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.loc_de610209),
                                color = TextSecondary,
                            )
                        }
                    }
                    receiveState is LightningNodeReceiveState.Paid -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_739b859d),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                paid?.amountSats?.takeIf { it > 0 }?.let { amount ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text =
                                            if (privacyMode) {
                                                LN_HIDDEN
                                            } else {
                                                formatAmount(
                                                    amount.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                )
                                            },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    receiveState is LightningNodeReceiveState.Ready -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_5fd82ed8),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier =
                                        Modifier
                                            .size(252.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .combinedClickable(
                                                enabled = qrBitmap != null,
                                                onClick = { showEnlargedQr = true },
                                                onLongClick = { copyInvoice() },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (qrBitmap != null && !privacyMode) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = stringResource(R.string.loc_416323aa),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Text(
                                            if (privacyMode) LN_HIDDEN else "…",
                                            color = TextSecondary,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text =
                                        if (privacyMode) {
                                            LN_HIDDEN
                                        } else {
                                            receiveState.paymentRequest
                                        },
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { copyInvoice() }
                                            .padding(horizontal = 4.dp),
                                )
                                if (receiveState.amountSats != null && receiveState.amountSats > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text =
                                            if (privacyMode) {
                                                LN_HIDDEN
                                            } else {
                                                formatAmount(
                                                    receiveState.amountSats.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                )
                                            },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LightningYellow,
                                    )
                                }
                                receiveState.description?.takeIf { it.isNotBlank() }?.let { memo ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(memo, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                ) {
                                    ReceiveActionButton(
                                        text = stringResource(R.string.loc_ed8814bc),
                                        icon = Icons.Default.ContentCopy,
                                        tint = LightningYellow,
                                        onClick = { copyInvoice() },
                                        enabled = true,
                                        iconSize = 17.dp,
                                    )
                                    ReceiveActionButton(
                                        text = stringResource(R.string.loc_53ae02a5),
                                        icon = Icons.Default.Refresh,
                                        tint = LightningYellow,
                                        onClick = {
                                            amountText = ""
                                            description = ""
                                            showDescription = false
                                            onReset()
                                        },
                                        enabled = true,
                                        iconSize = 20.dp,
                                    )
                                    ReceiveActionButton(
                                        text = stringResource(R.string.loc_2ec7b25e),
                                        icon = Icons.Default.Share,
                                        tint = LightningYellow,
                                        onClick = { shareInvoice() },
                                        enabled = true,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_c5f4423b),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
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
                                                            LightningYellow.copy(alpha = 0.15f)
                                                        } else {
                                                            DarkCard
                                                        },
                                                ),
                                            border =
                                                BorderStroke(
                                                    1.dp,
                                                    if (isUsdMode) LightningYellow else BorderColor,
                                                ),
                                        ) {
                                            Text(
                                                text = fiatCurrency,
                                                style = MaterialTheme.typography.labelMedium,
                                                color =
                                                    if (isUsdMode) LightningYellow else TextSecondary,
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            )
                                        }
                                    }
                                }

                                val conversionText =
                                    if (
                                        amountText.isNotEmpty() &&
                                        amountInSats != null &&
                                        amountInSats > 0 &&
                                        btcPrice != null &&
                                        btcPrice > 0
                                    ) {
                                        if (privacyMode) {
                                            "≈ ****"
                                        } else if (isUsdMode) {
                                            "≈ ${formatAmount(amountInSats.toULong(), useSats, includeUnit = true)}"
                                        } else {
                                            val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                                            "≈ ${formatFiat(usdValue, fiatCurrency)}"
                                        }
                                    } else {
                                        null
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
                                                    color = LightningYellow,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                if (useSats && !isUsdMode) {
                                                    KeyboardType.Number
                                                } else {
                                                    KeyboardType.Decimal
                                                },
                                        ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LightningYellow,
                                            unfocusedBorderColor = BorderColor,
                                            cursorColor = LightningYellow,
                                        ),
                                )

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showDescription = !showDescription }
                                            .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.ln_node_memo),
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 15.sp,
                                                lineHeight = 21.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    SquareToggle(
                                        checked = showDescription,
                                        onCheckedChange = { enabled ->
                                            showDescription = enabled
                                            if (!enabled) description = ""
                                        },
                                        checkedColor = LightningYellow,
                                    )
                                }

                                AnimatedVisibility(visible = showDescription) {
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { description = it },
                                        placeholder = {
                                            Text(stringResource(R.string.ln_node_memo))
                                        },
                                        singleLine = true,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .bringIntoViewRequester(descBringIntoView),
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = LightningYellow,
                                                unfocusedBorderColor = BorderColor,
                                                cursorColor = LightningYellow,
                                            ),
                                    )
                                }

                                if (receiveState is LightningNodeReceiveState.Error) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        receiveState.message,
                                        color = ErrorRed,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        if (!isAddressTab) {
            Spacer(modifier = Modifier.height(8.dp))

            when (receiveState) {
                is LightningNodeReceiveState.Ready,
                is LightningNodeReceiveState.Paid,
                -> {
                    Button(
                        onClick = {
                            amountText = ""
                            description = ""
                            showDescription = false
                            onReset()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LightningYellow,
                                contentColor = TextPrimary,
                            ),
                    ) {
                        Text(
                            stringResource(R.string.ln_node_new),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                is LightningNodeReceiveState.Generating -> Unit
                else -> {
                    Button(
                        onClick = {
                            val amount = amountInSats?.takeIf { it > 0 }
                            val memo =
                                description.trim().takeIf { showDescription && it.isNotBlank() }
                            onCreateInvoice(amount, memo)
                        },
                        enabled = isConnected && !isConnecting,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LightningYellow,
                                contentColor = TextPrimary,
                                disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                                disabledContentColor = TextPrimary.copy(alpha = 0.5f),
                            ),
                    ) {
                        Text(
                            stringResource(R.string.loc_c5f4423b),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LnReceiveTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val inactiveTint = LightningYellow.copy(alpha = 0.12f)
    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) LightningYellow else inactiveTint)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LnLightningAddressReceiveContent(
    savedAddress: String,
    addressDraft: String,
    editingAddress: Boolean,
    addressQrBitmap: Bitmap?,
    privacyMode: Boolean,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEnlargeQr: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.ln_node_lightning_address_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ln_node_lightning_address_help),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (editingAddress || savedAddress.isBlank()) {
                OutlinedTextField(
                    value = addressDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.ln_node_lightning_address_hint))
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LightningYellow,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LightningYellow,
                        ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSave,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LightningYellow,
                            contentColor = TextPrimary,
                        ),
                ) {
                    Text(stringResource(R.string.ln_node_lightning_address_save))
                }
                if (savedAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onClear,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    ) {
                        Text(stringResource(R.string.ln_node_lightning_address_clear))
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(252.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .combinedClickable(
                                enabled = addressQrBitmap != null,
                                onClick = onEnlargeQr,
                                onLongClick = onCopy,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (addressQrBitmap != null && !privacyMode) {
                        Image(
                            bitmap = addressQrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_416323aa),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            if (privacyMode) LN_HIDDEN else "…",
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (privacyMode) LN_HIDDEN else savedAddress,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 4.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_ed8814bc),
                        icon = Icons.Default.ContentCopy,
                        tint = LightningYellow,
                        onClick = onCopy,
                        enabled = true,
                        iconSize = 17.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.ln_node_lightning_address_edit),
                        icon = Icons.Default.Settings,
                        tint = LightningYellow,
                        onClick = onStartEdit,
                        enabled = true,
                        iconSize = 18.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_2ec7b25e),
                        icon = Icons.Default.Share,
                        tint = LightningYellow,
                        onClick = onShare,
                        enabled = true,
                    )
                }
            }
        }
    }
}

@Composable
fun LightningNodeSendScreen(
    sendState: LightningNodeSendState,
    sendDraft: SendScreenDraft,
    isConnected: Boolean,
    isConnecting: Boolean = false,
    connectionType: LightningNodeConnectionType = LightningNodeConnectionType.NONE,
    connectionTarget: String? = null,
    availableBalanceSats: Long,
    denomination: String,
    privacyMode: Boolean,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    onUpdateDraft: (SendScreenDraft) -> Unit,
    onPrepareSend: (paymentRequest: String, amountSats: Long?) -> Unit,
    onSendPrepared: () -> Unit,
    onUpdateMaxFeePercent: (Double) -> Unit = {},
    onResetSend: () -> Unit,
    onToggleDenomination: () -> Unit = {},
    onOpenConnectionSettings: () -> Unit,
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var paymentRequest by remember { mutableStateOf(sendDraft.recipientAddress) }
    var amountInput by remember { mutableStateOf(sendDraft.amountInput) }
    var isUsdMode by remember { mutableStateOf(false) }
    var isMaxMode by remember { mutableStateOf(sendDraft.isMaxSend) }
    var showQrScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var prepareError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sendDraft) {
        if (sendDraft.recipientAddress.isNotBlank()) paymentRequest = sendDraft.recipientAddress
        if (sendDraft.amountInput.isNotBlank()) amountInput = sendDraft.amountInput
        isMaxMode = sendDraft.isMaxSend
    }

    LaunchedEffect(paymentRequest, amountInput, isMaxMode) {
        val updated =
            SendScreenDraft(
                recipientAddress = paymentRequest,
                amountInput = amountInput,
                isMaxSend = isMaxMode,
            )
        if (updated != sendDraft) onUpdateDraft(updated)
    }

    val amountSats =
        remember(amountInput, denomination, isUsdMode, btcPrice) {
            parseLnSendAmount(amountInput, useSats, isUsdMode, btcPrice)
        }
    val supportsRoutingFeeLimit = connectionType != LightningNodeConnectionType.NWC
    val parsedRecipient = parseSendRecipient(paymentRequest.trim())
    val recipientValidationError =
        remember(parsedRecipient, paymentRequest, connectionType) {
            if (paymentRequest.isBlank()) {
                null
            } else {
                layer2RecipientValidationError(
                    parsedRecipient,
                    Layer2Provider.LIGHTNING,
                    connectionType,
                )
            }
        }
    val recipientPlaceholder =
        remember(connectionType) {
            lightningNodeRecipientPlaceholder(connectionType)
        }
    val busy =
        sendState is LightningNodeSendState.Decoding ||
            sendState is LightningNodeSendState.Paying

    LaunchedEffect(amountInput, paymentRequest, isMaxMode) {
        prepareError = null
    }
    LaunchedEffect(sendState) {
        when (sendState) {
            is LightningNodeSendState.Decoding -> prepareError = null
            is LightningNodeSendState.Error -> {
                showConfirmDialog = false
                prepareError = sendState.message
            }
            is LightningNodeSendState.Preview -> prepareError = null
            else -> Unit
        }
    }

    val invoiceFixedAmountSats =
        (parsedRecipient as? ParsedSendRecipient.Lightning)?.amountSats
    val amountLocked = invoiceFixedAmountSats != null && invoiceFixedAmountSats > 0L
    val amountForSpendCheck: Long? =
        when {
            isMaxMode -> null
            amountSats != null && amountSats > 0L -> amountSats
            invoiceFixedAmountSats != null && invoiceFixedAmountSats > 0L -> invoiceFixedAmountSats
            else -> null
        }
    val hasUsableSendAmount =
        isMaxMode ||
            (amountSats != null && amountSats > 0L) ||
            (invoiceFixedAmountSats != null && invoiceFixedAmountSats > 0L)
    val overBalance =
        !isMaxMode &&
            amountForSpendCheck != null &&
            amountForSpendCheck > availableBalanceSats
    val amountFieldError =
        prepareError?.takeIf { it.isNotBlank() }
            ?: if (overBalance) {
                stringResource(
                    R.string.balance_insufficient_funds_available_format,
                    formatAmount(availableBalanceSats.toULong(), useSats, includeUnit = false),
                    if (useSats) "sats" else "BTC",
                )
            } else {
                null
            }
    val canReview =
        isConnected &&
            !isConnecting &&
            paymentRequest.isNotBlank() &&
            recipientValidationError == null &&
            !busy &&
            prepareError == null &&
            !overBalance &&
            hasUsableSendAmount

    fun applyParsedRecipient(parsed: ParsedSendRecipient) {
        when (parsed) {
            is ParsedSendRecipient.Lightning -> {
                paymentRequest = parsed.paymentInput
                amountInput =
                    parsed.amountSats?.let { formatLnSendAmountInput(it, useSats) }.orEmpty()
                isMaxMode = false
                isUsdMode = false
            }
            else -> Unit
        }
    }

    fun clearSuccessfulSendDraft() {
        paymentRequest = ""
        amountInput = ""
        isMaxMode = false
        isUsdMode = false
        onUpdateDraft(SendScreenDraft())
        onResetSend()
    }

    if (showConfirmDialog) {
        LnSendConfirmationDialog(
            sendState = sendState,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            supportsRoutingFeeLimit = supportsRoutingFeeLimit,
            onConfirm = onSendPrepared,
            onMaxFeePercentChange = onUpdateMaxFeePercent,
            onDismiss = {
                showConfirmDialog = false
                if (sendState is LightningNodeSendState.Paid) {
                    clearSuccessfulSendDraft()
                } else if (
                    sendState !is LightningNodeSendState.Paying &&
                    sendState !is LightningNodeSendState.Decoding
                ) {
                    onResetSend()
                }
            },
            onDone = {
                showConfirmDialog = false
                clearSuccessfulSendDraft()
            },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                val parsed = parseSendRecipient(code.trim())
                val error =
                    layer2RecipientValidationError(
                        parsed,
                        Layer2Provider.LIGHTNING,
                        connectionType,
                    )
                if (error != null) {
                    scanError = error
                } else {
                    scanError = null
                    applyParsedRecipient(parsed)
                }
            },
            onDismiss = { showQrScanner = false },
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
        if (!isConnected && !isConnecting) {
            LnConnectionStatusBanner(
                isConnecting = false,
                nodeTarget = connectionTarget,
                onOpenConnectionSettings = onOpenConnectionSettings,
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
                    Text(
                        text = stringResource(R.string.ln_node_send_lightning),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
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
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = paymentRequest,
                    onValueChange = { input ->
                        val normalized = input.trim()
                        when (val parsed = parseSendRecipient(normalized)) {
                            is ParsedSendRecipient.Lightning -> applyParsedRecipient(parsed)
                            else -> {
                                paymentRequest = normalized
                                if (normalized.isEmpty()) amountInput = ""
                                isMaxMode = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = recipientPlaceholder,
                            color = TextSecondary.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showQrScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.loc_59b2cdc5),
                                tint = LightningYellow,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    supportingText =
                        recipientValidationError?.let {
                            {
                                Text(
                                    text = it,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                    isError = recipientValidationError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LightningYellow,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LightningYellow,
                            errorBorderColor = ErrorRed,
                        ),
                )
                if (parsedRecipient is ParsedSendRecipient.Lightning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LnRecipientModeBadgeChip(
                            label = lightningPaymentKindLabel(parsedRecipient),
                            color = LightningYellow,
                        )
                    }
                }
                scanError?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }

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
                    if (btcPrice != null && btcPrice > 0 && !amountLocked) {
                        Card(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        amountInput =
                                            convertLnAmountInput(
                                                amountSats,
                                                useSats,
                                                !isUsdMode,
                                                btcPrice,
                                            )
                                        isUsdMode = !isUsdMode
                                        isMaxMode = false
                                    },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isUsdMode) {
                                            LightningYellow.copy(alpha = 0.15f)
                                        } else {
                                            DarkSurface
                                        },
                                ),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isUsdMode) LightningYellow else BorderColor,
                                ),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) LightningYellow else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val conversionText =
                    if (
                        amountInput.isNotBlank() &&
                        amountSats != null &&
                        amountSats > 0 &&
                        btcPrice != null &&
                        btcPrice > 0
                    ) {
                        if (privacyMode) {
                            "≈ $LN_HIDDEN"
                        } else if (isUsdMode) {
                            "≈ ${formatAmount(amountSats.toULong(), useSats, includeUnit = true)}"
                        } else {
                            "≈ ${formatFiat((amountSats / 100_000_000.0) * btcPrice, fiatCurrency)}"
                        }
                    } else {
                        null
                    }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { value ->
                        if (amountLocked) return@OutlinedTextField
                        amountInput = filterLnAmountInput(value, useSats, isUsdMode)
                        isMaxMode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !amountLocked,
                    placeholder = {
                        Text(
                            text = if (isUsdMode) "0.00" else "0",
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
                        conversionText?.let {
                            {
                                Text(
                                    text = it,
                                    color = LightningYellow,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LightningYellow,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LightningYellow,
                            errorBorderColor = ErrorRed,
                        ),
                    isError = amountFieldError != null,
                )
                if (amountFieldError != null && (amountInput.isNotBlank() || prepareError != null)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = amountFieldError,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                AvailableBalanceMaxRow(
                    amountText =
                        if (privacyMode) {
                            LN_HIDDEN
                        } else {
                            formatAmount(availableBalanceSats.toULong(), useSats, includeUnit = true)
                        },
                    fiatText =
                        if (btcPrice != null && btcPrice > 0 && !privacyMode) {
                            val usdValue = (availableBalanceSats.toDouble() / 100_000_000.0) * btcPrice
                            " · ${formatFiat(usdValue, fiatCurrency)}"
                        } else {
                            null
                        },
                    accentColor = LightningYellow,
                    isMaxMode = isMaxMode,
                    maxEnabled = availableBalanceSats > 0 && !busy && !amountLocked,
                    fadeWhenDisabled = true,
                    onMaxClick = {
                        amountInput = formatLnSendAmountInput(availableBalanceSats, useSats)
                        isUsdMode = false
                        isMaxMode = true
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val amountForPrepare: Long? =
                    when {
                        isMaxMode -> availableBalanceSats
                        amountSats != null && amountSats > 0L -> amountSats
                        else -> invoiceFixedAmountSats?.takeIf { it > 0L }
                    }
                showConfirmDialog = true
                onPrepareSend(paymentRequest.trim(), amountForPrepare)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canReview,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = LightningYellow,
                    contentColor = DarkBackground,
                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                    disabledContentColor = DarkBackground.copy(alpha = 0.5f),
                ),
        ) {
            Text(
                text = stringResource(R.string.ln_node_review_payment),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LnRecipientModeBadgeChip(
    label: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun LnSendConfirmationDialog(
    sendState: LightningNodeSendState,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    supportsRoutingFeeLimit: Boolean,
    onConfirm: () -> Unit,
    onMaxFeePercentChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val preview = sendState as? LightningNodeSendState.Preview
    var editingMaxFee by remember(preview?.paymentRequest) { mutableStateOf(false) }
    var maxFeeDraft by remember(preview?.maxFeePercent, preview?.paymentRequest) {
        mutableStateOf(formatLnMaxFeePercent(preview?.maxFeePercent ?: 5.0))
    }
    val maxFeeValid = parseLnMaxFeePercent(maxFeeDraft)

    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = DarkSurface,
        actions = {
            when (sendState) {
                LightningNodeSendState.Idle,
                LightningNodeSendState.Decoding,
                -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LightningYellow,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.loc_68504b99),
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                            stringResource(R.string.loc_51bac044),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                is LightningNodeSendState.Preview -> {
                    Button(
                        onClick = {
                            if (supportsRoutingFeeLimit && editingMaxFee) {
                                maxFeeValid?.let {
                                    onMaxFeePercentChange(it)
                                    editingMaxFee = false
                                } ?: return@Button
                            }
                            onConfirm()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !supportsRoutingFeeLimit || !editingMaxFee || maxFeeValid != null,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LightningYellow,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.loc_a274c658),
                            style = MaterialTheme.typography.titleMedium,
                        )
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
                            stringResource(R.string.loc_51bac044),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                is LightningNodeSendState.Paying -> {
                    Button(
                        onClick = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LightningYellow,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.loc_57cf7ca4), style = MaterialTheme.typography.titleMedium)
                    }
                }
                is LightningNodeSendState.Error,
                is LightningNodeSendState.Paid,
                -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick =
                            if (sendState is LightningNodeSendState.Paid) {
                                onDone
                            } else {
                                onDismiss
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(
                            stringResource(R.string.loc_d2c0aec0),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) {
        Text(
            text =
                stringResource(
                    when (sendState) {
                        is LightningNodeSendState.Paying -> R.string.ln_node_payment_progress_title
                        is LightningNodeSendState.Paid -> R.string.loc_30e230be
                        is LightningNodeSendState.Error -> R.string.loc_666a54bd
                        else -> R.string.loc_81f5c0cf
                    },
                ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (val s = sendState) {
            LightningNodeSendState.Idle,
            LightningNodeSendState.Decoding,
            -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = LightningYellow,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.loc_b86dbd12),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is LightningNodeSendState.Preview -> {
                Text(
                    text = stringResource(R.string.loc_895ab1d4),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = abbreviateLnText(s.paymentRequest),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                s.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                s.destination?.takeIf { it.isNotBlank() }?.let { dest ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = abbreviateLnText(dest, prefix = 12, suffix = 12),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderColor),
                )
                Spacer(modifier = Modifier.height(16.dp))
                val amount = s.amountSats ?: 0L
                val fee = s.feeSats ?: 0L
                if (s.amountSats != null) {
                    LnReviewAmountRow(
                        label = stringResource(R.string.loc_d19e8dd8),
                        subtitle = abbreviateLnText(s.paymentRequest, prefix = 8, suffix = 8),
                        valueText =
                            if (privacyMode) {
                                "-$LN_HIDDEN"
                            } else {
                                "-${formatAmount(amount.toULong(), useSats, includeUnit = true)}"
                            },
                        valueSubtext =
                            if (!privacyMode && btcPrice != null && btcPrice > 0) {
                                formatFiat((amount / 100_000_000.0) * btcPrice, fiatCurrency)
                            } else {
                                null
                            },
                        color = AccentRed,
                    )
                }
                if (supportsRoutingFeeLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LnMaxFeeRow(
                        percent = s.maxFeePercent ?: 5.0,
                        amountSats = s.amountSats,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        editing = editingMaxFee,
                        draft = maxFeeDraft,
                        onDraftChange = { maxFeeDraft = filterLnMaxFeePercentInput(it) },
                        onStartEdit = {
                            maxFeeDraft = formatLnMaxFeePercent(s.maxFeePercent ?: 5.0)
                            editingMaxFee = true
                        },
                        onCommit = { value ->
                            onMaxFeePercentChange(value)
                            editingMaxFee = false
                        },
                        onCancel = {
                            maxFeeDraft = formatLnMaxFeePercent(s.maxFeePercent ?: 5.0)
                            editingMaxFee = false
                        },
                    )
                }
                s.feeSats?.let { feeAmount ->
                    Spacer(modifier = Modifier.height(12.dp))
                    LnReviewAmountRow(
                        label = stringResource(R.string.ln_node_review_fee_label),
                        valueText =
                            if (privacyMode) {
                                "-$LN_HIDDEN"
                            } else {
                                "-${formatAmount(feeAmount.toULong(), useSats, includeUnit = true)}"
                            },
                        valueSubtext =
                            if (!privacyMode && btcPrice != null && btcPrice > 0) {
                                formatFiat((feeAmount / 100_000_000.0) * btcPrice, fiatCurrency)
                            } else {
                                null
                            },
                        color = LightningYellow,
                    )
                }
                if (s.amountSats != null || s.feeSats != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BorderColor),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LnReviewAmountRow(
                        label = stringResource(R.string.loc_03eece5a),
                        valueText =
                            if (privacyMode) {
                                "-$LN_HIDDEN"
                            } else {
                                "-${formatAmount((amount + fee).toULong(), useSats, includeUnit = true)}"
                            },
                        valueSubtext =
                            if (!privacyMode && btcPrice != null && btcPrice > 0) {
                                formatFiat(((amount + fee) / 100_000_000.0) * btcPrice, fiatCurrency)
                            } else {
                                null
                            },
                        color = AccentRed,
                        bold = true,
                    )
                }
            }
            is LightningNodeSendState.Paying -> LnPaymentProgressContent(
                payment = s,
                useSats = useSats,
                btcPrice = btcPrice,
                fiatCurrency = fiatCurrency,
                privacyMode = privacyMode,
            )
            is LightningNodeSendState.Paid -> {
                Text(
                    text = stringResource(R.string.ln_node_payment_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuccessGreen,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LnReviewAmountRow(
                    label = stringResource(R.string.loc_d19e8dd8),
                    valueText =
                        if (privacyMode) {
                            "-$LN_HIDDEN"
                        } else {
                            "-${formatAmount(s.amountSats.toULong(), useSats, includeUnit = true)}"
                        },
                    valueSubtext =
                        if (!privacyMode && btcPrice != null && btcPrice > 0) {
                            formatFiat((s.amountSats / 100_000_000.0) * btcPrice, fiatCurrency)
                        } else {
                            null
                        },
                    color = AccentRed,
                )
                if (s.feeSats > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LnReviewAmountRow(
                        label = stringResource(R.string.ln_node_review_fee_label),
                        valueText =
                            if (privacyMode) {
                                "-$LN_HIDDEN"
                            } else {
                                "-${formatAmount(s.feeSats.toULong(), useSats, includeUnit = true)}"
                            },
                        valueSubtext =
                            if (!privacyMode && btcPrice != null && btcPrice > 0) {
                                formatFiat((s.feeSats / 100_000_000.0) * btcPrice, fiatCurrency)
                            } else {
                                null
                            },
                        color = LightningYellow,
                    )
                }
                s.paymentHash?.takeIf { it.isNotBlank() }?.let { hash ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = abbreviateLnText(hash),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is LightningNodeSendState.Error -> {
                Text(
                    text = stringResource(R.string.ln_node_status_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ln_node_failure_reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = s.message.ifBlank { stringResource(R.string.ln_node_status_failed) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            }
        }
    }
}

@Composable
private fun LnPaymentProgressContent(
    payment: LightningNodeSendState.Paying,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
) {
    Text(
        text = stringResource(R.string.ln_node_payment_progress_message),
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
    )
    payment.amountSats?.let { amount ->
        Spacer(modifier = Modifier.height(16.dp))
        LnReviewAmountRow(
            label = stringResource(R.string.loc_d19e8dd8),
            subtitle = abbreviateLnText(payment.paymentRequest, prefix = 8, suffix = 8),
            valueText =
                if (privacyMode) {
                    "-$LN_HIDDEN"
                } else {
                    "-${formatAmount(amount.toULong(), useSats, includeUnit = true)}"
                },
            valueSubtext =
                if (!privacyMode && btcPrice != null && btcPrice > 0) {
                    formatFiat((amount / 100_000_000.0) * btcPrice, fiatCurrency)
                } else {
                    null
                },
            color = LightningYellow,
        )
    }
    payment.destination?.takeIf { it.isNotBlank() }?.let { destination ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = abbreviateLnText(destination, prefix = 12, suffix = 12),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    LnPaymentProgressStep(
        title = stringResource(R.string.ln_node_payment_progress_review_title),
        detail = stringResource(R.string.ln_node_payment_progress_review_detail),
        complete = true,
    )
    LnPaymentProgressStep(
        title = stringResource(R.string.ln_node_payment_progress_route_title),
        detail = stringResource(R.string.ln_node_payment_progress_route_detail),
        active = true,
    )
    LnPaymentProgressStep(
        title = stringResource(R.string.ln_node_payment_progress_settle_title),
        detail = stringResource(R.string.ln_node_payment_progress_settle_detail),
    )
}

@Composable
private fun LnPaymentProgressStep(
    title: String,
    detail: String,
    complete: Boolean = false,
    active: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 3.dp)
                    .size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LightningYellow,
                    strokeWidth = 2.dp,
                )
                complete -> Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(LightningYellow, RoundedCornerShape(2.dp)),
                )
                else -> Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(BorderColor, RoundedCornerShape(2.dp)),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (complete || active) TextPrimary else TextSecondary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun LnReviewAmountRow(
    label: String,
    valueText: String,
    color: Color,
    valueSubtext: String? = null,
    subtitle: String? = null,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
                textAlign = TextAlign.End,
            )
            valueSubtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun lightningPaymentKindLabel(parsed: ParsedSendRecipient.Lightning): String =
    when {
        isLightningAddressPayment(parsed) -> "LN Address"
        parsed.kind == LightningKind.BOLT12 -> "BOLT12"
        parsed.kind == LightningKind.LNURL -> "LNURL"
        else -> "BOLT11"
    }

private fun abbreviateLnText(
    value: String,
    prefix: Int = 12,
    suffix: Int = 8,
): String =
    if (value.length <= prefix + suffix + 3) {
        value
    } else {
        value.take(prefix) + "..." + value.takeLast(suffix)
    }

private fun filterLnAmountInput(
    value: String,
    useSats: Boolean,
    isUsdMode: Boolean,
): String {
    // Do NOT strip commas: "0,5" would become "05" and parse as 5 BTC.
    // Commas simply fail the pattern, matching the Layer 1 / Liquid filters.
    var v = value
    val pattern =
        when {
            isUsdMode -> Regex("^\\d*\\.?\\d{0,2}$")
            useSats -> Regex("^\\d*$")
            else -> Regex("^\\d*\\.?\\d{0,8}$")
        }
    while (v.isNotEmpty() && !v.matches(pattern)) {
        v = v.dropLast(1)
    }
    return v
}

private fun parseLnSendAmount(
    input: String,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
): Long? {
    // No comma stripping — "0,5" must fail parsing, not become 5 BTC.
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return when {
        isUsdMode && btcPrice != null && btcPrice > 0 ->
            trimmed.toDoubleOrNull()?.let { ((it / btcPrice) * 100_000_000).roundToLong() }
        useSats -> trimmed.toLongOrNull()
        else -> trimmed.toDoubleOrNull()?.let { (it * 100_000_000).roundToLong() }
    }?.takeIf { it > 0 }
}

private fun convertLnAmountInput(
    amountSats: Long?,
    useSats: Boolean,
    toUsdMode: Boolean,
    btcPrice: Double,
): String {
    val sats = amountSats ?: return ""
    return if (toUsdMode) {
        String.format(Locale.US, "%.2f", (sats / 100_000_000.0) * btcPrice)
    } else if (useSats) {
        sats.toString()
    } else {
        String.format(Locale.US, "%.8f", sats / 100_000_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun formatLnSendAmountInput(
    amountSats: Long,
    useSats: Boolean,
): String =
    if (useSats) {
        amountSats.toString()
    } else {
        String.format(Locale.US, "%.8f", amountSats / 100_000_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }

private fun formatLnMaxFeePercent(percent: Double): String =
    String.format(Locale.US, "%.2f", percent.coerceIn(0.0, 100.0))
        .trimEnd('0')
        .trimEnd('.')

private fun parseLnMaxFeePercent(input: String): Double? =
    input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

private fun filterLnMaxFeePercentInput(input: String): String {
    val normalized = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = normalized.indexOf('.')
    return if (firstDot < 0) {
        normalized.take(3)
    } else {
        normalized.take(firstDot + 1) + normalized.drop(firstDot + 1).replace(".", "").take(2)
    }
}

@Composable
private fun LnMaxFeeRow(
    percent: Double,
    amountSats: Long?,
    useSats: Boolean,
    privacyMode: Boolean,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommit: (Double) -> Unit,
    onCancel: () -> Unit,
) {
    val maxAllowedSats =
        amountSats
            ?.takeIf { it > 0L }
            ?.let { kotlin.math.ceil(it * percent.coerceIn(0.0, 100.0) / 100.0).toLong() }
    val parsedDraft = parseLnMaxFeePercent(draft)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ln_node_max_fee_percent),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            if (!editing && !privacyMode && maxAllowedSats != null) {
                Text(
                    text = formatAmount(maxAllowedSats.toULong(), useSats, includeUnit = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        if (editing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.width(88.dp),
                    singleLine = true,
                    isError = draft.isNotBlank() && parsedDraft == null,
                    suffix = {
                        Text("%", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LightningYellow,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LightningYellow,
                            errorBorderColor = ErrorRed,
                        ),
                )
                IconButton(
                    onClick = { parsedDraft?.let(onCommit) },
                    enabled = parsedDraft != null,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.loc_3822fc21),
                        tint = if (parsedDraft != null) LightningYellow else TextTertiary,
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.loc_51bac044),
                        tint = TextSecondary,
                    )
                }
            }
        } else {
            // Outlined chip + pencil cue: value is clearly tappable/editable (no helper copy).
            Surface(
                onClick = onStartEdit,
                shape = RoundedCornerShape(8.dp),
                color = LightningYellow.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, LightningYellow),
            ) {
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.ln_node_max_fee_percent_value_format,
                                formatLnMaxFeePercent(percent),
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = LightningYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = LightningYellow,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun LightningNodeConnectionScreen(
    walletId: String?,
    initialConfig: LightningNodeConfig,
    isTesting: Boolean,
    testPhase: LightningNodeConnectionTestPhase = LightningNodeConnectionTestPhase.IDLE,
    testResult: LightningNodeConnectionTestResult?,
    onTest: (LightningNodeConfig) -> Unit,
    onSave: (String, LightningNodeConfig) -> Unit,
    onClearTest: () -> Unit,
    onBack: () -> Unit,
) {
    // Parent remounts this composable (key) after each successful create so state resets.
    val context = LocalContext.current
    val qrImportSuccessMessage = stringResource(R.string.ln_node_qr_import_success)
    val qrImportErrorMessage = stringResource(R.string.ln_node_qr_import_error)
    var walletName by remember { mutableStateOf("") }
    var type by remember {
        mutableStateOf(
            initialConfig.type.let {
                if (it == LightningNodeConnectionType.NONE) LightningNodeConnectionType.LND_REST else it
            },
        )
    }
    var host by remember { mutableStateOf(initialConfig.host) }
    var port by remember {
        mutableStateOf(
            if (initialConfig.port > 0) {
                initialConfig.port.toString()
            } else {
                when (initialConfig.type) {
                    LightningNodeConnectionType.CLN_REST ->
                        LightningNodeConfig.DEFAULT_CLN_REST_PORT.toString()
                    else -> LightningNodeConfig.DEFAULT_LND_REST_PORT.toString()
                }
            },
        )
    }
    var macaroon by remember { mutableStateOf(initialConfig.macaroonHex) }
    var clnRune by remember { mutableStateOf(initialConfig.clnRune) }
    var tlsCert by remember { mutableStateOf(initialConfig.tlsCertPem) }
    var nwcUri by remember { mutableStateOf(initialConfig.nwcUri) }
    var useTor by remember {
        mutableStateOf(initialConfig.useTor || initialConfig.host.endsWith(".onion"))
    }
    var tlsEnabled by remember {
        // Follow the saved preference only — a leftover cert must not force TLS on
        // when the user explicitly chose plain HTTP.
        mutableStateOf(initialConfig.useTls)
    }
    var showQrScanner by remember { mutableStateOf(false) }
    var showSuccessSaveDialog by remember { mutableStateOf(false) }
    var successDialogPayload by remember {
        mutableStateOf<LightningNodeConnectionTestResult.Success?>(null)
    }

    LaunchedEffect(testResult) {
        when (val result = testResult) {
            is LightningNodeConnectionTestResult.Success -> {
                successDialogPayload = result
                showSuccessSaveDialog = true
            }
            is LightningNodeConnectionTestResult.Failure -> {
                showSuccessSaveDialog = false
                successDialogPayload = null
            }
            null -> {
                showSuccessSaveDialog = false
                successDialogPayload = null
            }
        }
    }

    fun defaultPortForType(connectionType: LightningNodeConnectionType): Int =
        when (connectionType) {
            LightningNodeConnectionType.CLN_REST -> LightningNodeConfig.DEFAULT_CLN_REST_PORT
            else -> LightningNodeConfig.DEFAULT_LND_REST_PORT
        }

    fun applyImportedConfig(imported: LightningNodeConfig) {
        type = imported.type
        host = imported.host
        port =
            if (imported.port > 0) {
                imported.port.toString()
            } else {
                defaultPortForType(imported.type).toString()
            }
        macaroon = imported.macaroonHex
        clnRune = imported.clnRune
        tlsCert = imported.tlsCertPem
        nwcUri = imported.nwcUri
        useTor =
            imported.useTor ||
                imported.host.endsWith(".onion", ignoreCase = true) ||
                imported.nwcUri.contains(".onion", ignoreCase = true)
        tlsEnabled = imported.useTls
        onClearTest()
    }

    fun currentConfig(): LightningNodeConfig {
        val cleanedHost =
            host
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
        val onion =
            cleanedHost.endsWith(".onion", ignoreCase = true) ||
                nwcUri.contains(".onion", ignoreCase = true)
        return LightningNodeConfig(
            type = type,
            host = cleanedHost,
            port = port.toIntOrNull() ?: defaultPortForType(type),
            useTor = useTor || onion,
            macaroonHex = macaroon.trim(),
            tlsCertPem = if (tlsEnabled) tlsCert.trim() else "",
            useTls = tlsEnabled,
            allowInsecureTls = !tlsEnabled,
            // Carry over after a successful test so LAN reconnects skip dead HTTP.
            preferSessionTls = successDialogPayload?.preferSessionTls == true ||
                initialConfig.preferSessionTls,
            nwcUri = nwcUri.trim(),
            clnRune = clnRune.trim(),
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { payload ->
                showQrScanner = false
                runCatching {
                    LightningNodeConnectionUriParser.parse(payload).config
                }.onSuccess { imported ->
                    applyImportedConfig(imported)
                    Toast
                        .makeText(
                            context,
                            qrImportSuccessMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                }.onFailure { err ->
                    Toast
                        .makeText(
                            context,
                            err.message?.takeIf { it.isNotBlank() }
                                ?: qrImportErrorMessage,
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
            onDismiss = { showQrScanner = false },
        )
    }

    fun resolvedWalletName(): String {
        val typed = walletName.trim()
        if (typed.isNotBlank()) return typed
        val success = successDialogPayload
        val alias = success?.info?.alias?.trim()?.takeIf { it.isNotBlank() }
        if (alias != null) return alias
        val pk = success?.info?.pubkey?.trim()?.takeIf { it.isNotBlank() }
        if (pk != null) {
            return if (pk.length > 16) "${pk.take(8)}…${pk.takeLast(6)}" else pk
        }
        val hostLabel =
            host
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
                .takeIf { it.isNotBlank() }
        if (hostLabel != null) {
            val shortHost =
                if (hostLabel.length > 22) {
                    "${hostLabel.take(10)}…${hostLabel.takeLast(6)}"
                } else {
                    hostLabel
                }
            return "${type.listTypeChipLabel()} $shortHost"
        }
        return type.listTypeChipLabel()
    }

    if (showSuccessSaveDialog) {
        val success = successDialogPayload
        val canSave = currentConfig().isConfigured
        val previewName = resolvedWalletName()
        AlertDialog(
            onDismissRequest = {
                showSuccessSaveDialog = false
            },
            containerColor = DarkCard,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = stringResource(R.string.ln_node_test_success_title),
                    color = SuccessGreen,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.ln_node_test_success_body),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.ln_node_test_success_type_format,
                                type.listTypeChipLabel(),
                            ),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    success?.info?.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                        Text(
                            text = stringResource(R.string.ln_node_test_success_alias_format, alias),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    success?.info?.network?.takeIf { it.isNotBlank() }?.let { network ->
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_test_success_network_format,
                                    network,
                                ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    success?.info?.numActiveChannels?.let { channels ->
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_test_success_channels_format,
                                    channels,
                                ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    success?.let { ok ->
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_test_success_balance_format,
                                    formatAmount(
                                        ok.balance.totalSats.toULong(),
                                        true,
                                        includeUnit = true,
                                    ),
                                ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    success?.info?.pubkey?.takeIf { it.isNotBlank() }?.let { pk ->
                        val shortPk =
                            if (pk.length > 20) {
                                "${pk.take(10)}…${pk.takeLast(8)}"
                            } else {
                                pk
                            }
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_test_success_pubkey_format,
                                    shortPk,
                                ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (walletId == null && walletName.isBlank()) {
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_test_success_default_name_format,
                                    previewName,
                                ),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ln_node_test_success_save_prompt),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessSaveDialog = false
                        onSave(resolvedWalletName(), currentConfig())
                    },
                    enabled = canSave,
                ) {
                    Text(
                        text = stringResource(R.string.ln_node_test_success_save),
                        color = if (canSave) BitcoinOrange else TextSecondary,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSuccessSaveDialog = false },
                ) {
                    Text(
                        text = stringResource(R.string.ln_node_test_success_later),
                        color = TextSecondary,
                    )
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ln_node_connection_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (walletId == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.ln_node_wallet_name),
                        color = LightningYellow,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LnField(
                        value = walletName,
                        onValueChange = onClearTest.let { clear -> { value -> walletName = value; clear() } },
                        label = stringResource(R.string.ln_node_wallet_name_optional),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ln_node_connection_type),
                    color = LightningYellow,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionTypeChip(
                        label = "LND",
                        selected = type == LightningNodeConnectionType.LND_REST,
                        onClick = {
                            if (type != LightningNodeConnectionType.LND_REST) {
                                type = LightningNodeConnectionType.LND_REST
                                if (port == LightningNodeConfig.DEFAULT_CLN_REST_PORT.toString()) {
                                    port = LightningNodeConfig.DEFAULT_LND_REST_PORT.toString()
                                }
                                onClearTest()
                            }
                        },
                    )
                    ConnectionTypeChip(
                        label = "CLN",
                        selected = type == LightningNodeConnectionType.CLN_REST,
                        onClick = {
                            if (type != LightningNodeConnectionType.CLN_REST) {
                                type = LightningNodeConnectionType.CLN_REST
                                if (port == LightningNodeConfig.DEFAULT_LND_REST_PORT.toString() ||
                                    port.isBlank()
                                ) {
                                    port = LightningNodeConfig.DEFAULT_CLN_REST_PORT.toString()
                                }
                                onClearTest()
                            }
                        },
                    )
                    ConnectionTypeChip(
                        label = "NWC",
                        selected = type == LightningNodeConnectionType.NWC,
                        onClick = {
                            type = LightningNodeConnectionType.NWC
                            onClearTest()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (type) {
                    LightningNodeConnectionType.LND_REST, LightningNodeConnectionType.NONE -> {
                        Text(
                            text = stringResource(R.string.ln_node_lnd_rest_hint),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LnField(
                            host,
                            {
                                host = it
                                if (it.contains(".onion", ignoreCase = true)) {
                                    useTor = true
                                }
                                onClearTest()
                            },
                            stringResource(R.string.ln_node_host),
                            trailingIcon = {
                                IconButton(onClick = { showQrScanner = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.ln_node_qr_scan_cd),
                                        tint = LightningYellow,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LnField(
                            port,
                            {
                                port = it.filter(Char::isDigit)
                                onClearTest()
                            },
                            stringResource(R.string.ln_node_port),
                            KeyboardType.Number,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LnField(macaroon, { macaroon = it; onClearTest() }, stringResource(R.string.ln_node_macaroon), minLines = 2)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.ln_node_enable_tls),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            SquareToggle(
                                checked = tlsEnabled,
                                onCheckedChange = {
                                    tlsEnabled = it
                                    if (!it) {
                                        tlsCert = ""
                                    }
                                    onClearTest()
                                },
                            )
                        }
                        if (tlsEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LnField(
                                tlsCert,
                                {
                                    tlsCert = it
                                    onClearTest()
                                },
                                stringResource(R.string.ln_node_tls_cert),
                                minLines = 3,
                            )
                        }
                    }
                    LightningNodeConnectionType.CLN_REST -> {
                        Text(
                            text = stringResource(R.string.ln_node_cln_rest_hint),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LnField(
                            host,
                            {
                                host = it
                                if (it.contains(".onion", ignoreCase = true)) {
                                    useTor = true
                                }
                                onClearTest()
                            },
                            stringResource(R.string.ln_node_host),
                            trailingIcon = {
                                IconButton(onClick = { showQrScanner = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.ln_node_qr_scan_cd),
                                        tint = LightningYellow,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LnField(
                            port,
                            {
                                port = it.filter(Char::isDigit)
                                onClearTest()
                            },
                            stringResource(R.string.ln_node_port),
                            KeyboardType.Number,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LnField(
                            clnRune,
                            { clnRune = it; onClearTest() },
                            stringResource(R.string.ln_node_cln_rune),
                            minLines = 2,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.ln_node_enable_tls),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            SquareToggle(
                                checked = tlsEnabled,
                                onCheckedChange = {
                                    tlsEnabled = it
                                    if (!it) {
                                        tlsCert = ""
                                    }
                                    onClearTest()
                                },
                            )
                        }
                        if (tlsEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LnField(
                                tlsCert,
                                {
                                    tlsCert = it
                                    onClearTest()
                                },
                                stringResource(R.string.ln_node_cln_tls_cert),
                                minLines = 3,
                            )
                        }
                    }
                    LightningNodeConnectionType.NWC -> {
                        Text(
                            text = stringResource(R.string.ln_node_nwc_hint),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LnField(
                            nwcUri,
                            {
                                nwcUri = it
                                if (it.contains(".onion", ignoreCase = true)) {
                                    useTor = true
                                }
                                onClearTest()
                            },
                            stringResource(R.string.ln_node_nwc_uri),
                            minLines = 3,
                            bottomTrailingContent = {
                                IconButton(onClick = { showQrScanner = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.ln_node_qr_scan_cd),
                                        tint = LightningYellow,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.ln_node_use_tor), color = TextPrimary, modifier = Modifier.weight(1f))
                    SquareToggle(checked = useTor, onCheckedChange = { useTor = it; onClearTest() })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Failures stay inline; successes use the save confirmation dialog.
        (testResult as? LightningNodeConnectionTestResult.Failure)?.let { failure ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Text(
                    failure.message,
                    color = ErrorRed,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { onTest(currentConfig()) },
            enabled = !isTesting && currentConfig().isConfigured,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LightningYellow, contentColor = TextPrimary),
        ) {
            if (isTesting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = connectionTestPhaseLabel(testPhase),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(stringResource(R.string.ln_node_test_connection))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onSave(resolvedWalletName(), currentConfig()) },
            enabled = currentConfig().isConfigured,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BitcoinOrange),
        ) {
            Text(stringResource(if (walletId == null) R.string.ln_node_add_wallet else R.string.ln_node_save))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun connectionTestPhaseLabel(phase: LightningNodeConnectionTestPhase): String =
    when (phase) {
        LightningNodeConnectionTestPhase.IDLE,
        LightningNodeConnectionTestPhase.PREPARING,
        -> stringResource(R.string.ln_node_test_phase_preparing)
        LightningNodeConnectionTestPhase.STARTING_TOR ->
            stringResource(R.string.ln_node_test_phase_starting_tor)
        LightningNodeConnectionTestPhase.WAITING_FOR_TOR ->
            stringResource(R.string.ln_node_test_phase_waiting_tor)
        LightningNodeConnectionTestPhase.CONNECTING ->
            stringResource(R.string.ln_node_test_phase_connecting)
        LightningNodeConnectionTestPhase.FETCHING_BALANCE ->
            stringResource(R.string.ln_node_test_phase_fetching_balance)
    }

private fun LightningNodeConnectionType.listTypeChipLabel(): String =
    when (this) {
        LightningNodeConnectionType.LND_REST -> "LND"
        LightningNodeConnectionType.CLN_REST -> "CLN"
        LightningNodeConnectionType.NWC -> "NWC"
        LightningNodeConnectionType.NONE -> "Lightning"
    }

@Composable
private fun ConnectionTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) LightningYellow.copy(alpha = 0.25f) else DarkCard)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) LightningYellow else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LnField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
    bottomTrailingContent: (@Composable () -> Unit)? = null,
) {
    if (bottomTrailingContent != null && minLines > 1) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                minLines = minLines,
                singleLine = false,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                // Reserve room so typed text doesn’t run under the bottom-right control.
                trailingIcon = { Spacer(modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LightningYellow,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = LightningYellow,
                    ),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp),
            ) {
                bottomTrailingContent()
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            minLines = minLines,
            singleLine = minLines == 1,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LightningYellow,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = LightningYellow,
                ),
        )
    }
}

@Composable
private fun LnConnectionStatusBanner(
    isConnecting: Boolean,
    nodeTarget: String? = null,
    onOpenConnectionSettings: () -> Unit,
) {
    val connectingDetail =
        if (!nodeTarget.isNullOrBlank()) {
            stringResource(R.string.ln_node_connecting_to_format, nodeTarget)
        } else {
            stringResource(R.string.ln_node_onchain_connecting_wait)
        }
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
                text =
                    if (isConnecting) {
                        connectingDetail
                    } else {
                        stringResource(R.string.ln_node_not_connected_hint)
                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightningNodeChannelsScreen(
    channels: List<LightningNodeChannel>,
    isLoading: Boolean,
    error: String?,
    isConnected: Boolean,
    isConnecting: Boolean,
    connectionType: LightningNodeConnectionType,
    denomination: String,
    privacyMode: Boolean,
    onRefresh: () -> Unit,
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(isLoading) {
        if (!isLoading) isPullRefreshing = false
    }
    LaunchedEffect(isConnected) {
        if (isConnected) onRefresh()
    }

    val nwcUnavailable =
        connectionType == LightningNodeConnectionType.NWC

    PullToRefreshBox(
        isRefreshing = isPullRefreshing || isLoading,
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
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ln_node_channels_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                when {
                    nwcUnavailable || !isConnected || isConnecting -> {
                        // Empty state cards carry the full message; avoid duplicate subtitle text.
                    }
                    else -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.ln_node_channels_count_format,
                                    channels.size,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            error?.takeIf { it.isNotBlank() }?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = message,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            when {
                nwcUnavailable -> {
                    item {
                        LnChannelsEmptyCard(
                            text = stringResource(R.string.ln_node_channels_nwc_unavailable),
                        )
                    }
                }
                !isConnected -> {
                    item {
                        LnChannelsEmptyCard(
                            text = stringResource(R.string.ln_node_channels_connect_hint),
                        )
                    }
                }
                isLoading && channels.isEmpty() -> {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = LightningYellow,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                channels.isEmpty() -> {
                    item {
                        LnChannelsEmptyCard(
                            text = stringResource(R.string.ln_node_channels_empty),
                        )
                    }
                }
                else -> {
                    items(channels, key = { it.id }) { channel ->
                        LnChannelCard(
                            channel = channel,
                            useSats = useSats,
                            privacyMode = privacyMode,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LnChannelsEmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun localizedLnChannelState(
    rawState: String?,
    isActive: Boolean,
): String {
    val state = rawState?.trim().orEmpty()
    if (state.isBlank()) {
        return if (isActive) {
            stringResource(R.string.ln_node_channels_active)
        } else {
            stringResource(R.string.ln_node_channels_inactive)
        }
    }
    val upper = state.uppercase(java.util.Locale.US)
    // LND conf progress: "PENDING OPEN 0/2" | "AWAITING LOCKIN 1/3"
    val progressMatch =
        Regex("""^(?:PENDING\s+OPEN|AWAITING\s+LOCKIN)\s+(\d+)\s*/\s*(\d+)$""")
            .matchEntire(upper)
    if (progressMatch != null) {
        return stringResource(
            R.string.ln_node_channels_state_pending_open_progress,
            progressMatch.groupValues[1].toInt(),
            progressMatch.groupValues[2].toInt(),
        )
    }
    return when {
        // Opening / lock-in (LND pending open + CLN *AWAITING_LOCKIN / dual open)
        upper == "PENDING OPEN" ||
            upper.startsWith("PENDING OPEN ") ||
            upper == "AWAITING LOCKIN" ||
            upper.contains("AWAITING_LOCKIN") ||
            upper.contains("AWAITING LOCKIN") ||
            upper == "CHANNELD_AWAITING_LOCKIN" ||
            upper == "DUALOPEND_AWAITING_LOCKIN" ||
            upper == "DUALOPEND_OPEN_INIT" ||
            upper == "DUALOPEND_OPEN_COMMITTED" ||
            upper == "DUALOPEND_OPEN_COMMIT_READY" ||
            upper.startsWith("DUALOPEND") && upper.contains("OPEN")
        -> stringResource(R.string.ln_node_channels_state_pending_open)

        // Pre-broadcast / negotiation
        upper == "OPENINGD" ||
            upper == "OPENING" ||
            upper.startsWith("OPENINGD")
        -> stringResource(R.string.ln_node_channels_state_opening)

        // Cooperative close path
        upper == "PENDING CLOSE" ||
            upper.startsWith("PENDING CLOSE") ||
            upper == "CHANNELD_SHUTTING_DOWN" ||
            upper.startsWith("CLOSINGD") ||
            upper == "CLOSING"
        -> stringResource(R.string.ln_node_channels_state_pending_close)

        // Force / unilateral
        upper == "FORCE CLOSING" ||
            upper == "FORCE CLOSE" ||
            upper.contains("FORCE CLOS") ||
            upper == "AWAITING_UNILATERAL" ||
            upper == "FUNDING_SPEND_SEEN"
        -> stringResource(R.string.ln_node_channels_state_force_closing)

        upper == "WAITING CLOSE" ||
            upper.startsWith("WAITING CLOSE") ||
            upper == "WAIT CLOSE"
        -> stringResource(R.string.ln_node_channels_state_waiting_close)

        upper == "ONCHAIN" || upper == "ON-CHAIN"
        -> stringResource(R.string.ln_node_channels_state_onchain)

        upper == "ACTIVE" ||
            upper == "CHANNELD_NORMAL" ||
            upper == "CHANNELD_AWAITING_SPLICE"
        -> stringResource(R.string.ln_node_channels_active)

        upper == "INACTIVE"
        -> stringResource(R.string.ln_node_channels_inactive)

        // Fallback: collapse remaining CLN_* noise to short tokens
        upper.startsWith("CHANNELD_") || upper.startsWith("DUALOPEND_")
        ->
            when {
                upper.contains("LOCKIN") || upper.contains("OPEN") ->
                    stringResource(R.string.ln_node_channels_state_pending_open)
                upper.contains("SHUT") || upper.contains("CLOS") ->
                    stringResource(R.string.ln_node_channels_state_pending_close)
                else -> stringResource(R.string.ln_node_channels_inactive)
            }

        else -> state
    }
}

@Composable
private fun LnChannelCard(
    channel: LightningNodeChannel,
    useSats: Boolean,
    privacyMode: Boolean,
) {
    val capacity = channel.capacitySats.coerceAtLeast(0L)
    val local = channel.localBalanceSats.coerceAtLeast(0L).coerceAtMost(capacity)
    val remote = channel.remoteBalanceSats.coerceAtLeast(0L)
    val localFrac =
        if (capacity > 0L) {
            (local.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val peerLabel =
        channel.remoteAlias?.takeIf { it.isNotBlank() }
            ?: channel.remotePubkey.takeIf { it.isNotBlank() }?.let { pk ->
                if (pk.length > 16) "${pk.take(8)}…${pk.takeLast(8)}" else pk
            }
            ?: "—"
    val rawState = channel.state.orEmpty()
    val statusText = localizedLnChannelState(channel.state, channel.isActive)
    val statusColor =
        when {
            channel.isActive -> SuccessGreen
            rawState.contains("PENDING", ignoreCase = true) ||
                rawState.contains("AWAIT", ignoreCase = true) ||
                rawState.contains("LOCKIN", ignoreCase = true) ||
                rawState.contains("OPENING", ignoreCase = true) ||
                rawState.contains("DUALOPEND", ignoreCase = true) ||
                (
                    rawState.contains("OPEN", ignoreCase = true) &&
                        !rawState.equals("INACTIVE", ignoreCase = true)
                    )
            -> BitcoinOrange
            rawState.contains("CLOSE", ignoreCase = true) ||
                rawState.contains("FORCE", ignoreCase = true) ||
                rawState.contains("ONCHAIN", ignoreCase = true) ||
                rawState.contains("UNILATERAL", ignoreCase = true)
            -> AccentRed
            else -> TextSecondary
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = peerLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(label = statusText, color = statusColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "${stringResource(R.string.ln_node_channels_capacity)}: " +
                        if (privacyMode) {
                            LN_HIDDEN
                        } else {
                            formatAmount(capacity.toULong(), useSats, includeUnit = true)
                        },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.isPrivate) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ln_node_channels_private),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            channel.shortChannelId?.takeIf { it.isNotBlank() }?.let { scid ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.ln_node_channels_scid)}: $scid",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentRed.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(localFrac)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentGreen.copy(alpha = 0.85f)),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ln_node_channels_local),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text =
                            if (privacyMode) {
                                LN_HIDDEN
                            } else {
                                formatAmount(local.toULong(), useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.ln_node_channels_remote),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text =
                            if (privacyMode) {
                                LN_HIDDEN
                            } else {
                                formatAmount(remote.toULong(), useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (channel.remotePubkey.isNotBlank() && channel.remoteAlias != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        channel.remotePubkey.let { pk ->
                            if (pk.length > 20) "${pk.take(10)}…${pk.takeLast(10)}" else pk
                        },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
