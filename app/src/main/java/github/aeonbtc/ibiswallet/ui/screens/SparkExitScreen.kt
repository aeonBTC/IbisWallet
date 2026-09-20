package github.aeonbtc.ibiswallet.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import github.aeonbtc.ibiswallet.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import github.aeonbtc.ibiswallet.data.model.FeeEstimateSource
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.SparkExitFlowState
import github.aeonbtc.ibiswallet.data.model.SparkExitFundingUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitQuote
import github.aeonbtc.ibiswallet.data.model.SparkExitRedoReason
import github.aeonbtc.ibiswallet.data.model.SparkExitTx
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.repository.SparkUnilateralExitPolicy
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.SecureClipboard

/**
 * Guided Spark unilateral-exit flow: setup → quote → funding → build →
 * broadcast with SDK-checked progress. Broadcast stays manual (tree-tx + CPFP
 * pairs need package relay); statuses come from `check_unilateral_exit`
 * against the chain tip, never from manual flags.
 */
@Composable
fun SparkExitScreen(
    exitFlow: SparkExitFlowState,
    layer1Utxos: List<UtxoInfo>,
    layer1Address: String?,
    useSats: Boolean,
    privacyMode: Boolean,
    feeEstimationState: FeeEstimationResult,
    onRefreshBitcoinFees: () -> Unit,
    onQuote: (feeRateSatPerVb: Long, destination: String) -> Unit,
    onBuild: (funding: List<SparkExitFundingUtxo>) -> Unit,
    onRebuild: (feeRateSatPerVb: Long, funding: List<SparkExitFundingUtxo>) -> Unit,
    onCheckStatus: () -> Unit,
    onDiscard: () -> Unit,
    onExportBackup: () -> Unit,
    onExportExitCopy: (android.net.Uri) -> Unit,
    onImportExitBackup: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
    utxoScriptHex: (String) -> String?,
    modifier: Modifier = Modifier,
    isExitBackupStale: Boolean = false,
    /** Chain tip height for timelock countdowns; null falls back to height-free text. */
    chainTipHeight: Long? = null,
    /** Settled Spark balance for the quote-coverage hint; null disables it. */
    sparkBalanceSats: Long? = null,
    /** Unclaimed on-chain deposit total (not exitable until claimed/matured). */
    unclaimedDepositSats: Long = 0L,
) {
    val context = LocalContext.current
    val defaultFeeRate =
        remember(feeEstimationState) {
            // Whole sats up front: exits price integer sat/vB, so the screen
            // never carries a fractional default into the widget.
            kotlin.math.ceil(
                (feeEstimationState as? FeeEstimationResult.Success)
                    ?.estimates?.fastestFee?.takeIf { it > 0.0 }
                    ?: SparkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB.toDouble(),
            )
        }
    var feeRate by remember(defaultFeeRate) { mutableDoubleStateOf(defaultFeeRate) }
    var destination by remember(layer1Address) { mutableStateOf(layer1Address.orEmpty()) }
    // Saveable: a fee-bump rebuild after process death or navigation reuses
    // the selection when non-empty; otherwise the repository falls back to
    // the funding inputs persisted at build time.
    var selectedOutpoints by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showFundingDialog by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showBumpDialog by remember { mutableStateOf(false) }
    // Build confirmation: the reviewed quote + funding snapshot captured when
    // the user taps Build, so the confirm dialog cannot drift from what was
    // reviewed if the funding selection changes underneath it.
    var showBuildConfirm by remember { mutableStateOf(false) }
    var pendingBuildFunding by remember { mutableStateOf<List<SparkExitFundingUtxo>>(emptyList()) }
    // Portable exit-state copy (the private snapshot cannot leave the device,
    // so this is the blob an import elsewhere reads back).
    val exitCopyName =
        remember {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            "ibis-spark-exit-$stamp.txt"
        }
    val exportCopyLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri: Uri? ->
            uri?.let(onExportExitCopy)
        }
    val importBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let(onImportExitBackup)
        }

    val eligibleUtxos =
        remember(layer1Utxos) {
            layer1Utxos.filter { utxo ->
                // Silent-payment outputs live outside the BDK wallet graph,
                // so the exit signer cannot sign for them — offering them
                // would fail confusingly at build time.
                !utxo.isSilentPayment && !utxo.isFrozen && utxo.isConfirmed &&
                    utxoScriptHex(utxo.address)?.let {
                        SparkUnilateralExitPolicy.classifyFundingScript(it) != null
                    } == true
            }
        }
    // Drop selections that left the wallet (spent since quoting).
    val liveSelection = selectedOutpoints.filter { outpoint ->
        eligibleUtxos.any { it.outpoint == outpoint }
    }.toSet()
    if (liveSelection.size != selectedOutpoints.size) {
        selectedOutpoints = liveSelection
    }

    fun fundingInputsFor(outpoints: Set<String>): List<SparkExitFundingUtxo> =
        outpoints.mapNotNull { outpoint ->
            val utxo = eligibleUtxos.firstOrNull { it.outpoint == outpoint } ?: return@mapNotNull null
            val scriptHex = utxoScriptHex(utxo.address) ?: return@mapNotNull null
            val scriptClass =
                SparkUnilateralExitPolicy.classifyFundingScript(scriptHex) ?: return@mapNotNull null
            SparkExitFundingUtxo(
                txid = utxo.txid,
                vout = utxo.vout,
                valueSats = utxo.amountSats.toLong(),
                scriptPubkeyHex = scriptHex,
                signedInputWeight = SparkUnilateralExitPolicy.signedInputWeightFor(scriptClass),
            )
        }

    val amountText: (Long) -> String = { sats ->
        if (privacyMode) "****" else formatAmount(sats.toULong(), useSats, includeUnit = true)
    }
    // Synchronous action guard, reset on every flow transition: quote/build/
    // rebuild flip the flow only after the ViewModel processes them, so a
    // rapid double-tap would otherwise fire the same action twice.
    var exitActionGuard by remember(exitFlow) { mutableStateOf(false) }
    fun guardedExitAction(action: () -> Unit) {
        if (!exitActionGuard) {
            exitActionGuard = true
            action()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.spark_exit_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Coverage gap for the quote state: gross quoted leaf values should sum
        // to the settled balance. Shown in the top card slot (replacing the
        // intro) instead of inside the quote card.
        val quoteCoverageGap: Long? =
            (exitFlow as? SparkExitFlowState.QuoteReady)?.let { ready ->
                val quotedSum = ready.quote.leaves.sumOf { it.valueSats }
                if (sparkBalanceSats != null &&
                    !SparkUnilateralExitPolicy.isQuoteCoveringBalance(sparkBalanceSats, quotedSum)
                ) {
                    (sparkBalanceSats - quotedSum).coerceAtLeast(0L)
                } else {
                    null
                }
            }

        if (quoteCoverageGap != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Text(
                        text = stringResource(
                            R.string.spark_exit_stale_hint,
                            amountText(quoteCoverageGap),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningYellow,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else if (exitFlow !is SparkExitFlowState.QuoteReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Text(
                        text = stringResource(R.string.spark_exit_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        when (val flow = exitFlow) {
            is SparkExitFlowState.Idle,
            is SparkExitFlowState.Quoting,
            is SparkExitFlowState.Failed,
            -> {
                if (flow is SparkExitFlowState.Failed) {
                    item {
                        ExitErrorCard(flow.message)
                    }
                }
                if (unclaimedDepositSats > 0L) {
                    item {
                        ExitUnclaimedBanner(
                            unclaimedDepositSats = unclaimedDepositSats,
                            amountText = amountText,
                            onViewBalance = onBack,
                        )
                    }
                }
                item {
                    ExitSetupCard(
                        feeEstimationState = feeEstimationState,
                        feeRate = feeRate,
                        onFeeRateChange = { feeRate = it },
                        onRefreshFees = onRefreshBitcoinFees,
                        destination = destination,
                        onDestinationChange = { destination = it.trim() },
                        quoting = flow is SparkExitFlowState.Quoting,
                        onQuote = {
                            guardedExitAction {
                                onQuote(
                                    SparkUnilateralExitPolicy.floorFeeRateSatPerVb(
                                        kotlin.math.ceil(feeRate).toLong(),
                                    ),
                                    destination,
                                )
                            }
                        },
                        onImportBackup = {
                            importBackupLauncher.launch(arrayOf("text/plain", "*/*"))
                        },
                    )
                }
            }

            is SparkExitFlowState.QuoteReady -> {
                if (unclaimedDepositSats > 0L) {
                    item {
                        ExitUnclaimedBanner(
                            unclaimedDepositSats = unclaimedDepositSats,
                            amountText = amountText,
                            onViewBalance = onBack,
                        )
                    }
                }
                item {
                    ExitQuoteCard(
                        quote = flow.quote,
                        amountText = amountText,
                        selectedFunding = fundingInputsFor(selectedOutpoints),
                        eligibleCount = eligibleUtxos.size,
                        onPickFunding = { showFundingDialog = true },
                        onBuild = {
                            pendingBuildFunding = fundingInputsFor(selectedOutpoints)
                            showBuildConfirm = true
                        },
                        onBackToSetup = onDiscard,
                    )
                }
            }

            is SparkExitFlowState.Building -> {
                item {
                    ExitStatusCard(stringResource(R.string.ark_lifecycle_working))
                }
            }

            is SparkExitFlowState.InProgress -> {
                item {
                    ExitProgressCard(
                        state = flow,
                        amountText = amountText,
                        selectedFundingCount = liveSelection.size,
                        isExitBackupStale = isExitBackupStale,
                        chainTipHeight = chainTipHeight,
                        onCopy = { SecureClipboard.copyAndScheduleClear(context, it) },
                        onCheckStatus = onCheckStatus,
                        onPickFunding = { showFundingDialog = true },
                        onBump = {
                            showBumpDialog = true
                        },
                        onExportBackup = onExportBackup,
                        onSaveCopy = { exportCopyLauncher.launch(exitCopyName) },
                        onDiscard = { showDiscardConfirm = true },
                    )
                }
            }

            is SparkExitFlowState.RedoRequired -> {
                item {
                    ExitRedoCard(
                        state = flow,
                        amountText = amountText,
                        selectedFunding = fundingInputsFor(selectedOutpoints),
                        eligibleCount = eligibleUtxos.size,
                        onPickFunding = { showFundingDialog = true },
                        onRebuild = {
                            guardedExitAction {
                                onRebuild(
                                    flow.quote.feeRateSatPerVb,
                                    fundingInputsFor(selectedOutpoints),
                                )
                            }
                        },
                        // Discards the stored reference set: route through the
                        // confirm dialog like the progress card does.
                        onBackToSetup = { showDiscardConfirm = true },
                    )
                }
            }

            is SparkExitFlowState.Completed -> {
                item {
                    ExitStatusCard(
                        text = stringResource(R.string.spark_exit_done),
                        accent = SuccessGreen,
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showFundingDialog) {
        CoinControlDialog(
            utxos = eligibleUtxos,
            selectedUtxos = eligibleUtxos.filter { it.outpoint in selectedOutpoints },
            useSats = useSats,
            privacyMode = privacyMode,
            onUtxoToggle = { utxo ->
                selectedOutpoints =
                    if (utxo.outpoint in selectedOutpoints) {
                        selectedOutpoints - utxo.outpoint
                    } else {
                        selectedOutpoints + utxo.outpoint
                    }
            },
            onSelectAll = {
                selectedOutpoints = eligibleUtxos.map { it.outpoint }.toSet()
            },
            onClearAll = { selectedOutpoints = emptySet() },
            onDismiss = { showFundingDialog = false },
        )
    }
    if (showBumpDialog) {
        val progress = exitFlow as? SparkExitFlowState.InProgress
        ExitFeeBumpDialog(
            feeEstimationState = feeEstimationState,
            initialRate = (
                progress?.feeRateSatPerVb?.toDouble()?.plus(1.0)
                    ?: SparkUnilateralExitPolicy.DEFAULT_EXIT_FEE_RATE_SAT_VB.toDouble()
            ).coerceAtLeast(
                SparkUnilateralExitPolicy.MIN_EXIT_FEE_RATE_SAT_VB.toDouble(),
            ),
            onRefreshFees = onRefreshBitcoinFees,
            onDismiss = { showBumpDialog = false },
            onConfirm = { rate ->
                showBumpDialog = false
                if (progress != null) {
                    guardedExitAction {
                        onRebuild(
                            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(
                                kotlin.math.ceil(rate).toLong(),
                            ),
                            fundingInputsFor(selectedOutpoints),
                        )
                    }
                }
            },
        )
    }

    if (showDiscardConfirm) {
        ExitDiscardDialog(
            onDismiss = { showDiscardConfirm = false },
            onConfirm = {
                showDiscardConfirm = false
                selectedOutpoints = emptySet()
                onDiscard()
            },
        )
    }

    // Confirm the irreversible build: destination + fee + exact funding set.
    // Dismissed automatically if the flow leaves QuoteReady underneath.
    val confirmQuote = (exitFlow as? SparkExitFlowState.QuoteReady)?.quote
    LaunchedEffect(exitFlow) {
        if (exitFlow !is SparkExitFlowState.QuoteReady) showBuildConfirm = false
    }
    if (showBuildConfirm && confirmQuote != null) {
        ExitBuildConfirmDialog(
            quote = confirmQuote,
            funding = pendingBuildFunding,
            amountText = amountText,
            onDismiss = { showBuildConfirm = false },
            onConfirm = {
                showBuildConfirm = false
                guardedExitAction { onBuild(pendingBuildFunding) }
            },
        )
    }
}

@Composable
private fun ExitErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, ErrorRed),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ErrorRed,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Unclaimed-deposit banner: on-chain deposits are not exit leaves until
 * claimed or matured, so a quote that looks low next to the Balance header
 * is usually this bucket. Routes back to Balance where deposits are claimed.
 */
@Composable
private fun ExitUnclaimedBanner(
    unclaimedDepositSats: Long,
    amountText: (Long) -> String,
    onViewBalance: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, WarningYellow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.spark_exit_unclaimed_format,
                    amountText(unclaimedDepositSats),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = WarningYellow,
            )
            SparkExitSecondaryButton(
                onClick = onViewBalance,
                label = stringResource(R.string.spark_exit_view_balance),
            )
        }
    }
}

@Composable
private fun ExitStatusCard(
    text: String,
    accent: androidx.compose.ui.graphics.Color = TextSecondary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            modifier = Modifier.padding(16.dp),
        )
    }
}
/**
 * Primary action button for the exit flow. Mirrors the sibling Layer screens'
 * primary pattern (filled `Button`, full width, 48.dp, 8.dp corners,
 * `titleMedium`) with the provider accent swapped to [SparkPurple] and dark
 * content per the Spark send/confirm surfaces.
 */
@Composable
private fun SparkExitPrimaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(8.dp),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SparkPurple,
                contentColor = DarkBackground,
                disabledContainerColor = SparkPurple.copy(alpha = 0.3f),
            ),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Secondary action button for the exit flow. Mirrors the sibling pattern of
 * outlined [IbisButton] secondaries stacked full width at 48.dp below the
 * primary (e.g. Cancel on the Spark send-confirm surface).
 */
@Composable
private fun SparkExitSecondaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = TextSecondary,
) {
    IbisButton(
        onClick = onClick,
        enabled = enabled,
        activeColor = activeColor,
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ExitSetupCard(
    feeEstimationState: FeeEstimationResult,
    feeRate: Double,
    onFeeRateChange: (Double) -> Unit,
    onRefreshFees: () -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    quoting: Boolean,
    onQuote: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = { Text(stringResource(R.string.spark_exit_destination)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SparkPurple,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = SparkPurple,
                    ),
            )
            ExitFeeRateSection(
                feeEstimationState = feeEstimationState,
                currentFeeRate = feeRate,
                onFeeRateChange = onFeeRateChange,
                onRefreshFees = onRefreshFees,
                enabled = !quoting,
            )
            // The floor is network-enforced, not our choice: relay policy
            // drops anything below it, so lower input always prices at 1.
            Text(
                text =
                    stringResource(
                        R.string.spark_exit_fee_floor_note,
                        formatFeeRate(SparkUnilateralExitPolicy.MIN_EXIT_FEE_RATE_SAT_VB.toDouble()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            val quoteLabel =
                if (quoting) {
                    stringResource(R.string.ark_lifecycle_working)
                } else {
                    stringResource(R.string.spark_exit_get_quote)
                }
            SparkExitPrimaryButton(
                onClick = onQuote,
                enabled = !quoting && destination.isNotBlank(),
                label = quoteLabel,
            )
            // Operator-outage recovery: merge a previously saved exit-state
            // blob (e.g. from another device) into this wallet.
            SparkExitSecondaryButton(
                onClick = onImportBackup,
                enabled = !quoting,
                label = stringResource(R.string.spark_exit_import),
            )
        }
    }
}

@Composable
private fun ExitQuoteCard(
    quote: SparkExitQuote,
    amountText: (Long) -> String,
    selectedFunding: List<SparkExitFundingUtxo>,
    eligibleCount: Int,
    onPickFunding: () -> Unit,
    onBuild: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    val economics =
        remember(quote) {
            SparkUnilateralExitPolicy.evaluateQuote(
                quote.leafIds.size,
                quote.recoverableValueSats,
                quote.totalFeeSats,
            )
        }
    val fundingTotal = selectedFunding.sumOf { it.valueSats }
    // Single source of truth with the repository build gate; tested in
    // SparkUnilateralExitPolicyTest.
    val fundingReady =
        SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
            quote = quote,
            selectedCount = selectedFunding.size,
            selectedTotalSats = fundingTotal,
        )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExitFeeBreakdown(
                recoverableValueSats = quote.recoverableValueSats,
                totalFeeSats = quote.totalFeeSats,
                cpfpFeeSats = quote.cpfpFeeSats,
                fanoutFeeSats = quote.fanoutFeeSats,
                sweepFeeSats = quote.sweepFeeSats,
                feeRateSatPerVb = quote.feeRateSatPerVb,
                amountText = amountText,
            )
            HorizontalDivider(color = BorderColor)
            when (economics) {
                is SparkUnilateralExitPolicy.QuoteEconomics.Empty -> {
                    Text(
                        text = stringResource(R.string.spark_exit_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                    )
                }
                is SparkUnilateralExitPolicy.QuoteEconomics.NotWorthIt -> {
                    Text(
                        text = stringResource(R.string.spark_exit_not_worth_it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                    )
                }
                is SparkUnilateralExitPolicy.QuoteEconomics.WorthIt -> Unit
            }
            if (eligibleCount == 0) {
                Text(
                    text = stringResource(R.string.spark_exit_funding_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            } else {
                Text(
                    text = stringResource(R.string.spark_exit_funding_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SparkExitSecondaryButton(
                    onClick = onPickFunding,
                    label = stringResource(R.string.spark_exit_funding_button),
                    modifier = Modifier.weight(1f),
                )
                SparkExitPrimaryButton(
                    onClick = onBuild,
                    enabled = fundingReady &&
                        economics is SparkUnilateralExitPolicy.QuoteEconomics.WorthIt,
                    label = stringResource(R.string.spark_exit_build_button),
                    modifier = Modifier.weight(1f),
                )
            }
            SparkExitSecondaryButton(
                onClick = onBackToSetup,
                label = stringResource(R.string.loc_51bac044),
            )
        }
    }
}

@Composable
private fun ExitAmountRow(
    label: String,
    value: String,
    indented: Boolean = false,
    highlighted: Boolean = false,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().then(
                if (indented) Modifier.padding(start = 16.dp) else Modifier,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (indented) FontWeight.Normal else FontWeight.SemiBold,
            color =
                when {
                    highlighted -> SuccessGreen
                    indented -> TextSecondary
                    else -> MaterialTheme.colorScheme.onBackground
                },
        )
    }
}

/**
 * Fee breakdown shared by the quote, progress, and redo cards: headline
 * recoverable and total (with the priced rate), indented per-component rows
 * for nonzero legs, and the net arrival floor.
 *
 * The arrival is `recoverable − sweepFee`: CPFP and fan-out come out of the
 * funding UTXOs, while the sweep takes its fee from the recovered value
 * (unspent funding change lands on top, so this is a floor, hence ≈).
 * Hidden when there is nothing (or nothing net) to receive.
 */
@Composable
private fun ExitFeeBreakdown(
    recoverableValueSats: Long,
    totalFeeSats: Long,
    cpfpFeeSats: Long,
    fanoutFeeSats: Long,
    sweepFeeSats: Long,
    feeRateSatPerVb: Long,
    amountText: (Long) -> String,
) {
    ExitAmountRow(stringResource(R.string.spark_exit_recoverable), amountText(recoverableValueSats))
    ExitAmountRow(
        "${stringResource(R.string.spark_exit_fee)} @ " +
            "${formatFeeRate(feeRateSatPerVb.toDouble())} ${stringResource(R.string.loc_aedd48eb)}",
        amountText(totalFeeSats),
    )
    if (cpfpFeeSats > 0L) {
        ExitAmountRow(stringResource(R.string.spark_exit_fee_cpfp), amountText(cpfpFeeSats), indented = true)
    }
    if (fanoutFeeSats > 0L) {
        ExitAmountRow(stringResource(R.string.spark_exit_fee_fanout), amountText(fanoutFeeSats), indented = true)
    }
    if (sweepFeeSats > 0L) {
        ExitAmountRow(stringResource(R.string.spark_exit_sweep_fee), amountText(sweepFeeSats), indented = true)
    }
    if (recoverableValueSats > sweepFeeSats) {
        ExitAmountRow(
            stringResource(R.string.spark_exit_you_receive),
            "≈ ${amountText(recoverableValueSats - sweepFeeSats)}",
            highlighted = true,
        )
    }
}

@Composable
private fun ExitProgressCard(
    state: SparkExitFlowState.InProgress,
    amountText: (Long) -> String,
    selectedFundingCount: Int,
    isExitBackupStale: Boolean,
    chainTipHeight: Long?,
    onCopy: (String) -> Unit,
    onCheckStatus: () -> Unit,
    onPickFunding: () -> Unit,
    onBump: () -> Unit,
    onExportBackup: () -> Unit,
    onSaveCopy: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExitFeeBreakdown(
                recoverableValueSats = state.recoverableValueSats,
                totalFeeSats = state.totalFeeSats,
                cpfpFeeSats = state.cpfpFeeSats,
                fanoutFeeSats = state.fanoutFeeSats,
                sweepFeeSats = state.sweepFeeSats,
                feeRateSatPerVb = state.feeRateSatPerVb,
                amountText = amountText,
            )
            HorizontalDivider(color = BorderColor)
            if (isExitBackupStale) {
                Text(
                    text = stringResource(R.string.spark_exit_backup_stale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BitcoinOrange,
                )
            }
            Text(
                text = stringResource(R.string.spark_exit_broadcast_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            state.txs.forEach { tx ->
                ExitTxRow(
                    tx = tx,
                    chainTipHeight = chainTipHeight,
                    onCopy = onCopy,
                )
            }
            // Chain truth lives in the SDK: refresh statuses after broadcasts
            // and whenever progress is reopened. Resending a step is harmless.
            SparkExitPrimaryButton(
                onClick = onCheckStatus,
                label = stringResource(R.string.spark_exit_check_status),
            )
            // Fee bumps rebuild from the funding selection when present and
            // otherwise reuse the inputs persisted at build time. Re-picking
            // matters after the selection is gone (restart) or spent.
            val fundingLabel =
                if (selectedFundingCount == 0) {
                    stringResource(R.string.spark_exit_pick_funding)
                } else {
                    "${stringResource(R.string.spark_exit_pick_funding)} ($selectedFundingCount)"
                }
            SparkExitSecondaryButton(
                onClick = onPickFunding,
                label = fundingLabel,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBump, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.spark_exit_fee_bump), color = SparkPurple)
                }
                TextButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.spark_exit_backup), color = TextSecondary)
                }
                TextButton(onClick = onSaveCopy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.spark_exit_save_copy), color = TextSecondary)
                }
            }
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.spark_exit_discard), color = ErrorRed)
            }
        }
    }
}

