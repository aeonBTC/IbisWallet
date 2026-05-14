@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCodeBytes
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrExportProfile
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.rememberAnimatedQrEncodingPlan
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.Bbqr
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.parseTxFileBytes
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import github.aeonbtc.ibiswallet.viewmodel.PsbtState
import github.aeonbtc.ibiswallet.viewmodel.WalletUiState
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import androidx.compose.material3.Text

/**
 * PSBT export and signing flow screen for watch-only and multisig wallets.
 *
 * Three-phase flow:
 * 1. Display unsigned or partially signed PSBT as animated QR code
 * 2. Scan signed/partial PSBT or raw transaction back into the wallet
 * 3. Confirm transaction details before broadcasting
 */
private enum class PsbtQrExportFormat {
    BC_UR,
    BBQR,
}

private data class BbqrVersionRange(
    val minVersion: Int,
    val maxVersion: Int,
)

private fun resolvePsbtBbqrVersionRange(density: SecureStorage.QrDensity): BbqrVersionRange =
    when (density) {
        SecureStorage.QrDensity.LOW -> BbqrVersionRange(minVersion = 8, maxVersion = 12)
        SecureStorage.QrDensity.MEDIUM -> BbqrVersionRange(minVersion = 8, maxVersion = 14)
        SecureStorage.QrDensity.HIGH -> BbqrVersionRange(minVersion = 10, maxVersion = 18)
    }

