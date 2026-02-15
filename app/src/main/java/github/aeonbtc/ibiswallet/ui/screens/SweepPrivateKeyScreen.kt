package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.model.FeeEstimationResult
import github.aeonbtc.ibiswallet.ui.components.FeeRateSection
import github.aeonbtc.ibiswallet.ui.components.ImportQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.formatFeeRate
import github.aeonbtc.ibiswallet.ui.theme.*
import github.aeonbtc.ibiswallet.viewmodel.SweepState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SweepPrivateKeyScreen(
    sweepState: SweepState,
    isConnected: Boolean,
    onScanBalances: (wif: String) -> Unit,
    onSweep: (wif: String, destination: String, feeRate: Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    currentReceiveAddress: String? = null,
    isWifValid: (String) -> Boolean,
    feeEstimationState: FeeEstimationResult = FeeEstimationResult.Disabled,
    minFeeRate: Double = 1.0,
    onRefreshFees: () -> Unit = {},
) {
    var wifKey by remember { mutableStateOf("") }
    var destinationAddress by remember { mutableStateOf("") }
    var feeRate by remember { mutableFloatStateOf(5f) }
    var showWifQrScanner by remember { mutableStateOf(false) }
    var showDestQrScanner by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    // Refresh fee estimates when screen opens
    LaunchedEffect(Unit) { onRefreshFees() }

    val isValidWif =
        remember(wifKey) {
            wifKey.isNotBlank() && isWifValid(wifKey.trim())
        }

    val isValidFeeRate = feeRate >= minFeeRate.toFloat()
    val isValidDestination = destinationAddress.isNotBlank()
    val canScan = isValidWif && isConnected && !sweepState.isScanning
    val canSweep = sweepState.hasBalance && isValidDestination && isValidFeeRate && !sweepState.isSweeping

    // QR scanner dialogs
    if (showWifQrScanner) {
        ImportQrScannerDialog(
            onCodeScanned = { scanned ->
                wifKey = scanned
                showWifQrScanner = false
            },
            onDismiss = { showWifQrScanner = false },
        )
    }
    if (showDestQrScanner) {
        ImportQrScannerDialog(
            onCodeScanned = { scanned ->
                // Strip bitcoin: URI prefix if present
                val addr = scanned.removePrefix("bitcoin:").split("?").first().trim()
                destinationAddress = addr
                showDestQrScanner = false
            },
            onDismiss = { showDestQrScanner = false },
        )
    }

    // Review / confirm sweep dialog
    if (showReviewDialog && sweepState.hasBalance) {
        AlertDialog(
            onDismissRequest = { if (!sweepState.isSweeping) showReviewDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    "Confirm Sweep",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    sweepState.scanResults.forEach { result ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                result.addressType.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Text(
                                "${result.balanceSats} sats",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = BorderColor,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "${sweepState.totalBalanceSats} sats",
                            style = MaterialTheme.typography.titleSmall,
                            color = BitcoinOrange,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Fee rate: ${formatFeeRate(feeRate)} sat/vB",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "To: ${destinationAddress.take(16)}...${destinationAddress.takeLast(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    if (sweepState.isSweeping) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = BitcoinOrange,
                            trackColor = BorderColor,
                        )
                        sweepState.sweepProgress?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSweep(wifKey.trim(), destinationAddress.trim(), feeRate)
                    },
                    enabled = !sweepState.isSweeping,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                            disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                        ),
                ) {
                    if (sweepState.isSweeping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Broadcast")
                    }
                }
            },
            dismissButton = {
                if (!sweepState.isSweeping) {
                    TextButton(onClick = { showReviewDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            },
        )
    }

    // Clear WIF key from memory once sweep completes
    LaunchedEffect(sweepState.isComplete) {
        if (sweepState.isComplete) {
            wifKey = ""
        }
    }

    // Success dialog
    if (sweepState.isComplete) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = DarkCard,
            title = {
                Text(
                    "Sweep Successful",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    Text(
                        "${sweepState.sweepTxids.size} transaction${if (sweepState.sweepTxids.size > 1) "s" else ""} broadcast",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    sweepState.sweepTxids.forEach { txid ->
                        Text(
                            "${txid.take(16)}...${txid.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        onBack()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BitcoinOrange),
                ) {
                    Text("Done")
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
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                onReset()
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sweep Private Key",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WIF Private Key input
        Text(
            text = "Private Key (WIF)",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = wifKey,
                onValueChange = { wifKey = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                shape = RoundedCornerShape(8.dp),
                placeholder = { Text("Enter WIF key (K..., L..., 5...)", color = TextSecondary.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(autoCorrect = false),
                singleLine = false,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = BitcoinOrange,
                    ),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showWifQrScanner = true },
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = BitcoinOrange,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // WIF validation feedback
        if (wifKey.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isValidWif) "Valid WIF key" else "Invalid WIF key",
                style = MaterialTheme.typography.bodySmall,
                color = if (isValidWif) SuccessGreen else ErrorRed,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Destination Address
        Text(
            text = "Destination Address",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = destinationAddress,
            onValueChange = { destinationAddress = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            placeholder = { Text("Bitcoin address to receive funds", color = TextSecondary.copy(alpha = 0.5f)) },
            keyboardOptions = KeyboardOptions(autoCorrect = false),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showDestQrScanner = true }) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        tint = BitcoinOrange,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BitcoinOrange,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = BitcoinOrange,
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fee Rate
        FeeRateSection(
            feeEstimationState = feeEstimationState,
            currentFeeRate = feeRate,
            minFeeRate = minFeeRate,
            onFeeRateChange = { feeRate = it },
            onRefreshFees = onRefreshFees,
            enabled = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Check Balance button
        Button(
            onClick = { onScanBalances(wifKey.trim()) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canScan,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                ),
        ) {
            if (sweepState.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(sweepState.scanProgress ?: "Scanning...")
            } else {
                Text("Check Balance")
            }
        }

        // Scan Results
        if (sweepState.scanResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Funds Found",
                        style = MaterialTheme.typography.titleSmall,
                        color = SuccessGreen,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    sweepState.scanResults.forEach { result ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    result.addressType.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    result.address.take(16) + "..." + result.address.takeLast(8),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Text(
                                "${result.balanceSats} sats",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BitcoinOrange,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = BorderColor,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "${sweepState.totalBalanceSats} sats",
                            style = MaterialTheme.typography.titleSmall,
                            color = BitcoinOrange,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sweep button
            Button(
                onClick = { showReviewDialog = true },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = canSweep,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                    ),
            ) {
                Text("Review Sweep")
            }
        } else if (!sweepState.isScanning && sweepState.scanResults.isEmpty() && sweepState.error == null &&
            wifKey.isNotBlank() && isValidWif && sweepState.scanProgress == null &&
            sweepState.totalBalanceSats == 0UL && sweepState.sweepTxids.isEmpty()
        ) {
            // Show "no funds" only after a scan has been attempted (scanResults is empty but no error)
        }

        // Error display
        if (sweepState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
            ) {
                Text(
                    text = sweepState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (!isConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect to an Electrum server to sweep",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
