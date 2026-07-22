package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.data.model.LiquidPsetState
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrCode
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrExportProfile
import github.aeonbtc.ibiswallet.ui.components.AnimatedQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.rememberAnimatedQrEncodingPlan
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.parseTxFileBytes
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import java.text.NumberFormat
import java.util.Locale

/**
 * PSET export / scan / broadcast flow for Liquid watch-only wallets (Jade QR).
 * Mirrors [PsbtScreen] with Liquid accent and `ur:crypto-psbt` export only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidPsetScreen(
    psetState: LiquidPsetState,
    isConnected: Boolean,
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
    val exportBase64 = psetState.unsignedPsetBase64
    val assetTicker = remember(psetState.assetId) {
        val id = psetState.assetId
        if (id == null || LiquidAsset.isPolicyAsset(id)) {
            "L-BTC"
        } else {
            LiquidAsset.resolve(id).ticker
        }
    }

    val savePsetLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            if (uri != null && exportBase64 != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        val bytes = android.util.Base64.decode(exportBase64, android.util.Base64.DEFAULT)
                        stream.write(bytes)
                    }
                    Toast.makeText(context, context.getString(R.string.loc_pset_saved), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.loc_pset_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                    Toast.makeText(context, context.getString(R.string.loc_pset_read_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    if (showScanner) {
        AnimatedQrScannerDialog(
            onDataReceived = { data ->
                showScanner = false
                onSignedDataReceived(data)
            },
            onDismiss = { showScanner = false },
        )
    }

    if (showPasteDialog) {
        LiquidPasteSignedPsetDialog(
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
                        text = stringResource(R.string.loc_pset_title),
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
                psetState.isCreating -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(color = LiquidTeal, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.loc_pset_creating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                psetState.isCombining -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(color = LiquidTeal, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.loc_pset_combining),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                psetState.isBroadcasting -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator(color = LiquidTeal, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = psetState.broadcastStatus ?: stringResource(R.string.loc_pset_broadcasting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }

                psetState.signedData != null -> {
                    LiquidBroadcastConfirmation(
                        psetState = psetState,
                        assetTicker = assetTicker,
                        isConnected = isConnected,
                        onConfirm = onConfirmBroadcast,
                        onCancel = onCancelBroadcast,
                    )
                }

                exportBase64 != null -> {
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
                                text = stringResource(R.string.loc_pset_export_step),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.loc_pset_export_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            QrDensityDropdown(
                                currentDensity = qrDensity,
                                onDensitySelected = onQrDensityChange,
                                modifier = Modifier.width(156.dp),
                            )
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
                                val exportQrSize =
                                    if (maxWidth > 32.dp) maxWidth - 32.dp else maxWidth
                                val qrEncodingPlan =
                                    rememberAnimatedQrEncodingPlan(
                                        dataBase64 = exportBase64,
                                        density = qrDensity,
                                        exportProfile = AnimatedQrExportProfile.LIQUID_PSET,
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

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        mainActivity?.skipNextBackgroundLockForActivityResult()
                                        savePsetLauncher.launch("unsigned.pset")
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
                                        SecureClipboard.copyAndScheduleClear(context, exportBase64)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.loc_pset_copied),
                                            Toast.LENGTH_SHORT,
                                        ).show()
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

                    if (psetState.feeSats > 0L || psetState.recipientAmountSats > 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LiquidPsetDetailsCard(psetState = psetState, assetTicker = assetTicker)
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
                                text = stringResource(R.string.loc_pset_import_step),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.loc_pset_import_hint),
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
                                        containerColor = LiquidTeal,
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
                                    text = stringResource(R.string.loc_pset_scan_qr),
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
                                        text = stringResource(R.string.loc_pset_load_file),
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

                            if (!isConnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.loc_61673a2c),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }

                    if (psetState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                        ) {
                            Text(
                                text = psetState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                psetState.error != null -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_pset_create_failed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = psetState.error,
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

@Composable
private fun LiquidPsetDetailsCard(
    psetState: LiquidPsetState,
    assetTicker: String,
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
        ) {
            Text(
                text = stringResource(R.string.loc_98c5fbdc),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!psetState.recipientAddress.isNullOrBlank()) {
                PsetDetailRow(
                    label = "To",
                    value =
                        psetState.recipientAddress.take(12) + "..." +
                            psetState.recipientAddress.takeLast(8),
                )
            }
            PsetDetailRow(
                label = "Amount",
                value = formatLiquidPsetAmount(psetState.recipientAmountSats, assetTicker),
            )
            psetState.changeAmountSats?.let { change ->
                PsetDetailRow(
                    label = "Change",
                    value = formatLiquidPsetAmount(change, "L-BTC"),
                )
            }
            if (psetState.feeSats > 0L) {
                PsetDetailRow(
                    label = "Fee",
                    value = formatLiquidPsetAmount(psetState.feeSats, "L-BTC"),
                    valueColor = LiquidTeal,
                )
            }
        }
    }
}

@Composable
private fun LiquidBroadcastConfirmation(
    psetState: LiquidPsetState,
    assetTicker: String,
    isConnected: Boolean,
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
                text = stringResource(R.string.loc_pset_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.loc_pset_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!psetState.recipientAddress.isNullOrBlank()) {
                PsetDetailRow(
                    label = "To",
                    value =
                        psetState.recipientAddress.take(12) + "..." +
                            psetState.recipientAddress.takeLast(8),
                )
            }
            PsetDetailRow(
                label = "Amount",
                value = formatLiquidPsetAmount(psetState.recipientAmountSats, assetTicker),
            )
            psetState.changeAmountSats?.let { change ->
                PsetDetailRow(
                    label = "Change",
                    value = formatLiquidPsetAmount(change, "L-BTC"),
                )
            }
            if (psetState.feeSats > 0L) {
                PsetDetailRow(
                    label = "Fee",
                    value = formatLiquidPsetAmount(psetState.feeSats, "L-BTC"),
                    valueColor = LiquidTeal,
                )
            }
            if (psetState.totalInputSats > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(4.dp))
                PsetDetailRow(
                    label = "Total",
                    value =
                        formatLiquidPsetAmount(
                            psetState.recipientAmountSats + psetState.feeSats,
                            if (LiquidAsset.isPolicyAsset(psetState.assetId ?: LiquidAsset.LBTC_ASSET_ID)) {
                                "L-BTC"
                            } else {
                                assetTicker
                            },
                        ),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = isConnected,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = LiquidTeal,
                        contentColor = DarkBackground,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.loc_f45861d1),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loc_61673a2c),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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

    if (psetState.error != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
        ) {
            Text(
                text = psetState.error,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PsetDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun LiquidPasteSignedPsetDialog(
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
                    text = stringResource(R.string.loc_pset_paste_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loc_pset_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_pset_paste_placeholder),
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
                            focusedBorderColor = LiquidTeal,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LiquidTeal,
                        ),
                )
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
                                containerColor = LiquidTeal,
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

private fun formatLiquidPsetAmount(
    amount: Long,
    unit: String,
): String {
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
    return "$formatted $unit"
}
