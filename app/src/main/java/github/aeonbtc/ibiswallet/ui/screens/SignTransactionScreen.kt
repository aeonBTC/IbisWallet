@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.components.SecureDialogSideEffect
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.nfc.NfcPsbtCodec
import github.aeonbtc.ibiswallet.nfc.PsbtNfcEvent
import github.aeonbtc.ibiswallet.nfc.PsbtNfcReadError
import github.aeonbtc.ibiswallet.nfc.PsbtNfcWriteError
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCodeBytes
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrExportProfile
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.PasteTileButton
import github.aeonbtc.ibiswallet.ui.components.rememberAnimatedQrEncodingPlan
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.Bbqr
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import github.aeonbtc.ibiswallet.util.parseTxFileBytes
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import github.aeonbtc.ibiswallet.viewmodel.SignPsbtState
import kotlinx.coroutines.delay

/**
 * Offline PSBT signer for the cold-storage flow.
 *
 * A watch-only coordinator (e.g. another Ibis install on an online phone)
 * builds the unsigned PSBT; this device — holding the seed but kept offline —
 * imports it, shows a full review (outputs, fee, ownership), signs with its
 * keys, and exports the signed PSBT back. This screen never broadcasts.
 *
 * Three phases:
 * 1. Import unsigned/partial PSBT via QR scan, file, paste, or NFC tag.
 * 2. Review decoded outputs + explicit acknowledgement, then Sign.
 * 3. Export signed/partial PSBT via animated QR, file, copy, or NFC tag.
 */
private enum class SignQrExportFormat {
    BC_UR,
    BBQR,
}

private data class SignBbqrVersionRange(
    val minVersion: Int,
    val maxVersion: Int,
)

private fun resolveSignBbqrVersionRange(density: SecureStorage.QrDensity): SignBbqrVersionRange =
    when (density) {
        SecureStorage.QrDensity.LOW -> SignBbqrVersionRange(minVersion = 8, maxVersion = 12)
        SecureStorage.QrDensity.MEDIUM -> SignBbqrVersionRange(minVersion = 8, maxVersion = 14)
        SecureStorage.QrDensity.HIGH -> SignBbqrVersionRange(minVersion = 10, maxVersion = 18)
    }

private fun resolveSignBbqrFrameDelayMs(density: SecureStorage.QrDensity): Long =
    when (density) {
        SecureStorage.QrDensity.LOW -> 300L
        SecureStorage.QrDensity.MEDIUM -> 340L
        SecureStorage.QrDensity.HIGH -> 380L
    }

