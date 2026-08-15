package github.aeonbtc.ibiswallet.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.ArkTransferState
import github.aeonbtc.ibiswallet.data.model.ArkWalletState
import github.aeonbtc.ibiswallet.data.model.DryRunResult
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.data.model.UtxoInfo
import github.aeonbtc.ibiswallet.data.repository.ArkDepositPolicy
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.ElectrumConnectionBanner
import github.aeonbtc.ibiswallet.ui.components.FeeRateOption
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableDialogSurface
import github.aeonbtc.ibiswallet.ui.theme.ArkRust
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.util.ParsedSendRecipient
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.parseSendRecipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

private val Layer1Accent = Color(0xFFCD7F32)

private sealed interface ArkTransferReview {
    val amountSats: Long
    val feeSats: Long?
    val netAmountSats: Long?
    val feeRateSatPerVb: Double?
    val grossAmountSats: Long?

    data class Board(
        override val amountSats: Long,
        override val feeSats: Long?,
        override val netAmountSats: Long?,
        val boardAll: Boolean,
        /** Bark on-chain Bitcoin address used for BTC→Ark deposit. */
        val bitcoinDepositAddress: String?,
        override val feeRateSatPerVb: Double? = null,
        override val grossAmountSats: Long? = null,
        val changeSats: Long = 0L,
        val hasChange: Boolean = false,
        val selectedUtxos: List<UtxoInfo> = emptyList(),
    ) : ArkTransferReview

    data class Offboard(
        override val amountSats: Long,
        override val feeSats: Long?,
        override val netAmountSats: Long?,
        val destinationAddress: String,
        val offboardAll: Boolean,
        override val feeRateSatPerVb: Double? = null,
        override val grossAmountSats: Long? = null,
    ) : ArkTransferReview
}

