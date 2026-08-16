package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkMovement
import github.aeonbtc.ibiswallet.data.repository.ArkDepositPolicy
import github.aeonbtc.ibiswallet.data.model.ArkReceiveKind
import github.aeonbtc.ibiswallet.data.model.ArkReceiveState
import github.aeonbtc.ibiswallet.data.model.ArkWalletState
import github.aeonbtc.ibiswallet.data.model.TransactionDetails
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.ui.components.BalanceAmountText
import github.aeonbtc.ibiswallet.ui.components.EditableLabelChip
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.QuickReceiveDialog
import github.aeonbtc.ibiswallet.ui.components.TransactionHistoryHideAllDialog
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentRed
import github.aeonbtc.ibiswallet.ui.theme.ArkRust
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.startActivityWithTaskFallback
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArkBalanceScreen(
    arkState: ArkWalletState,
    receiveState: ArkReceiveState = ArkReceiveState.Idle,
    denomination: String,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrices: Map<String, Double> = emptyMap(),
    showHistoricalTxPrices: Boolean = false,
    onShowHistoricalTxPricesChange: (Boolean) -> Unit = {},
    privacyMode: Boolean,
    dateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    layer1Transactions: List<TransactionDetails> = emptyList(),
    layer1BlockHeight: UInt? = null,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    movementLabels: Map<String, String>,
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDenomination: () -> Unit,
    onQuickReceive: () -> Unit,
    onScanQrResult: (String) -> Unit,
    onSaveMovementLabel: (Int, String) -> Unit,
    onDeleteMovementFromHistory: (Int) -> Unit = {},
    onDeleteAllMovementsFromHistory: () -> Unit = {},
    onOpenLifecycle: () -> Unit = {},
    onOpenArkBackup: () -> Unit = onOpenLifecycle,
    onQuickRefreshVtxos: () -> Unit = {},
    isDbBackupProtected: Boolean = false,
    /** Process-lifetime dismiss (survives leaving Balance); clears on app restart. */
    backupAlertDismissed: Boolean = false,
    onDismissBackupAlert: () -> Unit = {},
    autoRefreshEnabled: Boolean = false,
    /** Sweep stuck Bark on-chain deposit back to Layer 1. */
    onRecoverBelowMinBoard: () -> Unit = {},
    isRecoveringBelowMinBoard: Boolean = false,
    /** Open Manage → Boarding tab for unboarded on-chain funds. */
    onOpenBoarding: () -> Unit = onOpenLifecycle,
) {
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var showQrScanner by remember { mutableStateOf(false) }
    var showQuickReceive by remember { mutableStateOf(false) }
    var selectedMovement by remember { mutableStateOf<ArkMovement?>(null) }
    var movementPendingDelete by remember { mutableStateOf<ArkMovement?>(null) }
    var showDeleteAllHistoryDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var displayLimit by remember { mutableIntStateOf(25) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    // Rail filters (none selected = show all), same behavior as Spark/Liquid.
    var showArkTransactions by remember { mutableStateOf(false) }
    var showLightningTransactions by remember { mutableStateOf(false) }
    var showBitcoinTransactions by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val needsBackupAlert =
        arkState.isInitialized &&
            arkState.hasVtxoActivity &&
            !isDbBackupProtected
    // Checkbox is local; dismiss flag is process-lifetime from ViewModel (app restart only).
    var backupRiskAcknowledged by remember(arkState.walletId) { mutableStateOf(false) }
    val showBackupRequiredDialog = needsBackupAlert && !backupAlertDismissed
    // Recommended (not yet due) can be dismissed this session; due / near-expiry cannot.
    var refreshAlertDismissed by remember(arkState.walletId) { mutableStateOf(false) }
    val refreshDueCount =
        when {
            arkState.needsRefresh -> arkState.vtxosToRefresh.size.coerceAtLeast(1)
            arkState.expiringSoonVtxos.isNotEmpty() -> arkState.expiringSoonVtxos.size
            arkState.refreshSoon -> 1
            else -> 0
        }
    val blocksUntilRefresh = arkState.blocksUntilRequiredRefresh
    val refreshIsBlocking =
        arkState.needsRefresh ||
            (blocksUntilRefresh != null && blocksUntilRefresh <= ARK_REFRESH_BLOCKING_BLOCKS)
    val needsRefreshAlert =
        arkState.isInitialized &&
            !autoRefreshEnabled &&
            !arkState.isAutoRefreshing &&
            (arkState.needsRefresh || arkState.refreshSoon) &&
            refreshDueCount > 0
    val showRefreshRequiredDialog =
        needsRefreshAlert &&
            !showBackupRequiredDialog &&
            (refreshIsBlocking || !refreshAlertDismissed)

    LaunchedEffect(arkState.isSyncing) {
        if (!arkState.isSyncing) isPullRefreshing = false
    }
    LaunchedEffect(arkState.walletId, showBackupRequiredDialog) {
        if (!showBackupRequiredDialog) {
            backupRiskAcknowledged = false
        }
    }
    LaunchedEffect(arkState.walletId, arkState.needsRefresh, arkState.refreshSoon, refreshDueCount) {
        if (!needsRefreshAlert) {
            refreshAlertDismissed = false
        }
    }
    LaunchedEffect(
        isSearchActive,
        searchQuery.trim(),
        showArkTransactions,
        showLightningTransactions,
        showBitcoinTransactions,
    ) {
        displayLimit = 25
    }

    val hasRailFilters =
        showArkTransactions || showLightningTransactions || showBitcoinTransactions
    val railFilteredMovements =
        remember(
            arkState.movements,
            showArkTransactions,
            showLightningTransactions,
            showBitcoinTransactions,
        ) {
            if (!hasRailFilters) {
                arkState.movements
            } else {
                arkState.movements.filter { movement ->
                    when (arkMovementRail(movement)) {
                        ArkHistoryRail.ARK -> showArkTransactions
                        ArkHistoryRail.LIGHTNING -> showLightningTransactions
                        ArkHistoryRail.BITCOIN -> showBitcoinTransactions
                    }
                }
            }
        }
    val filteredMovements =
        remember(railFilteredMovements, movementLabels, searchQuery) {
            val q = searchQuery.trim().lowercase(Locale.US)
            if (q.isBlank()) {
                railFilteredMovements
            } else {
                railFilteredMovements.filter { movement ->
                    val label =
                        (movementLabels[movement.id.toString()] ?: movement.label).orEmpty()
                    val rail = arkMovementRail(movement).name
                    label.lowercase(Locale.US).contains(q) ||
                        movement.status.lowercase(Locale.US).contains(q) ||
                        movement.subsystemName.lowercase(Locale.US).contains(q) ||
                        movement.subsystemKind.lowercase(Locale.US).contains(q) ||
                        movement.paymentHash?.lowercase(Locale.US)?.contains(q) == true ||
                        movement.lightningInvoice?.lowercase(Locale.US)?.contains(q) == true ||
                        movement.onchainTxids.any { it.contains(q) } ||
                        movement.receivedOnAddresses.any { it.lowercase(Locale.US).contains(q) } ||
                        movement.sentToAddresses.any { it.lowercase(Locale.US).contains(q) } ||
                        rail.lowercase(Locale.US).contains(q) ||
                        movement.id.toString().contains(q)
                }
            }
        }
    val visibleMovements = filteredMovements.take(displayLimit)

    val quickReceiveRequest =
        (receiveState as? ArkReceiveState.Ready)
            ?.takeIf { it.kind == ArkReceiveKind.ARK_ADDRESS }
            ?.paymentRequest

    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                showQrScanner = false
                onScanQrResult(code)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    if (showQuickReceive) {
        QuickReceiveDialog(
            payload = quickReceiveRequest,
            title = stringResource(R.string.ark_quick_receive_title),
            accentColor = ArkRust,
            isLoading = receiveState is ArkReceiveState.Loading || quickReceiveRequest == null,
            errorMessage = (receiveState as? ArkReceiveState.Error)?.message,
            hidePayload = privacyMode,
            onDismiss = { showQuickReceive = false },
        )
    }

    if (showBackupRequiredDialog) {
        IbisConfirmDialog(
            onDismissRequest = {
                if (backupRiskAcknowledged) {
                    onDismissBackupAlert()
                }
            },
            title = stringResource(R.string.ark_backup_required_title),
            confirmText = stringResource(R.string.ark_backup_required_confirm),
            dismissText = stringResource(R.string.ark_backup_required_dismiss),
            showDismissButton = true,
            dismissEnabled = backupRiskAcknowledged,
            confirmColor = ArkRust,
            maxWidth = 720.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
            bottomSpacing = 20.dp,
            actionHeight = 48.dp,
            titleStyle = MaterialTheme.typography.titleLarge,
            actionTextStyle = MaterialTheme.typography.bodyLarge,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = backupRiskAcknowledged,
                    dismissOnClickOutside = backupRiskAcknowledged,
                ),
            onConfirm = {
                onDismissBackupAlert()
                onOpenArkBackup()
            },
            onDismissAction = {
                if (backupRiskAcknowledged) {
                    onDismissBackupAlert()
                }
            },
            body = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorRed.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.ark_backup_required_status),
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.ark_backup_required_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { backupRiskAcknowledged = !backupRiskAcknowledged }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = backupRiskAcknowledged,
                        onCheckedChange = { backupRiskAcknowledged = it },
                        colors =
                            CheckboxDefaults.colors(
                                checkedColor = ErrorRed,
                                uncheckedColor = TextSecondary,
                            ),
                    )
                    Text(
                        text = stringResource(R.string.ark_backup_required_ack),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                }
            },
        )
    }

    if (showRefreshRequiredDialog) {
        val accent = if (refreshIsBlocking) ErrorRed else WarningYellow
        val blocksSuffix =
            if (blocksUntilRefresh != null) {
                stringResource(R.string.ark_refresh_required_blocks_suffix, blocksUntilRefresh)
            } else {
                ""
            }
        IbisConfirmDialog(
            onDismissRequest = {
                if (!refreshIsBlocking) {
                    refreshAlertDismissed = true
                }
            },
            title = stringResource(R.string.ark_refresh_required_title),
            confirmText = stringResource(R.string.ark_refresh_required_confirm),
            dismissText = stringResource(R.string.ark_refresh_required_dismiss),
            showDismissButton = !refreshIsBlocking,
            dismissEnabled = !refreshIsBlocking,
            confirmColor = ArkRust,
            maxWidth = 720.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
            bottomSpacing = 20.dp,
            actionHeight = 48.dp,
            titleStyle = MaterialTheme.typography.titleLarge,
            actionTextStyle = MaterialTheme.typography.bodyLarge,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = !refreshIsBlocking,
                    dismissOnClickOutside = !refreshIsBlocking,
                ),
            onConfirm = {
                refreshAlertDismissed = true
                onOpenLifecycle()
            },
            onDismissAction = {
                if (!refreshIsBlocking) {
                    refreshAlertDismissed = true
                }
            },
            body = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text =
                                if (refreshIsBlocking) {
                                    stringResource(R.string.ark_refresh_required_status)
                                } else {
                                    stringResource(R.string.ark_refresh_required_status_soon)
                                },
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text =
                                if (refreshIsBlocking) {
                                    stringResource(
                                        R.string.ark_refresh_required_message,
                                        refreshDueCount,
                                    )
                                } else {
                                    stringResource(
                                        R.string.ark_refresh_required_message_soon,
                                        refreshDueCount,
                                        blocksSuffix,
                                    )
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                    }
                }
            },
        )
    }

    movementPendingDelete?.let { movement ->
        IbisConfirmDialog(
            onDismissRequest = { movementPendingDelete = null },
            title = stringResource(R.string.transaction_history_hide_title),
            message = stringResource(R.string.transaction_history_hide_message),
            confirmText = stringResource(R.string.transaction_history_hide_confirm),
            onConfirm = {
                onDeleteMovementFromHistory(movement.id)
                movementPendingDelete = null
                if (selectedMovement?.id == movement.id) {
                    selectedMovement = null
                }
            },
        )
    }

    if (showDeleteAllHistoryDialog) {
        TransactionHistoryHideAllDialog(
            entryCount = arkState.movements.size,
            onDismissRequest = { showDeleteAllHistoryDialog = false },
            onConfirm = {
                onDeleteAllMovementsFromHistory()
                showDeleteAllHistoryDialog = false
                selectedMovement = null
            },
        )
    }

    selectedMovement?.let { movement ->
        val linkedLayer1 =
            remember(movement, layer1Transactions) {
                arkResolveLayer1Transaction(movement, layer1Transactions)
            }
        ArkMovementDetailSheet(
            movement = movement,
            label = movementLabels[movement.id.toString()] ?: movement.label,
            privacyMode = privacyMode,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            historicalBtcPrice =
                if (showHistoricalTxPrices) {
                    historicalBtcPrices[movement.id.toString()]
                } else {
                    null
                },
            dateFormat = dateFormat,
            layer1Transaction = linkedLayer1,
            layer1BlockHeight = layer1BlockHeight,
            requiredBoardConfirmations =
                arkState.requiredBoardConfirmations ?: ARK_BOARD_REQUIRED_CONFIRMATIONS,
            // Display-only fallback when Bark omits received-on (ARKOOR inbound).
            ownArkAddress = arkState.currentAddress,
            mempoolUrl = mempoolUrl,
            mempoolServer = mempoolServer,
            onSaveLabel = { onSaveMovementLabel(movement.id, it) },
            onHideFromHistory = { movementPendingDelete = movement },
            onDismiss = { selectedMovement = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing,
        onRefresh = {
            if (arkState.isInitialized) {
                isPullRefreshing = true
                onRefresh()
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
                        val progress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                        translationY = progress * 40f
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ArkBalanceCard(
                    arkState = arkState,
                    useSats = useSats,
                    btcPrice = btcPrice,
                    fiatCurrency = fiatCurrency,
                    privacyMode = privacyMode,
                    onTogglePrivacy = onTogglePrivacy,
                    onRefresh = onRefresh,
                    onToggleDenomination = onToggleDenomination,
                    onQuickReceive = {
                        showQuickReceive = true
                        onQuickReceive()
                    },
                    onScan = { showQrScanner = true },
                )
            }

            val hasUnboardedOnchain =
                arkState.onchainTotalSats > 0L || arkState.onchainUtxos.isNotEmpty()
            val stuckBelowMin =
                ArkDepositPolicy.isStuckBelowMinBoard(
                    onchainConfirmedSats = arkState.onchainConfirmedSats,
                    pendingBoardSats = arkState.pendingBoardSats,
                    minBoardAmountSats = arkState.minBoardAmountSats,
                )
            if (hasUnboardedOnchain) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    val bannerBorder =
                        if (stuckBelowMin) {
                            WarningYellow.copy(alpha = 0.45f)
                        } else {
                            ArkRust.copy(alpha = 0.45f)
                        }
                    val bannerColor = if (stuckBelowMin) WarningYellow else ArkRust
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenBoarding),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, bannerBorder),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                        ) {
                            Text(
                                text =
                                    when {
                                        privacyMode && stuckBelowMin ->
                                            stringResource(
                                                R.string.ark_boarding_balance_banner_below_min_format,
                                                "****",
                                                "****",
                                            )
                                        privacyMode ->
                                            stringResource(
                                                R.string.ark_boarding_balance_banner_format,
                                                "****",
                                            )
                                        stuckBelowMin && arkState.minBoardAmountSats != null ->
                                            stringResource(
                                                R.string.ark_boarding_balance_banner_below_min_format,
                                                formatAmount(
                                                    arkState.onchainTotalSats.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                ),
                                                formatAmount(
                                                    arkState.minBoardAmountSats.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                ),
                                            )
                                        else ->
                                            stringResource(
                                                R.string.ark_boarding_balance_banner_format,
                                                formatAmount(
                                                    arkState.onchainTotalSats.toULong(),
                                                    useSats,
                                                    includeUnit = true,
                                                ),
                                            )
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = bannerColor,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenBoarding,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = BitcoinOrange,
                                    ),
                            ) {
                                Text(stringResource(R.string.ark_boarding_balance_banner_action))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                val manageVtxosColor =
                    if (arkState.isInitialized) {
                        ArkRust
                    } else {
                        ArkRust.copy(alpha = 0.4f)
                    }
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
                        if (arkState.movements.isNotEmpty()) {
                            ArkHistoryFilterButton(
                                icon = Icons.Default.Delete,
                                contentDescription =
                                    stringResource(R.string.transaction_history_hide_all_content_description),
                                tint = TextSecondary,
                                isSelected = true,
                                onClick = { showDeleteAllHistoryDialog = true },
                            )
                        }
                        if (historicalBtcPrices.isNotEmpty()) {
                            ArkHistoryFilterButton(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = manageVtxosColor,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .clickable(enabled = arkState.isInitialized) {
                                        onOpenLifecycle()
                                    },
                        ) {
                            if (arkState.isAutoRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = manageVtxosColor,
                                    strokeWidth = 1.5.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.ark_refresh_exit_button),
                                    tint = manageVtxosColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        ArkHistoryMarkFilterButton(
                            contentDescription = stringResource(R.string.ark_filter_ark_content_description),
                            tint = ArkRust,
                            isSelected = showArkTransactions,
                            onClick = { showArkTransactions = !showArkTransactions },
                        )
                        ArkHistoryFilterButton(
                            icon = Icons.Default.Bolt,
                            contentDescription = stringResource(R.string.loc_ce31119b),
                            tint = LightningYellow,
                            isSelected = showLightningTransactions,
                            onClick = { showLightningTransactions = !showLightningTransactions },
                        )
                        ArkHistoryFilterButton(
                            icon = Icons.Default.CurrencyBitcoin,
                            contentDescription = stringResource(R.string.ark_filter_bitcoin_content_description),
                            tint = BitcoinOrange,
                            isSelected = showBitcoinTransactions,
                            onClick = { showBitcoinTransactions = !showBitcoinTransactions },
                        )
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
                                    if (isSearchActive) {
                                        Icons.Default.Close
                                    } else {
                                        Icons.Default.Search
                                    },
                                contentDescription =
                                    if (isSearchActive) {
                                        stringResource(R.string.loc_dda0ea3a)
                                    } else {
                                        stringResource(R.string.loc_b35cde91)
                                    },
                                tint = if (isSearchActive) ArkRust else TextSecondary,
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
                                text = stringResource(R.string.ark_search_history_placeholder),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArkRust,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = ArkRust,
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            arkState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    )
                }
            }

            if (filteredMovements.isEmpty()) {
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
                                            stringResource(R.string.ark_history_empty)
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (searchQuery.isNotBlank()) {
                                            stringResource(R.string.loc_9febfd40)
                                        } else if (arkState.isInitialized) {
                                            stringResource(R.string.loc_2aebf14e)
                                        } else {
                                            stringResource(R.string.ark_loading_wallet_message)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            } else {
                items(visibleMovements, key = { it.id }) { movement ->
                    val layer1Tx =
                        remember(movement.id, movement.onchainTxids, layer1Transactions) {
                            arkResolveLayer1Transaction(movement, layer1Transactions)
                        }
                    ArkMovementRow(
                        movement = movement,
                        label = movementLabels[movement.id.toString()] ?: movement.label,
                        privacyMode = privacyMode,
                        useSats = useSats,
                        btcPrice = btcPrice,
                        fiatCurrency = fiatCurrency,
                        historicalBtcPrice =
                            if (showHistoricalTxPrices) {
                                historicalBtcPrices[movement.id.toString()]
                            } else {
                                null
                            },
                        dateFormat = dateFormat,
                        // Board deposits clear pending via conf depth in the row; other Bitcoin
                        // rails clear once the linked L1 tx confirms.
                        layer1Confirmed = layer1Tx?.isConfirmed == true,
                        layer1Transaction = layer1Tx,
                        layer1BlockHeight = layer1BlockHeight,
                        layer1Transactions = layer1Transactions,
                        requiredBoardConfirmations =
                            arkState.requiredBoardConfirmations
                                ?: ARK_BOARD_REQUIRED_CONFIRMATIONS,
                        onClick = { selectedMovement = movement },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (displayLimit < filteredMovements.size) {
                    item {
                        TextButton(
                            onClick = { displayLimit += 25 },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.loc_0ee47e3c), color = ArkRust)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ArkBalanceCard(
    arkState: ArkWalletState,
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
                        tint = if (privacyMode) ArkRust else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                val syncEnabled = arkState.isInitialized && !arkState.isSyncing
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
                    if (arkState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ArkRust,
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
                // Boarded Ark only (spendable VTXOs). Unboarded on-chain is the boarding banner.
                val displaySats = arkState.spendableSats
                BalanceAmountText(
                    amountText =
                        if (privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else if (useSats) {
                            formatAmount(displaySats.toULong(), true)
                        } else {
                            formatAmount(displaySats.toULong(), false)
                        },
                    showBtcSymbol = !privacyMode && !useSats,
                    showSatsUnit = !privacyMode && useSats,
                    onClick = onToggleDenomination,
                )
                if (btcPrice != null && btcPrice > 0) {
                    val fiatValue = (displaySats.toDouble() / 100_000_000.0) * btcPrice
                    Text(
                        text =
                            if (privacyMode) {
                                ARK_HIDDEN_AMOUNT
                            } else {
                                formatFiat(fiatValue, fiatCurrency)
                            },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = TextSecondary,
                    )
                }
            }

            // Bottom control strip: QR corners only.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = arkState.isInitialized) { onQuickReceive() },
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = stringResource(R.string.loc_a397da3c),
                        tint = ArkRust,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = arkState.isInitialized) { onScan() },
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.loc_60129540),
                        tint = ArkRust,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** Within this many blocks of required refresh, the popup cannot be dismissed. */
private const val ARK_REFRESH_BLOCKING_BLOCKS = 24

@Composable
private fun ArkMovementRow(
    movement: ArkMovement,
    label: String?,
    privacyMode: Boolean,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double? = null,
    dateFormat: String,
    layer1Confirmed: Boolean = false,
    layer1Transaction: TransactionDetails? = null,
    layer1BlockHeight: UInt? = null,
    layer1Transactions: List<TransactionDetails> = emptyList(),
    requiredBoardConfirmations: Int = ARK_BOARD_REQUIRED_CONFIRMATIONS,
    onClick: () -> Unit,
) {
    val amount = movement.displayBalanceSats()
    val isRecoveredL1 = ArkDepositPolicy.isRecoveredOnchainMovement(movement)
    val isBelowMin = ArkDepositPolicy.isBelowMinOnchainMovement(movement)
    val isReceive = amount >= 0 && !isRecoveredL1
    val absAmount = abs(amount).toULong()
    val isFailed = arkMovementIsFailed(movement.status)
    val isBoardDeposit = arkMovementIsBoardDeposit(movement)
    val isRefresh = arkMovementIsRefresh(movement)
    val requiredConfs =
        movement.requiredBoardConfirmations?.takeIf { it > 0 }
            ?: requiredBoardConfirmations.takeIf { it > 0 }
            ?: ARK_BOARD_REQUIRED_CONFIRMATIONS
    // Board deposits stay pending until a board tx exists and hits ASP confs.
    // Funding confs alone never clear the badge (board may not have run yet).
    val boardDepositConfsMet =
        isBoardDeposit &&
            isReceive &&
            !movement.boardTxid.isNullOrBlank() &&
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = movement.boardConfirmations,
                requiredBoardConfirmations = requiredConfs,
            )
    val isPending =
        !isRecoveredL1 &&
            !isBelowMin &&
            !boardDepositConfsMet &&
            (
                arkMovementIsPending(movement) ||
                    (isBoardDeposit && isReceive && movement.boardTxid.isNullOrBlank())
            ) &&
            !isFailed &&
            !(
                !isBoardDeposit &&
                    layer1Confirmed &&
                    arkMovementRail(movement) == ArkHistoryRail.BITCOIN
            )
    val icon =
        when {
            isRefresh -> Icons.Default.Sync
            isRecoveredL1 -> Icons.Default.Undo
            isReceive -> Icons.AutoMirrored.Filled.CallReceived
            else -> Icons.AutoMirrored.Filled.CallMade
        }
    val iconTint =
        when {
            isRefresh -> ArkRust
            isRecoveredL1 -> BitcoinOrange
            isBelowMin || isReceive -> AccentGreen
            else -> AccentRed
        }
    val iconBackground = iconTint.copy(alpha = 0.1f)
    val amountColor =
        when {
            isFailed || isRecoveredL1 -> TextSecondary
            isRefresh -> ArkRust
            isBelowMin || isReceive -> AccentGreen
            else -> AccentRed
        }
    val rail = remember(movement.subsystemName, movement.subsystemKind) { arkMovementRail(movement) }
    val title =
        label?.takeIf { it.isNotBlank() }
            ?: arkMovementDisplayTitle(movement, layer1Transactions)
    val formattedTimestamp =
        remember(movement.createdAt, movement.completedAt, movement.updatedAt, dateFormat) {
            formatArkMovementTimestamp(
                preferred = movement.createdAt,
                fallbacks = listOf(movement.completedAt.orEmpty(), movement.updatedAt),
                dateFormat = dateFormat,
            )
        }
    val subtitle =
        formattedTimestamp.takeIf { it.isNotBlank() }
            ?: arkMovementDisplayStatus(movement.status)

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
                        .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription =
                        when {
                            isRefresh -> stringResource(R.string.ark_movement_refresh)
                            isRecoveredL1 -> stringResource(R.string.ark_history_title_recovered)
                            isReceive -> stringResource(R.string.loc_301a5b91)
                            else -> stringResource(R.string.loc_1af68597)
                        },
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    ArkHistoryRailBadge(rail = rail)
                }
                if (!label.isNullOrBlank() && label != title) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = ArkRust,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        if (privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else if (isFailed || isRecoveredL1) {
                            formatAmount(absAmount, useSats, includeUnit = true)
                        } else {
                            "${if (isReceive || isBelowMin) "+" else "-"}${formatAmount(absAmount, useSats, includeUnit = true)}"
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    textAlign = TextAlign.End,
                )
                val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                if (!privacyMode && effectiveBtcPrice != null && effectiveBtcPrice > 0) {
                    ArkHistoricalFiatText(
                        text = formatFiat(abs(amount) / 100_000_000.0 * effectiveBtcPrice, fiatCurrency),
                        isHistorical = historicalBtcPrice != null && historicalBtcPrice > 0,
                    )
                }
                when {
                    isFailed ->
                        ArkPendingBadge(
                            text = arkMovementDisplayStatus(movement.status),
                            color = ErrorRed,
                        )
                    isBelowMin ->
                        ArkPendingBadge(
                            text = stringResource(R.string.ark_history_badge_below_min),
                            color = WarningYellow,
                        )
                    isRecoveredL1 ->
                        ArkPendingBadge(
                            text = stringResource(R.string.ark_history_badge_recovered_l1),
                            color = BitcoinOrange,
                        )
                    isPending ->
                        ArkPendingBadge(text = stringResource(R.string.loc_1b684325))
                }
            }
        }
    }
}

@Composable
private fun ArkHistoricalFiatText(
    text: String,
    isHistorical: Boolean,
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
private fun ArkHistoryFilterButton(
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
private fun ArkHistoryMarkFilterButton(
    contentDescription: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (isSelected) tint else TextSecondary
    // Same 30dp chip + 20dp mark footprint as [ArkHistoryFilterButton] Material icons.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) tint.copy(alpha = 0.16f) else DarkSurfaceVariant)
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
    ) {
        ArkMarkIcon(
            tint = color,
            // Slight lift so the math glyph lines up with Material icons in this row.
            modifier =
                Modifier
                    .size(20.dp)
                    .offset(y = (-1).dp),
        )
    }
}

/** Ark mark: distinctive Unicode A (not a plain Latin-A monogram). */
@Composable
private fun ArkMarkIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Math double-struck 𝔸 has uneven metrics; fill the box and nudge for optical center.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = "\uD835\uDD38",
            color = tint,
            modifier =
                Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .offset(y = (-1.5).dp),
            style =
                TextStyle(
                    color = tint,
                    // ~match 20dp Material icon visual weight inside the same chip.
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ArkPendingBadge(
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

private enum class ArkHistoryRail {
    BITCOIN,
    LIGHTNING,
    ARK,
}

@Composable
private fun ArkHistoryRailBadge(rail: ArkHistoryRail) {
    val color =
        when (rail) {
            ArkHistoryRail.BITCOIN -> BitcoinOrange
            ArkHistoryRail.LIGHTNING -> LightningYellow
            ArkHistoryRail.ARK -> ArkRust
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.16f)),
    ) {
        when (rail) {
            ArkHistoryRail.BITCOIN ->
                Icon(
                    imageVector = Icons.Default.CurrencyBitcoin,
                    contentDescription = stringResource(R.string.loc_197cebf2),
                    tint = color,
                    modifier = Modifier.size(21.dp),
                )
            ArkHistoryRail.LIGHTNING ->
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.ark_movement_lightning),
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            ArkHistoryRail.ARK ->
                ArkMarkIcon(
                    tint = color,
                    // Match bolt/bitcoin icon footprint in the 22.dp rail chip.
                    modifier =
                        Modifier
                            .size(20.dp)
                            .offset(x = (-1).dp, y = (-2).dp),
                )
        }
    }
}

/** Classify Bark movement rails for the list badge (Bitcoin deposit/withdraw vs LN vs Ark). */
private fun arkMovementRail(movement: ArkMovement): ArkHistoryRail {
    val blob =
        listOf(movement.subsystemName, movement.subsystemKind, movement.status)
            .joinToString(" ")
            .lowercase(Locale.US)
    return when {
        blob.contains("lightning") ||
            blob.contains("bolt") ||
            blob.contains("lnurl") ||
            blob.contains("invoice") ||
            !movement.paymentHash.isNullOrBlank() ||
            !movement.lightningInvoice.isNullOrBlank() ||
            !movement.lightningOffer.isNullOrBlank() ->
            ArkHistoryRail.LIGHTNING
        blob.contains("board") ||
            blob.contains("offboard") ||
            blob.contains("deposit") ||
            blob.contains("withdraw") ||
            blob.contains("onchain") ||
            blob.contains("on-chain") ||
            blob.contains("on_chain") ||
            blob.contains("bitcoin") ->
            ArkHistoryRail.BITCOIN
        else -> ArkHistoryRail.ARK
    }
}

private fun arkMovementIsUnilateralExit(movement: ArkMovement): Boolean {
    val blob =
        listOf(movement.subsystemName, movement.subsystemKind)
            .joinToString(" ")
            .lowercase(Locale.US)
    return blob.contains("exit") && !blob.contains("offboard")
}

private fun arkMovementIsExitClaim(movement: ArkMovement): Boolean =
    movement.subsystemKind.contains("exit-claim", ignoreCase = true)

/** ASP VTXO refresh round fee (not a peer send). */
private fun arkMovementIsRefresh(movement: ArkMovement): Boolean {
    val blob =
        listOf(movement.subsystemName, movement.subsystemKind)
            .joinToString(" ")
            .lowercase(Locale.US)
    if (blob.contains("refresh")) return true
    // Fee-only negative movement with empty peers is typically a refresh round fee.
    if (movement.effectiveBalanceSats >= 0L) return false
    if (blob.contains("board") || blob.contains("offboard") || blob.contains("exit")) return false
    if (blob.contains("lightning") || blob.contains("bolt") || blob.contains("invoice")) return false
    val hasPeer =
        movement.sentToAddresses.any { it.isNotBlank() } ||
            movement.receivedOnAddresses.any { it.isNotBlank() }
    if (hasPeer) return false
    val fee = movement.offchainFeeSats.takeIf { it > 0L } ?: movement.onchainFeeSats?.takeIf { it > 0L }
    val absAmount = abs(movement.effectiveBalanceSats)
    return fee != null && fee == absAmount
}

/** True for center Swap control board/offboard — not peer BTC/LN/Ark send/receive. */
private fun arkMovementIsCenterSwap(
    movement: ArkMovement,
    layer1Transactions: List<TransactionDetails> = emptyList(),
): Boolean {
    val blob =
        listOf(movement.subsystemName, movement.subsystemKind)
            .joinToString(" ")
            .lowercase(Locale.US)
    if (blob.contains("lightning")) return false
    if (arkMovementIsRefresh(movement)) return false
    // Synthetic unboarded deposit is always Received (self-fund or external).
    if (ArkDepositPolicy.isSyntheticPendingOnchainDeposit(movement)) return false
    // Board/deposit inbound: Swap only when L1 funding was center Swap (Transfer).
    if (
        (blob.contains("board") && !blob.contains("offboard")) ||
        blob.contains("deposit")
    ) {
        return arkResolveLayer1Transaction(movement, layer1Transactions)?.isSwapHistory == true
    }
    // Offboard/withdraw: Swap only when L1 leg is tagged center-swap. Self-fund and plain
    // on-chain sends → Sent (not Swap).
    if (blob.contains("offboard") || blob.contains("withdraw")) {
        return arkResolveLayer1Transaction(movement, layer1Transactions)?.isSwapHistory == true
    }
    return false
}

@Composable
private fun arkMovementDisplayTitle(
    movement: ArkMovement,
    layer1Transactions: List<TransactionDetails> = emptyList(),
): String {
    if (arkMovementIsExitClaim(movement)) {
        return stringResource(R.string.ark_exit_claim_history_title)
    }
    if (arkMovementIsUnilateralExit(movement)) {
        return stringResource(R.string.ark_exit_history_title)
    }
    if (arkMovementIsRefresh(movement)) {
        return stringResource(R.string.ark_movement_refresh)
    }
    if (ArkDepositPolicy.isRecoveredOnchainMovement(movement)) {
        return stringResource(R.string.ark_history_title_recovered)
    }
    // Center Swap control board/offboard → Swap; peer pays → Received/Sent. LN excluded.
    if (arkMovementIsCenterSwap(movement, layer1Transactions)) {
        return stringResource(R.string.loc_85a12a5f)
    }
    return if (movement.displayBalanceSats() >= 0) {
        stringResource(R.string.loc_301a5b91)
    } else {
        stringResource(R.string.loc_1af68597)
    }
}

@Composable
private fun arkMovementDisplayStatus(status: String): String {
    val normalized = status.trim().lowercase(Locale.US)
    return when {
        normalized.isBlank() -> stringResource(R.string.loc_1b684325)
        normalized == ArkDepositPolicy.STATUS_BELOW_MIN ->
            stringResource(R.string.ark_history_status_below_min)
        normalized == ArkDepositPolicy.STATUS_RECOVERED_L1 ->
            stringResource(R.string.ark_history_badge_recovered_l1)
        normalized in setOf("pending", "inprogress", "in_progress", "confirming") ->
            stringResource(R.string.loc_1b684325)
        normalized in setOf("complete", "completed", "confirmed", "settled", "success") ->
            stringResource(R.string.loc_4ab75d7f)
        normalized in setOf("failed", "error", "cancelled", "canceled") ->
            stringResource(R.string.ln_node_status_failed)
        else -> humanizeArkJargon(status)
    }
}

private fun arkMovementStatusIsTerminalComplete(status: String): Boolean {
    val n = status.trim().lowercase(Locale.US)
    if (n == ArkDepositPolicy.STATUS_RECOVERED_L1) return true
    return n in
        setOf(
            "complete",
            "completed",
            "confirmed",
            "settled",
            "success",
            "finished",
            "done",
            "ok",
            "finalized",
        ) ||
        n.contains("complete") ||
        n.contains("settled") ||
        n.contains("finish")
}

private fun arkMovementStatusIsExplicitPending(status: String): Boolean {
    val n = status.trim().lowercase(Locale.US)
    if (arkMovementStatusIsTerminalComplete(n) || arkMovementIsFailed(n)) return false
    if (
        n == ArkDepositPolicy.STATUS_BELOW_MIN ||
        n == ArkDepositPolicy.STATUS_RECOVERED_L1
    ) {
        return false
    }
    // Status-only — do not use subsystem names like "board" (those never go away after boarding).
    // Avoid bare "confirm" substring: it matches "confirmed"/"confirmation" and sticks pending.
    return n.isBlank() ||
        n in
        setOf(
            "pending",
            "inprogress",
            "in_progress",
            "confirming",
            "unconfirmed",
            "boarding",
            "mempool",
            "broadcast",
            "broadcasted",
        ) ||
        n.contains("pending") ||
        n.contains("boarding") ||
        n.contains("unconfirm") ||
        n.contains("in_progress") ||
        n.contains("inprogress") ||
        (n.contains("confirming") && !n.contains("confirmed"))
}

private fun arkMovementHasCompletedAt(movement: ArkMovement): Boolean =
    !movement.completedAt.isNullOrBlank()

/**
 * Still waiting for ASP board into spendable VTXOs — true only for unfinished board movements.
 * Once boarded, Bark sets status / completedAt; subsystem still says "board" forever.
 */
private fun arkMovementIsUnfinishedBoardDeposit(movement: ArkMovement): Boolean {
    if (arkMovementIsFailed(movement.status)) return false
    if (ArkDepositPolicy.isRecoveredOnchainMovement(movement)) return false
    if (ArkDepositPolicy.isBelowMinOnchainMovement(movement)) return false
    if (arkMovementHasCompletedAt(movement)) return false
    if (arkMovementStatusIsTerminalComplete(movement.status)) return false
    val kindBlob =
        listOf(movement.subsystemName, movement.subsystemKind)
            .joinToString(" ")
            .lowercase(Locale.US)
    if (!(kindBlob.contains("board") && !kindBlob.contains("offboard"))) return false
    // Unfinished board: no completedAt, status still open / pending-ish.
    return arkMovementStatusIsExplicitPending(movement.status) ||
        movement.status.isBlank() ||
        movement.outputVtxoIds.isEmpty()
}

/** True while Bark has not finished the movement (history row + detail badge). */
private fun arkMovementIsPending(movement: ArkMovement): Boolean {
    if (arkMovementIsFailed(movement.status)) return false
    if (arkMovementHasCompletedAt(movement)) return false
    if (arkMovementStatusIsTerminalComplete(movement.status)) return false
    // Refresh rounds often keep status="pending" after the round tx is known — treat settled.
    if (arkMovementIsRefresh(movement)) {
        if (movement.onchainTxids.any { it.isNotBlank() }) return false
        if (movement.outputVtxoIds.any { it.isNotBlank() }) return false
        return arkMovementStatusIsExplicitPending(movement.status) || movement.status.isBlank()
    }
    if (arkMovementStatusIsExplicitPending(movement.status)) return true
    // Board deposits only while still unfinished — never forever by subsystem name alone.
    return arkMovementIsUnfinishedBoardDeposit(movement)
}

/** Board-rail deposit movement (for note copy), finished or not. */
private fun arkMovementIsBoardDeposit(movement: ArkMovement): Boolean {
    val kindBlob =
        listOf(movement.subsystemName, movement.subsystemKind)
            .joinToString(" ")
            .lowercase(Locale.US)
    return kindBlob.contains("board") && !kindBlob.contains("offboard")
}

private fun arkMovementIsFailed(status: String): Boolean {
    val n = status.trim().lowercase(Locale.US)
    return n in setOf("failed", "error", "cancelled", "canceled") ||
        n.contains("fail") ||
        n.contains("error")
}

private fun arkLooksLikeRawStatus(value: String): Boolean {
    val n = value.trim().lowercase(Locale.US)
    return n in setOf("pending", "complete", "completed", "failed", "error")
}

private fun humanizeArkJargon(value: String): String {
    if (value.isBlank()) return value
    return value
        .replace(Regex("(?i)bark\\.board"), "Deposit")
        .replace(Regex("(?i)bark\\.offboard"), "Withdrawal")
        .replace(Regex("(?i)board\\s*all"), "deposit all")
        .replace(Regex("(?i)offboard\\s*all"), "withdraw all")
        .replace(Regex("(?i)\\bboarding\\b"), "deposit")
        .replace(Regex("(?i)\\boffboarding\\b"), "withdrawal")
        .replace(Regex("(?i)\\boffboard\\b"), "withdraw")
        .replace(Regex("(?i)\\bboard\\b"), "deposit")
        .replace(Regex("(?i)^bark\\."), "")
        .replace('.', ' ')
        .trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

/**
 * Bark exposes movement times as ISO-8601 / RFC3339 strings (sometimes space-separated).
 * Parse to epoch ms, then format with the app's Settings balance date format.
 */
private fun formatArkMovementTimestamp(
    preferred: String,
    fallbacks: List<String>,
    dateFormat: String,
    full: Boolean = false,
): String {
    val rawCandidates = (listOf(preferred) + fallbacks).map { it.trim() }.filter { it.isNotBlank() }
    for (raw in rawCandidates) {
        val millis = parseArkTimestampMillis(raw) ?: continue
        return if (full) {
            formatFullTimestamp(millis, dateFormat)
        } else {
            formatBalanceTimestamp(millis, dateFormat)
        }
    }
    return ""
}

private fun parseArkTimestampMillis(raw: String): Long? {
    val value = raw.trim()
    if (value.isEmpty() || arkLooksLikeRawStatus(value)) return null
    value.toLongOrNull()?.takeIf { it > 0L }?.let { return normalizeArkEpoch(it) }

    // ISO-8601 / RFC3339 via java.time (handles offsets and Z).
    runCatching {
        return Instant.parse(value).toEpochMilli()
    }
    runCatching {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }

    // Common Bark / SQLite variants: "yyyy-MM-dd HH:mm:ss[.SSS][Z|+00:00]"
    val patterns =
        listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
    val normalized = value.replace(' ', 'T', ignoreCase = false).let { candidate ->
        // Squash trailing +0000 without colon.
        if (candidate.matches(Regex(".*[+-]\\d{4}$"))) {
            candidate.dropLast(2) + ":" + candidate.takeLast(2)
        } else {
            candidate
        }
    }
    for (pattern in patterns) {
        val parsed =
            runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(if (pattern.contains('T')) normalized else value)?.time
            }.getOrNull()
        if (parsed != null && parsed > 0L) return parsed
    }
    // Last resort: treat space as T for Instant.
    runCatching {
        return Instant.parse(value.replace(' ', 'T')).toEpochMilli()
    }.getOrNull()
    return null
}

private fun normalizeArkEpoch(timestamp: Long): Long =
    if (timestamp > 10_000_000_000L) timestamp else timestamp * 1000L

@Composable
private fun ArkMovementDetailSheet(
    movement: ArkMovement,
    label: String?,
    privacyMode: Boolean,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    historicalBtcPrice: Double? = null,
    dateFormat: String,
    layer1Transaction: TransactionDetails? = null,
    layer1BlockHeight: UInt? = null,
    requiredBoardConfirmations: Int = ARK_BOARD_REQUIRED_CONFIRMATIONS,
    /** Wallet Ark receive address — display fallback for inbound ARKOOR when Bark omits peers. */
    ownArkAddress: String? = null,
    mempoolUrl: String = "https://mempool.space",
    mempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    onSaveLabel: (String) -> Unit,
    onHideFromHistory: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val amount = movement.displayBalanceSats()
    val isRefresh = arkMovementIsRefresh(movement)
    val isRecoveredL1 = ArkDepositPolicy.isRecoveredOnchainMovement(movement)
    val isReceive = amount >= 0 && !isRefresh
    val absAmount = abs(amount).toULong()
    val accentColor =
        when {
            isRefresh -> ArkRust
            isRecoveredL1 -> BitcoinOrange
            isReceive -> AccentGreen
            else -> AccentRed
        }
    val rail = remember(movement.subsystemName, movement.subsystemKind, movement.onchainTxids) {
        arkMovementRail(movement)
    }
    val isBitcoinRail = rail == ArkHistoryRail.BITCOIN
    val isLightningRail = rail == ArkHistoryRail.LIGHTNING
    val isFailed = arkMovementIsFailed(movement.status)
    val isBelowMin = ArkDepositPolicy.isBelowMinOnchainMovement(movement)
    val isBoardDeposit = arkMovementIsBoardDeposit(movement)
    val isUnfinishedBoard = arkMovementIsUnfinishedBoardDeposit(movement)
    val resolvedRequiredBoardConfirmations =
        movement.requiredBoardConfirmations?.takeIf { it > 0 }
            ?: requiredBoardConfirmations.takeIf { it > 0 }
            ?: ARK_BOARD_REQUIRED_CONFIRMATIONS
    // Prefer positive funding over board=0 (Kotlin `0 ?: funding` stays 0).
    val actualDepositConfirmations =
        ArkDepositPolicy.depositDepthConfirmations(
            boardConfirmations = movement.boardConfirmations,
            fundingConfirmations = movement.fundingConfirmations,
        )
    // Progress toward ASP threshold: board tx confs once boarded, else funding depth.
    // When Bark/Esplora conf fields are still null, fall back to linked L1 depth (or 0)
    // so unfinished onboard always shows Pending X/required — never bare "Pending".
    val movementProgressConfirmations =
        ArkDepositPolicy.progressConfirmations(
            boardTxid = movement.boardTxid,
            boardConfirmations = movement.boardConfirmations,
            fundingConfirmations = movement.fundingConfirmations,
        )
    val progressConfirmations =
        when {
            movementProgressConfirmations != null -> movementProgressConfirmations
            isBoardDeposit && isReceive && (isUnfinishedBoard || arkMovementIsPending(movement)) ->
                if (layer1Transaction != null) {
                    arkBoardConfirmationCount(
                        layer1Transaction,
                        layer1BlockHeight,
                        resolvedRequiredBoardConfirmations,
                    )
                } else {
                    0
                }
            else -> null
        }
    val boardConfProgress =
        if (isBoardDeposit && isReceive) {
            ArkDepositPolicy.boardProgressLabel(
                progressConfirmations,
                resolvedRequiredBoardConfirmations,
            )
                ?: ArkDepositPolicy.boardProgressLabel(0, resolvedRequiredBoardConfirmations)
                ?: "0/$resolvedRequiredBoardConfirmations"
        } else {
            null
        }
    // ASP threshold applies to the board tx only. Pre-board (no board tx yet) is never
    // "met" — deep funding confs alone do not mean boarding happened.
    val boardConfsMet =
        isBoardDeposit &&
            isReceive &&
            !movement.boardTxid.isNullOrBlank() &&
            ArkDepositPolicy.boardConfirmationsMet(
                boardConfirmations = movement.boardConfirmations,
                requiredBoardConfirmations = resolvedRequiredBoardConfirmations,
            )
    val layer1Confirmed = layer1Transaction?.isConfirmed == true
    // Board deposits are pending X/required until the board tx reaches the ASP threshold,
    // then show confirmed (Bark flips the movement shortly after via syncPendingBoards).
    val isPending =
        when {
            isFailed || isBelowMin || isRecoveredL1 -> false
            isUnfinishedBoard -> !boardConfsMet
            else ->
                arkMovementIsPending(movement) &&
                    !(layer1Confirmed && isBitcoinRail && !isBoardDeposit)
        }
    val isConfirmed =
        when {
            isFailed || isBelowMin -> false
            isRecoveredL1 -> true
            isUnfinishedBoard -> boardConfsMet
            isPending -> false
            layer1Confirmed && isBitcoinRail -> true
            layer1Transaction != null -> layer1Transaction.isConfirmed || isBoardDeposit
            else -> true
        }
    val statusColor =
        when {
            isFailed -> ErrorRed
            isBelowMin -> WarningYellow
            isRecoveredL1 -> BitcoinOrange
            isConfirmed -> AccentGreen
            else -> BitcoinOrange
        }
    val pendingLabel = stringResource(R.string.loc_1b684325)
    val statusLabel =
        when {
            isFailed -> arkMovementDisplayStatus(movement.status)
            isBelowMin -> stringResource(R.string.ark_history_status_below_min)
            isRecoveredL1 -> stringResource(R.string.ark_history_badge_recovered_l1)
            isUnfinishedBoard && boardConfsMet ->
                stringResource(R.string.loc_4ab75d7f)
            (isUnfinishedBoard || (isBoardDeposit && isPending)) && boardConfProgress != null ->
                "$pendingLabel $boardConfProgress"
            isPending -> pendingLabel
            layer1Transaction != null && layer1Transaction.isConfirmed ->
                stringResource(R.string.loc_4ab75d7f)
            layer1Transaction != null && !layer1Transaction.isConfirmed && !isBoardDeposit ->
                pendingLabel
            else -> arkMovementDisplayStatus(movement.status)
        }
    val directionLabel = arkMovementDisplayTitle(movement, layer1Transactions = listOfNotNull(layer1Transaction))
    val railLabel =
        when (rail) {
            ArkHistoryRail.BITCOIN -> stringResource(R.string.loc_197cebf2)
            ArkHistoryRail.LIGHTNING -> stringResource(R.string.ark_movement_lightning)
            ArkHistoryRail.ARK -> stringResource(R.string.ark_title)
        }
    val amountText =
        if (privacyMode) {
            ARK_HIDDEN_AMOUNT
        } else if (isFailed || isRecoveredL1) {
            formatAmount(absAmount, useSats, includeUnit = true)
        } else {
            "${if (isReceive) "+" else "-"}${formatAmount(absAmount, useSats, includeUnit = true)}"
        }
    val fullTimestamp =
        remember(
            movement.createdAt,
            movement.completedAt,
            movement.updatedAt,
            layer1Transaction?.timestamp,
            dateFormat,
        ) {
            val fromMovement =
                formatArkMovementTimestamp(
                    preferred = movement.createdAt,
                    fallbacks = listOf(movement.completedAt.orEmpty(), movement.updatedAt),
                    dateFormat = dateFormat,
                    full = true,
                )
            if (fromMovement.isNotBlank()) {
                fromMovement
            } else {
                layer1Transaction?.timestamp?.takeIf { it > 0L }?.let {
                    formatFullTimestamp(it, dateFormat)
                }.orEmpty()
            }
        }
    // LN has no on-chain txid / explorer (payment hashes are 64-hex and must not surface here).
    val onchainTxid =
        if (isLightningRail) {
            null
        } else {
            movement.onchainTxids.firstOrNull()
                ?: layer1Transaction?.txid?.takeIf { it.isNotBlank() }
        }
    val showOnchainExplorer =
        mempoolServer != SecureStorage.MEMPOOL_DISABLED && mempoolUrl.isNotBlank()
    val lightningFeeSats =
        if (isLightningRail && !isReceive) {
            movement.offchainFeeSats.takeIf { it > 0L }
        } else {
            null
        }
    val refreshFeeSats =
        if (isRefresh) {
            movement.offchainFeeSats.takeIf { it > 0L }
                ?: movement.onchainFeeSats?.takeIf { it > 0L }
                ?: abs(amount).takeIf { it > 0L }
        } else {
            null
        }
    val networkFeeSats =
        when {
            isRefresh -> refreshFeeSats
            isLightningRail -> lightningFeeSats
            else ->
                movement.onchainFeeSats?.takeIf { it > 0L }
                    ?: layer1Transaction?.fee?.toLong()?.takeIf { it > 0L }
                    ?: movement.offchainFeeSats.takeIf { it > 0L && !isReceive }
        }
    // Prefer Bark addresses / Ibis-stored dest; receive falls back to wallet Ark address
    // (display only — not written into history, so it won't force receive-address rotation).
    // Board deposits: L1 counterparty address is the paid deposit address (most accurate).
    // Refresh rounds have no peer — omit recipient entirely.
    val recoveredDestination =
        movement.sentToAddresses
            .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("{") }
    val recipientOrSource =
        when {
            isRefresh || isLightningRail -> null
            isRecoveredL1 ->
                recoveredDestination
                    ?: layer1Transaction?.address?.takeIf { it.isNotBlank() }
            isReceive && isBoardDeposit ->
                movement.receivedOnAddresses
                    .firstOrNull {
                        it.isNotBlank() &&
                            !it.trimStart().startsWith("{") &&
                            looksLikeBitcoinAddress(it)
                    }
                    ?: layer1Transaction?.address?.takeIf {
                        it.isNotBlank() && looksLikeBitcoinAddress(it)
                    }
            isReceive ->
                movement.receivedOnAddresses
                    .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("{") }
                    ?: layer1Transaction?.address?.takeIf { it.isNotBlank() }
                    ?: ownArkAddress?.takeIf {
                        it.isNotBlank() && !isBitcoinRail
                    }
            else ->
                movement.sentToAddresses
                    .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("{") }
                    ?: layer1Transaction?.address?.takeIf { it.isNotBlank() }
        }
    // Fee row: refresh fee, on-chain / Ark sends, and Lightning sends when fee is known.
    val showArkFeeRow =
        isRefresh ||
            (!isReceive && networkFeeSats != null && networkFeeSats > 0L)
    // LN invoice amount = what was paid to the invoice (exclude routing/offchain fee).
    val lightningInvoiceAmountSats =
        if (isLightningRail && !isReceive) {
            val total = abs(amount)
            val fee = lightningFeeSats ?: 0L
            val intended = abs(movement.intendedBalanceSats).takeIf { it > 0L }
            when {
                intended != null && intended <= total -> intended
                fee > 0L && fee < total -> total - fee
                else -> total
            }
        } else {
            null
        }
    // Match header amount (Ark movement net). L1 addressAmount is the on-chain
    // output before ASP board fee — using it caused mismatches (e.g. 73138 vs 73055).
    // Refresh is fee-only — no recipient amount row.
    val recipientAmountSats =
        when {
            isRefresh -> null
            privacyMode -> null
            isBoardDeposit || isBitcoinRail -> abs(amount)
            layer1Transaction?.addressAmount != null && layer1Transaction.addressAmount > 0u ->
                layer1Transaction.addressAmount.toLong()
            else -> abs(amount)
        }
    val paymentHash = movement.paymentHash?.takeIf { it.isNotBlank() }
    val invoice = movement.lightningInvoice?.takeIf { it.isNotBlank() }
    val feeColor =
        when (rail) {
            ArkHistoryRail.LIGHTNING -> LightningYellow
            ArkHistoryRail.BITCOIN -> BitcoinOrange
            ArkHistoryRail.ARK -> ArkRust
        }

    var showCopiedAmount by remember { mutableStateOf(false) }
    var showCopiedTxid by remember { mutableStateOf(false) }
    var showCopiedRecipient by remember { mutableStateOf(false) }
    var showCopiedHash by remember { mutableStateOf(false) }
    var showCopiedInvoice by remember { mutableStateOf(false) }
    var showTorBrowserError by remember { mutableStateOf(false) }
    var isEditingLabel by remember { mutableStateOf(false) }
    var labelText by remember(label) { mutableStateOf(label.orEmpty()) }
    val scrollState = rememberScrollState()

    if (showTorBrowserError) {
        ArkTorBrowserErrorDialog(onDismiss = { showTorBrowserError = false })
    }

    LaunchedEffect(showCopiedAmount) {
        if (showCopiedAmount) {
            delay(3000)
            showCopiedAmount = false
        }
    }
    LaunchedEffect(showCopiedTxid) {
        if (showCopiedTxid) {
            delay(3000)
            showCopiedTxid = false
        }
    }
    LaunchedEffect(showCopiedRecipient) {
        if (showCopiedRecipient) {
            delay(3000)
            showCopiedRecipient = false
        }
    }
    LaunchedEffect(showCopiedHash) {
        if (showCopiedHash) {
            delay(3000)
            showCopiedHash = false
        }
    }
    LaunchedEffect(showCopiedInvoice) {
        if (showCopiedInvoice) {
            delay(3000)
            showCopiedInvoice = false
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
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkCard.copy(alpha = 0.72f))
                                    .clickable(onClick = onHideFromHistory),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription =
                                    stringResource(R.string.transaction_history_hide_confirm),
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector =
                                    when {
                                        isRefresh -> Icons.Default.Sync
                                        isRecoveredL1 -> Icons.Default.Undo
                                        isReceive -> Icons.AutoMirrored.Filled.CallReceived
                                        else -> Icons.AutoMirrored.Filled.CallMade
                                    },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    if (isRefresh) {
                                        directionLabel
                                    } else {
                                        stringResource(
                                            R.string.spark_payment_direction_with_rail_format,
                                            directionLabel,
                                            railLabel,
                                        )
                                    },
                                style = MaterialTheme.typography.titleSmall,
                                color = accentColor,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = amountText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isFailed) TextSecondary else accentColor,
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
                                    color = ArkRust,
                                )
                            }
                            val effectiveBtcPrice = historicalBtcPrice ?: btcPrice
                            if (effectiveBtcPrice != null && effectiveBtcPrice > 0 && !privacyMode) {
                                Spacer(modifier = Modifier.height(2.dp))
                                ArkHistoricalFiatText(
                                    text =
                                        formatFiat(
                                            abs(amount) / 100_000_000.0 * effectiveBtcPrice,
                                            fiatCurrency,
                                        ),
                                    isHistorical = historicalBtcPrice != null && historicalBtcPrice > 0,
                                    large = true,
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
                                if (showOnchainExplorer) {
                                    onchainTxid?.let { explorerTxid ->
                                        ArkExplorerBadge(
                                            tint = BitcoinOrange,
                                            onClick = {
                                                arkOpenBitcoinExplorer(
                                                    context = context,
                                                    mempoolUrl = mempoolUrl,
                                                    txid = explorerTxid,
                                                    onTorBrowserMissing = { showTorBrowserError = true },
                                                )
                                            },
                                        )
                                    }
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
                                if (fullTimestamp.isNotBlank()) {
                                    Text(
                                        text = fullTimestamp,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            // Bitcoin rails: txid → received at/recipient → fee (L1-style).
                            // Other rails keep payment/movement identifiers first.
                            if (onchainTxid != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Text(
                                    text =
                                        if (isRefresh) {
                                            stringResource(R.string.ark_refresh_round_txid_title)
                                        } else {
                                            stringResource(R.string.loc_13e398d0)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ArkDetailCopyRow(
                                    value = onchainTxid,
                                    copyLabel =
                                        if (isRefresh) {
                                            stringResource(R.string.ark_refresh_round_txid_copy)
                                        } else {
                                            stringResource(R.string.loc_09d663eb)
                                        },
                                    copied = showCopiedTxid,
                                    accentColor = if (isRefresh) ArkRust else BitcoinOrange,
                                    showFullValue = true,
                                    onCopy = {
                                        SecureClipboard.copyAndScheduleClear(context, onchainTxid)
                                        showCopiedTxid = true
                                    },
                                )
                                if (showCopiedTxid) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isRefresh) ArkRust else BitcoinOrange,
                                    )
                                }
                            }

                            // Recipient / Received at — non-LN peer payments only.
                            // Refresh fee has no counterparty; omit the unknown-recipient row.
                            val showRecipientSection =
                                !isLightningRail && !isRefresh && recipientOrSource != null
                            if (showRecipientSection) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Text(
                                    text =
                                        when {
                                            isRecoveredL1 ->
                                                stringResource(R.string.ark_history_recovered_to)
                                            isReceive ->
                                                stringResource(R.string.loc_b47edf23)
                                            else ->
                                                stringResource(R.string.loc_eaf579ea)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val addressAmountText =
                                    if (privacyMode) {
                                        ARK_HIDDEN_AMOUNT
                                    } else if (recipientAmountSats != null) {
                                        val formatted =
                                            formatAmount(
                                                recipientAmountSats.toULong(),
                                                useSats,
                                                includeUnit = true,
                                            )
                                        when {
                                            isRecoveredL1 -> formatted
                                            isReceive -> "+$formatted"
                                            else -> "-$formatted"
                                        }
                                    } else {
                                        amountText
                                    }
                                val peerDisplay = recipientOrSource.orEmpty()
                                ArkDetailCopyRow(
                                    value = peerDisplay,
                                    copyLabel =
                                        when {
                                            isRecoveredL1 ->
                                                stringResource(R.string.ark_history_recovered_to)
                                            isReceive ->
                                                stringResource(R.string.loc_b47edf23)
                                            else ->
                                                stringResource(R.string.loc_eaf579ea)
                                        },
                                    copied = showCopiedRecipient,
                                    accentColor = if (isBitcoinRail) BitcoinOrange else ArkRust,
                                    amountText = addressAmountText,
                                    amountColor = accentColor,
                                    onCopy = {
                                        SecureClipboard.copyAndScheduleClear(context, peerDisplay)
                                        showCopiedRecipient = true
                                    },
                                )
                                if (showCopiedRecipient) {
                                    Text(
                                        text = stringResource(R.string.loc_e287255d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isBitcoinRail) BitcoinOrange else ArkRust,
                                    )
                                }
                            }

                            // Fee on sends / refresh when known and > 0 (Spark-style).
                            // Zero ARKOOR fee is omitted. Refresh is fee-only (no recipient).
                            if (showArkFeeRow) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Text(
                                    text =
                                        when {
                                            isRefresh ->
                                                stringResource(R.string.ark_review_fee_refresh)
                                            isLightningRail ->
                                                stringResource(R.string.ark_review_fee_lightning)
                                            isBitcoinRail ->
                                                stringResource(R.string.loc_f72cc482)
                                            else ->
                                                stringResource(R.string.ark_review_fee_ark)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val displayFeeRate = layer1Transaction?.feeRate
                                val displayVsize = layer1Transaction?.vsize
                                if (
                                    !isRefresh &&
                                        displayFeeRate != null &&
                                        displayVsize != null &&
                                        !privacyMode
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.liquid_fee_rate_vbytes_format,
                                                formatFeeRate(displayFeeRate),
                                                formatVBytes(displayVsize),
                                            ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                }
                                val feeDisplaySats = networkFeeSats ?: 0L
                                Text(
                                    text =
                                        if (privacyMode) {
                                            ARK_HIDDEN_AMOUNT
                                        } else {
                                            "-${formatAmount(feeDisplaySats.toULong(), useSats, includeUnit = true)}"
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isRefresh) ArkRust else feeColor,
                                )
                            }

                            if (isBitcoinRail && !isRefresh && !isRecoveredL1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = TextSecondary.copy(alpha = 0.1f),
                                )
                                Text(
                                    text = stringResource(R.string.ark_deposit_note_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text =
                                        if (isReceive) {
                                            when {
                                                isConfirmed && !isUnfinishedBoard ->
                                                    stringResource(
                                                        R.string.ark_deposit_note_confirmed_format,
                                                        resolvedRequiredBoardConfirmations,
                                                    )
                                                boardConfsMet && isUnfinishedBoard ->
                                                    stringResource(
                                                        R.string.ark_deposit_note_confirmed_format,
                                                        resolvedRequiredBoardConfirmations,
                                                    )
                                                else ->
                                                    stringResource(
                                                        R.string.ark_deposit_note_pending_format,
                                                        resolvedRequiredBoardConfirmations,
                                                    )
                                            }
                                        } else {
                                            stringResource(R.string.ark_withdrawal_note)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }

                            // LN: Invoice (with amount under it) then payment hash — matches LN Node details.
                            if (isLightningRail) {
                                invoice?.let { inv ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = TextSecondary.copy(alpha = 0.1f),
                                    )
                                    Text(
                                        text = stringResource(R.string.loc_5fd82ed8),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val invoiceAmountText =
                                        if (privacyMode) {
                                            ARK_HIDDEN_AMOUNT
                                        } else {
                                            val invoiceSats =
                                                lightningInvoiceAmountSats ?: abs(amount)
                                            "-${formatAmount(invoiceSats.toULong(), useSats, includeUnit = true)}"
                                        }
                                    ArkDetailCopyRow(
                                        value = inv,
                                        copyLabel = stringResource(R.string.loc_a1329beb),
                                        copied = showCopiedInvoice,
                                        accentColor = LightningYellow,
                                        amountText = invoiceAmountText,
                                        amountColor = accentColor,
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(context, inv)
                                            showCopiedInvoice = true
                                        },
                                    )
                                    if (showCopiedInvoice) {
                                        Text(
                                            text = stringResource(R.string.loc_e287255d),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LightningYellow,
                                        )
                                    }
                                }

                                paymentHash?.let { hash ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = TextSecondary.copy(alpha = 0.1f),
                                    )
                                    Text(
                                        text = stringResource(R.string.ln_node_payment_hash),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ArkDetailCopyRow(
                                        value = hash,
                                        copyLabel = stringResource(R.string.ln_node_copy_payment_hash),
                                        copied = showCopiedHash,
                                        accentColor = LightningYellow,
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(context, hash)
                                            showCopiedHash = true
                                        },
                                    )
                                    if (showCopiedHash) {
                                        Text(
                                            text = stringResource(R.string.loc_e287255d),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LightningYellow,
                                        )
                                    }
                                }
                            } else {
                                paymentHash?.let { hash ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = TextSecondary.copy(alpha = 0.1f),
                                    )
                                    Text(
                                        text = stringResource(R.string.ln_node_payment_hash),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ArkDetailCopyRow(
                                        value = hash,
                                        copyLabel = stringResource(R.string.ln_node_copy_payment_hash),
                                        copied = showCopiedHash,
                                        accentColor = LightningYellow,
                                        onCopy = {
                                            SecureClipboard.copyAndScheduleClear(context, hash)
                                            showCopiedHash = true
                                        },
                                    )
                                    if (showCopiedHash) {
                                        Text(
                                            text = stringResource(R.string.loc_e287255d),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LightningYellow,
                                        )
                                    }
                                }

                                invoice?.let { inv ->
                                    if (inv != recipientOrSource) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            color = TextSecondary.copy(alpha = 0.1f),
                                        )
                                        Text(
                                            text = stringResource(R.string.loc_5fd82ed8),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ArkDetailCopyRow(
                                            value = inv,
                                            copyLabel = stringResource(R.string.loc_a1329beb),
                                            copied = showCopiedInvoice,
                                            accentColor = LightningYellow,
                                            onCopy = {
                                                SecureClipboard.copyAndScheduleClear(context, inv)
                                                showCopiedInvoice = true
                                            },
                                        )
                                        if (showCopiedInvoice) {
                                            Text(
                                                text = stringResource(R.string.loc_e287255d),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LightningYellow,
                                            )
                                        }
                                    }
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
                                            focusedBorderColor = ArkRust,
                                            unfocusedBorderColor = BorderColor,
                                            cursorColor = ArkRust,
                                        ),
                                    trailingIcon = {
                                        TextButton(
                                            onClick = {
                                                onSaveLabel(labelText)
                                                isEditingLabel = false
                                            },
                                        ) {
                                            Text(
                                                text = stringResource(R.string.loc_f55495e0),
                                                color = ArkRust,
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                EditableLabelChip(
                                    label = labelText.takeIf { it.isNotBlank() },
                                    accentColor = ArkRust,
                                    onClick = { isEditingLabel = true },
                                    onDelete =
                                        if (labelText.isNotBlank()) {
                                            {
                                                labelText = ""
                                                onSaveLabel("")
                                            }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ArkDetailCopyRow(
    value: String,
    copyLabel: String,
    copied: Boolean,
    accentColor: Color,
    onCopy: () -> Unit,
    amountText: String? = null,
    amountColor: Color = TextSecondary,
    /**
     * When true, show the full value and let layout wrap (txids — matches L1).
     * When false, apply L1 address-style head...tail truncation.
     */
    showFullValue: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showFullValue) value else arkTruncateDetailValue(value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (showFullValue) 2 else 1,
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

/** Same head/tail truncation as L1 [truncateSwapDetailValue] for addresses / long ids. */
private fun arkTruncateDetailValue(
    value: String,
    leadingChars: Int = 16,
    trailingChars: Int = 8,
): String {
    if (value.length <= leadingChars + trailingChars + 3) return value
    return "${value.take(leadingChars)}...${value.takeLast(trailingChars)}"
}

/** Fallback ASP board depth when [ArkInfo.requiredBoardConfirmations] is unknown (Second ASP uses 6). */
private const val ARK_BOARD_REQUIRED_CONFIRMATIONS = 6

private fun looksLikeBitcoinAddress(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty() || v.startsWith("{")) return false
    return v.startsWith("bc1", ignoreCase = true) ||
        v.startsWith("tb1", ignoreCase = true) ||
        v.startsWith("1") ||
        v.startsWith("3") ||
        v.startsWith("2") ||
        v.startsWith("m") ||
        v.startsWith("n")
}

private fun arkBoardConfirmationCount(
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
    requiredConfirmations: Int = ARK_BOARD_REQUIRED_CONFIRMATIONS,
): Int {
    val required = requiredConfirmations.takeIf { it > 0 } ?: ARK_BOARD_REQUIRED_CONFIRMATIONS
    val transaction = layer1Transaction ?: return 0
    if (!transaction.isConfirmed) return 0
    val confirmationHeight = transaction.confirmationTime?.height ?: return 1
    val blockHeight = layer1BlockHeight ?: return 1
    return (blockHeight.toLong() - confirmationHeight.toLong() + 1L)
        .coerceAtLeast(1L)
        .coerceAtMost(required.toLong())
        .toInt()
}

private fun arkBoardConfirmationProgress(
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
    requiredConfirmations: Int = ARK_BOARD_REQUIRED_CONFIRMATIONS,
): String {
    val required = requiredConfirmations.takeIf { it > 0 } ?: ARK_BOARD_REQUIRED_CONFIRMATIONS
    val confs = arkBoardConfirmationCount(layer1Transaction, layer1BlockHeight, required)
    return "$confs/$required"
}

private fun arkBoardHasRequiredConfirmations(
    layer1Transaction: TransactionDetails?,
    layer1BlockHeight: UInt?,
    requiredConfirmations: Int = ARK_BOARD_REQUIRED_CONFIRMATIONS,
): Boolean {
    val required = requiredConfirmations.takeIf { it > 0 } ?: ARK_BOARD_REQUIRED_CONFIRMATIONS
    val transaction = layer1Transaction ?: return false
    if (!transaction.isConfirmed) return false
    val confirmationHeight = transaction.confirmationTime?.height ?: return false
    val blockHeight = layer1BlockHeight ?: return false
    return blockHeight.toLong() - confirmationHeight.toLong() + 1L >= required.toLong()
}

/**
 * Prefer explicit metadata txids, then amount/time heuristics.
 *
 * Important: Ibis L1 → Ark appears as an **outgoing** L1 spend to Bark's deposit
 * address, while the Ark movement is **incoming**. Match across directions.
 * External deposits only appear in L1 history when this wallet funded them.
 */
private fun arkResolveLayer1Transaction(
    movement: ArkMovement,
    layer1Transactions: List<TransactionDetails>,
): TransactionDetails? {
    if (layer1Transactions.isEmpty()) return null
    // Lightning has no L1 counterpart; never match payment-hash hex against chain txids.
    if (arkMovementRail(movement) == ArkHistoryRail.LIGHTNING) return null
    movement.onchainTxids.firstOrNull()?.let { txid ->
        layer1Transactions.firstOrNull { it.txid.equals(txid, ignoreCase = true) }?.let { return it }
    }
    val isArkReceive = movement.effectiveBalanceSats >= 0
    val absAmount = abs(movement.effectiveBalanceSats)
    if (absAmount <= 0L) return null
    val depositAddrs =
        (
            movement.receivedOnAddresses +
                movement.sentToAddresses
        ).map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val movementMs =
        parseArkTimestampMillis(movement.createdAt)
            ?: parseArkTimestampMillis(movement.completedAt.orEmpty())
            ?: parseArkTimestampMillis(movement.updatedAt)
    // Deposit (Ark +): match L1 sends whose recipient amount/outflow ≈ deposit.
    // Withdrawal (Ark -): match L1 receives/inflows of similar size.
    val candidates =
        layer1Transactions.filter { tx ->
            val txAbs = abs(tx.amountSats)
            val addressAmt = tx.addressAmount?.toLong()
            val addressMatches =
                depositAddrs.isNotEmpty() &&
                    tx.address?.let { addr ->
                        depositAddrs.any { it.equals(addr, ignoreCase = true) }
                    } == true
            val amountMatches =
                addressMatches ||
                    txAbs == absAmount ||
                    addressAmt == absAmount ||
                    // L1 send total can exceed board amount by the miner fee.
                    (
                        isArkReceive &&
                            tx.amountSats < 0L &&
                            txAbs >= absAmount &&
                            txAbs - absAmount < absAmount
                    )
            if (!amountMatches) return@filter false
            if (isArkReceive) tx.amountSats < 0L else tx.amountSats > 0L
        }
    if (candidates.isEmpty()) return null
    // Prefer exact deposit-address match when available.
    val addressRanked =
        candidates.sortedByDescending { tx ->
            tx.address?.let { addr ->
                depositAddrs.any { it.equals(addr, ignoreCase = true) }
            } == true
        }
    if (movementMs == null) {
        return addressRanked.maxByOrNull { it.timestamp ?: 0L }
    }
    return addressRanked.minByOrNull { tx ->
        val txMs = tx.timestamp?.let(::normalizeArkEpoch) ?: Long.MAX_VALUE / 4
        abs(txMs - movementMs)
    }?.takeIf { tx ->
        val txMs = tx.timestamp?.let(::normalizeArkEpoch) ?: return@takeIf true
        // Within ~3 days — deposits can lag while waiting confs / board.
        abs(txMs - movementMs) <= 3L * 24 * 60 * 60 * 1000
    }
}

@Composable
private fun ArkExplorerBadge(
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
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun arkOpenBitcoinExplorer(
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

@Composable
private fun ArkTorBrowserErrorDialog(onDismiss: () -> Unit) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.loc_d2c0aec0), color = ArkRust)
                    }
                }
            }
        }
    }
}