@Composable
private fun ExitRedoCard(
    state: SparkExitFlowState.RedoRequired,
    amountText: (Long) -> String,
    selectedFunding: List<SparkExitFundingUtxo>,
    eligibleCount: Int,
    onPickFunding: () -> Unit,
    onRebuild: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    // Same build gate as the quote card (tested in
    // SparkUnilateralExitPolicyTest): the stored quote determines the funding
    // mode, so an underfunded selection can never reach the SDK.
    val fundingTotal = selectedFunding.sumOf { it.valueSats }
    val fundingReady =
        SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
            quote = state.quote,
            selectedCount = selectedFunding.size,
            selectedTotalSats = fundingTotal,
        )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, BitcoinOrange),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    when (state.reason) {
                        SparkExitRedoReason.ON_CHAIN_STATE_DIVERGED ->
                            stringResource(R.string.spark_exit_redo_diverged)
                        SparkExitRedoReason.SCHEMA_UPGRADED ->
                            stringResource(R.string.spark_exit_redo_upgraded)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = BitcoinOrange,
            )
            ExitFeeBreakdown(
                recoverableValueSats = state.quote.recoverableValueSats,
                totalFeeSats = state.quote.totalFeeSats,
                cpfpFeeSats = state.quote.cpfpFeeSats,
                fanoutFeeSats = state.quote.fanoutFeeSats,
                sweepFeeSats = state.quote.sweepFeeSats,
                feeRateSatPerVb = state.quote.feeRateSatPerVb,
                amountText = amountText,
            )
            HorizontalDivider(color = BorderColor)
            if (eligibleCount == 0) {
                Text(
                    text = stringResource(R.string.spark_exit_funding_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                )
            } else {
                Text(
                    text = stringResource(R.string.spark_exit_funding_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SparkExitSecondaryButton(
                    onClick = onPickFunding,
                    label = stringResource(R.string.spark_exit_funding_button),
                    modifier = Modifier.weight(1f),
                )
                SparkExitPrimaryButton(
                    onClick = onRebuild,
                    enabled = fundingReady,
                    label = stringResource(R.string.spark_exit_build_button),
                    modifier = Modifier.weight(1f),
                )
            }
            SparkExitSecondaryButton(
                onClick = onBackToSetup,
                label = stringResource(R.string.spark_exit_start_over),
            )
        }
    }
}

