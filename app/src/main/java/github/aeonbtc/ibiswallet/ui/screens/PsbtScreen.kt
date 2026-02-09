package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }

    // Scanner dialog
    if (showScanner) {
        AnimatedQrScannerDialog(
            onDataReceived = { data ->
                showScanner = false
                onSignedDataReceived(data)
            },
            onDismiss = { showScanner = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sign with Hardware Wallet",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // Creating PSBT
                psbtState.isCreating -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(
                        color = BitcoinOrange,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Creating PSBT...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                // Broadcasting signed transaction
                psbtState.isBroadcasting -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(
                        color = BitcoinOrange,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = psbtState.broadcastStatus ?: "Broadcasting transaction...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                // Step 3: Signed data received — confirm before broadcast
                psbtState.signedData != null -> {
                    BroadcastConfirmation(
                        psbtState = psbtState,
                        uiState = uiState,
                        onConfirm = onConfirmBroadcast,
                        onCancel = onCancelBroadcast
                    )
                }

                // PSBT ready - show QR and scan button
                psbtState.unsignedPsbtBase64 != null -> {
                    // Step 1 - Export PSBT
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Step 1: Scan with Hardware Wallet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Point your hardware wallet's camera at this QR code",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Animated QR code
                            AnimatedQrCode(
                                psbtBase64 = psbtState.unsignedPsbtBase64,
                                qrSize = 280.dp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Copy PSBT base64 to clipboard
                            OutlinedButton(
                                onClick = {
                                    SecureClipboard.copyAndScheduleClear(context, "PSBT", psbtState.unsignedPsbtBase64)
                                    Toast.makeText(context, "PSBT copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Copy PSBT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Transaction details (actual values from BDK)
                    if (psbtState.actualFeeSats > 0UL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Transaction Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Recipient
                                if (!psbtState.recipientAddress.isNullOrBlank()) {
                                    PsbtDetailRow(
                                        label = "To",
                                        value = psbtState.recipientAddress.take(12) + "..." +
                                            psbtState.recipientAddress.takeLast(8)
                                    )
                                }

                                // Send amount
                                PsbtDetailRow(
                                    label = "Amount",
                                    value = formatSats(psbtState.recipientAmountSats)
                                )

                                // Change
                                if (psbtState.changeAmountSats != null) {
                                    PsbtDetailRow(
                                        label = "Change",
                                        value = formatSats(psbtState.changeAmountSats)
                                    )
                                }

                                // Fee
                                PsbtDetailRow(
                                    label = "Fee",
                                    value = formatSats(psbtState.actualFeeSats),
                                    valueColor = BitcoinOrange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Step 2 - Scan signed transaction back
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Step 2: Scan Signed Transaction",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "After signing on your hardware wallet, scan the result back",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showScanner = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = uiState.isConnected,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BitcoinOrange,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scan Signed Transaction",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            if (!uiState.isConnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect to Electrum to broadcast",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
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
                            colors = CardDefaults.cardColors(
                                containerColor = ErrorRed.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = psbtState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
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
                        colors = CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Failed to Create PSBT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = psbtState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                textAlign = TextAlign.Center
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
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Confirm Broadcast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Signed transaction received. Review and confirm to broadcast.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction breakdown
            if (!psbtState.recipientAddress.isNullOrBlank()) {
                PsbtDetailRow(
                    label = "To",
                    value = psbtState.recipientAddress.take(12) + "..." +
                        psbtState.recipientAddress.takeLast(8)
                )
            }

            PsbtDetailRow(
                label = "Amount",
                value = formatSats(psbtState.recipientAmountSats)
            )

            if (psbtState.changeAmountSats != null) {
                PsbtDetailRow(
                    label = "Change",
                    value = formatSats(psbtState.changeAmountSats)
                )
            }

            if (psbtState.actualFeeSats > 0UL) {
                PsbtDetailRow(
                    label = "Fee",
                    value = formatSats(psbtState.actualFeeSats),
                    valueColor = BitcoinOrange
                )
            }

            if (psbtState.totalInputSats > 0UL) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(4.dp))
                PsbtDetailRow(
                    label = "Total",
                    value = formatSats(psbtState.recipientAmountSats + psbtState.actualFeeSats)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Confirm broadcast
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = uiState.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "Broadcast Transaction",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!uiState.isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect to Electrum to broadcast",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
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
            colors = CardDefaults.cardColors(
                containerColor = ErrorRed.copy(alpha = 0.1f)
            )
        ) {
            Text(
                text = psbtState.error,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PsbtDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor
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
