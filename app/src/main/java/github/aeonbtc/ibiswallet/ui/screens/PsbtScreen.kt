package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.parseTxFileBytes
import github.aeonbtc.ibiswallet.viewmodel.PsbtState
import github.aeonbtc.ibiswallet.viewmodel.WalletUiState

/**
 * PSBT export and signing flow screen for watch-only wallets.
 *
 * Three-phase flow:
 * 1. Display unsigned PSBT as animated QR code for hardware wallet to scan
 * 2. Scan the signed PSBT/raw transaction back from the hardware wallet
 * 3. Confirm transaction details before broadcasting
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsbtScreen(
    psbtState: PsbtState,
    uiState: WalletUiState,
    onSignedDataReceived: (String) -> Unit,
    onConfirmBroadcast: () -> Unit,
    onCancelBroadcast: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    // File picker for saving unsigned PSBT
    val savePsbtLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            if (uri != null && psbtState.unsignedPsbtBase64 != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        // Write raw PSBT bytes (base64-decoded) for maximum compatibility
                        val bytes =
                            android.util.Base64.decode(
                                psbtState.unsignedPsbtBase64,
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
                        val result = parseTxFileBytes(stream.readBytes())
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
            onScanQr = {
                showPasteDialog = false
                showScanner = true
            },
            onDismiss = { showPasteDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Build PSBT",
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
                        text = "Creating PSBT...",
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
                psbtState.unsignedPsbtBase64 != null -> {
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
                                text = "Step 1: Export Unsigned PSBT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Scan or save PSBT for signing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Animated QR code
                            AnimatedQrCode(
                                psbtBase64 = psbtState.unsignedPsbtBase64,
                                qrSize = 280.dp,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Export options: Copy + Save to File
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        savePsbtLauncher.launch("unsigned.psbt")
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
                                        text = "Save File",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        SecureClipboard.copyAndScheduleClear(
                                            context,
                                            "PSBT",
                                            psbtState.unsignedPsbtBase64,
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
                                        text = "Copy PSBT",
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
                                    text = "Transaction Details",
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
                                    value = formatSats(psbtState.recipientAmountSats),
                                )

                                // Change
                                if (psbtState.changeAmountSats != null) {
                                    PsbtDetailRow(
                                        label = "Change",
                                        value = formatSats(psbtState.changeAmountSats),
                                    )
                                }

                                // Fee
                                PsbtDetailRow(
                                    label = "Fee",
                                    value = formatSats(psbtState.actualFeeSats),
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
                                text = "Step 2: Import Signed PSBT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Scan or load signed PSBT for broadcasting.",
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
                                    text = "Scan QR",
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
                                        text = "Load File",
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
                                        text = "Paste",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            if (!uiState.isConnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect to Electrum to broadcast",
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
                                text = "Failed to Create PSBT",
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
                text = "Confirm Broadcast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Signed transaction received. Review and confirm to broadcast.",
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
                value = formatSats(psbtState.recipientAmountSats),
            )

            if (psbtState.changeAmountSats != null) {
                PsbtDetailRow(
                    label = "Change",
                    value = formatSats(psbtState.changeAmountSats),
                )
            }

            if (psbtState.actualFeeSats > 0UL) {
                PsbtDetailRow(
                    label = "Fee",
                    value = formatSats(psbtState.actualFeeSats),
                    valueColor = BitcoinOrange,
                )
            }

            if (psbtState.totalInputSats > 0UL) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(4.dp))
                PsbtDetailRow(
                    label = "Total",
                    value = formatSats(psbtState.recipientAmountSats + psbtState.actualFeeSats),
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
                    text = "Broadcast Transaction",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (!uiState.isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect to Electrum to broadcast",
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
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            ) {
                Text(
                    text = "Cancel",
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
    onScanQr: () -> Unit,
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
                    text = "Paste Signed Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Paste signed PSBT base64 or raw transaction hex",
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
                                "Signed PSBT or raw tx hex",
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
                        Text("Cancel", color = TextSecondary)
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
                        Text("Submit")
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

/** Format sats for display: shows BTC for large amounts, sats for small */
private fun formatSats(sats: ULong): String {
    return if (sats >= 100_000UL) {
        val btc = sats.toLong() / 100_000_000.0
        "%.8f BTC".format(btc)
    } else {
        "%,d sats".format(sats.toLong())
    }
}