@Composable
private fun ExitTxRow(
    tx: SparkExitTx,
    chainTipHeight: Long?,
    onCopy: (String) -> Unit,
) {
    val readiness = SparkUnilateralExitPolicy.broadcastReadinessOf(tx)
    val statusColor =
        when (readiness) {
            SparkUnilateralExitPolicy.BroadcastReadiness.Ready -> SparkPurple
            is SparkUnilateralExitPolicy.BroadcastReadiness.AlreadyConfirmed -> SuccessGreen
            is SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnTimelock,
            is SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnDependencies,
            -> BitcoinOrange
            is SparkUnilateralExitPolicy.BroadcastReadiness.Unknown -> ErrorRed
        }
    val statusText =
        when (readiness) {
            SparkUnilateralExitPolicy.BroadcastReadiness.Ready ->
                stringResource(R.string.spark_exit_ready)
            is SparkUnilateralExitPolicy.BroadcastReadiness.AlreadyConfirmed ->
                stringResource(R.string.spark_exit_confirmed)
            is SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnDependencies ->
                stringResource(R.string.spark_exit_wait_deps)
            is SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnTimelock -> {
                val height = readiness.spendableAtHeight?.toLong()
                if (height != null && height > 0L && chainTipHeight != null && chainTipHeight > 0L) {
                    stringResource(
                        R.string.spark_exit_wait_timelock_countdown_format,
                        height,
                        chainTipHeight,
                    )
                } else if (height != null && height > 0L) {
                    stringResource(R.string.spark_exit_wait_timelock_at_format, height)
                } else {
                    stringResource(R.string.spark_exit_wait_timelock)
                }
            }
            is SparkUnilateralExitPolicy.BroadcastReadiness.Unknown ->
                stringResource(R.string.spark_exit_unverified)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tx.kind.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SparkPurple,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (readiness is SparkUnilateralExitPolicy.BroadcastReadiness.AlreadyConfirmed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                        )
                    }
                    // Copy affordances only while the step still needs action;
                    // confirmed steps need nothing from the user.
                    if (readiness !is SparkUnilateralExitPolicy.BroadcastReadiness.AlreadyConfirmed) {
                        IconButton(onClick = { onCopy(tx.txHex) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    }
                }
            }
            Text(
                text = tx.txid.take(16) + "…",
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            tx.csvTimelockBlocks?.let { blocks ->
                Text(
                    text = stringResource(R.string.spark_exit_tx_csv_format, blocks.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange,
                )
            }
            if (tx.dependsOn.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.spark_exit_tx_depends_format, tx.dependsOn.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (readiness is SparkUnilateralExitPolicy.BroadcastReadiness.Ready) {
                tx.cpfpTxHex?.let { cpfp ->
                    TextButton(onClick = { onCopy(cpfp) }) {
                        Text(stringResource(R.string.spark_exit_tx_cpfp_copy), color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitFeeBumpDialog(
    feeEstimationState: FeeEstimationResult,
    initialRate: Double,
    onRefreshFees: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var rate by remember(initialRate) { mutableDoubleStateOf(initialRate) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.spark_exit_fee_bump),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                ExitFeeRateSection(
                    feeEstimationState = feeEstimationState,
                    currentFeeRate = rate,
                    onFeeRateChange = { rate = it },
                    onRefreshFees = onRefreshFees,
                    enabled = true,
                    // The dialog carries its own deliberate initial rate
                    // (last rate + 1); don't let auto-select discard it.
                    preselectFastest = false,
                )
                SparkExitPrimaryButton(
                    onClick = { onConfirm(rate) },
                    label = stringResource(R.string.spark_exit_fee_bump),
                )
            }
        }
    }
}

@Composable
private fun ExitDiscardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                    )
                    Text(
                        text = stringResource(R.string.spark_exit_discard),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                SparkExitSecondaryButton(
                    onClick = onConfirm,
                    label = stringResource(R.string.spark_exit_discard),
                    activeColor = ErrorRed,
                )
                SparkExitSecondaryButton(
                    onClick = onDismiss,
                    label = stringResource(R.string.loc_51bac044),
                )
            }
        }
    }
}

/**
 * Pre-build confirmation: the build signs L1 funding inputs and the exit set,
 * so the user re-confirms the exact destination, fee, and funding snapshot
 * (captured at Build-tap time) before anything is signed. Reuses existing
 * strings only — no new localization keys.
 */
@Composable
private fun ExitBuildConfirmDialog(
    quote: SparkExitQuote,
    funding: List<SparkExitFundingUtxo>,
    amountText: (Long) -> String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.spark_exit_build_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.spark_exit_destination),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = quote.destination,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                HorizontalDivider(color = BorderColor)
                ExitAmountRow(
                    stringResource(R.string.spark_exit_funding_needed),
                    amountText(funding.sumOf { it.valueSats }),
                )
                ExitAmountRow(
                    stringResource(R.string.spark_exit_fee),
                    amountText(quote.totalFeeSats),
                )
                ExitAmountRow(
                    stringResource(R.string.spark_exit_recoverable),
                    amountText(quote.recoverableValueSats),
                    highlighted = true,
                )
                SparkExitPrimaryButton(
                    onClick = onConfirm,
                    label = stringResource(R.string.spark_exit_build_button),
                )
                SparkExitSecondaryButton(
                    onClick = onDismiss,
                    label = stringResource(R.string.loc_51bac044),
                )
            }
        }
    }
}