// Import arming auto-expires so a forgotten reader session cannot accept a
// substituted PSBT from later background taps.
private const val PSBT_NFC_IMPORT_TIMEOUT_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignTransactionScreen(
    signState: SignPsbtState,
    privacyMode: Boolean = false,
    qrDensity: SecureStorage.QrDensity,
    onQrDensityChange: (SecureStorage.QrDensity) -> Unit,
    qrBrightness: Float,
    onQrBrightnessChange: (Float) -> Unit,
    onInputReceived: (String) -> Unit,
    onSign: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    fun maskedSignAmount(sats: ULong): String =
        if (privacyMode) "****" else formatSignAmount(sats)
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val savedToast = stringResource(R.string.sign_saved_toast)
    val saveFailedToast = stringResource(R.string.sign_save_failed_toast)
    var showScanner by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var qrExportFormat by rememberSaveable { mutableStateOf(SignQrExportFormat.BC_UR) }
    // The user must re-acknowledge the review for every newly imported PSBT
    // so a fresh import can never inherit a prior confirmation.
    var reviewed by remember(signState.inputPsbtBase64) { mutableStateOf(false) }

    val signedPsbtBase64 = signState.signedPsbtBase64
    val signedPsbtBytes =
        remember(signedPsbtBase64) {
            signedPsbtBase64?.let {
                runCatching {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                }.getOrNull()
            }
        }
    val signedTooLargeForNfc =
        signedPsbtBytes != null && !NfcPsbtCodec.isExportableSize(signedPsbtBytes)

    // NFC import/export: tap an NFC tag to pull the unsigned PSBT or push
    // the signed PSBT back (Coldcard-compatible wire format). Reader mode is
    // screen-scoped; taps route to the PSBT path while armed.
    val nfcReaderOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canRead
    val nfcShareAvailable = context.getNfcAvailability().canBroadcast
    DisposableEffect(mainActivity, nfcAvailable) {
        if (mainActivity != null && nfcAvailable) {
            mainActivity.requestNfcReaderMode(nfcReaderOwner)
        }
        onDispose {
            mainActivity?.disarmPsbtNfc()
            mainActivity?.disarmPsbtHceShare()
            mainActivity?.releaseNfcReaderMode(nfcReaderOwner)
        }
    }
    var nfcExportArmed by remember { mutableStateOf(false) }
    var nfcImportArmed by remember { mutableStateOf(false) }
    var nfcShareArmed by remember { mutableStateOf(false) }
    var nfcErrorRes by remember { mutableStateOf<Int?>(null) }
    var nfcErrorIsExport by remember { mutableStateOf(false) }
    // Read payloads wait behind explicit user acceptance — a tap that lands
    // while another transfer is on screen replaces nothing automatically.
    var nfcPendingRead by remember { mutableStateOf<PsbtNfcEvent.ReadSucceeded?>(null) }
    // A completed signature ends any armed import session: taps must never
    // silently overwrite the reviewed input while the export is displayed.
    // (Export arming only happens afterwards, from the export section.)
    LaunchedEffect(signedPsbtBase64, mainActivity) {
        if (signedPsbtBase64 != null && nfcImportArmed) {
            mainActivity?.disarmPsbtNfc()
            nfcImportArmed = false
        }
    }
    val nfcSentToast = stringResource(R.string.psbt_nfc_sent)
    LaunchedEffect(mainActivity) {
        mainActivity?.psbtNfcEvents?.collect { event ->
            when (event) {
                is PsbtNfcEvent.WriteSucceeded -> {
                    nfcExportArmed = false
                    nfcErrorRes = null
                    mainActivity?.disarmPsbtNfc()
                    Toast.makeText(context, nfcSentToast, Toast.LENGTH_SHORT).show()
                }
                is PsbtNfcEvent.WriteFailed -> {
                    nfcExportArmed = false
                    mainActivity?.disarmPsbtNfc()
                    nfcErrorIsExport = true
                    nfcErrorRes =
                        if (event.error == PsbtNfcWriteError.TOO_LARGE) {
                            R.string.psbt_nfc_too_large
                        } else {
                            R.string.psbt_nfc_write_failed
                        }
                }
                is PsbtNfcEvent.ReadSucceeded -> {
                    nfcImportArmed = false
                    nfcErrorRes = null
                    mainActivity?.disarmPsbtNfc()
                    nfcPendingRead = event
                }
                is PsbtNfcEvent.ReadFailed -> {
                    nfcImportArmed = false
                    mainActivity?.disarmPsbtNfc()
                    nfcErrorIsExport = false
                    nfcErrorRes =
                        when (event.error) {
                            PsbtNfcReadError.CHECKSUM_MISMATCH ->
                                R.string.psbt_nfc_checksum_mismatch
                            PsbtNfcReadError.EMPTY ->
                                R.string.psbt_nfc_empty
                            else -> R.string.psbt_nfc_read_failed
                    }
                }
            }
        }
    }

    // Armed import sessions expire; the reader stays passive until re-armed.
    LaunchedEffect(nfcImportArmed, mainActivity) {
        if (nfcImportArmed) {
            delay(PSBT_NFC_IMPORT_TIMEOUT_MS)
            if (nfcImportArmed) {
                mainActivity?.disarmPsbtNfc()
                nfcImportArmed = false
                nfcErrorIsExport = false
                nfcErrorRes = R.string.psbt_nfc_timed_out
            }
        }
    }

    // Explicit acceptance before a wirelessly received payload touches the
    // signer input — accepting replaces whatever is currently there.
    nfcPendingRead?.let { read ->
        IbisConfirmDialog(
            onDismissRequest = { nfcPendingRead = null },
            title = stringResource(R.string.psbt_nfc_receive_confirm_title),
            message = stringResource(R.string.psbt_nfc_receive_confirm_message),
            confirmText = stringResource(R.string.psbt_nfc_receive_confirm_accept),
            onConfirm = {
                val data = read.data
                nfcPendingRead = null
                onInputReceived(data)
            },
        )
    }

    // File picker for loading an unsigned/partial PSBT (.psbt, .txt, or any file).
    // Every non-null parse is forwarded to the signer input — the classifier
    // turns wrong-kind payloads (raw hex, PSET, ambiguous) into explicit
    // errors instead of silently ignoring the file.
    val loadPsbtLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        parseTxFileBytes(stream.readBytesWithLimit(InputLimits.TX_FILE_BYTES))?.let { result ->
                            onInputReceived(result.data)
                        }
                    }
                } catch (_: Exception) {
                    // File read failed — ignore silently; user can retry
                }
            }
        }

    // File picker for saving the signed/partial PSBT
    val saveSignedLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            if (uri != null && signedPsbtBase64 != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        val bytes =
                            android.util.Base64.decode(
                                signedPsbtBase64,
                                android.util.Base64.DEFAULT,
                            )
                        stream.write(bytes)
                    }
                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, saveFailedToast, Toast.LENGTH_SHORT).show()
                }
            }
        }

    if (showScanner) {
        AnimatedQrScannerDialog(
            onDataReceived = { data ->
                showScanner = false
                onInputReceived(data)
            },
            onDismiss = { showScanner = false },
        )
    }

    if (showPasteDialog) {
        SignPastePsbtDialog(
            onSubmit = { data ->
                showPasteDialog = false
                onInputReceived(data)
            },
            onDismiss = { showPasteDialog = false },
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sign_transaction_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (signedPsbtBase64 != null) {
            SignExportSection(
                signState = signState,
                signedPsbtBase64 = signedPsbtBase64,
                signedPsbtBytes = signedPsbtBytes,
                signedTooLargeForNfc = signedTooLargeForNfc,
                qrDensity = qrDensity,
                onQrDensityChange = onQrDensityChange,
                qrBrightness = qrBrightness,
                onQrBrightnessChange = onQrBrightnessChange,
                qrExportFormat = qrExportFormat,
                onFormatSelected = { qrExportFormat = it },
                nfcAvailable = nfcAvailable,
                nfcShareAvailable = nfcShareAvailable,
                mainActivity = mainActivity,
                nfcExportArmed = nfcExportArmed,
                onNfcExportArmedChange = { armed ->
                    nfcExportArmed = armed
                    if (armed) nfcImportArmed = false
                },
                shareArmed = nfcShareArmed,
                onShareArmedChange = { nfcShareArmed = it },
                onDisarmTagSession = {
                    mainActivity?.disarmPsbtNfc()
                    nfcExportArmed = false
                    nfcImportArmed = false
                    nfcErrorRes = null
                },
                nfcErrorRes = if (nfcErrorIsExport) nfcErrorRes else null,
                onSaveFile = {
                    val fileName = if (signState.isFinalized) "signed.psbt" else "partial.psbt"
                    mainActivity?.skipNextBackgroundLockForActivityResult()
                    saveSignedLauncher.launch(fileName)
                },
                onStartOver = {
                    mainActivity?.disarmPsbtNfc()
                    mainActivity?.disarmPsbtHceShare()
                    nfcExportArmed = false
                    nfcImportArmed = false
                    nfcShareArmed = false
                    nfcErrorRes = null
                    onClear()
                },
            )
        } else {
            // Step 1 — Import unsigned/partial PSBT
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
                        text = stringResource(R.string.sign_step1_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.sign_step1_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showScanner = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sign_scan_qr),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                mainActivity?.skipNextBackgroundLockForActivityResult()
                                loadPsbtLauncher.launch(arrayOf("*/*"))
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TextSecondary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.loc_a5e83581),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                            )
                        }

                        OutlinedButton(
                            onClick = { showPasteDialog = true },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TextSecondary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.loc_5d97579c),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // NFC import: tap the tag carrying the unsigned PSBT.
                    if (nfcAvailable && !nfcImportArmed) {
                        OutlinedButton(
                            onClick = {
                                mainActivity?.disarmPsbtHceShare()
                                nfcShareArmed = false
                                mainActivity?.armPsbtNfcImport()
                                nfcImportArmed = mainActivity != null
                                nfcExportArmed = false
                                nfcErrorRes = null
                            },
                            enabled = mainActivity != null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TextSecondary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.psbt_nfc_receive),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                            )
                        }
                    } else if (nfcAvailable) {
                        NfcStatusIndicator(
                            label = stringResource(R.string.psbt_nfc_tap_to_receive),
                            contentDescription = stringResource(R.string.psbt_nfc_tap_to_receive),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = {
                                mainActivity?.disarmPsbtNfc()
                                nfcImportArmed = false
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.psbt_nfc_cancel),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    nfcErrorRes?.let { errorRes ->
                        if (!nfcErrorIsExport) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(errorRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (signState.isDecoding) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.sign_decoding),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step 2 — Review & Sign
            val preview = signState.preview
            if (preview != null) {
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
                            text = stringResource(R.string.sign_step2_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.sign_review_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.sign_txid_format, preview.txid),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        preview.outputs.forEachIndexed { index, output ->
                            val tag =
                                if (output.ownedByLoadedWallet) {
                                    stringResource(R.string.sign_output_own)
                                } else {
                                    stringResource(R.string.sign_output_external)
                                }
                            val tagColor =
                                if (output.ownedByLoadedWallet) SuccessGreen else WarningYellow
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text =
                                    "#$index  " +
                                        (if (privacyMode) "****" else "${output.amountSats} sats") +
                                        "  $tag",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tagColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = output.address ?: stringResource(R.string.sign_output_unparseable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 2,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SignDetailRow(
                            label = stringResource(R.string.sign_fee_label),
                            value =
                                preview.feeSats?.let { maskedSignAmount(it.toULong()) }
                                    ?: stringResource(R.string.sign_unknown),
                            valueColor = BitcoinOrange,
                        )
                        preview.feeRateSatPerVb?.let { rate ->
                            val rateValue =
                                preview.txVBytes?.let { vbytes ->
                                    stringResource(
                                        R.string.sign_fee_rate_with_vbytes_format,
                                        "%.2f".format(rate),
                                        "%.1f".format(vbytes),
                                    )
                                } ?: stringResource(R.string.sign_fee_rate_format, "%.2f".format(rate))
                            SignDetailRow(
                                label = stringResource(R.string.sign_fee_rate_label),
                                value = rateValue,
                            )
                        }
                        SignDetailRow(
                            label = "Inputs",
                            value =
                                stringResource(
                                    R.string.sign_inputs_format,
                                    preview.ownedInputCount,
                                    preview.numInputs,
                                ),
                            valueColor =
                                if (preview.isFromLoadedWallet) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    WarningYellow
                                },
                        )

                        if (preview.anyOutputUnowned) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sign_outputs_unowned_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningYellow,
                            )
                        }
                        if (!preview.isFromLoadedWallet) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sign_inputs_foreign_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningYellow,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = reviewed,
                                onCheckedChange = { reviewed = it },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary,
                                    ),
                            )
                            Text(
                                text = stringResource(R.string.sign_reviewed_check),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val canSign =
                            reviewed && !signState.isSigning && !signState.isDecoding
                        Button(
                            onClick = onSign,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = canSign,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BitcoinOrange,
                                    contentColor = DarkBackground,
                                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                                ),
                        ) {
                            if (signState.isSigning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = DarkBackground,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.sign_signing),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.sign_button),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Error display (import decode or signing failure)
            if (signState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.1f),
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = signState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Step 3: signed PSBT ready — export it back to the online coordinator via
 * animated QR, file, copy, or NFC tag. Nothing here broadcasts.
 */
@Composable
private fun SignExportSection(
    signState: SignPsbtState,
    signedPsbtBase64: String,
    signedPsbtBytes: ByteArray?,
    signedTooLargeForNfc: Boolean,
    qrDensity: SecureStorage.QrDensity,
    onQrDensityChange: (SecureStorage.QrDensity) -> Unit,
    qrBrightness: Float,
    onQrBrightnessChange: (Float) -> Unit,
    qrExportFormat: SignQrExportFormat,
    onFormatSelected: (SignQrExportFormat) -> Unit,
    nfcAvailable: Boolean,
    nfcShareAvailable: Boolean,
    mainActivity: MainActivity?,
    nfcExportArmed: Boolean,
    onNfcExportArmedChange: (Boolean) -> Unit,
    shareArmed: Boolean,
    onShareArmedChange: (Boolean) -> Unit,
    onDisarmTagSession: () -> Unit,
    nfcErrorRes: Int?,
    onSaveFile: () -> Unit,
    onStartOver: () -> Unit,
) {
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.sign_copied_toast)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (signState.isFinalized) {
                        SuccessGreen.copy(alpha = 0.1f)
                    } else {
                        WarningYellow.copy(alpha = 0.1f)
                    },
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
                text =
                    if (signState.isFinalized) {
                        stringResource(R.string.sign_status_ready)
                    } else {
                        stringResource(R.string.sign_status_partial)
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color =
                    if (signState.isFinalized) {
                        SuccessGreen
                    } else {
                        WarningYellow
                    },
                textAlign = TextAlign.Center,
            )
            signState.signedTxid?.let { txid ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sign_txid_format, txid),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sign_step3_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.sign_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                QrDensityDropdown(
                    currentDensity = qrDensity,
                    onDensitySelected = onQrDensityChange,
                    modifier = Modifier.weight(1f),
                )

                SignFormatToggle(
                    currentFormat = qrExportFormat,
                    onFormatSelected = onFormatSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            QrBrightnessSlider(
                brightness = qrBrightness,
                onBrightnessChange = onQrBrightnessChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bytes are always present (the signer just produced them); if decoding
            // ever failed, fall back to BC-UR rather than showing nothing.
            val effectiveFormat =
                if (qrExportFormat == SignQrExportFormat.BBQR && signedPsbtBytes == null) {
                    SignQrExportFormat.BC_UR
                } else {
                    qrExportFormat
                }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val bbqrVersionRange = resolveSignBbqrVersionRange(qrDensity)
                val exportQrSize =
                    if (maxWidth > 32.dp) {
                        maxWidth - 32.dp
                    } else {
                        maxWidth
                    }

                when (effectiveFormat) {
                    SignQrExportFormat.BC_UR -> {
                        val qrEncodingPlan =
                            rememberAnimatedQrEncodingPlan(
                                dataBase64 = signedPsbtBase64,
                                density = qrDensity,
                                exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                            )

                        if (qrEncodingPlan != null) {
                            AnimatedQrCode(
                                encodingPlan = qrEncodingPlan,
                                qrSize = exportQrSize,
                                brightness = qrBrightness,
                            )
                        } else {
                            CircularProgressIndicator(
                                color = BitcoinOrange,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }

                    SignQrExportFormat.BBQR -> {
                        // Null-guarded above by effectiveFormat: bytes are present here.
                        AnimatedQrCodeBytes(
                            data = signedPsbtBytes ?: ByteArray(0),
                            qrSize = exportQrSize,
                            frameDelayMs = resolveSignBbqrFrameDelayMs(qrDensity),
                            fileType = Bbqr.FILE_TYPE_PSBT,
                            brightness = qrBrightness,
                            minVersion = bbqrVersionRange.minVersion,
                            maxVersion = bbqrVersionRange.maxVersion,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(
                    onClick = onSaveFile,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.loc_aa3edbf6),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        SecureClipboard.copyAndScheduleClear(
                            context,
                            signedPsbtBase64,
                        )
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.loc_02e71d7c),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // NFC export: push the signed PSBT to a tag for the online
            // coordinator to pull. Large PSBTs stay on QR/file.
            if (!nfcAvailable) {
                Text(
                    text = stringResource(R.string.psbt_nfc_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else if (signedTooLargeForNfc) {
                Text(
                    text = stringResource(R.string.psbt_nfc_too_large),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else if (!nfcExportArmed) {
                OutlinedButton(
                    onClick = {
                        mainActivity?.disarmPsbtHceShare()
                        onShareArmedChange(false)
                        mainActivity?.armPsbtNfcExport(signedPsbtBase64)
                        onNfcExportArmedChange(mainActivity != null)
                    },
                    enabled = mainActivity != null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.psbt_nfc_send),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                NfcStatusIndicator(
                    label = stringResource(R.string.psbt_nfc_tap_to_send),
                    contentDescription = stringResource(R.string.psbt_nfc_tap_to_send),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        mainActivity?.disarmPsbtNfc()
                        onNfcExportArmedChange(false)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.psbt_nfc_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }

            nfcErrorRes?.let { exportNfcErrorRes ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(exportNfcErrorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // HCE share: present the signed PSBT for the online phone
            // with import armed to pull with a tap.
            PsbtNfcShareSection(
                payloadBase64 = signedPsbtBase64,
                sizeOk = !signedTooLargeForNfc,
                nfcShareAvailable = nfcShareAvailable,
                mainActivity = mainActivity,
                shareArmed = shareArmed,
                onShareArmedChange = onShareArmedChange,
                onDisarmTagSession = onDisarmTagSession,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    IbisButton(
        onClick = onStartOver,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
    ) {
        Text(
            text = stringResource(R.string.sign_start_over),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SignFormatToggle(
    currentFormat: SignQrExportFormat,
    onFormatSelected: (SignQrExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.sign_qr_format_label),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SignFormatChip(
                label = "BC-UR",
                selected = currentFormat == SignQrExportFormat.BC_UR,
                onClick = { onFormatSelected(SignQrExportFormat.BC_UR) },
                modifier = Modifier.weight(1f),
            )
            SignFormatChip(
                label = "BBQr",
                selected = currentFormat == SignQrExportFormat.BBQR,
                onClick = { onFormatSelected(SignQrExportFormat.BBQR) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SignFormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        BitcoinOrange.copy(alpha = 0.15f)
                    } else {
                        DarkSurface
                    },
            ),
        border = BorderStroke(1.dp, if (selected) BitcoinOrange else BorderColor),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) BitcoinOrange else TextSecondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SignDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
private fun SignPastePsbtDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SecureDialogSideEffect()
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
            ) {
                Text(
                    text = stringResource(R.string.sign_step1_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp, max = 300.dp),
                        placeholder = {
                            Text(
                                text = "cHNidP8...",
                                color = TextSecondary.copy(alpha = 0.6f),
                            )
                        },
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                    )
                    PasteTileButton(
                        onPaste = { inputText = it },
                        tint = BitcoinOrange,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.loc_51bac044), color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSubmit(inputText.trim()) },
                        enabled = inputText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                contentColor = DarkBackground,
                            ),
                    ) {
                        Text(stringResource(R.string.loc_389db675))
                    }
                }
            }
        }
    }
}

/** Format sats for display: shows BTC for large amounts, sats for small */
private fun formatSignAmount(sats: ULong): String {
    return if (sats >= 100_000UL) {
        val btc = sats.toLong() / 100_000_000.0
        "%.8f BTC".format(btc)
    } else {
        "%,d sats".format(sats.toLong())
    }
}
