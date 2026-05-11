@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.parseTxFileBytes
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import github.aeonbtc.ibiswallet.viewmodel.ManualBroadcastState
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect

/**
 * Screen for manually broadcasting a signed transaction.
 * Accepts raw transaction hex or signed PSBT base64 via text input, QR scan, or file import.
 * Standalone — the transaction does not need to belong to any wallet loaded in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastTransactionScreen(
    broadcastState: ManualBroadcastState,
    isConnected: Boolean,
    onPreview: (String) -> Unit,
    onBroadcast: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var inputData by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // File picker for loading transaction from file (.psbt, .txn, .txt, or any file)
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val result = parseTxFileBytes(stream.readBytesWithLimit(InputLimits.TX_FILE_BYTES))
                        if (result != null) {
                            inputData = result.data
                        }
                    }
                } catch (_: Exception) {
                    // File read failed — ignore silently
                }
            }
        }

    // QR Scanner Dialog
    if (showQrScanner) {
        AnimatedQrScannerDialog(
            onDataReceived = { data ->
                inputData = data
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    // Detect input format for display
    val trimmedInput = inputData.trim()
    val detectedFormat =
        remember(trimmedInput) {
            detectFormat(trimmedInput)
        }

    // Recompute decoded outputs whenever the input changes so the on-screen
    // review reflects exactly what the broadcast will commit.
    LaunchedEffect(trimmedInput, detectedFormat) {
        if (trimmedInput.isNotEmpty() && detectedFormat != InputFormat.INVALID) {
            onPreview(trimmedInput)
        }
    }

    // Reset the user's "I have reviewed" acknowledgement whenever the input
    // or its decoded preview changes so a fresh paste cannot inherit a prior
    // confirmation.
    var reviewed by remember(trimmedInput, broadcastState.previewInput) {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.loc_89bab973),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onClear()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Input card (input field, format indicator, load file) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = DarkCard,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                ) {
                    // Input text field with QR button at bottom right
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = inputData,
                            onValueChange = {
                                inputData = it
                                // Clear previous result when user edits input
                                if (broadcastState.txid != null || broadcastState.error != null) {
                                    onClear()
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.loc_7177b2f3),
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

                        // QR scan button at bottom right
                        IconButton(
                            onClick = { showQrScanner = true },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = BitcoinOrange,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text =
                            stringResource(R.string.loc_e1614c61) +
                                "Use the original PSBT flow when available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    // Format detection indicator
                    if (trimmedInput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text =
                                when (detectedFormat) {
                                    InputFormat.RAW_HEX -> "Raw transaction hex detected"
                                    InputFormat.PSBT_BASE64 -> "Signed PSBT detected"
                                    InputFormat.INVALID -> "Unrecognized format"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when (detectedFormat) {
                                    InputFormat.RAW_HEX, InputFormat.PSBT_BASE64 -> TextSecondary
                                    InputFormat.INVALID -> WarningYellow
                                },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Import from file
                    IbisButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_a5e83581),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Decoded outputs review ---
            val preview = broadcastState.preview
            val previewMatchesInput =
                preview != null && broadcastState.previewInput == trimmedInput
            if (previewMatchesInput && preview != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = DarkCard,
                        ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        Text(
                            text = "Review decoded outputs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Txid: ${preview.txid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                        if (!preview.isFromLoadedWallet) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Inputs are not from the loaded wallet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningYellow,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        preview.outputs.forEachIndexed { index, output ->
                            val tag =
                                if (output.ownedByLoadedWallet) "[own]" else "[external]"
                            val tagColor =
                                if (output.ownedByLoadedWallet) SuccessGreen else WarningYellow
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "#$index  ${output.amountSats} sats  $tag",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tagColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = output.address ?: "<unparseable scriptPubKey>",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 2,
                                )
                            }
                        }
                        if (preview.anyOutputUnowned) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text =
                                    "At least one output is not owned by the loaded wallet — " +
                                        "double-check the destination(s) before broadcasting.",
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
                                text = "I have reviewed the decoded outputs",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (trimmedInput.isNotEmpty() &&
                detectedFormat != InputFormat.INVALID &&
                broadcastState.previewInput == trimmedInput &&
                preview == null
            ) {
                // The repo could not decode the payload at all even though the
                // surface-level format check passed (e.g. PSBT body bytes are
                // malformed). Surface this loudly so the user does not bypass
                // the review by checking a stale "reviewed" box.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WarningYellow.copy(alpha = 0.1f),
                        ),
                ) {
                    Text(
                        text = "Could not decode this payload. Broadcast disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Not connected warning ---
            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = WarningYellow.copy(alpha = 0.1f),
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.loc_d893f956),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // --- Broadcast button ---
            val canBroadcast =
                trimmedInput.isNotEmpty() &&
                    detectedFormat != InputFormat.INVALID &&
                    isConnected &&
                    !broadcastState.isBroadcasting &&
                    previewMatchesInput &&
                    preview != null &&
                    reviewed

            Button(
                onClick = { onBroadcast(trimmedInput) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = canBroadcast,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                    ),
            ) {
                if (broadcastState.isBroadcasting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.loc_1900fe56),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // --- Broadcast status ---
            if (broadcastState.isBroadcasting && broadcastState.broadcastStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = broadcastState.broadcastStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            // --- Success card ---
            if (broadcastState.txid != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = SuccessGreen.copy(alpha = 0.1f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_caad9ed8),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = broadcastState.txid,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = SuccessGreen.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                SecureClipboard.copyAndScheduleClear(
                                    context,
                                    broadcastState.txid,
                                )
                            },
                        ) {
                            Text(
                                stringResource(R.string.loc_e934eeef),
                                color = SuccessGreen,
                            )
                        }
                    }
                }
            }

            // --- Error card ---
            if (broadcastState.error != null) {
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
                        text = broadcastState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private enum class InputFormat {
    RAW_HEX,
    PSBT_BASE64,
    INVALID,
}

/**
 * Auto-detect whether the input is raw transaction hex, base64 PSBT, or invalid.
 */
private fun detectFormat(input: String): InputFormat {
    if (input.isEmpty()) return InputFormat.INVALID

    val isHex = input.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (isHex && input.length % 2 == 0 && input.length > 20) {
        return InputFormat.RAW_HEX
    }

    // Base64 characters (standard + URL-safe variants) with possible padding
    val isBase64 =
        input.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it == '-' || it == '_'
        }
    if (isBase64 && input.length > 10) {
        return InputFormat.PSBT_BASE64
    }

    return InputFormat.INVALID
}