private enum class ExitFeeOption {
    FASTEST,
    HALF_HOUR,
    HOUR,
    CUSTOM,
}

/**
 * Fee-rate picker for unilateral exits: 1 sat/vB floor, no
 * ceiling, whole sats only.
 *
 * Unlike the shared [FeeRateSection] (capped at 2000 sat/vB for general
 * sends), this widget only floors at the SDK's minimum expressible rate and
 * otherwise passes rates through untouched — an emergency exit must be
 * quotable at spike market rates, and the quote review (recoverable vs.
 * total fee) is the guardrail against overpayment, not a rate cap.
 * Displayed preset rates are floored too, so a sub-sat network estimate
 * shows (and quotes) as 1 sat/vB rather than implying precision the exit
 * path cannot price.
 *
 * Exits cannot price fractions at all (the SDK takes integer sat/vB and the
 * quote path ceils), so every value here is normalized to whole sats up
 * front: presets display and push `ceil`, and the custom field accepts digits
 * only. The shared fee fetch is untouched — sub-sat precision still serves
 * flows that can use it (e.g. L1).
 *
 * The widget is rate-controlled but selection-internal: the 1-block
 * (fastest) preset is auto-selected once estimates load (matching the
 * screen's initial rate), and the selection then follows repricing until
 * the user picks a preset or edits the custom field.
 */
