@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.NfcShareUiState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.CompactDropdownField
import github.aeonbtc.ibiswallet.ui.components.DropdownOptionText
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.ReceiveActionButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import github.aeonbtc.ibiswallet.ui.components.SecureDialogSideEffect
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.SpScanStartPolicy
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    walletState: WalletState = WalletState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    onGenerateAddress: () -> Unit = {},
    onSaveLabel: (String, String) -> Unit = { _, _ -> },
    onShowAllAddresses: () -> Unit = {},
    onShowAllUtxos: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
    silentReceivePreferred: Boolean = false,
    onSilentReceiveChange: (Boolean) -> Unit = {},
    silentScanDisclosureAcknowledged: Boolean = true,
    onAcknowledgeSilentScanDisclosure: () -> Unit = {},
    spScanTipHeight: Int? = null,
    spScanMinHeight: Int = 709632,
    onApplySilentScanStartHeight: (Int?) -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val copyReceiveToast = stringResource(R.string.loc_b6b10bfe)
    val receiveShareRequestTitle = stringResource(R.string.receive_share_request_title)
    val receiveNoShareAppMessage = stringResource(R.string.receive_no_share_app)
    val labelSavedToast = stringResource(R.string.loc_171555e8)
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var silentMode by remember(walletState.activeWallet?.id, silentReceivePreferred) {
        mutableStateOf(silentReceivePreferred)
    }
    val showSilentToggle = walletState.canReceiveSilentPayments && !walletState.silentPaymentAddress.isNullOrBlank()
    var showSilentScanDisclosure by remember(walletState.activeWallet?.id) { mutableStateOf(false) }
    fun setSilentModeWithDisclosure(enabled: Boolean) {
        silentMode = enabled
        onSilentReceiveChange(enabled)
        // One-time privacy disclosure: finding your silent payments requires
        // sharing the scan key with the Electrum server. Show it the first
        // time the toggle is switched on; the toggle itself stays effective
        // regardless of the dialog outcome.
        if (enabled && !silentScanDisclosureAcknowledged) {
            showSilentScanDisclosure = true
        }
    }
    val displayAddress =
        if (silentMode && showSilentToggle) {
            walletState.silentPaymentAddress
        } else {
            walletState.currentAddress
        }
    // Loud fail-closed share state: when the server cannot scan SP, sharing
    // the sp1 address would accept funds this wallet cannot detect. Block
    // copy/share/NFC payload in that state; the address stays visible so a
    // pending receive can still be compared, but it cannot be handed out.
    val silentUnavailable = silentMode && showSilentToggle && walletState.silentPaymentsSupported == false

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var labelText by remember { mutableStateOf("") }
    var showAmountField by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    var showLabelField by remember { mutableStateOf(false) }
    var embedLabelInQr by remember(walletState.currentAddress) { mutableStateOf(false) }
    val amountBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAmountField, "receive_amount")
    val labelBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showLabelField, "receive_label")
    val savedAddressLabel = walletState.currentAddressInfo?.label.orEmpty()

    // Convert amount to sats for URI (handles BTC, sats, and USD input)
    val amountInSats =
        remember(amountText, useSats, isUsdMode, btcPrice) {
            if (amountText.isEmpty()) {
                null
            } else if (isUsdMode && btcPrice != null && btcPrice > 0) {
                // USD input - convert to sats: USD / price * 100_000_000
                amountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).roundToLong()
                }
            } else if (useSats) {
                amountText.toLongOrNull()
            } else {
                // BTC input - convert to sats
                amountText.toDoubleOrNull()?.let { btc ->
                    (btc * 100_000_000).roundToLong()
                }
            }
        }

    // Build the URI/content for QR code
    val qrContent =
        remember(displayAddress, amountInSats, showAmountField, labelText, showLabelField, embedLabelInQr) {
            displayAddress?.let { address ->
                val bitcoinAmountSats = amountInSats?.takeIf { showAmountField && it > 0 }
                val label =
                    labelText.trim().takeIf {
                        !silentMode && showLabelField && embedLabelInQr && it.isNotBlank()
                    }
                if (bitcoinAmountSats != null || label != null) {
                    val params = mutableListOf<String>()
                    bitcoinAmountSats?.let {
                        val btcAmount = it.toDouble() / 100_000_000.0
                        params += "amount=${String.format(Locale.US, "%.8f", btcAmount)}"
                    }
                    label?.let {
                        params += "label=${java.net.URLEncoder.encode(it, "UTF-8")}"
                    }
                    "bitcoin:$address?${params.joinToString("&")}"
                } else {
                    address
                }
            }
        }
    val copyReceiveRequest: () -> Unit = {
        if (!silentUnavailable) {
            qrContent?.let { content ->
                SecureClipboard.copyAndScheduleClear(context, content)
                Toast.makeText(context, copyReceiveToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Drop the previous wallet's QR immediately on switch so a stale address is
    // never shown as the new wallet's.
    LaunchedEffect(walletState.activeWallet?.id) {
        qrBitmap = null
    }

    // Generate QR code when content changes
    LaunchedEffect(qrContent) {
        qrContent?.let { content ->
            qrBitmap =
                withContext(Dispatchers.Default) {
                    generateQrBitmap(content)
                }
        }
    }

    LaunchedEffect(walletState.currentAddress, savedAddressLabel) {
        labelText = savedAddressLabel
        if (savedAddressLabel.isNotBlank()) {
            showLabelField = true
        }
    }

    // NFC broadcasting: set NDEF payload to current address/BIP21 URI while on this screen.
    // Another phone tapping this device will receive the same data as scanning the QR code.
    // Respects the NFC setting in Settings — user can disable broadcasting entirely.
    val mainActivity = context as? MainActivity
    val nfcShareOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canBroadcast
    val hasNfcSharePayload = nfcAvailable && qrContent != null && !silentUnavailable
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
    DisposableEffect(hasNfcSharePayload, qrContent) {
        // Never publish an sp1 address the configured server cannot scan:
        // hasNfcSharePayload already excludes silentUnavailable.
        if (hasNfcSharePayload && qrContent != null) {
            NdefHostApduService.setNdefPayload(qrContent)
        } else {
            NdefHostApduService.setNdefPayload(null)
        }
        onDispose {
            NdefHostApduService.setNdefPayload(null)
        }
    }

    LaunchedEffect(walletState.isInitialized, walletState.currentAddress) {
        if (walletState.isInitialized && walletState.currentAddress == null) {
            onGenerateAddress()
        }
    }

    // Enlarged QR Code Dialog
    if (showEnlargedQr && qrBitmap != null) {
        Dialog(
            onDismissRequest = { showEnlargedQr = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SecureDialogSideEffect()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { showEnlargedQr = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
        // ════════════════════════════════════════════
        // Layer 1 Receive (existing Bitcoin content)
        // ════════════════════════════════════════════
        // QR Code Card
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
                // QR Code - clickable to enlarge
                Box(
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .combinedClickable(
                                enabled = displayAddress != null,
                                onClick = { showEnlargedQr = true },
                                onLongClick = copyReceiveRequest,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null && displayAddress != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_416323aa),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text =
                                if (walletState.isInitialized) {
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

                // Address display — chunked every 7 chars, two lines
                Text(
                    text = formatChunkedAddress(displayAddress)
                        .ifBlank { stringResource(R.string.loc_fb85740c) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color =
                        if (displayAddress != null) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            TextSecondary
                        },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
                        enabled = displayAddress != null && !silentUnavailable,
                        iconSize = 17.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_53ae02a5),
                        icon = Icons.Default.Refresh,
                        tint = BitcoinOrange,
                        onClick = onGenerateAddress,
                        enabled = walletState.isInitialized && !silentMode,
                        iconSize = 20.dp,
                    )
                    ReceiveActionButton(
                        text = stringResource(R.string.loc_2ec7b25e),
                        icon = Icons.Default.Share,
                        tint = BitcoinOrange,
                        onClick = {
                            if (!silentUnavailable) {
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
                            }
                        },
                        enabled = qrContent != null && !silentUnavailable,
                    )
                }

                // Toggle switches for Amount and Label
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showSilentToggle) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        setSilentModeWithDisclosure(!silentMode)
                                    }
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.receive_silent),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            SquareToggle(
                                checked = silentMode,
                                onCheckedChange = { enabled ->
                                    setSilentModeWithDisclosure(enabled)
                                },
                            )
                        }
                        if (silentUnavailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.receive_silent_server_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = onOpenServerSettings,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.sp_scan_start_open_servers),
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (showSilentScanDisclosure) {
                        var scanStartUseTip by remember(walletState.activeWallet?.id) {
                            mutableStateOf(true)
                        }
                        var scanStartCustom by remember(walletState.activeWallet?.id) {
                            mutableStateOf("")
                        }
                        var scanStartMenuExpanded by remember { mutableStateOf(false) }
                        val scanSelection =
                            remember(scanStartUseTip, scanStartCustom, spScanTipHeight, spScanMinHeight) {
                                SpScanStartPolicy.resolveSelection(
                                    scanStartUseTip,
                                    scanStartCustom,
                                    spScanMinHeight,
                                    spScanTipHeight,
                                )
                            }
                        val tipLabel =
                            if (spScanTipHeight != null) {
                                stringResource(R.string.sp_scan_start_tip_format, spScanTipHeight)
                            } else {
                                stringResource(R.string.sp_scan_start_tip_option)
                            }
                        ScrollableAlertDialog(
                            onDismissRequest = {
                                showSilentScanDisclosure = false
                                onAcknowledgeSilentScanDisclosure()
                            },
                            containerColor = DarkCard,
                            title = {
                                Text(
                                    stringResource(R.string.receive_silent),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            text = {
                                Column {
                                    Text(
                                        stringResource(R.string.receive_silent_disclosure_message),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.sp_scan_start_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ExposedDropdownMenuBox(
                                        expanded = scanStartMenuExpanded,
                                        onExpandedChange = { scanStartMenuExpanded = it },
                                    ) {
                                        CompactDropdownField(
                                            value =
                                                if (scanStartUseTip) {
                                                    tipLabel
                                                } else {
                                                    stringResource(R.string.sp_scan_start_custom_option)
                                                },
                                            expanded = scanStartMenuExpanded,
                                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                        )
                                        ExposedDropdownMenu(
                                            expanded = scanStartMenuExpanded,
                                            onDismissRequest = { scanStartMenuExpanded = false },
                                            modifier =
                                                Modifier
                                                    .exposedDropdownSize(true)
                                                    .background(DarkSurface),
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    DropdownOptionText(
                                                        title = tipLabel,
                                                        subtitle = "",
                                                        selected = scanStartUseTip,
                                                    )
                                                },
                                                onClick = {
                                                    scanStartUseTip = true
                                                    scanStartMenuExpanded = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    DropdownOptionText(
                                                        title = stringResource(R.string.sp_scan_start_custom_option),
                                                        subtitle = "",
                                                        selected = !scanStartUseTip,
                                                    )
                                                },
                                                onClick = {
                                                    scanStartUseTip = false
                                                    scanStartMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                    if (!scanStartUseTip) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = scanStartCustom,
                                            onValueChange = { value ->
                                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                                    scanStartCustom = value
                                                }
                                            },
                                            label = { Text(stringResource(R.string.sp_scan_start_custom_hint)) },
                                            singleLine = true,
                                            isError = scanSelection is SpScanStartPolicy.Selection.Invalid &&
                                                scanStartCustom.isNotBlank(),
                                            supportingText = {
                                                if (scanSelection is SpScanStartPolicy.Selection.Invalid &&
                                                    scanStartCustom.isNotBlank()
                                                ) {
                                                    val beyondTip = spScanTipHeight != null &&
                                                        (scanStartCustom.toIntOrNull() ?: 0) > spScanTipHeight
                                                    Text(
                                                        text =
                                                            if (beyondTip) {
                                                                stringResource(
                                                                    R.string.sp_scan_start_beyond_tip_format,
                                                                    spScanTipHeight!!,
                                                                )
                                                            } else {
                                                                stringResource(R.string.sp_scan_start_invalid)
                                                            },
                                                        color = ErrorRed,
                                                    )
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(8.dp),
                                            colors =
                                                OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = BitcoinOrange,
                                                    unfocusedBorderColor = BorderColor,
                                                    cursorColor = BitcoinOrange,
                                                ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.sp_scan_start_manage_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showSilentScanDisclosure = false
                                        onAcknowledgeSilentScanDisclosure()
                                        when (scanSelection) {
                                            is SpScanStartPolicy.Selection.Tip ->
                                                onApplySilentScanStartHeight(null)
                                            is SpScanStartPolicy.Selection.Height ->
                                                onApplySilentScanStartHeight(scanSelection.value)
                                            is SpScanStartPolicy.Selection.Invalid -> Unit
                                        }
                                    },
                                    enabled = scanSelection is SpScanStartPolicy.Selection.Tip ||
                                        scanSelection is SpScanStartPolicy.Selection.Height,
                                    modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = BitcoinOrange,
                                        ),
                                ) {
                                    Text(stringResource(R.string.receive_silent_disclosure_ok))
                                }
                            },
                        )
                    }
                    // Amount toggle
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        SquareToggle(
                            checked = showAmountField,
                            onCheckedChange = { showAmountField = it },
                        )
                    }

                    // Amount input field (shown when toggled)
                    AnimatedVisibility(visible = showAmountField) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(amountBringIntoViewRequester),
                        ) {
                            // Amount label row with USD toggle
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
                                // USD toggle button (only show if price is available)
                                if (btcPrice != null && btcPrice > 0) {
                                    Card(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    isUsdMode = !isUsdMode
                                                    amountText = "" // Clear input when switching modes
                                                },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    if (isUsdMode) {
                                                        BitcoinOrange.copy(
                                                            alpha = 0.15f,
                                                        )
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

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    when {
                                        isUsdMode -> {
                                            // USD input: allow decimals up to 2 places
                                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                amountText = input
                                            }
                                        }
                                        useSats -> {
                                            amountText = input.filter { c -> c.isDigit() }
                                        }
                                        else -> {
                                            // BTC input: allow decimals up to 8 places
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
                                        keyboardType = if (useSats && !isUsdMode) KeyboardType.Number else KeyboardType.Decimal,
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = BitcoinOrange,
                                    ),
                                enabled = walletState.currentAddress != null,
                            )

                            // Show conversion: USD when in BTC/sats mode, BTC/sats when in USD mode
                            if (amountText.isNotEmpty() && amountInSats != null && amountInSats > 0 && btcPrice != null && btcPrice > 0) {
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

                    // Label toggle
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !silentMode) { showLabelField = !showLabelField }
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color =
                                if (silentMode) {
                                    TextSecondary.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                        )
                        SquareToggle(
                            checked = showLabelField,
                            onCheckedChange = { showLabelField = it },
                            enabled = !silentMode,
                        )
                    }

                    // Label input field (shown when toggled)
                    AnimatedVisibility(visible = showLabelField) {
                        Column(modifier = Modifier.bringIntoViewRequester(labelBringIntoViewRequester)) {
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                placeholder = { Text(stringResource(R.string.loc_c5ff4e34)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = BitcoinOrange,
                                    ),
                                enabled = !silentMode && walletState.currentAddress != null,
                                trailingIcon = {
                                    if (labelText.isNotEmpty() && walletState.currentAddress != null) {
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                onSaveLabel(walletState.currentAddress, labelText)
                                                Toast.makeText(context, labelSavedToast, Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = !silentMode,
                                        ) {
                                            Text(stringResource(R.string.loc_f55495e0), color = BitcoinOrange)
                                        }
                                    }
                                },
                            )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_2b196e9d),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (silentMode) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                SquareToggle(
                                    checked = embedLabelInQr,
                                    onCheckedChange = { embedLabelInQr = it },
                                    enabled = !silentMode && walletState.currentAddress != null,
                                    trackWidth = 36.dp,
                                    trackHeight = 20.dp,
                                    thumbSize = 14.dp,
                                    thumbPadding = 2.dp,
                                    trackCornerRadius = 3.dp,
                                    thumbCornerRadius = 2.dp,
                                )
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
                enabled = walletState.isInitialized,
            ) {
                Text(
                    text = stringResource(R.string.loc_5c96cb11),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            IbisButton(
                onClick = onShowAllUtxos,
                modifier = Modifier.weight(1f),
                enabled = walletState.isInitialized,
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


