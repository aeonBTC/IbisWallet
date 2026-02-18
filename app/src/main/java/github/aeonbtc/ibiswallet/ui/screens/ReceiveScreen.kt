@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.WalletState
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import java.util.Locale

@Composable
fun ReceiveScreen(
    walletState: WalletState = WalletState(),
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    privacyMode: Boolean = false,
    onGenerateAddress: () -> Unit = {},
    onSaveLabel: (String, String) -> Unit = { _, _ -> },
    onShowAllAddresses: () -> Unit = {},
    onShowAllUtxos: () -> Unit = {},
) {
    val context = LocalContext.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var labelText by remember(walletState.currentAddressInfo?.label) {
        mutableStateOf(walletState.currentAddressInfo?.label ?: "")
    }
    var showAmountField by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    var showLabelField by remember { mutableStateOf(false) }

    // Convert amount to sats for URI (handles BTC, sats, and USD input)
    val amountInSats =
        remember(amountText, useSats, isUsdMode, btcPrice) {
            if (amountText.isEmpty()) {
                null
            } else if (isUsdMode && btcPrice != null && btcPrice > 0) {
                // USD input - convert to sats: USD / price * 100_000_000
                amountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).toLong()
                }
            } else if (useSats) {
                amountText.toLongOrNull()
            } else {
                // BTC input - convert to sats
                amountText.toDoubleOrNull()?.let { btc ->
                    (btc * 100_000_000).toLong()
                }
            }
        }

    // Build the URI/content for QR code
    val qrContent =
        remember(walletState.currentAddress, amountInSats, showAmountField) {
            walletState.currentAddress?.let { address ->
                if (showAmountField && amountInSats != null && amountInSats > 0) {
                    val btcAmount = amountInSats.toDouble() / 100_000_000.0
                    "bitcoin:$address?amount=${String.format(Locale.US, "%.8f", btcAmount)}"
                } else {
                    address
                }
            }
        }

    // Generate QR code when content changes
    LaunchedEffect(qrContent) {
        qrContent?.let { content ->
            qrBitmap = generateQrCode(content)
        }
    }

    // Generate address if wallet is initialized but no address
    LaunchedEffect(walletState.isInitialized, walletState.currentAddress) {
        if (walletState.isInitialized && walletState.currentAddress == null) {
            onGenerateAddress()
        }
    }

    // Enlarged QR Code Dialog
    if (showEnlargedQr && qrBitmap != null) {
        Dialog(
            onDismissRequest = { showEnlargedQr = false },
        ) {
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
                            contentDescription = "Enlarged QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Tap anywhere to close",
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
                    text = "Receive Bitcoin",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
                // QR Code - clickable to enlarge
                Box(
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable(
                                enabled = walletState.currentAddress != null,
                            ) { showEnlargedQr = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null && walletState.currentAddress != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code - Tap to enlarge",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = if (walletState.isInitialized) "Generating..." else "No wallet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Address display — chunked every 6 chars, split across 2 lines
                Text(
                    text = formatAddress(walletState.currentAddress),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color =
                        if (walletState.currentAddress != null) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            TextSecondary
                        },
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Copy, Refresh (New Address)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .clickable(enabled = walletState.currentAddress != null) {
                                    qrContent?.let { content ->
                                        SecureClipboard.copyAndScheduleClear(context, "Address", content)
                                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint =
                                if (walletState.currentAddress != null) {
                                    BitcoinOrange
                                } else {
                                    TextSecondary.copy(alpha = 0.5f)
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .clickable(enabled = walletState.isInitialized) {
                                    onGenerateAddress()
                                },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Generate New Address",
                            tint =
                                if (walletState.isInitialized) {
                                    BitcoinOrange
                                } else {
                                    TextSecondary.copy(alpha = 0.5f)
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Toggle switches for Amount and Label
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            text = "Amount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        SquareToggle(
                            checked = showAmountField,
                            onCheckedChange = { showAmountField = it },
                        )
                    }

                    // Amount input field (shown when toggled)
                    AnimatedVisibility(visible = showAmountField) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Amount label row with USD toggle
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        when {
                                            isUsdMode -> "Amount (USD)"
                                            useSats -> "Amount (sats)"
                                            else -> "Amount (BTC)"
                                        },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary,
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
                                            text = "USD",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isUsdMode) BitcoinOrange else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        { Text("$", color = TextSecondary) }
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
                                        "≈ ${formatAmountForReceive(
                                            amountInSats.toULong(),
                                            useSats,
                                        )} ${if (useSats) "sats" else "BTC"}"
                                    } else {
                                        val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                                        "≈ $${String.format(Locale.US, "%,.2f", usdValue)}"
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
                                .clickable { showLabelField = !showLabelField }
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Label",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        SquareToggle(
                            checked = showLabelField,
                            onCheckedChange = { showLabelField = it },
                        )
                    }

                    // Label input field (shown when toggled)
                    AnimatedVisibility(visible = showLabelField) {
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            placeholder = { Text("e.g. Payment from Alice") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BitcoinOrange,
                                    unfocusedBorderColor = BorderColor,
                                    cursorColor = BitcoinOrange,
                                ),
                            enabled = walletState.currentAddress != null,
                            trailingIcon = {
                                if (labelText.isNotEmpty() && walletState.currentAddress != null) {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            onSaveLabel(walletState.currentAddress, labelText)
                                            Toast.makeText(context, "Label saved", Toast.LENGTH_SHORT).show()
                                        },
                                    ) {
                                        Text("Save", color = BitcoinOrange)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Show All Addresses and Show All UTXOs buttons
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
                    text = "All Addresses",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            IbisButton(
                onClick = onShowAllUtxos,
                modifier = Modifier.weight(1f),
                enabled = walletState.isInitialized,
            ) {
                Text(
                    text = "All UTXOs",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Format amount for display based on denomination
 */
private fun formatAmountForReceive(
    sats: ULong,
    useSats: Boolean,
): String {
    return if (useSats) {
        java.text.NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())
    } else {
        val btc = sats.toDouble() / 100_000_000.0
        String.format(Locale.US, "%.8f", btc)
    }
}

/**
 * Generate a QR code bitmap for the given content
 */
private fun generateQrCode(content: String): Bitmap? {
    return try {
        val size = 512
        val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
        val hints =
            mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1, // Minimal margin
            )
        val bitMatrix =
            qrCodeWriter.encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints,
            )

        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] =
                    if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

/**
 * Formats a Bitcoin address for display: chunks every 7 characters.
 * Segwit (42 chars) → 2 lines, Taproot (62 chars) → 3 lines.
 */
private fun formatAddress(address: String?): String {
    if (address == null) return "No wallet"
    val chunks = address.chunked(7)
    val numLines = if (address.length > 50) 3 else 2
    val perLine = (chunks.size + numLines - 1) / numLines
    return chunks
        .chunked(perLine)
        .joinToString("\n") { it.joinToString(" ") }
}