@Composable
private fun ExitFeeRateSection(
    feeEstimationState: FeeEstimationResult,
    currentFeeRate: Double,
    onFeeRateChange: (Double) -> Unit,
    onRefreshFees: () -> Unit,
    enabled: Boolean,
    preselectFastest: Boolean = true,
) {
    val minRate = SparkUnilateralExitPolicy.MIN_EXIT_FEE_RATE_SAT_VB.toDouble()
    var selected by remember { mutableStateOf<ExitFeeOption?>(null) }
    var userPicked by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf<String?>(null) }
    val customFocusRequester = remember { FocusRequester() }

    // Whole sats only, floored at the SDK minimum: the exit path prices
    // integer sat/vB, so fractions are rounded up here — where the user sees
    // them — instead of silently at quote time.
    fun wholeSats(rate: Double): Double = kotlin.math.ceil(rate).coerceAtLeast(minRate)

    val estimates = (feeEstimationState as? FeeEstimationResult.Success)?.estimates

    LaunchedEffect(selected) {
        if (selected == ExitFeeOption.CUSTOM) {
            customFocusRequester.requestFocus()
        }
    }

    // Default to the 1-block (fastest) preset and follow the selected preset
    // across estimate refreshes: a rate picked from an older snapshot must
    // not linger after the network reprices. Auto-select is skippable (fee
    // bumps carry their own deliberate initial rate); otherwise, until the
    // user interacts, the fastest preset is selected to match the screen's
    // initial rate. A null selection with no estimates means "caller default
    // stands" and is never pushed.
    LaunchedEffect(estimates) {
        val live = estimates ?: return@LaunchedEffect
        when {
            !userPicked && preselectFastest -> {
                selected = ExitFeeOption.FASTEST
                onFeeRateChange(wholeSats(live.fastestFee))
            }
            selected == ExitFeeOption.FASTEST -> onFeeRateChange(wholeSats(live.fastestFee))
            selected == ExitFeeOption.HALF_HOUR -> onFeeRateChange(wholeSats(live.halfHourFee))
            selected == ExitFeeOption.HOUR -> onFeeRateChange(wholeSats(live.hourFee))
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.loc_943c89b7),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )

            if (feeEstimationState !is FeeEstimationResult.Disabled) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable(enabled = enabled && feeEstimationState !is FeeEstimationResult.Loading) {
                                onRefreshFees()
                            },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh fees",
                        tint =
                            if (feeEstimationState is FeeEstimationResult.Loading) {
                                TextSecondary.copy(alpha = 0.5f)
                            } else {
                                BitcoinOrange
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (feeEstimationState is FeeEstimationResult.Disabled) {
            if (customInput == null) {
                customInput = formatFeeRate(kotlin.math.ceil(currentFeeRate))
            }
            ExitManualFeeInput(
                value = customInput ?: "",
                minRate = minRate,
                enabled = enabled,
                onValueChange = { input ->
                    customInput = input
                    userPicked = true
                    input.toDoubleOrNull()?.let { onFeeRateChange(wholeSats(it)) }
                },
            )
        } else {
            val isLoading = feeEstimationState is FeeEstimationResult.Loading
            val isElectrum = estimates?.source == FeeEstimateSource.ELECTRUM_SERVER
            val fastCount = if (isElectrum) 2 else 1
            val medCount = if (isElectrum) 6 else 3
            val slowCount = if (isElectrum) 12 else 6
            val fastLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, fastCount, fastCount)
            val medLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, medCount, medCount)
            val slowLabel = pluralStringResource(R.plurals.fee_estimate_approx_blocks, slowCount, slowCount)

            val errorState = feeEstimationState as? FeeEstimationResult.Error
            if (errorState != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WarningYellow.copy(alpha = 0.1f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_82c7cb6e),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningYellow,
                        )
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                ExitFeeTargetButton(
                    label = fastLabel,
                    feeRate = estimates?.fastestFee?.let { wholeSats(it) },
                    isSelected = selected == ExitFeeOption.FASTEST,
                    onClick = {
                        estimates?.let {
                            selected = ExitFeeOption.FASTEST
                            userPicked = true
                            customInput = null
                            onFeeRateChange(wholeSats(it.fastestFee))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                ExitFeeTargetButton(
                    label = medLabel,
                    feeRate = estimates?.halfHourFee?.let { wholeSats(it) },
                    isSelected = selected == ExitFeeOption.HALF_HOUR,
                    onClick = {
                        estimates?.let {
                            selected = ExitFeeOption.HALF_HOUR
                            userPicked = true
                            customInput = null
                            onFeeRateChange(wholeSats(it.halfHourFee))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )

                ExitFeeTargetButton(
                    label = slowLabel,
                    feeRate = estimates?.hourFee?.let { wholeSats(it) },
                    isSelected = selected == ExitFeeOption.HOUR,
                    onClick = {
                        estimates?.let {
                            selected = ExitFeeOption.HOUR
                            userPicked = true
                            customInput = null
                            onFeeRateChange(wholeSats(it.hourFee))
                        }
                    },
                    enabled = enabled,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            if (selected == ExitFeeOption.CUSTOM) {
                if (customInput == null) {
                    customInput = formatFeeRate(kotlin.math.ceil(currentFeeRate))
                }
                ExitManualFeeInput(
                    value = customInput ?: "",
                    minRate = minRate,
                    enabled = enabled,
                    modifier = Modifier.focusRequester(customFocusRequester),
                    onValueChange = { input ->
                        customInput = input
                        userPicked = true
                        input.toDoubleOrNull()?.let { onFeeRateChange(wholeSats(it)) }
                    },
                )
            } else {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled && !isLoading) {
                                selected = ExitFeeOption.CUSTOM
                                userPicked = true
                                customInput = formatFeeRate(kotlin.math.ceil(currentFeeRate))
                            },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_f22813ad),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitFeeTargetButton(
    label: String,
    feeRate: Double?,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val backgroundColor = if (isSelected) SparkPurple.copy(alpha = 0.15f) else DarkSurface
    val borderColor = if (isSelected) SparkPurple else BorderColor
    val textColor = if (isSelected) SparkPurple else TextSecondary

    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled && !isLoading, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SparkPurple,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = feeRate?.let { formatFeeRate(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.loc_aedd48eb),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExitManualFeeInput(
    value: String,
    minRate: Double,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val parsedRate = value.toDoubleOrNull()
    val isBelowMin = parsedRate != null && parsedRate > 0.0 && parsedRate < minRate

    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty()) {
                onValueChange(input)
                return@OutlinedTextField
            }

            // Whole sats only: exits cannot price fractions, so the field
            // accepts digits alone (no decimal point).
            val isValidFormat = input.matches(Regex("^\\d*$"))
            val hasInvalidLeadingZeros = input.length > 1 && input.startsWith("0")

            if (isValidFormat && !hasInvalidLeadingZeros) {
                onValueChange(input)
            }
        },
        modifier = modifier.fillMaxWidth(),
        suffix = {
            Text(
                text = stringResource(R.string.loc_aedd48eb),
                color = TextSecondary.copy(alpha = 0.7f),
            )
        },
        placeholder = { Text(formatFeeRate(minRate), color = TextSecondary.copy(alpha = 0.5f)) },
        isError = isBelowMin,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isBelowMin) WarningYellow else SparkPurple,
                unfocusedBorderColor = if (isBelowMin) WarningYellow else BorderColor,
                focusedLabelColor = if (isBelowMin) WarningYellow else SparkPurple,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = SparkPurple,
            ),
    )
}