private fun resolvePsbtBbqrFrameDelayMs(density: SecureStorage.QrDensity): Long =
    when (density) {
        SecureStorage.QrDensity.LOW -> 300L
        SecureStorage.QrDensity.MEDIUM -> 340L
        SecureStorage.QrDensity.HIGH -> 380L
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsbtScreen(
    psbtState: PsbtState,
    uiState: WalletUiState,
    qrDensity: SecureStorage.QrDensity,
    onQrDensityChange: (SecureStorage.QrDensity) -> Unit,
    qrBrightness: Float,
    onQrBrightnessChange: (Float) -> Unit,
    onSignedDataReceived: (String) -> Unit,
    onConfirmBroadcast: () -> Unit,
    onCancelBroadcast: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    var showScanner by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var qrExportFormat by rememberSaveable { mutableStateOf(PsbtQrExportFormat.BC_UR) }
    val signerExportPsbtBase64 = psbtState.signerExportPsbtBase64 ?: psbtState.unsignedPsbtBase64
    val signerExportPsbtBytes =
        remember(signerExportPsbtBase64) {
            signerExportPsbtBase64?.let {
                android.util.Base64.decode(it, android.util.Base64.DEFAULT)
            }
        }

    // File picker for saving unsigned PSBT
    val savePsbtLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            if (uri != null && signerExportPsbtBase64 != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        // Write raw PSBT bytes (base64-decoded) for maximum compatibility
                        val bytes =
                            android.util.Base64.decode(
                                signerExportPsbtBase64,
                                android.util.Base64.DEFAULT,
                            )
                        stream.write(bytes)
                    }
                    Toast.makeText(context, "PSBT saved", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Failed to save PSBT", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // File picker for loading signed PSBT/tx (.psbt, .txn, .txt, or any file)
    val loadSignedLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val result = parseTxFileBytes(stream.readBytesWithLimit(InputLimits.TX_FILE_BYTES))
                        if (result != null) {
                            onSignedDataReceived(result.data)
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Scanner dialog
    if (showScanner) {
        AnimatedQrScannerDialog(
            onDataReceived = { data ->
                showScanner = false
                onSignedDataReceived(data)
            },
            onDismiss = { showScanner = false },
        )
    }

    // Paste signed transaction dialog
    if (showPasteDialog) {
        PasteSignedTransactionDialog(
            onSubmit = { data ->
                showPasteDialog = false
                onSignedDataReceived(data)
            },
            onDismiss = { showPasteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.loc_1959e119),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        containerColor = DarkBackground,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // Creating PSBT
                psbtState.isCreating -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(
                        color = BitcoinOrange,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.loc_b6d2470a),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                psbtState.isCombining -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(
                        color = BitcoinOrange,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.loc_e44c4bc5),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                // Broadcasting signed transaction
                psbtState.isBroadcasting -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(
                        color = BitcoinOrange,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = psbtState.broadcastStatus ?: "Broadcasting transaction...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                // Step 3: Signed data received — confirm before broadcast
                psbtState.signedData != null -> {
                    BroadcastConfirmation(
                        psbtState = psbtState,
                        uiState = uiState,
                        onConfirm = onConfirmBroadcast,
                        onCancel = onCancelBroadcast,
                    )
                }

                // PSBT ready - show QR and scan button
                signerExportPsbtBase64 != null -> {
                    // Step 1 - Export PSBT
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
                                text =
                                    if (psbtState.requiredSignatures != null) {
                                        "Export Partial PSBT"
                                    } else {
                                        "Step 1: Export Unsigned PSBT"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text =
                                    if (psbtState.requiredSignatures != null) {
                                        "Pass this PSBT to the next signer."
                                    } else {
                                        "Scan or save PSBT for signing."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            psbtState.requiredSignatures?.let { required ->
                                Text(
                                    text = "Signatures: ${psbtState.presentSignatures}/$required",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (psbtState.isReadyToBroadcast) {
                                            BitcoinOrange
                                        } else {
                                            TextSecondary
                                        },
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                QrDensityDropdown(
                                    currentDensity = qrDensity,
                                    onDensitySelected = onQrDensityChange,
                                    modifier = Modifier.width(156.dp),
                                )

                                QrExportFormatDropdown(
                                    currentFormat = qrExportFormat,
                                    onFormatSelected = { qrExportFormat = it },
                                    modifier = Modifier.width(156.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            QrBrightnessSlider(
                                brightness = qrBrightness,
                                onBrightnessChange = onQrBrightnessChange,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bbqrVersionRange = resolvePsbtBbqrVersionRange(qrDensity)
                                val exportQrSize =
                                    if (maxWidth > 32.dp) {
                                        maxWidth - 32.dp
                                    } else {
                                        maxWidth
                                    }

                                when (qrExportFormat) {
                                    PsbtQrExportFormat.BC_UR -> {
                                        val qrEncodingPlan =
                                            rememberAnimatedQrEncodingPlan(
                                                dataBase64 = signerExportPsbtBase64,
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
                                            Text(
                                                text = stringResource(R.string.loc_fb52d8a9),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                            )
                                        }
                                    }

                                    PsbtQrExportFormat.BBQR -> {
                                        signerExportPsbtBytes?.let { psbtBytes ->
                                            AnimatedQrCodeBytes(
                                                data = psbtBytes,
                                                qrSize = exportQrSize,
                                                frameDelayMs = resolvePsbtBbqrFrameDelayMs(qrDensity),
                                                fileType = Bbqr.FILE_TYPE_PSBT,
                                                brightness = qrBrightness,
                                                minVersion = bbqrVersionRange.minVersion,
                                                maxVersion = bbqrVersionRange.maxVersion,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Export options: Copy + Save to File
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val fileName =
                                            if (psbtState.requiredSignatures != null) {
                                                "partial.psbt"
                                            } else {
                                                "unsigned.psbt"
                                            }
                                        mainActivity?.skipNextBackgroundLockForActivityResult()
                                        savePsbtLauncher.launch(fileName)
                                    },
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
                                            signerExportPsbtBase64,
                                        )
                                        Toast.makeText(context, "PSBT copied", Toast.LENGTH_SHORT).show()
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
                        }
                    }

                    // Transaction details (actual values from BDK)
                    if (psbtState.actualFeeSats > 0UL) {
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
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_98c5fbdc),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Recipient
                                if (!psbtState.recipientAddress.isNullOrBlank()) {
                                    PsbtDetailRow(
                                        label = "To",
                                        value =
                                            psbtState.recipientAddress.take(12) + "..." +
                                                psbtState.recipientAddress.takeLast(8),
                                    )
                                }

                                // Send amount
                                PsbtDetailRow(
                                    label = "Amount",
                                    value = formatPsbtAmount(psbtState.recipientAmountSats),
                                )

                                // Change
                                if (psbtState.changeAmountSats != null) {
                                    PsbtDetailRow(
                                        label = "Change",
                                        value = formatPsbtAmount(psbtState.changeAmountSats),
                                    )
                                }

                                // Fee
                                PsbtDetailRow(
                                    label = "Fee",
                                    value = formatPsbtAmount(psbtState.actualFeeSats),
                                    valueColor = BitcoinOrange,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Step 2 - Scan signed transaction back
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
                                text = stringResource(R.string.loc_a413cd58),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = stringResource(R.string.loc_85833e18),
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
                                    text =
                                        if (psbtState.requiredSignatures != null) {
                                            "Import Partial"
                                        } else {
                                            "Scan QR"
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Import from file or paste
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        mainActivity?.skipNextBackgroundLockForActivityResult()
                                        loadSignedLauncher.launch(arrayOf("*/*"))
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
                                        text =
                                            if (psbtState.requiredSignatures != null) {
                                                "Load Partial"
                                            } else {
                                                "Load File"
                                            },
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

                            if (!uiState.isConnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.loc_61673a2c),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }

                    // Error display
                    if (psbtState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = ErrorRed.copy(alpha = 0.1f),
                                ),
                        ) {
                            Text(
                                text = psbtState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Error state (PSBT creation failed)
                psbtState.error != null -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = ErrorRed.copy(alpha = 0.1f),
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
                                text = stringResource(R.string.loc_5a0b70fc),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = psbtState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * Broadcast confirmation screen shown after scanning signed transaction.
 * Displays transaction details and requires explicit confirmation before broadcasting.
 */
@Composable
private fun BroadcastConfirmation(
    psbtState: PsbtState,
    uiState: WalletUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
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
                text = stringResource(R.string.loc_c1c941b3),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.loc_3d3785e2),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction breakdown
            if (!psbtState.recipientAddress.isNullOrBlank()) {
                PsbtDetailRow(
                    label = "To",
                    value =
                        psbtState.recipientAddress.take(12) + "..." +
                            psbtState.recipientAddress.takeLast(8),
                )
            }

            PsbtDetailRow(
                label = "Amount",
                value = formatPsbtAmount(psbtState.recipientAmountSats),
            )

            if (psbtState.changeAmountSats != null) {
                PsbtDetailRow(
                    label = "Change",
                    value = formatPsbtAmount(psbtState.changeAmountSats),
                )
            }

            if (psbtState.actualFeeSats > 0UL) {
                PsbtDetailRow(
                    label = "Fee",
                    value = formatPsbtAmount(psbtState.actualFeeSats),
                    valueColor = BitcoinOrange,
                )
            }

            if (psbtState.totalInputSats > 0UL) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(4.dp))
                PsbtDetailRow(
                    label = "Total",
                    value = formatPsbtAmount(psbtState.recipientAmountSats + psbtState.actualFeeSats),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Confirm broadcast
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = uiState.isConnected,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        contentColor = DarkBackground,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.loc_f45861d1),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (!uiState.isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loc_61673a2c),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel
            OutlinedButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Text(
                    text = stringResource(R.string.loc_51bac044),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }
        }
    }

    // Error display
    if (psbtState.error != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = ErrorRed.copy(alpha = 0.1f),
                ),
        ) {
            Text(
                text = psbtState.error,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Dialog for pasting a signed PSBT (base64) or raw transaction (hex).
 */
@Composable
private fun PasteSignedTransactionDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }

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
                        .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_ea6d758a),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.loc_07fafe3b),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.loc_460573b5),
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

@Composable
private fun PsbtDetailRow(
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
private fun QrDensityDropdown(
    currentDensity: SecureStorage.QrDensity,
    onDensitySelected: (SecureStorage.QrDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            QrDropdownOption(
                title = "Low",
                onClick = { onDensitySelected(SecureStorage.QrDensity.LOW) },
                selected = currentDensity == SecureStorage.QrDensity.LOW,
            ),
            QrDropdownOption(
                title = "Medium",
                onClick = { onDensitySelected(SecureStorage.QrDensity.MEDIUM) },
                selected = currentDensity == SecureStorage.QrDensity.MEDIUM,
            ),
            QrDropdownOption(
                title = "High",
                onClick = { onDensitySelected(SecureStorage.QrDensity.HIGH) },
                selected = currentDensity == SecureStorage.QrDensity.HIGH,
            ),
        )

    QrDropdown(
        currentValue = currentDensity.displayName(),
        label = "QR Density",
        options = options,
        modifier = modifier,
    )
}

@Composable
private fun QrExportFormatDropdown(
    currentFormat: PsbtQrExportFormat,
    onFormatSelected: (PsbtQrExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            QrDropdownOption(
                title = "BC-UR",
                onClick = { onFormatSelected(PsbtQrExportFormat.BC_UR) },
                selected = currentFormat == PsbtQrExportFormat.BC_UR,
            ),
            QrDropdownOption(
                title = "BBQr",
                onClick = { onFormatSelected(PsbtQrExportFormat.BBQR) },
                selected = currentFormat == PsbtQrExportFormat.BBQR,
            ),
        )

    QrDropdown(
        currentValue = currentFormat.displayName(),
        label = "QR Format",
        options = options,
        modifier = modifier,
    )
}

private fun SecureStorage.QrDensity.displayName(): String =
    when (this) {
        SecureStorage.QrDensity.LOW -> "Low"
        SecureStorage.QrDensity.MEDIUM -> "Medium"
        SecureStorage.QrDensity.HIGH -> "High"
    }

private fun PsbtQrExportFormat.displayName(): String =
    when (this) {
        PsbtQrExportFormat.BC_UR -> "BC-UR"
        PsbtQrExportFormat.BBQR -> "BBQr"
    }

private data class QrDropdownOption(
    val title: String,
    val onClick: () -> Unit,
    val selected: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrDropdown(
    currentValue: String,
    label: String,
    options: List<QrDropdownOption>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        QrDropdownField(
            value = currentValue,
            label = label,
            expanded = expanded,
            modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        QrDropdownOptionText(
                            title = option.title,
                            selected = option.selected,
                        )
                    },
                    onClick = {
                        option.onClick()
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QrDropdownField(
    value: String,
    label: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = expanded,
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BitcoinOrange,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = BitcoinOrange,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = BitcoinOrange,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
    )
}

@Composable
private fun QrDropdownOptionText(
    title: String,
    selected: Boolean,
) {
    Text(
        text = title,
        style = TextStyle(fontSize = 14.5.sp),
        color = if (selected) BitcoinOrange else MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun QrBrightnessSlider(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.loc_f8155e77),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.loc_0dd3b9b2),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = SecureStorage.MIN_PSBT_QR_BRIGHTNESS..1f,
            colors =
                SliderDefaults.colors(
                    thumbColor = BitcoinOrange,
                    activeTrackColor = BitcoinOrange,
                ),
        )
    }
}

/** Format sats for display: shows BTC for large amounts, sats for small */
private fun formatPsbtAmount(sats: ULong): String {
    return if (sats >= 100_000UL) {
        val btc = sats.toLong() / 100_000_000.0
        "%.8f BTC".format(btc)
    } else {
        "%,d sats".format(sats.toLong())
    }
}

