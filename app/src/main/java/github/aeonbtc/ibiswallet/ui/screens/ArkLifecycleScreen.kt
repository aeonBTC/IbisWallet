package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkAutoDbBackupInfo
import github.aeonbtc.ibiswallet.data.model.ArkLifecycleState
import github.aeonbtc.ibiswallet.data.model.ArkOnchainUtxo
import github.aeonbtc.ibiswallet.data.model.ArkVtxo
import github.aeonbtc.ibiswallet.data.model.ArkWalletState
import github.aeonbtc.ibiswallet.data.repository.ArkBarkMappers
import github.aeonbtc.ibiswallet.data.repository.ArkDepositPolicy
import github.aeonbtc.ibiswallet.data.repository.ArkUnilateralExitPolicy
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.ArkRust
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.viewmodel.ArkDbTransferProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArkLifecycleScreen(
    arkState: ArkWalletState,
    lifecycleState: ArkLifecycleState,
    denomination: String,
    privacyMode: Boolean,
    dateFormat: String = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
    autoDelegatedRefreshEnabled: Boolean = false,
    onAutoDelegatedRefreshEnabledChange: (Boolean) -> Unit = {},
    autoBoardEnabled: Boolean = false,
    onAutoBoardEnabledChange: (Boolean) -> Unit = {},
    autoDbBackupEnabled: Boolean = true,
    onAutoDbBackupEnabledChange: (Boolean) -> Unit = {},
    autoDbBackupFolderUri: String? = null,
    autoDbBackupLastMs: Long = 0L,
    latestAutoDbBackup: ArkAutoDbBackupInfo? = null,
    onPickAutoDbBackupFolder: () -> Unit = {},
    onPrepareRefresh: (List<String>) -> Unit,
    onExecuteRefresh: () -> Unit,
    onExportArkDb: (Uri) -> Unit = {},
    onImportArkDb: (Uri) -> Unit = {},
    dbTransferInProgress: ArkDbTransferProgress? = null,
    onStartExit: (List<String>, Boolean) -> Unit,
    onProgressExits: () -> Unit,
    onPrepareClaim: (String, List<String>) -> Unit,
    onExecuteClaim: () -> Unit,
    onBoardAll: () -> Unit = {},
    onBoardAmount: (Long) -> Unit = {},
    onTopUpOnchain: () -> Unit = {},
    onRecoverOnchain: () -> Unit = {},
    /** Layer 1 receive address shown in the recover confirmation dialog. */
    recoverDestinationAddress: String? = null,
    /** Called when opening recover dialog if L1 address is missing. */
    onEnsureRecoverAddress: () -> Unit = {},
    isBoarding: Boolean = false,
    isRecoveringOnchain: Boolean = false,
    onReset: () -> Unit,
    onBack: () -> Unit = {},
    initialTab: String? = null,
) {
    var showRecoverConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val suggestedExportName =
        remember {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            "ibis-ark-db-$stamp.zip"
        }
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri: Uri? ->
            uri?.let(onExportArkDb)
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let(onImportArkDb)
        }
    val latestBackup = latestAutoDbBackup
    val latestBackupTime =
        remember(latestBackup?.timestampMs, autoDbBackupLastMs, dateFormat) {
            val ms = latestBackup?.timestampMs?.takeIf { it > 0L } ?: autoDbBackupLastMs
            if (ms <= 0L) {
                null
            } else {
                formatFullTimestamp(ms, dateFormat)
            }
        }
    val autoDbBackupFolderPath =
        remember(autoDbBackupFolderUri, context) {
            autoDbBackupFolderUri
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveSafTreeDisplayPath(context, it) }
                ?.let { shortenBackupLocationPath(it) }
        }
    val latestBackupSizeLabel =
        remember(latestBackup?.sizeBytes) {
            latestBackup?.let { formatArkBackupSize(it.sizeBytes) }
        }
    var showDirectoryLinkedToast by remember { mutableStateOf(false) }
    var previousAutoDbBackupFolderUri by remember { mutableStateOf(autoDbBackupFolderUri) }
    LaunchedEffect(autoDbBackupFolderUri) {
        val previous = previousAutoDbBackupFolderUri
        previousAutoDbBackupFolderUri = autoDbBackupFolderUri
        val linkedNow = !autoDbBackupFolderUri.isNullOrBlank()
        val justLinked =
            linkedNow &&
                autoDbBackupFolderUri != previous
        if (justLinked) {
            showDirectoryLinkedToast = true
            kotlinx.coroutines.delay(DIRECTORY_LINKED_TOAST_MS)
            showDirectoryLinkedToast = false
        } else if (!linkedNow) {
            showDirectoryLinkedToast = false
        }
    }
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var selectedRefresh by remember { mutableStateOf(setOf<String>()) }
    var showRefreshReview by rememberSaveable { mutableStateOf(false) }
    var closedPendingRefresh by rememberSaveable { mutableStateOf(false) }
    // Start-exit selection only — never seeded from claimable ids.
    var selectedExit by remember { mutableStateOf(setOf<String>()) }
    var claimAddress by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(
        arkState.vtxos,
        arkState.vtxosToRefresh,
        arkState.firstExpiringHeight,
        arkState.pendingRefreshVtxoIds,
    ) {
        val pendingIds = arkState.pendingRefreshVtxoIds.toSet()
        val selectableIds =
            arkState.vtxos
                .filter {
                    ArkBarkMappers.isSpendableLabel(it.state) && it.id !in pendingIds
                }
                .map { it.id }
                .toSet()
        selectedRefresh = selectedRefresh.intersect(selectableIds)
        if (selectedRefresh.isEmpty()) {
            val defaults =
                if (arkState.vtxosToRefresh.isNotEmpty()) {
                    arkState.vtxosToRefresh
                } else {
                    val firstExpiry = arkState.firstExpiringHeight
                    arkState.vtxos.filter { it.expiryHeight == firstExpiry }
                }
            selectedRefresh =
                defaults
                    .filter { it.id in selectableIds }
                    .map { it.id }
                    .toSet()
        }
    }
    LaunchedEffect(arkState.vtxos.map { it.id }) {
        val spendable = arkState.vtxos.map { it.id }.toSet()
        selectedExit = selectedExit.intersect(spendable)
    }
    LaunchedEffect(lifecycleState, closedPendingRefresh) {
        if (
            closedPendingRefresh &&
            (lifecycleState is ArkLifecycleState.Completed || lifecycleState is ArkLifecycleState.Error)
        ) {
            closedPendingRefresh = false
            onReset()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ark_lifecycle_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        val defaultTab =
            remember(initialTab) {
                runCatching {
                    initialTab?.let { ArkManageTab.valueOf(it) }
                }.getOrNull() ?: ArkManageTab.BOARDING
            }
        var selectedTab by rememberSaveable(initialTab) { mutableStateOf(defaultTab.name) }
        val currentTab =
            remember(selectedTab) {
                runCatching { ArkManageTab.valueOf(selectedTab) }.getOrDefault(ArkManageTab.BOARDING)
            }

        ArkManageTabBar(
            current = currentTab,
            boardingLabel = stringResource(R.string.ark_manage_tab_boarding),
            refreshLabel = stringResource(R.string.ark_manage_tab_refresh),
            exitLabel = stringResource(R.string.ark_manage_tab_exit),
            backupLabel = stringResource(R.string.ark_manage_tab_backup),
            onSelect = { selectedTab = it.name },
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (currentTab) {
            ArkManageTab.BOARDING -> {
                ArkBoardingTabContent(
                    arkState = arkState,
                    useSats = denomination != SecureStorage.DENOMINATION_BTC,
                    privacyMode = privacyMode,
                    autoBoardEnabled = autoBoardEnabled,
                    onAutoBoardEnabledChange = onAutoBoardEnabledChange,
                    onBoardAll = onBoardAll,
                    onBoardAmount = onBoardAmount,
                    onTopUpOnchain = onTopUpOnchain,
                    onRecoverOnchain = {
                        if (recoverDestinationAddress.isNullOrBlank()) {
                            onEnsureRecoverAddress()
                        }
                        showRecoverConfirmDialog = true
                    },
                    isBoarding = isBoarding,
                    isRecoveringOnchain = isRecoveringOnchain,
                )
            }
            ArkManageTab.REFRESH -> {
                val tipHeight = arkState.chainTipHeight
                // Source of truth for badges / re-refresh lock — survives dialog close & Loading.
                val pendingRefreshIds =
                    remember(arkState.pendingRefreshVtxoIds) {
                        arkState.pendingRefreshVtxoIds.toSet()
                    }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.ark_refresh_new_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Include locked/in-round outputs so pending-refresh badges stay visible
                        // after Bark moves the VTXO out of Spendable mid-round.
                        val listedVtxos =
                            remember(arkState.vtxos, pendingRefreshIds) {
                                val spendable =
                                    arkState.vtxos.filter {
                                        ArkBarkMappers.isSpendableLabel(it.state)
                                    }
                                val pendingLocked =
                                    arkState.vtxos.filter { vtxo ->
                                        vtxo.id in pendingRefreshIds &&
                                            spendable.none { it.id == vtxo.id }
                                    }
                                (pendingLocked + spendable).distinctBy { it.id }
                            }
                        val refreshBusy =
                            lifecycleState is ArkLifecycleState.Loading ||
                                lifecycleState is ArkLifecycleState.InProgress
                        // Only block a new review while quote/submit is in-flight or the dialog is open.
                        val reviewOpen =
                            showRefreshReview ||
                                lifecycleState is ArkLifecycleState.Loading ||
                                lifecycleState is ArkLifecycleState.InProgress
                        val selectableSelected =
                            selectedRefresh.filter { it !in pendingRefreshIds }

                        if (listedVtxos.isEmpty()) {
                            Text(stringResource(R.string.ark_refresh_no_outputs), color = TextSecondary)
                        } else {
                            listedVtxos.take(40).forEach { vtxo ->
                                // Badge only the VTXOs the user (or auto) actually submitted.
                                val isPendingRefresh = vtxo.id in pendingRefreshIds
                                ArkVtxoSelectRow(
                                    amountLabel =
                                        if (privacyMode) ARK_HIDDEN_AMOUNT else formatArkAmount(vtxo.amountSats, useSats),
                                    expiryLabel = arkVtxoExpiryLabel(vtxo, tipHeight),
                                    selected =
                                        !isPendingRefresh && selectedRefresh.contains(vtxo.id),
                                    state = vtxo.state,
                                    pendingRefresh = isPendingRefresh,
                                    pendingRefreshLabel =
                                        if (isPendingRefresh) {
                                            stringResource(R.string.ark_refresh_vtxo_pending_round)
                                        } else {
                                            null
                                        },
                                    onToggle = {
                                        // Pending round outputs cannot be selected / re-refreshed.
                                        if (isPendingRefresh) return@ArkVtxoSelectRow
                                        selectedRefresh =
                                            if (selectedRefresh.contains(vtxo.id)) {
                                                selectedRefresh - vtxo.id
                                            } else {
                                                selectedRefresh + vtxo.id
                                            }
                                    },
                                )
                            }
                            Button(
                                onClick = {
                                    val targets = selectableSelected
                                    if (targets.isEmpty()) return@Button
                                    onReset()
                                    showRefreshReview = true
                                    onPrepareRefresh(targets)
                                },
                                enabled =
                                    selectableSelected.isNotEmpty() &&
                                        !refreshBusy &&
                                        !reviewOpen,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ArkRust),
                            ) {
                                Text(stringResource(R.string.ark_refresh_review_action))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_ark_auto_delegated_refresh_title),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.ark_refresh_auto_short),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            SquareToggle(
                                checked = autoDelegatedRefreshEnabled,
                                onCheckedChange = onAutoDelegatedRefreshEnabledChange,
                                checkedColor = ArkRust,
                            )
                        }
                    }
                }

                if (showRefreshReview) {
                    ArkRefreshReviewDialog(
                        state = lifecycleState,
                        useSats = useSats,
                        chainTipHeight = tipHeight,
                        onExecuteRefresh = onExecuteRefresh,
                        onClose = {
                            closedPendingRefresh = lifecycleState is ArkLifecycleState.RefreshPending
                            showRefreshReview = false
                            // Pending stays tracked in the repository so the list keeps badges.
                            // Only clear Error/Completed dialog leftovers.
                            when (lifecycleState) {
                                is ArkLifecycleState.Error,
                                is ArkLifecycleState.Completed,
                                -> onReset()
                                is ArkLifecycleState.RefreshPending -> onReset()
                                else -> Unit
                            }
                        },
                        onReset = onReset,
                    )
                }
            }

            ArkManageTab.EXIT -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.ark_exit_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.ark_exit_warning),
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        val spendableIds =
                            arkState.vtxos
                                .filter { ArkBarkMappers.isSpendableLabel(it.state) }
                                .map { it.id }
                        val canStartSelected =
                            ArkUnilateralExitPolicy.canStartSelectedExit(
                                selectedVtxoIds = selectedExit,
                                spendableVtxoIds = spendableIds,
                            )
                        val canStartEntire =
                            ArkUnilateralExitPolicy.canStartEntireExit(spendableIds)
                        val exitBusy = lifecycleState is ArkLifecycleState.InProgress
                        val hasPending = arkState.hasPendingExits || arkState.exitVtxos.isNotEmpty()
                        val hasClaimable = arkState.hasClaimableExits
                        val exitsNeedPush = arkState.exitVtxos.any { arkExitNeedsPush(it.state) }
                        val showProgressWithClaim =
                            ArkUnilateralExitPolicy.shouldShowProgressWithClaimable(
                                hasPendingExits = hasPending,
                                hasClaimableExits = hasClaimable,
                            )
                        val canQuoteClaim = ArkUnilateralExitPolicy.canQuoteClaim(claimAddress)
                        val pendingExitIds = arkState.exitVtxos.map { it.vtxoId }.toSet()
                        val pendingExitFee =
                            ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                                arkState.vtxos
                                    .filter { it.id in pendingExitIds }
                                    .map { it.exitTxWeightWu },
                            )
                        val confirmedFeeBalance = arkState.onchainConfirmedSats.coerceAtLeast(0L)
                        var pendingExitConfirm by remember {
                            mutableStateOf<Pair<List<String>, Boolean>?>(null)
                        }
                        var showProgressReview by remember { mutableStateOf(false) }
                        var showClaimReview by remember { mutableStateOf(false) }

                        LaunchedEffect(lifecycleState, showProgressReview) {
                            if (
                                showProgressReview &&
                                lifecycleState is ArkLifecycleState.ExitProgressing
                            ) {
                                showProgressReview = false
                            }
                        }

                        pendingExitConfirm?.let { (ids, entire) ->
                            val idSet = if (entire) spendableIds.toSet() else ids.toSet()
                            val exitVtxos = arkState.vtxos.filter { it.id in idSet }
                            val exitTotal = exitVtxos.sumOf { it.amountSats }
                            val estimatedFee =
                                ArkUnilateralExitPolicy.estimateCpfpFeeSats(
                                    exitVtxos.map { it.exitTxWeightWu },
                                )
                            ArkExitStartReviewDialog(
                                vtxoCount = exitVtxos.size,
                                amountSats = exitTotal,
                                estimatedFeeSats = estimatedFee,
                                confirmedFeeBalanceSats = confirmedFeeBalance,
                                privacyMode = privacyMode,
                                useSats = useSats,
                                onConfirm = {
                                    pendingExitConfirm = null
                                    onStartExit(ids, entire)
                                },
                                onDismiss = { pendingExitConfirm = null },
                            )
                        }

                        if (showProgressReview) {
                            ArkExitProgressReviewDialog(
                                state = lifecycleState,
                                estimatedFeeSats = pendingExitFee,
                                confirmedFeeBalanceSats = confirmedFeeBalance,
                                onConfirm = onProgressExits,
                                onDismiss = {
                                    if (lifecycleState !is ArkLifecycleState.InProgress) {
                                        showProgressReview = false
                                        onReset()
                                    }
                                },
                            )
                        }

                        if (showClaimReview) {
                            ArkExitClaimReviewDialog(
                                state = lifecycleState,
                                useSats = useSats,
                                onConfirm = onExecuteClaim,
                                onDismiss = {
                                    if (
                                        lifecycleState !is ArkLifecycleState.Loading &&
                                        lifecycleState !is ArkLifecycleState.InProgress
                                    ) {
                                        showClaimReview = false
                                        onReset()
                                    }
                                },
                            )
                        }

                        // Progressive: claim when ready; also keep Push if other exits still pending.
                        when {
                            exitBusy && !hasPending && !hasClaimable -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        color = ArkRust,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.ark_exit_starting),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            hasClaimable || hasPending -> {
                                if (hasPending) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.ark_exit_active_format,
                                                arkState.exitVtxos.size,
                                                if (privacyMode) {
                                                    ARK_HIDDEN_AMOUNT
                                                } else {
                                                    formatArkAmount(
                                                        arkState.exitVtxos.sumOf { it.amountSats },
                                                        useSats,
                                                    )
                                                },
                                            ),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                if (exitsNeedPush) {
                                                    R.string.ark_exit_ready_to_broadcast
                                                } else {
                                                    R.string.ark_exit_waiting_short
                                                },
                                        ),
                                        color = if (exitsNeedPush) ArkRust else TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (exitsNeedPush) {
                                        Button(
                                            onClick = {
                                                onReset()
                                                showProgressReview = true
                                            },
                                            enabled = !exitBusy,
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ArkRust),
                                        ) {
                                            Text(stringResource(R.string.ark_exit_review_broadcast))
                                        }
                                    }
                                    if (showProgressWithClaim || hasClaimable) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                if (hasClaimable) {
                                    Text(
                                        text = stringResource(R.string.ark_exit_claim),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.ark_exit_claimable_format,
                                                arkState.claimableExitVtxos.size,
                                                if (privacyMode) {
                                                    ARK_HIDDEN_AMOUNT
                                                } else {
                                                    formatArkAmount(
                                                        arkState.claimableExitVtxos.sumOf { it.amountSats },
                                                        useSats,
                                                    )
                                                },
                                            ),
                                        color = SuccessGreen,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val claimAddressError =
                                        when {
                                            claimAddress.isBlank() -> null
                                            !ArkUnilateralExitPolicy.isUsableBitcoinClaimAddress(claimAddress) ->
                                                stringResource(R.string.loc_9f80cab8)
                                            else -> null
                                        }
                                    OutlinedTextField(
                                        value = claimAddress,
                                        onValueChange = { claimAddress = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        isError = claimAddressError != null,
                                        supportingText =
                                            claimAddressError?.let {
                                                {
                                                    Text(it, color = ErrorRed)
                                                }
                                            },
                                        label = { Text(stringResource(R.string.ark_exit_destination_hint)) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ArkRust,
                                                unfocusedBorderColor = BorderColor,
                                                cursorColor = ArkRust,
                                            ),
                                    )
                                    Button(
                                        onClick = {
                                            onReset()
                                            showClaimReview = true
                                            onPrepareClaim(
                                                claimAddress.trim(),
                                                arkState.claimableExitVtxos.map { it.vtxoId },
                                            )
                                        },
                                        enabled = canQuoteClaim && claimAddressError == null,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ArkRust),
                                    ) {
                                        Text(stringResource(R.string.ark_exit_claim_quote))
                                    }
                                }
                            }

                            else -> {
                                if (spendableIds.isEmpty()) {
                                    Text(stringResource(R.string.ark_exit_no_outputs), color = TextSecondary)
                                } else {
                                    arkState.vtxos.take(40).forEach { vtxo ->
                                        ArkVtxoSelectRow(
                                            amountLabel =
                                                if (privacyMode) {
                                                    ARK_HIDDEN_AMOUNT
                                                } else {
                                                    formatArkAmount(vtxo.amountSats, useSats)
                                                },
                                            expiryLabel =
                                                arkVtxoExpiryLabel(vtxo, arkState.chainTipHeight),
                                            selected = selectedExit.contains(vtxo.id),
                                            state = vtxo.state,
                                            onToggle = {
                                                selectedExit =
                                                    if (selectedExit.contains(vtxo.id)) {
                                                        selectedExit - vtxo.id
                                                    } else {
                                                        selectedExit + vtxo.id
                                                    }
                                            },
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            pendingExitConfirm =
                                                selectedExit.intersect(spendableIds.toSet()).toList() to false
                                        },
                                        enabled = canStartSelected && !exitBusy,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ArkRust),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ark_exit_start),
                                            maxLines = 1,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { pendingExitConfirm = emptyList<String>() to true },
                                        enabled = canStartEntire && !exitBusy,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border =
                                            BorderStroke(
                                                1.dp,
                                                if (canStartEntire) ErrorRed else BorderColor,
                                            ),
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                contentColor = ErrorRed,
                                                disabledContentColor = TextSecondary,
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ark_exit_start_all),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ArkManageTab.BACKUP -> {
                val transferBusy = dbTransferInProgress != null
                val dbBusy =
                    transferBusy ||
                        lifecycleState is ArkLifecycleState.InProgress ||
                        lifecycleState is ArkLifecycleState.Loading ||
                        arkState.isConnecting ||
                        arkState.isSyncing
                val transferStatusText =
                    when (dbTransferInProgress) {
                        ArkDbTransferProgress.EXPORTING ->
                            stringResource(R.string.ark_db_export_progress)
                        ArkDbTransferProgress.IMPORTING ->
                            stringResource(R.string.ark_db_import_progress)
                        ArkDbTransferProgress.RESTORING ->
                            stringResource(R.string.ark_db_restore_progress)
                        null -> null
                    }
                val autoBackupArmed =
                    autoDbBackupEnabled && !autoDbBackupFolderUri.isNullOrBlank()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.ark_db_backup_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!autoBackupArmed) {
                            Text(
                                text = stringResource(R.string.ark_db_backup_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                            )
                        }
                        Text(
                            text = stringResource(R.string.ark_db_backup_encrypted_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (transferStatusText != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = ArkRust,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = transferStatusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ArkRust,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IbisButton(
                                onClick = {
                                    mainActivity?.skipNextBackgroundLockForActivityResult()
                                    exportLauncher.launch(suggestedExportName)
                                },
                                enabled = arkState.isInitialized && !dbBusy,
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.ark_db_export_action),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                            }
                            IbisButton(
                                onClick = {
                                    mainActivity?.skipNextBackgroundLockForActivityResult()
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                },
                                enabled = arkState.isInitialized && !dbBusy,
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.ark_db_import_action),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.ark_db_auto_backup_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.ark_db_auto_backup_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            SquareToggle(
                                checked = autoDbBackupEnabled,
                                onCheckedChange = onAutoDbBackupEnabledChange,
                                checkedColor = ArkRust,
                            )
                        }
                        if (autoDbBackupEnabled) {
                            when {
                                autoDbBackupFolderUri.isNullOrBlank() -> {
                                    Text(
                                        text = stringResource(R.string.ark_db_auto_backup_folder_unset),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ArkRust,
                                    )
                                }
                                showDirectoryLinkedToast -> {
                                    Text(
                                        text = stringResource(R.string.ark_db_auto_backup_folder_set),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SuccessGreen,
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ark_db_auto_backup_latest_title),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (latestBackup != null && latestBackupTime != null) {
                                        ArkBackupDetailRow(
                                            label = stringResource(R.string.ark_db_auto_backup_detail_when),
                                            value = latestBackupTime,
                                        )
                                        ArkBackupDetailRow(
                                            label = stringResource(R.string.ark_db_auto_backup_detail_files),
                                            value =
                                                latestBackup.fileName.ifBlank {
                                                    stringResource(R.string.ark_db_auto_backup_detail_files_latest)
                                                },
                                            monospace = true,
                                        )
                                        latestBackupSizeLabel?.let { sizeLabel ->
                                            ArkBackupDetailRow(
                                                label = stringResource(R.string.ark_db_auto_backup_detail_size),
                                                value = sizeLabel,
                                            )
                                        }
                                        ArkBackupDetailRow(
                                            label = stringResource(R.string.ark_db_auto_backup_detail_kept),
                                            value = stringResource(R.string.ark_db_auto_backup_detail_kept_format),
                                        )
                                        ArkBackupDetailRow(
                                            label = stringResource(R.string.ark_db_auto_backup_detail_location),
                                            value =
                                                autoDbBackupFolderPath
                                                    ?: autoDbBackupFolderUri
                                                    ?: stringResource(R.string.ark_db_auto_backup_location_external),
                                            monospace = true,
                                        )
                                    } else if (autoDbBackupFolderUri.isNullOrBlank()) {
                                        Text(
                                            text = stringResource(R.string.ark_db_auto_backup_folder_required),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextTertiary,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.ark_db_auto_backup_last_never),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextTertiary,
                                        )
                                    }
                                }
                            }
                            IbisButton(
                                onClick = {
                                    mainActivity?.skipNextBackgroundLockForActivityResult()
                                    onPickAutoDbBackupFolder()
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.ark_db_auto_backup_folder_pick),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    var wasRecoveringOnchain by remember { mutableStateOf(false) }
    LaunchedEffect(isRecoveringOnchain) {
        if (wasRecoveringOnchain && !isRecoveringOnchain) {
            showRecoverConfirmDialog = false
        }
        wasRecoveringOnchain = isRecoveringOnchain
    }
    if (showRecoverConfirmDialog || isRecoveringOnchain) {
        val dest = recoverDestinationAddress?.trim().orEmpty()
        val hasDest = dest.isNotBlank()
        IbisConfirmDialog(
            onDismissRequest = {
                if (!isRecoveringOnchain) showRecoverConfirmDialog = false
            },
            title = stringResource(R.string.ark_boarding_recover_dialog_title),
            message = stringResource(R.string.ark_boarding_recover_dialog_body),
            confirmText =
                if (isRecoveringOnchain) {
                    stringResource(R.string.ark_boarding_recover_dialog_working)
                } else {
                    stringResource(R.string.ark_boarding_recover_dialog_confirm)
                },
            confirmEnabled = hasDest && !isRecoveringOnchain,
            confirmColor = ArkRust,
            dismissEnabled = !isRecoveringOnchain,
            onConfirm = {
                onRecoverOnchain()
            },
            onDismissAction = {
                if (!isRecoveringOnchain) showRecoverConfirmDialog = false
            },
            body = {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.ark_boarding_recover_dialog_address_label),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        when {
                            privacyMode && hasDest -> "****"
                            hasDest -> dest
                            else -> stringResource(R.string.ark_boarding_recover_dialog_no_address)
                        },
                    color = if (hasDest) TextPrimary else WarningYellow,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (hasDest) FontFamily.Monospace else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

enum class ArkManageTab {
    BOARDING,
    REFRESH,
    EXIT,
    BACKUP,
}

@Composable
private fun ArkManageTabBar(
    current: ArkManageTab,
    boardingLabel: String,
    refreshLabel: String,
    exitLabel: String,
    backupLabel: String,
    onSelect: (ArkManageTab) -> Unit,
) {
    val tabs =
        listOf(
            ArkManageTab.BOARDING to boardingLabel,
            ArkManageTab.REFRESH to refreshLabel,
            ArkManageTab.EXIT to exitLabel,
            ArkManageTab.BACKUP to backupLabel,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (tab, label) ->
            val selected = tab == current
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ArkRust.copy(alpha = 0.2f) else DarkSurface)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) ArkRust else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Refresh quote + confirm as a modal popup (not an expanding card under the list).
 * Loading / in-progress spinner also lives here and on the Manual refresh button.
 */
@Composable
private fun ArkRefreshReviewDialog(
    state: ArkLifecycleState,
    useSats: Boolean,
    chainTipHeight: Int?,
    onExecuteRefresh: () -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit,
) {
    if (state is ArkLifecycleState.Idle) return
    val isBusy = state is ArkLifecycleState.Loading || state is ArkLifecycleState.InProgress
    val preview = state as? ArkLifecycleState.RefreshPreview
    val pending = state as? ArkLifecycleState.RefreshPending
    val terminal = state is ArkLifecycleState.Completed || state is ArkLifecycleState.Error
    IbisConfirmDialog(
        onDismissRequest = {
            if (!isBusy) {
                if (preview != null) onReset()
                onClose()
            }
        },
        title = stringResource(R.string.ark_refresh_review_title),
        confirmText =
            stringResource(
                if (pending != null || terminal) R.string.loc_d2c0aec0 else R.string.ark_refresh_confirm,
            ),
        confirmEnabled = (preview != null || pending != null || terminal) && !isBusy,
        confirmColor = ArkRust,
        dismissText = stringResource(R.string.loc_51bac044),
        dismissEnabled = !isBusy,
        showDismissButton = !isBusy && preview != null,
        onDismissAction = {
            if (!isBusy) {
                if (preview != null) onReset()
                onClose()
            }
        },
        maxWidth = 720.dp,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        bottomSpacing = 20.dp,
        actionHeight = 48.dp,
        titleStyle = MaterialTheme.typography.titleLarge,
        actionTextStyle = MaterialTheme.typography.bodyLarge,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isBusy,
                dismissOnClickOutside = !isBusy,
            ),
        onConfirm = {
            when {
                preview != null && !isBusy -> onExecuteRefresh()
                pending != null || terminal -> {
                    if (terminal) onReset()
                    onClose()
                }
            }
        },
        body = {
            if (isBusy) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = ArkRust,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.ark_refresh_working_body),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (preview != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArkExitReviewRow(
                        label = stringResource(R.string.ark_refresh_review_outputs),
                        value = preview.vtxoIds.size.toString(),
                    )
                    if (preview.scheduledHeight != null) {
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_mode),
                            value = stringResource(R.string.ark_refresh_mode_scheduled),
                        )
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_block),
                            value = preview.scheduledHeight.toString(),
                        )
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_fee),
                            value =
                                preview.feeSats?.let { formatArkAmount(it, useSats) }
                                    ?: stringResource(R.string.ark_exit_fee_unavailable),
                        )
                        Text(
                            text = stringResource(R.string.ark_refresh_scheduled_fee_note),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_mode),
                            value = stringResource(R.string.ark_refresh_mode_next_round),
                        )
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_fee),
                            value =
                                preview.feeSats?.let { formatArkAmount(it, useSats) }
                                    ?: stringResource(R.string.ark_exit_fee_unavailable),
                        )
                    }
                }
            } else if (pending != null) {
                val pendingCount = pending.vtxoIds.size.coerceAtLeast(1)
                val scheduled = pending.scheduledHeight
                val blocksToScheduled =
                    if (scheduled != null && chainTipHeight != null) {
                        (scheduled - chainTipHeight).coerceAtLeast(0)
                    } else {
                        null
                    }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (scheduled != null) {
                                    R.string.ark_refresh_scheduled_title
                                } else {
                                    R.string.ark_refresh_submitted_title
                                },
                            ),
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            when {
                                scheduled != null && blocksToScheduled != null ->
                                    stringResource(
                                        R.string.ark_refresh_pending_dialog_scheduled,
                                        pendingCount,
                                        scheduled,
                                        blocksToScheduled,
                                    )
                                scheduled != null ->
                                    stringResource(R.string.ark_refresh_scheduled_body, scheduled)
                                else ->
                                    stringResource(
                                        R.string.ark_refresh_pending_dialog_round,
                                        pendingCount,
                                    )
                            },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (chainTipHeight != null) {
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_tip),
                            value = chainTipHeight.toString(),
                        )
                    }
                    if (scheduled != null) {
                        ArkExitReviewRow(
                            label = stringResource(R.string.ark_refresh_review_block),
                            value = scheduled.toString(),
                        )
                    }
                    ArkExitReviewRow(
                        label = stringResource(R.string.ark_refresh_review_outputs),
                        value = pendingCount.toString(),
                    )
                }
            } else if (state is ArkLifecycleState.Completed) {
                Text(stringResource(R.string.ark_refresh_completed), color = SuccessGreen)
            } else if (state is ArkLifecycleState.Error) {
                Text(state.message, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
private fun ArkExitStartReviewDialog(
    vtxoCount: Int,
    amountSats: Long,
    estimatedFeeSats: Long?,
    confirmedFeeBalanceSats: Long,
    privacyMode: Boolean,
    useSats: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val requiredFunds = estimatedFeeSats?.plus(ArkUnilateralExitPolicy.CPFP_CHANGE_DUST_SATS)
    val shortfall = requiredFunds?.minus(confirmedFeeBalanceSats)?.coerceAtLeast(0L)
    IbisConfirmDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ark_exit_review_title),
        confirmText = stringResource(R.string.ark_exit_confirm_action),
        confirmColor = ErrorRed,
        dismissText = stringResource(R.string.loc_d2c0aec0),
        actionHeight = 48.dp,
        onConfirm = onConfirm,
        onDismissAction = onDismiss,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArkExitReviewRow(
                    label = stringResource(R.string.ark_exit_review_funds),
                    value = if (privacyMode) ARK_HIDDEN_AMOUNT else formatArkAmount(amountSats, useSats),
                )
                ArkExitReviewRow(
                    label = stringResource(R.string.ark_exit_review_count),
                    value = vtxoCount.toString(),
                )
                ArkExitFeeReview(
                    estimatedFeeSats = estimatedFeeSats,
                    confirmedFeeBalanceSats = confirmedFeeBalanceSats,
                    shortfallSats = shortfall,
                )
                Text(
                    text = stringResource(R.string.ark_exit_review_irreversible),
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun ArkExitProgressReviewDialog(
    state: ArkLifecycleState,
    estimatedFeeSats: Long?,
    confirmedFeeBalanceSats: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val requiredFunds = estimatedFeeSats?.plus(ArkUnilateralExitPolicy.CPFP_CHANGE_DUST_SATS)
    val shortfall = requiredFunds?.minus(confirmedFeeBalanceSats)?.coerceAtLeast(0L)
    val error = state as? ArkLifecycleState.Error
    val busy = state is ArkLifecycleState.InProgress
    IbisConfirmDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.ark_exit_broadcast_review_title),
        confirmText = stringResource(R.string.ark_exit_broadcast_action),
        confirmEnabled = !busy && error == null && (requiredFunds == null || shortfall == 0L),
        confirmColor = ArkRust,
        dismissText = stringResource(R.string.loc_d2c0aec0),
        dismissEnabled = !busy,
        showDismissButton = !busy,
        actionHeight = 48.dp,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !busy,
                dismissOnClickOutside = !busy,
            ),
        onConfirm = onConfirm,
        onDismissAction = onDismiss,
        body = {
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = ArkRust,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.ark_exit_broadcasting), color = TextSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArkExitFeeReview(
                        estimatedFeeSats = estimatedFeeSats,
                        confirmedFeeBalanceSats = confirmedFeeBalanceSats,
                        shortfallSats = shortfall,
                    )
                    error?.let {
                        Text(text = it.message, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

@Composable
private fun ArkExitFeeReview(
    estimatedFeeSats: Long?,
    confirmedFeeBalanceSats: Long,
    shortfallSats: Long?,
) {
    val requiredFunds = estimatedFeeSats?.plus(ArkUnilateralExitPolicy.CPFP_CHANGE_DUST_SATS)
    ArkExitReviewRow(
        label = stringResource(R.string.ark_exit_review_fee),
        value =
            estimatedFeeSats?.let { formatArkAmount(it, useSats = true) }
                ?: stringResource(R.string.ark_exit_fee_unavailable),
    )
    ArkExitReviewRow(
        label = stringResource(R.string.ark_exit_review_required),
        value =
            requiredFunds?.let { formatArkAmount(it, useSats = true) }
                ?: stringResource(R.string.ark_exit_fee_unavailable),
    )
    ArkExitReviewRow(
        label = stringResource(R.string.ark_exit_review_available),
        value = formatArkAmount(confirmedFeeBalanceSats, useSats = true),
    )
    if (shortfallSats != null && shortfallSats > 0L) {
        Text(
            text =
                stringResource(
                    R.string.ark_exit_review_add_funds_format,
                    formatArkAmount(shortfallSats, useSats = true),
                ),
            color = ErrorRed,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.ark_exit_review_add_funds_hint),
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ArkExitReviewRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ArkExitClaimReviewDialog(
    state: ArkLifecycleState,
    useSats: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = state as? ArkLifecycleState.ClaimPreview
    val busy = state is ArkLifecycleState.Loading || state is ArkLifecycleState.InProgress
    val terminal = state is ArkLifecycleState.Error || state is ArkLifecycleState.Completed
    IbisConfirmDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.ark_exit_claim_review_title),
        confirmText =
            stringResource(
                if (terminal) R.string.loc_d2c0aec0 else R.string.ark_exit_claim_confirm,
            ),
        confirmEnabled = !busy && (preview != null || terminal),
        confirmColor = ArkRust,
        showDismissButton = !busy && !terminal,
        onConfirm = if (terminal) onDismiss else onConfirm,
        onDismissAction = onDismiss,
        body = {
            when {
                busy -> {
                    CircularProgressIndicator(color = ArkRust, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                preview != null -> {
                    ArkExitReviewRow(
                        label = stringResource(R.string.ark_exit_review_fee),
                        value = formatArkAmount(preview.feeSats, useSats),
                    )
                    Text(preview.destinationAddress, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                state is ArkLifecycleState.Error -> {
                    Text(state.message, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                }
                state is ArkLifecycleState.Completed -> {
                    Text(stringResource(R.string.ark_lifecycle_complete), color = SuccessGreen)
                }
            }
        },
    )
}

@Composable
private fun ArkBackupDetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    fullWidthValue: Boolean = false,
) {
    if (fullWidthValue) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                // Monospace only for paths/ids; otherwise inherit settings typeface.
                fontFamily = if (monospace) FontFamily.Monospace else null,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DIRECTORY_LINKED_TOAST_MS = 2_500L

private fun formatArkBackupSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

/**
 * Compact path for snapshot Location row: ellipsize only the storage root prefix,
 * keep the full ending path (e.g. `…/Download/ark/ibis-ark-Wallet-abcd1234`).
 */
private fun shortenBackupLocationPath(path: String): String {
    val normalized = path.trim().replace('\\', '/').trimEnd('/')
    if (normalized.isEmpty()) return path
    val primaryRoot = "/storage/emulated/0"
    if (normalized == primaryRoot) return "…"
    if (normalized.startsWith("$primaryRoot/")) {
        return "…/" + normalized.removePrefix("$primaryRoot/")
    }
    val volumeRoot = Regex("^/storage/[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}").find(normalized)?.value
    if (volumeRoot != null) {
        if (normalized == volumeRoot) return "…"
        if (normalized.startsWith("$volumeRoot/")) {
            return "…/" + normalized.removePrefix("$volumeRoot/")
        }
    }
    return normalized
}

/**
 * Full human-readable tree directory for a SAF folder URI (best-effort).
 * Examples: `primary:Download/ark` → `/storage/emulated/0/Download/ark`
 *           `XXXX-YYYY:Photos/bk` → `/storage/XXXX-YYYY/Photos/bk`
 */
private fun resolveSafTreeDisplayPath(
    context: Context,
    uriString: String,
): String? {
    val treeUri = runCatching { uriString.toUri() }.getOrNull() ?: return null
    if (treeUri == Uri.EMPTY) return null
    val docId =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.toString().takeIf { it.isNotBlank() }
    val decoded = Uri.decode(docId).trim()
    if (decoded.isBlank()) return null
    val volume = decoded.substringBefore(':', missingDelimiterValue = "")
    val relative =
        decoded
            .substringAfter(':', missingDelimiterValue = decoded)
            .trim('/')
            .replace('\\', '/')
    if (relative.isBlank() && volume.isBlank()) return decoded
    val relativePath = relative.takeIf { it.isNotBlank() }
    return when {
        volume.equals("primary", ignoreCase = true) -> {
            val root = "/storage/emulated/0"
            if (relativePath == null) root else "$root/$relativePath"
        }
        volume.matches(Regex("^[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}$")) -> {
            val root = "/storage/$volume"
            if (relativePath == null) root else "$root/$relativePath"
        }
        volume.isNotBlank() && relativePath != null -> "$volume/$relativePath"
        relativePath != null -> relativePath
        volume.isNotBlank() -> volume
        else ->
            runCatching {
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver
                    .query(
                        documentUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val idx =
                            cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx < 0) null else cursor.getString(idx)?.takeIf { it.isNotBlank() }
                    }
            }.getOrNull() ?: decoded
    }
}

@Composable
private fun arkVtxoExpiryLabel(
    vtxo: ArkVtxo,
    chainTipHeight: Int?,
): String {
    if (vtxo.expiryHeight <= 0) return ""
    val tip = chainTipHeight ?: return ""
    val remaining = (vtxo.expiryHeight - tip).coerceAtLeast(0)
    return if (remaining == 0) {
        stringResource(R.string.ark_vtxo_expiry_now_short)
    } else {
        stringResource(R.string.ark_vtxo_expiry_blocks_short_format, remaining)
    }
}

@Composable
private fun ArkBoardingTabContent(
    arkState: ArkWalletState,
    useSats: Boolean,
    privacyMode: Boolean,
    autoBoardEnabled: Boolean,
    onAutoBoardEnabledChange: (Boolean) -> Unit,
    onBoardAll: () -> Unit,
    onBoardAmount: (Long) -> Unit,
    onTopUpOnchain: () -> Unit,
    onRecoverOnchain: () -> Unit,
    isBoarding: Boolean,
    isRecoveringOnchain: Boolean,
) {
    val minBoard = arkState.minBoardAmountSats?.takeIf { it > 0L }
    val utxos = arkState.onchainUtxos
    val confirmedTotal = arkState.onchainConfirmedSats.coerceAtLeast(0L)
    val exitBlocksBoard = arkState.hasPendingExits
    val canBoardAll =
        !exitBlocksBoard &&
            !isBoarding &&
            confirmedTotal > 0L &&
            minBoard != null &&
            confirmedTotal >= minBoard

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ark_boarding_title),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (exitBlocksBoard) {
                Text(
                    text = stringResource(R.string.ark_boarding_disabled_exit),
                    color = WarningYellow,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (utxos.isEmpty() && arkState.onchainTotalSats <= 0L) {
                Text(
                    text = stringResource(R.string.ark_boarding_empty),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val displayUtxos =
                    utxos.ifEmpty {
                        // Balance known but Esplora list empty — synthetic row (incl. 0-conf pending).
                        val pendingOnly = arkState.onchainPendingSats.coerceAtLeast(0L)
                        val amount =
                            when {
                                confirmedTotal > 0L -> confirmedTotal
                                pendingOnly > 0L -> pendingOnly
                                else -> arkState.onchainTotalSats
                            }
                        if (amount > 0L) {
                            listOf(
                                ArkOnchainUtxo(
                                    txid = "",
                                    vout = 0,
                                    amountSats = amount,
                                    confirmations = if (confirmedTotal > 0L) 1 else 0,
                                    address = "",
                                    isConfirmed = confirmedTotal > 0L,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    }
                displayUtxos.take(40).forEach { utxo ->
                    ArkOnchainUtxoRow(
                        utxo = utxo,
                        minBoard = minBoard,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        exitBlocksBoard = exitBlocksBoard,
                        isBoarding = isBoarding,
                        isRecoveringOnchain = isRecoveringOnchain,
                        onBoard = { onBoardAmount(utxo.amountSats) },
                        onTopUp = onTopUpOnchain,
                        onRecover = onRecoverOnchain,
                    )
                }
            }

            if (canBoardAll) {
                Spacer(modifier = Modifier.height(4.dp))
                IbisButton(
                    onClick = onBoardAll,
                    enabled = !isBoarding,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (isBoarding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ArkRust,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.ark_boarding_board_all))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.ark_boarding_auto_title),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ark_boarding_auto_note),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SquareToggle(
                    checked = autoBoardEnabled,
                    onCheckedChange = onAutoBoardEnabledChange,
                    checkedColor = ArkRust,
                )
            }
        }
    }
}

@Composable
private fun ArkOnchainUtxoRow(
    utxo: ArkOnchainUtxo,
    minBoard: Long?,
    useSats: Boolean,
    privacyMode: Boolean,
    exitBlocksBoard: Boolean,
    isBoarding: Boolean,
    isRecoveringOnchain: Boolean,
    onBoard: () -> Unit,
    onTopUp: () -> Unit,
    onRecover: () -> Unit,
) {
    val belowMin =
        minBoard != null &&
            utxo.amountSats > 0L &&
            utxo.amountSats < minBoard
    val utxoShortfall =
        minBoard?.takeIf { belowMin }?.let { min ->
            (min - utxo.amountSats).coerceAtLeast(0L)
        }
    val canBoard =
        utxo.isConfirmed &&
            !belowMin &&
            minBoard != null &&
            utxo.amountSats >= minBoard &&
            !exitBlocksBoard &&
            !isBoarding
    val confsLabel =
        if (!utxo.isConfirmed || utxo.confirmations <= 0) {
            stringResource(R.string.loc_1b684325)
        } else {
            stringResource(R.string.loc_4ab75d7f)
        }
    val confsColor =
        if (!utxo.isConfirmed || utxo.confirmations <= 0) {
            WarningYellow
        } else {
            SuccessGreen
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Shortfall + Top up / Recover sit above the UTXO card when below ASP min.
        if (utxoShortfall != null && utxoShortfall > 0L) {
            Text(
                text =
                    if (privacyMode) {
                        stringResource(R.string.ark_boarding_shortfall_format, "****")
                    } else {
                        stringResource(
                            R.string.ark_boarding_shortfall_format,
                            formatArkAmount(utxoShortfall, useSats),
                        )
                    },
                color = WarningYellow,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.ark_boarding_below_min_actions_note),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTopUp,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        stringResource(R.string.ark_stuck_below_min_top_up),
                        color = ArkRust,
                        maxLines = 1,
                    )
                }
                if (utxo.isConfirmed) {
                    OutlinedButton(
                        onClick = onRecover,
                        enabled = !isRecoveringOnchain && !exitBlocksBoard,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (isRecoveringOnchain) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                stringResource(R.string.ark_stuck_below_min_recover),
                                color = BitcoinOrange,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (privacyMode) {
                            "****"
                        } else {
                            formatArkAmount(utxo.amountSats, useSats)
                        },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = confsLabel,
                    color = confsColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (utxo.txid.isNotBlank()) {
                Text(
                    text = "${utxo.txid}:${utxo.vout}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Ready to board: Board only (recover/top-up already shown above when below min).
            if (canBoard || (utxo.isConfirmed && !belowMin && minBoard != null)) {
                Text(
                    text = stringResource(R.string.ark_boarding_board_note),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onBoard,
                    enabled = canBoard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isBoarding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ArkRust,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.ark_boarding_board),
                            color = ArkRust,
                            maxLines = 1,
                        )
                    }
                }
            } else if (utxo.isConfirmed && !belowMin) {
                // Confirmed but min unknown: still offer recover.
                Text(
                    text = stringResource(R.string.ark_boarding_recover_note),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onRecover,
                    enabled = !isRecoveringOnchain && !exitBlocksBoard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isRecoveringOnchain) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BitcoinOrange,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.ark_stuck_below_min_recover),
                            color = BitcoinOrange,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArkVtxoSelectRow(
    amountLabel: String,
    expiryLabel: String,
    selected: Boolean,
    state: String,
    pendingRefresh: Boolean = false,
    pendingRefreshLabel: String? = null,
    onToggle: () -> Unit,
) {
    val isSpendable = ArkBarkMappers.isSpendableLabel(state) && !pendingRefresh
    val highlight = selected || pendingRefresh
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        pendingRefresh -> SuccessGreen.copy(alpha = 0.12f)
                        selected -> ArkRust.copy(alpha = 0.15f)
                        else -> DarkSurface
                    },
                )
                .clickable(enabled = isSpendable, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            pendingRefresh -> SuccessGreen
                            selected -> ArkRust
                            else -> DarkCard
                        },
                    )
                    .border(
                        width = 1.dp,
                        color =
                            when {
                                pendingRefresh -> SuccessGreen
                                selected -> ArkRust
                                else -> BorderColor
                            },
                        shape = RoundedCornerShape(4.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (highlight) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = amountLabel,
                color =
                    when {
                        pendingRefresh -> SuccessGreen
                        selected -> ArkRust
                        else -> TextPrimary
                    },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expiryLabel.isNotBlank()) {
                Text(
                    text = expiryLabel,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pendingRefresh && !pendingRefreshLabel.isNullOrBlank()) {
                Text(
                    text = pendingRefreshLabel,
                    color = SuccessGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!isSpendable && !pendingRefresh) {
                Text(
                    text = stringResource(R.string.ark_vtxo_state_locked),
                    color = ArkRust,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun arkExitNeedsPush(state: String): Boolean =
    ArkBarkMappers.needsPushLabel(state)