@Composable
fun ArkTransferScreen(
    arkState: ArkWalletState,
    transferState: ArkTransferState,
    layer1Address: String?,
    layer1BalanceSats: Long,
    layer1Utxos: List<UtxoInfo> = emptyList(),
    spendUnconfirmed: Boolean = true,
    requireCoinControl: Boolean = false,
    denomination: String,
    btcPrice: Double? = null,
    fiatCurrency: String = "USD",
    privacyMode: Boolean,
    isElectrumConnected: Boolean,
    isElectrumConnecting: Boolean = false,
    electrumBannerDismissed: Boolean = false,
    hasElectrumServerConfigured: Boolean = true,
    onConnectElectrumServer: () -> Unit = {},
    onOpenElectrumServerSettings: () -> Unit = {},
    onDismissElectrumBanner: () -> Unit = {},
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    onRefreshBitcoinFees: () -> Unit = {},
    /** BIP39 passphrase wallets cannot board via Bark's built-in on-chain wallet. */
    arkOnchainBoardingAvailable: Boolean = true,
    onPrepareBoard: (Long, Boolean) -> Unit,
    /** L1 funding dry-run to Bark Bitcoin deposit address (matches Spark deposit fees). */
    onPreviewLayer1Funding: suspend (String, Long, Double, Boolean, List<UtxoInfo>?) -> DryRunResult? =
        { _, _, _, _, _ -> null },
    /**
     * Fund Bark's Bitcoin deposit address from Ibis L1 (Spark-style).
     * Must not call Bark boardAll alone — that spends Bark's empty on-chain wallet.
     * Completion/errors arrive via [transferState].
     */
    onExecuteBoard: (
        address: String,
        amountSats: Long,
        feeRateSatPerVb: Double,
        isMaxSend: Boolean,
        selectedUtxos: List<UtxoInfo>?,
        precomputedFeeSats: Long?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onPrepareOffboard: (String, Long?, Boolean) -> Unit,
    onExecuteOffboard: () -> Unit,
    onReset: () -> Unit,
    onGenerateLayer1Address: () -> Unit,
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    val scope = rememberCoroutineScope()
    var selectedDirection by remember { mutableIntStateOf(0) }
    var isPreparingReview by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var isMaxMode by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var reviewError by remember { mutableStateOf<String?>(null) }
    var reviewState by remember { mutableStateOf<ArkTransferReview?>(null) }
    var isExecutingReview by remember { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var useCustomDestination by rememberSaveable { mutableStateOf(false) }
    var customDestination by rememberSaveable { mutableStateOf("") }
    var showCustomDestinationQrScanner by rememberSaveable { mutableStateOf(false) }
    var useCustomFeeRate by rememberSaveable { mutableStateOf(false) }
    var customFeeRateInput by rememberSaveable { mutableStateOf("") }
    var customFeeRate by rememberSaveable { mutableDoubleStateOf(minFeeRate) }
    var customFeeOptionName by rememberSaveable { mutableStateOf(FeeRateOption.HALF_HOUR.name) }
    var showCoinControl by rememberSaveable { mutableStateOf(false) }
    val selectedFundingUtxos = remember { mutableStateListOf<UtxoInfo>() }
    val isLayer1ToArk = selectedDirection == 0
    val spendableBitcoinUtxos =
        remember(layer1Utxos, spendUnconfirmed) {
            layer1Utxos.filter { !it.isFrozen && (spendUnconfirmed || it.isConfirmed) }
        }
    LaunchedEffect(spendableBitcoinUtxos) {
        reconcileCoinControlSelection(selectedFundingUtxos, spendableBitcoinUtxos)
    }
    LaunchedEffect(isLayer1ToArk) {
        if (!isLayer1ToArk) {
            showCoinControl = false
            selectedFundingUtxos.clear()
        }
    }
    val selectedFundingSnapshot = selectedFundingUtxos.toList()
    val layer1AvailableBalance =
        if (selectedFundingSnapshot.isNotEmpty()) {
            selectedFundingSnapshot.sumOf { it.amountSats.toLong() }
        } else {
            spendableBitcoinUtxos.sumOf { it.amountSats.toLong() }.takeIf { it > 0L }
                ?: layer1BalanceSats
        }

    val layer1Label = stringResource(R.string.loc_b67a01a5)
    val layer2Label = stringResource(R.string.loc_2f73501f)
    val fromLayerLabel = if (isLayer1ToArk) layer1Label else layer2Label
    val toLayerLabel = if (isLayer1ToArk) layer2Label else layer1Label
    val fromLabel = if (isLayer1ToArk) "BTC" else stringResource(R.string.ark_title)
    val toLabel = if (isLayer1ToArk) stringResource(R.string.ark_title) else "BTC"
    val fromColor = if (isLayer1ToArk) Layer1Accent else ArkRust
    val toColor = if (isLayer1ToArk) ArkRust else Layer1Accent
    val availableBalance = if (isLayer1ToArk) layer1AvailableBalance else arkState.spendableSats
    val invalidBitcoinAddressLabel = stringResource(R.string.loc_04536bb4)
    val enterDestinationAddressLabel = stringResource(R.string.loc_a18fd453)
    val customFeeRateLabel = stringResource(R.string.spark_transfer_custom_fee_rate)
    val enterFeeRateLabel = stringResource(R.string.spark_transfer_enter_fee_rate)
    val enterValidFeeRateLabel = stringResource(R.string.loc_857c8623)
    val enterFeeRateAboveZeroLabel = stringResource(R.string.loc_f2b5f4d4)
    val destinationOverrideHint =
        if (isLayer1ToArk) {
            stringResource(R.string.ark_transfer_l1_to_ark_destination_hint)
        } else {
            stringResource(R.string.ark_transfer_ark_to_l1_destination_hint)
        }
    val destinationPlaceholder =
        if (isLayer1ToArk) {
            stringResource(R.string.ark_transfer_l1_to_ark_destination_placeholder)
        } else {
            stringResource(R.string.loc_a18fd453)
        }
    val customDestinationParsed =
        remember(customDestination, useCustomDestination) {
            customDestination
                .trim()
                .takeIf { useCustomDestination && it.isNotEmpty() }
                ?.let(::parseSendRecipient)
        }
    val destinationValidationError =
        when (val parsed = customDestinationParsed) {
            null -> null
            is ParsedSendRecipient.Bitcoin -> null
            is ParsedSendRecipient.Unknown -> parsed.errorMessage
            else -> invalidBitcoinAddressLabel
        }
    val resolvedCustomDestination =
        when (val parsed = customDestinationParsed) {
            is ParsedSendRecipient.Bitcoin -> parsed.address
            else -> customDestination.trim().takeIf { useCustomDestination && it.isNotEmpty() }
        }
    // Custom dest only for Ark→L1 offboard. Boarding has no dest param (always this wallet).
    val customDestinationSupported = !isLayer1ToArk
    val offboardDestination =
        when {
            customDestinationSupported && useCustomDestination && destinationValidationError == null ->
                resolvedCustomDestination
            else -> layer1Address
        }
    val isCustomDestinationMissing =
        customDestinationSupported && useCustomDestination && customDestination.isBlank()
    val customFeeOption =
        remember(customFeeOptionName) {
            runCatching { FeeRateOption.valueOf(customFeeOptionName) }
                .getOrDefault(FeeRateOption.HALF_HOUR)
        }
    val defaultFeeRate =
        (
            (feeEstimationState as? FeeEstimationResult.Success)?.estimates?.halfHourFee
                ?: minFeeRate
            )
            .coerceAtLeast(minFeeRate)
    // Cooperative offboard fee is set by Bark/ASP — custom fee UI only applies L1→Ark boarding.
    val customFeeRateSupported = isLayer1ToArk
    val feeRateValidationError =
        when {
            !customFeeRateSupported || !useCustomFeeRate -> null
            customFeeOption != FeeRateOption.CUSTOM -> null
            customFeeRateInput.isBlank() -> enterFeeRateLabel
            customFeeRateInput.toDoubleOrNull() == null -> enterValidFeeRateLabel
            customFeeRateInput.toDoubleOrNull()!! <= 0.0 -> enterFeeRateAboveZeroLabel
            else -> null
        }
    val customFeeUnavailableNote = stringResource(R.string.ark_transfer_custom_fee_unavailable)
    val customDestinationUnavailableNote =
        stringResource(R.string.ark_transfer_custom_destination_unavailable)

    val amountSats =
        remember(amountInput, useSats, isUsdMode, btcPrice, isMaxMode, availableBalance) {
            when {
                isMaxMode -> availableBalance.takeIf { it > 0 }
                amountInput.isBlank() -> null
                isUsdMode && btcPrice != null && btcPrice > 0 ->
                    amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                        kotlin.math.round((it / btcPrice) * 100_000_000.0).toLong()
                    }
                useSats -> amountInput.replace(",", "").toLongOrNull()?.takeIf { it > 0 }
                else ->
                    amountInput.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                        kotlin.math.round(it * 100_000_000.0).toLong()
                    }
            }
        }

    // Stay busy from click through dry-run + dialog open so controls don't re-enable mid-flow.
    val isBusy =
        isPreparingReview ||
            isExecutingReview ||
            transferState is ArkTransferState.Preparing ||
            transferState is ArkTransferState.InProgress ||
            (transferState is ArkTransferState.BoardPreview && reviewState == null) ||
            (transferState is ArkTransferState.OffboardPreview && reviewState == null)
    val fundingFeeRate =
        if (isLayer1ToArk && useCustomFeeRate) {
            customFeeRate
        } else {
            defaultFeeRate
        }
    val minBoardAmountSats = arkState.minBoardAmountSats?.takeIf { it > 0L }
    val amountBelowMinBoard =
        isLayer1ToArk &&
            minBoardAmountSats != null &&
            amountSats != null &&
            ArkDepositPolicy.isBelowMinBoardAmount(amountSats, minBoardAmountSats)
    val canPrepare =
        !isBusy &&
            amountSats != null &&
            amountSats > 0 &&
            isElectrumConnected &&
            !(isLayer1ToArk && !arkOnchainBoardingAvailable) &&
            !amountBelowMinBoard &&
            !isCustomDestinationMissing &&
            destinationValidationError == null &&
            feeRateValidationError == null &&
            reviewState == null
    val enterAmountLabel = stringResource(R.string.ark_enter_amount)
    val belowMinBoardLabel =
        minBoardAmountSats?.let { min ->
            stringResource(
                R.string.ark_transfer_below_min_board_format,
                formatAmount(min.toULong(), useSats, includeUnit = true),
            )
        }
    val layer1AddressMissingLabel = stringResource(R.string.loc_a18fd453)
    val needsElectrumLabel = stringResource(R.string.ark_transfer_needs_electrum)
    val unableToPrepareSwapLabel = stringResource(R.string.swap_unable_to_prepare)
    val passphraseBoardUnavailableLabel =
        stringResource(R.string.ark_error_passphrase_board_unavailable)
    val depositAddressMissingLabel = stringResource(R.string.ark_deposit_address_missing)

    LaunchedEffect(layer1Address) {
        if (layer1Address.isNullOrBlank()) onGenerateLayer1Address()
    }
    // Keyed by preview identity so dry-run runs once per prepare, not on recomposition thrash.
    LaunchedEffect(transferState) {
        when (val state = transferState) {
            is ArkTransferState.BoardPreview -> {
                if (reviewState is ArkTransferReview.Board) return@LaunchedEffect
                val depositAddress = state.bitcoinDepositAddress?.takeIf { it.isNotBlank() }
                // L1 dry-run against Bark's Bitcoin boarding address — same pattern as Spark.
                isPreparingReview = true
                reviewError = null
                try {
                    val fundingUtxos = selectedFundingSnapshot.takeIf { it.isNotEmpty() }
                    if (depositAddress != null) {
                        val dryRun =
                            onPreviewLayer1Funding(
                                depositAddress,
                                state.amountSats,
                                fundingFeeRate,
                                state.boardAll,
                                fundingUtxos,
                            )
                        if (dryRun == null || dryRun.isError) {
                            reviewError =
                                dryRun?.error
                                    ?: unableToPrepareSwapLabel
                            isPreparingReview = false
                            onReset()
                        } else {
                            reviewState =
                                ArkTransferReview.Board(
                                    amountSats = dryRun.recipientAmountSats.takeIf { it > 0L }
                                        ?: state.amountSats,
                                    feeSats = dryRun.feeSats,
                                    netAmountSats = dryRun.recipientAmountSats,
                                    boardAll = state.boardAll,
                                    bitcoinDepositAddress = depositAddress,
                                    feeRateSatPerVb = dryRun.effectiveFeeRate.takeIf { it > 0.0 }
                                        ?: fundingFeeRate,
                                    grossAmountSats =
                                        dryRun.recipientAmountSats + dryRun.feeSats + dryRun.changeSats,
                                    changeSats = dryRun.changeSats,
                                    hasChange = dryRun.hasChange,
                                    selectedUtxos = fundingUtxos.orEmpty(),
                                )
                            localError = null
                            // Keep busy flag until dialog is dismissible; drop after review is up.
                            isPreparingReview = false
                        }
                    } else {
                        // No on-chain wallet address — still open review with Bark estimate/fallback.
                        reviewState =
                            ArkTransferReview.Board(
                                amountSats = state.amountSats,
                                feeSats = state.feeSats,
                                netAmountSats = state.netAmountSats,
                                boardAll = state.boardAll,
                                bitcoinDepositAddress = null,
                                feeRateSatPerVb = state.feeRateSatPerVb ?: fundingFeeRate,
                                grossAmountSats = state.grossAmountSats,
                                selectedUtxos = fundingUtxos.orEmpty(),
                            )
                        reviewError = null
                        localError = null
                        isPreparingReview = false
                    }
                } catch (e: CancellationException) {
                    isPreparingReview = false
                    throw e
                } catch (e: Exception) {
                    reviewError = e.message ?: unableToPrepareSwapLabel
                    isPreparingReview = false
                    onReset()
                }
            }
            is ArkTransferState.OffboardPreview -> {
                if (reviewState is ArkTransferReview.Offboard) return@LaunchedEffect
                reviewState =
                    ArkTransferReview.Offboard(
                        amountSats = state.amountSats ?: 0L,
                        feeSats = state.feeSats,
                        netAmountSats = state.netAmountSats,
                        destinationAddress = state.destinationAddress,
                        offboardAll = state.offboardAll,
                        feeRateSatPerVb = state.feeRateSatPerVb,
                        grossAmountSats = state.grossAmountSats,
                    )
                reviewError = null
                localError = null
                isPreparingReview = false
            }
            is ArkTransferState.Error -> {
                isPreparingReview = false
                if (reviewState != null || isExecutingReview) {
                    reviewError = state.message
                    isExecutingReview = false
                } else {
                    localError = state.message
                }
            }
            is ArkTransferState.Completed -> {
                if (isExecutingReview || reviewState != null) {
                    val txid = state.detail?.takeIf { it.isNotBlank() }
                    if (txid != null) {
                        val short =
                            if (txid.length > 16) {
                                "${txid.take(8)}…${txid.takeLast(8)}"
                            } else {
                                txid
                            }
                        Toast
                            .makeText(context, "Sent $short", Toast.LENGTH_LONG)
                            .show()
                    }
                    reviewState = null
                    reviewError = null
                    isExecutingReview = false
                    amountInput = ""
                    isMaxMode = false
                    isUsdMode = false
                    onReset()
                }
            }
            ArkTransferState.InProgress -> {
                isExecutingReview = true
            }
            ArkTransferState.Preparing, ArkTransferState.Idle -> Unit
        }
    }
    LaunchedEffect(feeEstimationState, minFeeRate) {
        if (!useCustomFeeRate) {
            customFeeRate = defaultFeeRate
        }
    }
    LaunchedEffect(isLayer1ToArk) {
        if (!customDestinationSupported && useCustomDestination) {
            useCustomDestination = false
            customDestination = ""
        }
        if (!customFeeRateSupported && useCustomFeeRate) {
            useCustomFeeRate = false
            customFeeRateInput = ""
            customFeeRate = defaultFeeRate
            customFeeOptionName = FeeRateOption.HALF_HOUR.name
        }
    }

    fun resetDirectionUi() {
        isMaxMode = false
        amountInput = ""
        localError = null
        reviewError = null
        reviewState = null
        isExecutingReview = false
        customDestination = ""
        useCustomDestination = false
        useCustomFeeRate = false
        customFeeRateInput = ""
        customFeeRate = defaultFeeRate
        customFeeOptionName = FeeRateOption.HALF_HOUR.name
        onReset()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (!isElectrumConnected && !isElectrumConnecting && !electrumBannerDismissed) {
            ElectrumConnectionBanner(
                isConnecting = false,
                hasServerConfigured = hasElectrumServerConfigured,
                onConnect = onConnectElectrumServer,
                onOpenServerSettings = onOpenElectrumServerSettings,
                onWorkOffline = onDismissElectrumBanner,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.loc_f0100030),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLayer1ToArk) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val coinControlActive = selectedFundingSnapshot.isNotEmpty()
                        val hasFundingUtxos = spendableBitcoinUtxos.isNotEmpty()
                        Card(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = hasFundingUtxos && !isBusy) {
                                        showCoinControl = true
                                    },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (coinControlActive) {
                                            fromColor.copy(alpha = 0.15f)
                                        } else {
                                            DarkSurface
                                        },
                                ),
                            border = BorderStroke(1.dp, if (coinControlActive) fromColor else BorderColor),
                        ) {
                            Text(
                                text =
                                    if (coinControlActive) {
                                        stringResource(
                                            R.string.swap_coin_control_utxo_badge_format,
                                            selectedFundingSnapshot.size,
                                        )
                                    } else {
                                        stringResource(R.string.loc_abb2f6d2)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    when {
                                        coinControlActive -> fromColor
                                        hasFundingUtxos -> TextSecondary
                                        else -> TextSecondary.copy(alpha = 0.5f)
                                    },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArkLayerDirectionCard(
                        label = stringResource(R.string.loc_19280e4e),
                        layer = fromLayerLabel,
                        asset = fromLabel,
                        accent = fromColor,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDirection = 1 - selectedDirection
                            resetDirectionUi()
                        },
                    )
                    IconButton(
                        onClick = {
                            selectedDirection = 1 - selectedDirection
                            resetDirectionUi()
                        },
                        enabled = !isBusy,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    ArkRust.copy(alpha = if (isBusy) 0.12f else 0.22f),
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.loc_979d5904),
                            tint = if (isBusy) toColor.copy(alpha = 0.45f) else toColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    ArkLayerDirectionCard(
                        label = stringResource(R.string.loc_4203f666),
                        layer = toLayerLabel,
                        asset = toLabel,
                        accent = toColor,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDirection = 1 - selectedDirection
                            resetDirectionUi()
                        },
                    )
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
                    if (btcPrice != null && btcPrice > 0) {
                        Card(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (amountInput.isNotEmpty() && amountSats != null) {
                                            amountInput =
                                                if (!isUsdMode) {
                                                    val usdValue = (amountSats / 100_000_000.0) * btcPrice
                                                    String.format(Locale.US, "%.2f", usdValue)
                                                } else if (useSats) {
                                                    amountSats.toString()
                                                } else {
                                                    formatArkBtcInput(amountSats)
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
                                            ArkRust.copy(alpha = 0.15f)
                                        } else {
                                            DarkSurface
                                        },
                                ),
                            border = BorderStroke(1.dp, if (isUsdMode) ArkRust else BorderColor),
                        ) {
                            Text(
                                text = fiatCurrency,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUsdMode) ArkRust else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val conversionText =
                    if (amountInput.isNotEmpty() && amountSats != null && amountSats > 0 &&
                        btcPrice != null && btcPrice > 0
                    ) {
                        if (privacyMode) {
                            "≈ $ARK_HIDDEN_AMOUNT"
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
                    enabled = !isBusy,
                    onValueChange = { new ->
                        isMaxMode = false
                        localError = null
                        if (transferState !is ArkTransferState.Idle) onReset()
                        amountInput =
                            when {
                                isUsdMode ->
                                    new.takeIf { it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$")) }
                                        ?: amountInput
                                useSats -> new.filter { it.isDigit() }
                                else ->
                                    new.takeIf { it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,8}$")) }
                                        ?: amountInput
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
                            color = TextTertiary,
                        )
                    },
                    suffix =
                        if (conversionText != null) {
                            {
                                Text(
                                    text = conversionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ArkRust,
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
                            focusedBorderColor = if (isMaxMode) ArkRust else BorderColor,
                            unfocusedBorderColor = if (isMaxMode) ArkRust else BorderColor,
                            cursorColor = ArkRust,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_c624cde4),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (privacyMode) {
                                ARK_HIDDEN_AMOUNT
                            } else {
                                formatAmount(availableBalance.toULong(), useSats, includeUnit = true)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = fromColor,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Card(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = availableBalance > 0 && !isBusy) {
                                    isMaxMode = !isMaxMode
                                    if (transferState !is ArkTransferState.Idle) onReset()
                                    amountInput =
                                        if (isMaxMode) {
                                            if (useSats) {
                                                availableBalance.toString()
                                            } else {
                                                formatArkBtcInput(availableBalance)
                                            }
                                        } else {
                                            ""
                                        }
                                },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (isMaxMode) {
                                        ArkRust.copy(alpha = 0.15f)
                                    } else {
                                        DarkSurface
                                    },
                            ),
                        border = BorderStroke(1.dp, if (isMaxMode) ArkRust else BorderColor),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_a53b6469),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMaxMode) ArkRust else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }

                if (isLayer1ToArk && minBoardAmountSats != null && !privacyMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.ark_transfer_min_board_format,
                                formatAmount(minBoardAmountSats.toULong(), useSats, includeUnit = true),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                val displayError =
                    localError
                        ?: (transferState as? ArkTransferState.Error)?.message
                        ?: if (amountBelowMinBoard) {
                            belowMinBoardLabel
                        } else if (isLayer1ToArk && !arkOnchainBoardingAvailable) {
                            stringResource(R.string.ark_error_passphrase_board_unavailable)
                        } else if (!isLayer1ToArk && !isElectrumConnected) {
                            needsElectrumLabel
                        } else {
                            null
                        }
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isBusy) {
                                showAdvancedOptions = !showAdvancedOptions
                            }
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_20a1d916),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Icon(
                        imageVector =
                            if (showAdvancedOptions) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                }

                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    ) {
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        ArkSwapAdvancedToggleRow(
                            label = stringResource(R.string.loc_ee7df965),
                            checked = customDestinationSupported && useCustomDestination,
                            enabled = !isBusy && customDestinationSupported,
                            accentColor = toColor,
                            onCheckedChange = { enabled ->
                                if (!customDestinationSupported) return@ArkSwapAdvancedToggleRow
                                useCustomDestination = enabled
                                if (!enabled) customDestination = ""
                                localError = null
                                if (transferState !is ArkTransferState.Idle) onReset()
                            },
                        )
                        if (!customDestinationSupported) {
                            Text(
                                text = customDestinationUnavailableNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                        AnimatedVisibility(
                            visible = customDestinationSupported && useCustomDestination,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customDestination,
                                    onValueChange = {
                                        customDestination = it.trim()
                                        localError = null
                                        if (transferState !is ArkTransferState.Idle) onReset()
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    enabled = !isBusy,
                                    placeholder = {
                                        Text(
                                            text = destinationPlaceholder,
                                            color = TextTertiary,
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showCustomDestinationQrScanner = true },
                                            enabled = !isBusy,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = stringResource(R.string.loc_59b2cdc5),
                                                tint = toColor,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    isError = destinationValidationError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor =
                                                if (destinationValidationError != null) {
                                                    ErrorRed
                                                } else {
                                                    toColor
                                                },
                                            unfocusedBorderColor =
                                                if (destinationValidationError != null) {
                                                    ErrorRed
                                                } else {
                                                    BorderColor
                                                },
                                            cursorColor = toColor,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = destinationValidationError ?: destinationOverrideHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        if (destinationValidationError != null) {
                                            ErrorRed
                                        } else {
                                            TextSecondary
                                        },
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        ArkSwapAdvancedToggleRow(
                            label = customFeeRateLabel,
                            checked = customFeeRateSupported && useCustomFeeRate,
                            enabled = !isBusy && customFeeRateSupported,
                            accentColor = fromColor,
                            onCheckedChange = { enabled ->
                                if (!customFeeRateSupported) return@ArkSwapAdvancedToggleRow
                                useCustomFeeRate = enabled
                                if (!enabled) {
                                    customFeeRateInput = ""
                                    customFeeRate = defaultFeeRate
                                    customFeeOptionName = FeeRateOption.HALF_HOUR.name
                                }
                                localError = null
                                if (transferState !is ArkTransferState.Idle) onReset()
                            },
                        )
                        if (!customFeeRateSupported) {
                            Text(
                                text = customFeeUnavailableNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                        AnimatedVisibility(
                            visible = customFeeRateSupported && useCustomFeeRate,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 8.dp),
                            ) {
                                FeeRateSection(
                                    feeEstimationState = feeEstimationState,
                                    currentFeeRate = customFeeRate,
                                    minFeeRate = minFeeRate,
                                    onFeeRateChange = {
                                        customFeeRate = it
                                        localError = null
                                    },
                                    selectedOption = customFeeOption,
                                    onSelectedOptionChange = {
                                        customFeeOptionName = it.name
                                    },
                                    customFeeInput = customFeeRateInput.takeIf { it.isNotBlank() },
                                    onCustomFeeInputChange = {
                                        customFeeRateInput = it.orEmpty()
                                    },
                                    onRefreshFees = onRefreshBitcoinFees,
                                    enabled = !isBusy,
                                )
                                feeRateValidationError?.let { error ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRed,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val sats = amountSats
                        if (sats == null || sats <= 0L) {
                            localError = enterAmountLabel
                            return@Button
                        }
                        localError = null
                        reviewError = null
                        if (isCustomDestinationMissing) {
                            localError = enterDestinationAddressLabel
                            return@Button
                        }
                        if (destinationValidationError != null) {
                            localError = destinationValidationError
                            return@Button
                        }
                        if (feeRateValidationError != null) {
                            localError = feeRateValidationError
                            return@Button
                        }
                        if (isLayer1ToArk && !arkOnchainBoardingAvailable) {
                            localError = passphraseBoardUnavailableLabel
                            return@Button
                        }
                        if (amountBelowMinBoard) {
                            localError = belowMinBoardLabel
                            return@Button
                        }
                        if (isLayer1ToArk && requireCoinControl && selectedFundingSnapshot.isEmpty()) {
                            showCoinControl = true
                            return@Button
                        }
                        // Lock UI immediately so layer cards / advanced toggles don't flash.
                        isPreparingReview = true
                        reviewState = null
                        if (isLayer1ToArk) {
                            onPrepareBoard(sats, isMaxMode)
                        } else {
                            val dest = offboardDestination.orEmpty()
                            if (dest.isBlank()) {
                                isPreparingReview = false
                                localError = layer1AddressMissingLabel
                                onGenerateLayer1Address()
                                return@Button
                            }
                            onPrepareOffboard(dest, if (isMaxMode) null else sats, isMaxMode)
                        }
                    },
                    enabled = canPrepare,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ArkRust,
                            disabledContainerColor = ArkRust.copy(alpha = 0.3f),
                        ),
                ) {
                    if (isBusy && reviewState == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.loc_9a0b9f8e),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showCoinControl && isLayer1ToArk) {
        CoinControlDialog(
            utxos = spendableBitcoinUtxos,
            selectedUtxos = selectedFundingSnapshot,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            privacyMode = privacyMode,
            spendUnconfirmed = spendUnconfirmed,
            onUtxoToggle = { utxo ->
                toggleCoinControlSelection(selectedFundingUtxos, utxo)
                isMaxMode = false
            },
            onSelectAll = {
                selectedFundingUtxos.clear()
                selectedFundingUtxos.addAll(spendableBitcoinUtxos)
                isMaxMode = false
            },
            onClearAll = {
                selectedFundingUtxos.clear()
                isMaxMode = false
            },
            onDismiss = { showCoinControl = false },
        )
    }

    // Quote TTL: force re-prepare after 2 minutes so fee/UTXO snapshot can't go stale.
    LaunchedEffect(reviewState) {
        if (reviewState == null) return@LaunchedEffect
        delay(120_000L)
        if (reviewState != null &&
            !(isExecutingReview || transferState is ArkTransferState.InProgress)
        ) {
            reviewState = null
            onReset()
        }
    }

    reviewState?.let { review ->
        ArkTransferReviewDialog(
            review = review,
            useSats = useSats,
            btcPrice = btcPrice,
            fiatCurrency = fiatCurrency,
            // Confirm dialog always shows real amounts (privacy mode is ambient-only).
            privacyMode = false,
            isExecuting = isExecutingReview || transferState is ArkTransferState.InProgress,
            errorMessage = reviewError,
            onConfirm = {
                reviewError = null
                when (review) {
                    is ArkTransferReview.Board -> {
                        val deposit = review.bitcoinDepositAddress?.takeIf { it.isNotBlank() }
                        if (deposit == null) {
                            reviewError = depositAddressMissingLabel
                            return@ArkTransferReviewDialog
                        }
                        val feeRate = review.feeRateSatPerVb?.takeIf { it > 0.0 } ?: fundingFeeRate
                        // Lock reviewed dry-run amount/fee (same as Liquid/Spark swap funding).
                        // Busy flag comes from transferState.InProgress after spend-auth (PIN cancel stays idle).
                        val amount = review.netAmountSats?.takeIf { it > 0L } ?: review.amountSats
                        onExecuteBoard(
                            deposit,
                            amount,
                            feeRate,
                            false,
                            review.selectedUtxos.takeIf { it.isNotEmpty() },
                            review.feeSats?.takeIf { it > 0L },
                        )
                    }
                    is ArkTransferReview.Offboard -> {
                        isExecutingReview = true
                        onExecuteOffboard()
                    }
                }
            },
            onDismiss = {
                if (!(isExecutingReview || transferState is ArkTransferState.InProgress)) {
                    reviewState = null
                    reviewError = null
                    onReset()
                }
            },
        )
    }

    if (showCustomDestinationQrScanner) {
        QrScannerDialog(
            onDismiss = { showCustomDestinationQrScanner = false },
            onCodeScanned = { scanned ->
                showCustomDestinationQrScanner = false
                customDestination =
                    when (val parsed = parseSendRecipient(scanned.trim())) {
                        is ParsedSendRecipient.Bitcoin -> parsed.address
                        else -> scanned.trim()
                    }
                useCustomDestination = true
                localError = null
                if (transferState !is ArkTransferState.Idle) onReset()
            },
        )
    }
}

@Composable
private fun ArkSwapAdvancedToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = TextSecondary,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ArkTransferReviewDialog(
    review: ArkTransferReview,
    useSats: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    isExecuting: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isBoard = review is ArkTransferReview.Board
    val sendAccent = if (isBoard) Layer1Accent else ArkRust
    val receiveAccent = if (isBoard) ArkRust else Layer1Accent
    val sendLayerLabel =
        if (isBoard) stringResource(R.string.loc_b67a01a5) else stringResource(R.string.loc_2f73501f)
    val receiveLayerLabel =
        if (isBoard) stringResource(R.string.loc_2f73501f) else stringResource(R.string.loc_b67a01a5)
    val destinationAddressLabel = stringResource(R.string.loc_3083a5d1)
    val depositDestinationLabel = stringResource(R.string.ark_transfer_deposit_destination)
    val arkDepositAddressLabel = stringResource(R.string.ark_transfer_ark_deposit_address_label)
    val changeLabel = stringResource(R.string.loc_47fbfb16)
    val bitcoinFundingFeeLabel = stringResource(R.string.loc_85a9e0cb)
    val sendingSwapLabel = stringResource(R.string.loc_94bea00f)
    val confirmSwapLabel = stringResource(R.string.loc_7a4eb12d)
    val collapseFeesLabel = stringResource(R.string.loc_e607c9c3)
    val expandFeesLabel = stringResource(R.string.loc_cd1ee95d)
    val depositFeeLabel = stringResource(R.string.ark_transfer_deposit_fee)
    val withdrawalFeeLabel = stringResource(R.string.ark_transfer_withdrawal_fee)
    val totalFeesEstimateLabel = stringResource(R.string.loc_7d9effcf)
    // Prefer Bark/gross dry-run amounts so fee is visible (not stuck at zero).
    val sendAmount =
        review.grossAmountSats?.takeIf { it > 0L }
            ?: review.amountSats
    val receiveAmount =
        review.netAmountSats?.takeIf { it > 0L }
            ?: review.amountSats
    val totalFeeSats =
        review.feeSats?.takeIf { it >= 0L }
            ?: (sendAmount - receiveAmount).coerceAtLeast(0L)
    val feeRateText =
        review.feeRateSatPerVb?.takeIf { it > 0.0 }?.let { rate ->
            "≈ ${String.format(Locale.US, "%.1f", rate)} sat/vB"
        }
    val boardChangeSats = (review as? ArkTransferReview.Board)?.changeSats ?: 0L
    val boardHasChange = (review as? ArkTransferReview.Board)?.hasChange == true
    val orderId =
        when (review) {
            is ArkTransferReview.Board ->
                "board:${review.amountSats}:${review.boardAll}:${review.bitcoinDepositAddress.orEmpty()}:${review.feeSats}"
            is ArkTransferReview.Offboard ->
                "offboard:${review.destinationAddress}:${review.amountSats}:${review.offboardAll}:${review.feeSats}"
        }
    // Offboard destination is irreversible — expand by default so the user re-sees it.
    var addressesExpanded by rememberSaveable(orderId) {
        mutableStateOf(review is ArkTransferReview.Offboard)
    }
    var feesExpanded by rememberSaveable(orderId) { mutableStateOf(false) }
    val addressesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val feesBringIntoViewRequester = remember(orderId) { BringIntoViewRequester() }
    val context = LocalContext.current

    LaunchedEffect(addressesExpanded, orderId) {
        if (!addressesExpanded) return@LaunchedEffect
        delay(180)
        addressesBringIntoViewRequester.bringIntoView()
    }
    LaunchedEffect(feesExpanded, orderId) {
        if (!feesExpanded) return@LaunchedEffect
        delay(180)
        feesBringIntoViewRequester.bringIntoView()
    }

    ScrollableDialogSurface(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        contentPadding = PaddingValues(20.dp),
        actions = {
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                enabled = !isExecuting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArkRust),
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isExecuting) sendingSwapLabel else confirmSwapLabel, color = TextPrimary)
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(8.dp))
            IbisButton(
                onClick = onDismiss,
                enabled = !isExecuting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
            ) {
                Text(stringResource(R.string.loc_51bac044), style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.loc_75935701),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteBadgeArk(text = "ARK", accentColor = ArkRust)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_a9af1b38),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        if (privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else {
                            formatAmount(sendAmount.toULong(), useSats, includeUnit = true)
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    color = sendAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                arkReviewUsdText(sendAmount, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sendLayerLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = sendAccent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = BorderColor.copy(alpha = 0.3f),
                    )
                    Text(
                        text = stringResource(R.string.loc_4374aaee),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier =
                            Modifier
                                .background(DarkSurface)
                                .padding(horizontal = 10.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = receiveLayerLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = receiveAccent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        if (privacyMode) {
                            ARK_HIDDEN_AMOUNT
                        } else {
                            formatAmount(receiveAmount.toULong(), useSats, includeUnit = true)
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    color = receiveAccent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                arkReviewUsdText(receiveAmount, btcPrice, fiatCurrency, privacyMode)?.let { usdText ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { addressesExpanded = !addressesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_ed3bf7b5),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector =
                            if (addressesExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                        contentDescription =
                            if (addressesExpanded) {
                                stringResource(R.string.loc_0430ad11)
                            } else {
                                stringResource(R.string.loc_afb3fec8)
                            },
                        tint = ArkRust,
                    )
                }
                AnimatedVisibility(addressesExpanded) {
                    Column(
                        modifier =
                            Modifier
                                .bringIntoViewRequester(addressesBringIntoViewRequester)
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    ) {
                        when (review) {
                            is ArkTransferReview.Board -> {
                                // BTC→Ark boards via Bark on-chain wallet — Bitcoin address only.
                                val deposit =
                                    review.bitcoinDepositAddress?.takeIf { it.isNotBlank() }
                                if (deposit != null) {
                                    AddressRowArk(
                                        label = arkDepositAddressLabel,
                                        value = deposit,
                                        context = context,
                                    )
                                } else {
                                    AddressRowArk(
                                        label = depositDestinationLabel,
                                        value =
                                            stringResource(
                                                R.string.ark_transfer_deposit_destination_value,
                                            ),
                                        context = context,
                                        copyEnabled = false,
                                    )
                                }
                            }
                            is ArkTransferReview.Offboard ->
                                AddressRowArk(
                                    label = destinationAddressLabel,
                                    value = review.destinationAddress,
                                    context = context,
                                )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { feesExpanded = !feesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_00a16e52),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!feesExpanded) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text =
                                        if (privacyMode) {
                                            ARK_HIDDEN_AMOUNT
                                        } else {
                                            formatAmount(totalFeeSats.toULong(), useSats, includeUnit = true)
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                                arkReviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode)?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextTertiary,
                                        textAlign = TextAlign.End,
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector =
                                if (feesExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                            contentDescription = if (feesExpanded) collapseFeesLabel else expandFeesLabel,
                            tint = ArkRust,
                        )
                    }
                }
                AnimatedVisibility(feesExpanded) {
                    Column(
                        modifier =
                            Modifier
                                .bringIntoViewRequester(feesBringIntoViewRequester)
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    ) {
                        FeeRowArk(
                            label =
                                if (isBoard) {
                                    bitcoinFundingFeeLabel
                                } else {
                                    withdrawalFeeLabel
                                },
                            value =
                                if (privacyMode) {
                                    ARK_HIDDEN_AMOUNT
                                } else {
                                    formatAmount(totalFeeSats.toULong(), useSats, includeUnit = true)
                                },
                            subtext =
                                feeRateText
                                    ?: if (isBoard) {
                                        stringResource(R.string.ark_transfer_deposit_fee_note)
                                    } else {
                                        stringResource(R.string.ark_transfer_withdrawal_fee_note)
                                    },
                            valueSubtext =
                                arkReviewUsdText(
                                    totalFeeSats,
                                    btcPrice,
                                    fiatCurrency,
                                    privacyMode,
                                ),
                        )
                        if (isBoard && boardHasChange && boardChangeSats > 0L) {
                            FeeRowArk(
                                label = changeLabel,
                                value =
                                    if (privacyMode) {
                                        ARK_HIDDEN_AMOUNT
                                    } else {
                                        formatAmount(boardChangeSats.toULong(), useSats, includeUnit = true)
                                    },
                                valueSubtext =
                                    arkReviewUsdText(
                                        boardChangeSats,
                                        btcPrice,
                                        fiatCurrency,
                                        privacyMode,
                                    ),
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = BorderColor.copy(alpha = 0.3f),
                        )
                        ReviewRowArk(
                            label = totalFeesEstimateLabel,
                            value =
                                if (privacyMode) {
                                    ARK_HIDDEN_AMOUNT
                                } else {
                                    formatAmount(totalFeeSats.toULong(), useSats, includeUnit = true)
                                },
                            valueSubtext = arkReviewUsdText(totalFeeSats, btcPrice, fiatCurrency, privacyMode),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewRowArk(
    label: String,
    value: String,
    valueSubtext: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            valueSubtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun RouteBadgeArk(
    text: String,
    accentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
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
private fun AddressRowArk(
    label: String,
    value: String,
    context: android.content.Context,
    copyEnabled: Boolean = true,
) {
    val copyLabelContentDescription = stringResource(R.string.common_copy_with_label, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
        if (copyEnabled) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            SecureClipboard.copyAndScheduleClear(
                                context = context,
                                text = value,
                            )
                        },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = copyLabelContentDescription,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FeeRowArk(
    label: String,
    value: String,
    subtext: String? = null,
    valueSubtext: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            subtext?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            valueSubtext?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun arkReviewUsdText(
    amountSats: Long,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
): String? {
    if (privacyMode || btcPrice == null || btcPrice <= 0.0) return null
    return formatFiat((amountSats / 100_000_000.0) * btcPrice, fiatCurrency)
}

@Composable
private fun ArkLayerDirectionCard(
    label: String,
    layer: String,
    asset: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val displayAccent = if (enabled) accent else accent.copy(alpha = 0.45f)
    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) TextTertiary else TextTertiary.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = layer, style = MaterialTheme.typography.bodyLarge, color = displayAccent)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = asset,
                style = MaterialTheme.typography.bodySmall,
                color = displayAccent.copy(alpha = 0.7f),
            )
        }
    }
}

private fun formatArkBtcInput(amountSats: Long): String =
    String.format(Locale.US, "%.8f", amountSats / 100_000_000.0)
        .trimEnd('0')
        .trimEnd('.')

