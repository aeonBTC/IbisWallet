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
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceLimits
import github.aeonbtc.ibiswallet.data.model.LightningInvoiceState
import github.aeonbtc.ibiswallet.nfc.NdefHostApduService
import github.aeonbtc.ibiswallet.nfc.NfcRuntimeStatus
import github.aeonbtc.ibiswallet.nfc.NfcShareUiState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.components.ReceiveActionButton
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.data.model.LiquidAsset
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.getNfcAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

@Composable
fun LiquidReceiveScreen(
    liquidAddress: String? = null,
    currentAddressLabel: String? = null,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean = false,
    boltzEnabled: Boolean = true,
    lightningInvoiceState: LightningInvoiceState = LightningInvoiceState.Idle,
    lightningInvoiceLimits: LightningInvoiceLimits? = null,
    selectedAssetId: String? = null,
    onEnsureLiquidAddress: () -> Unit = {},
    onGenerateLiquidAddress: () -> Unit = {},
    onSaveLiquidAddressLabel: (String, String) -> Unit = { _, _ -> },
    onShowAllAddresses: () -> Unit = {},
    onShowAllUtxos: () -> Unit = {},
    onCreateLightningInvoice: (Long, String?, Boolean) -> Unit = { _, _, _ -> },
    onWarmLightningInvoice: () -> Unit = {},
    onFetchLightningLimits: () -> Unit = {},
    onResetLightningInvoice: () -> Unit = {},
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS

    var liquidReceiveTab by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var showAmountField by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    var showLabelField by remember { mutableStateOf(false) }
    var labelText by remember { mutableStateOf("") }
    var embedLabelInQr by remember(liquidAddress) { mutableStateOf(false) }
    var lightningAmountText by remember { mutableStateOf("") }
    var lightningIsUsdMode by remember { mutableStateOf(false) }
    var lightningShowLabelField by remember { mutableStateOf(false) }
    var lightningLabelText by remember { mutableStateOf("") }
    var lightningEmbedLabelInQr by remember { mutableStateOf(false) }
    var liquidQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val amountBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAmountField, "liquid_receive_amount")
    val labelBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showLabelField, "liquid_receive_label")

    val amountInSats =
        remember(amountText, useSats, isUsdMode, btcPrice) {
            if (amountText.isEmpty()) {
                null
            } else if (isUsdMode && btcPrice != null && btcPrice > 0) {
                amountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).toLong()
                }
            } else if (useSats) {
                amountText.toLongOrNull()
            } else {
                amountText.toDoubleOrNull()?.let { lbtc ->
                    (lbtc * 100_000_000).toLong()
                }
            }
        }

    val lightningAmountInSats =
        remember(lightningAmountText, useSats, lightningIsUsdMode, btcPrice) {
            if (lightningAmountText.isEmpty()) {
                null
            } else if (lightningIsUsdMode && btcPrice != null && btcPrice > 0) {
                lightningAmountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).toLong()
                }
            } else if (useSats) {
                lightningAmountText.toLongOrNull()
            } else {
                lightningAmountText.toDoubleOrNull()?.let { lbtc ->
                    (lbtc * 100_000_000).toLong()
                }
            }
        }

    fun convertAmountForUsdToggle(
        currentText: String,
        currentAmountSats: Long?,
        currentlyUsdMode: Boolean,
    ): String {
        if (currentText.isEmpty()) return currentText
        val sats = currentAmountSats ?: return currentText
        if (btcPrice == null || btcPrice <= 0 || sats <= 0) return currentText
        return if (!currentlyUsdMode) {
            val usdValue = (sats / 100_000_000.0) * btcPrice
            String.format(Locale.US, "%.2f", usdValue)
        } else if (useSats) {
            sats.toString()
        } else {
            String.format(Locale.US, "%.8f", sats / 100_000_000.0)
        }
    }

    val isNonLbtcAsset = selectedAssetId != null &&
        selectedAssetId != LiquidAsset.LBTC_ASSET_ID

    val liquidRequestContent =
        remember(liquidAddress, amountInSats, showAmountField, labelText, showLabelField, embedLabelInQr, selectedAssetId) {
            liquidAddress?.let { address ->
                val liquidAmountSats = amountInSats?.takeIf { showAmountField && it > 0 }
                val label = labelText.trim().takeIf { showLabelField && embedLabelInQr && it.isNotBlank() }
                if (liquidAmountSats != null || label != null || isNonLbtcAsset) {
                    val params = mutableListOf<String>()
                    liquidAmountSats?.let {
                        val liquidAmount = it.toDouble() / 100_000_000.0
                        params += "amount=${String.format(Locale.US, "%.8f", liquidAmount)}"
                    }
                    label?.let {
                        params += "label=${URLEncoder.encode(it, "UTF-8")}"
                    }
                    if (isNonLbtcAsset) {
                        params += "assetid=$selectedAssetId"
                    }
                    "liquidnetwork:$address?${params.joinToString("&")}"
                } else {
                    address
                }
            }
        }

    val activeQrContent =
        remember(liquidReceiveTab, liquidRequestContent, lightningInvoiceState) {
            when (liquidReceiveTab) {
                1 ->
                    if (lightningInvoiceState is LightningInvoiceState.Ready) {
                        lightningInvoiceState.invoice
                    } else {
                        null
                    }
                0 -> liquidRequestContent
                else -> null
            }
        }

    LaunchedEffect(activeQrContent) {
        liquidQrBitmap =
            activeQrContent?.let { content ->
                withContext(Dispatchers.Default) {
                    generateQrBitmap(content)
                }
            }
    }

    LaunchedEffect(liquidAddress) {
        if (liquidAddress == null) {
            onEnsureLiquidAddress()
        }
    }

    LaunchedEffect(liquidAddress, currentAddressLabel) {
        val savedAddressLabel = currentAddressLabel.orEmpty()
        labelText = savedAddressLabel
        if (savedAddressLabel.isNotBlank()) {
            showLabelField = true
        }
    }

    LaunchedEffect(liquidReceiveTab, boltzEnabled) {
        if (liquidReceiveTab == 1 && boltzEnabled) {
            onWarmLightningInvoice()
        }
    }

    LaunchedEffect(liquidReceiveTab, lightningInvoiceLimits) {
        if (liquidReceiveTab == 1 && boltzEnabled && lightningInvoiceLimits == null) {
            onFetchLightningLimits()
        }
    }

    LaunchedEffect(boltzEnabled) {
        if (!boltzEnabled && liquidReceiveTab == 1) {
            liquidReceiveTab = 0
            onResetLightningInvoice()
        }
    }

    LaunchedEffect(liquidReceiveTab, lightningInvoiceState) {
        if (liquidReceiveTab == 1 && lightningInvoiceState is LightningInvoiceState.Claimed) {
            onResetLightningInvoice()
        }
    }

    val mainActivity = context as? MainActivity
    val nfcShareOwner = remember { Any() }
    val nfcAvailable = context.getNfcAvailability().canBroadcast
    val hasNfcSharePayload = nfcAvailable && activeQrContent != null
    val wantsLightningNfcBadge = liquidReceiveTab == 1 && nfcAvailable
    val wantsPreferredHceService = hasNfcSharePayload || wantsLightningNfcBadge
    val nfcShareState by NfcRuntimeStatus.shareState.collectAsState()
    DisposableEffect(mainActivity, wantsPreferredHceService) {
        if (mainActivity != null && wantsPreferredHceService) {
            mainActivity.requestPreferredHceService(nfcShareOwner)
        }
        onDispose {
            mainActivity?.releasePreferredHceService(nfcShareOwner)
        }
    }
    val isNfcBroadcasting = hasNfcSharePayload && mainActivity?.isPreferredHceServiceActive == true
    DisposableEffect(activeQrContent, nfcAvailable) {
        if (nfcAvailable && activeQrContent != null) {
            NdefHostApduService.setNdefPayload(activeQrContent)
        }
        onDispose {
            NdefHostApduService.setNdefPayload(null)
        }
    }

    if (showEnlargedQr && liquidQrBitmap != null) {
        Dialog(onDismissRequest = { showEnlargedQr = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showEnlargedQr = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(16.dp),
                    ) {
                        Image(
                            bitmap = liquidQrBitmap!!.asImageBitmap(),
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (liquidReceiveTab == 0) "Receive Liquid" else "Receive Lightning",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isNfcBroadcasting || wantsLightningNfcBadge) {
                    val nfcStatusLabel =
                        when (nfcShareState) {
                            NfcShareUiState.Inactive,
                            NfcShareUiState.Ready,
                            -> stringResource(R.string.nfc_status_share_ready)
                            NfcShareUiState.Sharing -> stringResource(R.string.nfc_status_sharing)
                        }
                    val nfcStatusColor =
                        if (nfcShareState == NfcShareUiState.Sharing) {
                            LiquidTeal
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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Liquid" to 0, "Lightning" to 1).forEach { (label, idx) ->
                        val isSelected = liquidReceiveTab == idx
                        val selectedColor = if (idx == 1) LightningYellow else LiquidTeal
                        val inactiveTint = if (idx == 1) LightningYellow.copy(alpha = 0.12f) else LiquidTeal.copy(alpha = 0.12f)
                        val isEnabled = idx == 0 || boltzEnabled
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) selectedColor
                                    else inactiveTint,
                                )
                                .clickable(enabled = isEnabled) {
                                    liquidReceiveTab = idx
                                    if (idx == 0) {
                                        onResetLightningInvoice()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color =
                                    if (!isEnabled) {
                                        TextSecondary.copy(alpha = 0.4f)
                                    } else if (isSelected) {
                                        TextPrimary
                                    } else {
                                        TextSecondary
                                    },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (liquidReceiveTab == 0) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable(enabled = liquidAddress != null) { showEnlargedQr = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (liquidQrBitmap != null && liquidAddress != null) {
                            Image(
                                bitmap = liquidQrBitmap!!.asImageBitmap(),
                                contentDescription = "Liquid QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                text = if (liquidAddress != null) "Generating..." else "No wallet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = formatLiquidReceiveAddress(liquidAddress),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (liquidAddress != null) MaterialTheme.colorScheme.onBackground else TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReceiveActionButton(
                            text = "Copy",
                            icon = Icons.Default.ContentCopy,
                            tint = LiquidTeal,
                            onClick = {
                                liquidRequestContent?.let { content ->
                                    SecureClipboard.copyAndScheduleClear(context, "Liquid Address", content)
                                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = liquidRequestContent != null,
                            iconSize = 17.dp,
                        )
                        ReceiveActionButton(
                            text = "New",
                            icon = Icons.Default.Refresh,
                            tint = LiquidTeal,
                            onClick = onGenerateLiquidAddress,
                            iconSize = 20.dp,
                        )
                        ReceiveActionButton(
                            text = "Share",
                            icon = Icons.Default.Share,
                            tint = LiquidTeal,
                            onClick = {
                                liquidRequestContent?.let { content ->
                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, content)
                                        }
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share receive request"),
                                        )
                                    }.onFailure {
                                        Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = liquidRequestContent != null,
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAmountField = !showAmountField }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Amount",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            SquareToggle(
                                checked = showAmountField,
                                onCheckedChange = { showAmountField = it },
                                checkedColor = LiquidTeal,
                            )
                        }

                        AnimatedVisibility(visible = showAmountField) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(amountBringIntoViewRequester),
                            ) {
                                Row(
                                    modifier = Modifier
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
                                    if (btcPrice != null && btcPrice > 0) {
                                        Card(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    amountText = convertAmountForUsdToggle(
                                                        currentText = amountText,
                                                        currentAmountSats = amountInSats,
                                                        currentlyUsdMode = isUsdMode,
                                                    )
                                                    isUsdMode = !isUsdMode
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isUsdMode) LiquidTeal.copy(alpha = 0.15f) else DarkSurface,
                                            ),
                                            border = BorderStroke(1.dp, if (isUsdMode) LiquidTeal else BorderColor),
                                        ) {
                                            Text(
                                                text = fiatCurrency,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isUsdMode) LiquidTeal else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }

                                val conversionText =
                                    if (amountText.isNotEmpty() && amountInSats != null && amountInSats > 0 && btcPrice != null && btcPrice > 0) {
                                        if (privacyMode) {
                                            "≈ ****"
                                        } else if (isUsdMode) {
                                            "≈ ${formatLiquidAmountForReceive(amountInSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}"
                                        } else {
                                            val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                                            "≈ ${formatFiat(usdValue, fiatCurrency)}"
                                        }
                                    } else {
                                        null
                                    }

                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { input ->
                                        when {
                                            isUsdMode -> {
                                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                    amountText = input
                                                }
                                            }
                                            useSats -> {
                                                amountText = input.filter { c -> c.isDigit() }
                                            }
                                            else -> {
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
                                    suffix =
                                        if (conversionText != null) {
                                            {
                                                Text(
                                                    text = conversionText,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = LiquidTeal,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (useSats && !isUsdMode) KeyboardType.Number else KeyboardType.Decimal,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LiquidTeal,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = LiquidTeal,
                                    ),
                                    enabled = liquidAddress != null,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLabelField = !showLabelField }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Label",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            SquareToggle(
                                checked = showLabelField,
                                onCheckedChange = { showLabelField = it },
                                checkedColor = LiquidTeal,
                            )
                        }

                        AnimatedVisibility(visible = showLabelField) {
                            Column(modifier = Modifier.bringIntoViewRequester(labelBringIntoViewRequester)) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = { labelText = it },
                                    placeholder = { Text("e.g. Payment from Alice") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LiquidTeal,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = LiquidTeal,
                                    ),
                                    enabled = liquidAddress != null,
                                    trailingIcon = {
                                        if (labelText.isNotEmpty() && liquidAddress != null) {
                                            TextButton(
                                                onClick = {
                                                    onSaveLiquidAddressLabel(liquidAddress, labelText)
                                                    Toast.makeText(context, "Label saved", Toast.LENGTH_SHORT).show()
                                                },
                                            ) {
                                                Text("Save", color = LiquidTeal)
                                            }
                                        }
                                    },
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Embed in QR",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    SquareToggle(
                                        checked = embedLabelInQr,
                                        onCheckedChange = { embedLabelInQr = it },
                                        enabled = liquidAddress != null,
                                        checkedColor = LiquidTeal,
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
                } else {
                    when (lightningInvoiceState) {
                        is LightningInvoiceState.Generating -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = LiquidTeal,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Creating invoice...", color = TextSecondary)
                        }

                        is LightningInvoiceState.Ready -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Invoice",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Lightning receive via Boltz",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(252.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .clickable(enabled = liquidQrBitmap != null) { showEnlargedQr = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (liquidQrBitmap != null) {
                                            Image(
                                                bitmap = liquidQrBitmap!!.asImageBitmap(),
                                                contentDescription = "Lightning Invoice QR",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = lightningInvoiceState.invoice,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy invoice",
                                            tint = LightningYellow,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    SecureClipboard.copyAndScheduleClear(
                                                        context,
                                                        "Lightning Invoice",
                                                        lightningInvoiceState.invoice,
                                                    )
                                                    Toast.makeText(context, "Invoice copied", Toast.LENGTH_SHORT).show()
                                                },
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = "Amount",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text =
                                                "${formatLiquidAmountForReceive(lightningInvoiceState.amountSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = LiquidTeal,
                                            fontWeight = FontWeight.SemiBold,
                                        )

                                        if (lightningLabelText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Label",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextSecondary,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lightningLabelText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is LightningInvoiceState.Claimed -> {
                            Text(
                                text = "Payment Received!",
                                style = MaterialTheme.typography.titleMedium,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (lightningInvoiceState.txid.isNotBlank()) {
                                Text(
                                    text = "TX: ${lightningInvoiceState.txid.take(16)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    lightningAmountText = ""
                                    lightningLabelText = ""
                                    lightningShowLabelField = false
                                    lightningEmbedLabelInQr = false
                                    onResetLightningInvoice()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LightningYellow,
                                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                                ),
                            ) {
                                Text(
                                    text = "New Invoice",
                                    color = DarkSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }

                        is LightningInvoiceState.Failed -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Failed: ${lightningInvoiceState.error}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LightningInvoiceForm(
                                    amountText = lightningAmountText,
                                    amountInSats = lightningAmountInSats,
                                    useSats = useSats,
                                    isUsdMode = lightningIsUsdMode,
                                    btcPrice = btcPrice,
                                    fiatCurrency = fiatCurrency,
                                    privacyMode = privacyMode,
                                    lightningInvoiceLimits = lightningInvoiceLimits,
                                    labelText = lightningLabelText,
                                    showLabelField = lightningShowLabelField,
                                    onAmountTextChange = { lightningAmountText = it },
                                    onUsdModeChange = {
                                        lightningAmountText = convertAmountForUsdToggle(
                                            currentText = lightningAmountText,
                                            currentAmountSats = lightningAmountInSats,
                                            currentlyUsdMode = lightningIsUsdMode,
                                        )
                                        lightningIsUsdMode = it
                                    },
                                    onShowLabelFieldChange = { lightningShowLabelField = it },
                                    onLabelTextChange = { lightningLabelText = it },
                                    embedLabelInQr = lightningEmbedLabelInQr,
                                    onEmbedLabelInQrChange = { lightningEmbedLabelInQr = it },
                                    onToggleDenomination = onToggleDenomination,
                                )
                            }
                        }

                        is LightningInvoiceState.Idle -> {
                            LightningInvoiceForm(
                                amountText = lightningAmountText,
                                amountInSats = lightningAmountInSats,
                                useSats = useSats,
                                isUsdMode = lightningIsUsdMode,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                privacyMode = privacyMode,
                                lightningInvoiceLimits = lightningInvoiceLimits,
                                labelText = lightningLabelText,
                                showLabelField = lightningShowLabelField,
                                onAmountTextChange = { lightningAmountText = it },
                                onUsdModeChange = {
                                    lightningAmountText = convertAmountForUsdToggle(
                                        currentText = lightningAmountText,
                                        currentAmountSats = lightningAmountInSats,
                                        currentlyUsdMode = lightningIsUsdMode,
                                    )
                                    lightningIsUsdMode = it
                                },
                                onShowLabelFieldChange = { lightningShowLabelField = it },
                                onLabelTextChange = { lightningLabelText = it },
                                embedLabelInQr = lightningEmbedLabelInQr,
                                onEmbedLabelInQrChange = { lightningEmbedLabelInQr = it },
                                onToggleDenomination = onToggleDenomination,
                            )
                        }
                    }
                }
            }
        }

        if (liquidReceiveTab == 1 &&
            lightningInvoiceState is LightningInvoiceState.Ready
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    lightningAmountText = ""
                    lightningLabelText = ""
                    lightningShowLabelField = false
                    lightningEmbedLabelInQr = false
                    onResetLightningInvoice()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightningYellow,
                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = "New Invoice",
                    color = DarkSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (liquidReceiveTab == 1 &&
            (lightningInvoiceState is LightningInvoiceState.Idle || lightningInvoiceState is LightningInvoiceState.Failed)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    lightningAmountInSats?.let { amount ->
                        onCreateLightningInvoice(
                            amount,
                            lightningLabelText.trim().takeIf { it.isNotEmpty() },
                            lightningEmbedLabelInQr,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = lightningAmountInSats?.let { amount ->
                    amount > 0 &&
                        (lightningInvoiceLimits == null ||
                            if (lightningInvoiceLimits.maximumSats <= 0) {
                                amount >= lightningInvoiceLimits.minimumSats
                            } else {
                                amount in lightningInvoiceLimits.minimumSats..lightningInvoiceLimits.maximumSats
                            })
                } == true,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightningYellow,
                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = "Generate Invoice",
                    color = DarkSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (liquidReceiveTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IbisButton(
                    onClick = onShowAllAddresses,
                    modifier = Modifier.weight(1f),
                    enabled = liquidAddress != null,
                ) {
                    Text(
                        text = "All Addresses",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                IbisButton(
                    onClick = onShowAllUtxos,
                    modifier = Modifier.weight(1f),
                    enabled = liquidAddress != null,
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
}

@Composable
private fun LightningInvoiceForm(
    amountText: String,
    amountInSats: Long?,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    lightningInvoiceLimits: LightningInvoiceLimits?,
    labelText: String,
    showLabelField: Boolean,
    embedLabelInQr: Boolean,
    onAmountTextChange: (String) -> Unit,
    onUsdModeChange: (Boolean) -> Unit,
    onShowLabelFieldChange: (Boolean) -> Unit,
    onLabelTextChange: (String) -> Unit,
    onEmbedLabelInQrChange: (Boolean) -> Unit,
    onToggleDenomination: () -> Unit = {},
) {
    val context = LocalContext.current
    val labelBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showLabelField, "lightning_invoice_label")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Create Invoice",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
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
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onUsdModeChange(!isUsdMode) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUsdMode) LiquidTeal.copy(alpha = 0.15f) else DarkCard,
                        ),
                        border = BorderStroke(1.dp, if (isUsdMode) LiquidTeal else BorderColor),
                    ) {
                        Text(
                            text = fiatCurrency,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUsdMode) LiquidTeal else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            val conversionText =
                if (amountText.isNotEmpty() && amountInSats != null && amountInSats > 0 && btcPrice != null && btcPrice > 0) {
                    if (privacyMode) {
                        "≈ ****"
                    } else if (isUsdMode) {
                        "≈ ${formatLiquidAmountForReceive(amountInSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}"
                    } else {
                        val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                        "≈ ${formatFiat(usdValue, fiatCurrency)}"
                    }
                } else {
                    null
                }

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    when {
                        isUsdMode -> {
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                onAmountTextChange(input)
                            }
                        }
                        useSats -> onAmountTextChange(input.filter { c -> c.isDigit() })
                        else -> {
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,8}$"))) {
                                onAmountTextChange(input)
                            }
                        }
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
                leadingIcon =
                    if (isUsdMode) {
                        { Text(fiatCurrency, color = TextSecondary) }
                    } else {
                        null
                    },
                suffix =
                    if (conversionText != null) {
                        {
                            Text(
                                text = conversionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = LiquidTeal,
                            )
                        }
                    } else {
                        null
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useSats && !isUsdMode) KeyboardType.Number else KeyboardType.Decimal,
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiquidTeal,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = LiquidTeal,
                ),
            )

            Text(
                text = lightningInvoiceLimits?.let { limits ->
                    val maxText = if (limits.maximumSats > 0) {
                        "  |  Max ${formatLiquidAmountForReceive(limits.maximumSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}"
                    } else {
                        ""
                    }
                    "Min ${formatLiquidAmountForReceive(limits.minimumSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}$maxText"
                } ?: "Fetching limits...",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )

            if (lightningInvoiceLimits != null &&
                amountText.isNotEmpty() &&
                (amountInSats == null || amountInSats < lightningInvoiceLimits.minimumSats)
            ) {
                Text(
                    text = "Enter at least ${formatLiquidAmountForReceive(lightningInvoiceLimits.minimumSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            } else if (lightningInvoiceLimits != null &&
                amountText.isNotEmpty() &&
                lightningInvoiceLimits.maximumSats > 0 &&
                amountInSats != null &&
                amountInSats > lightningInvoiceLimits.maximumSats
            ) {
                Text(
                    text = "Enter no more than ${formatLiquidAmountForReceive(lightningInvoiceLimits.maximumSats.toULong(), useSats)} ${liquidDisplayUnit(useSats)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShowLabelFieldChange(!showLabelField) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Label",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SquareToggle(
                    checked = showLabelField,
                    onCheckedChange = { onShowLabelFieldChange(it) },
                    checkedColor = LightningYellow,
                )
            }

            AnimatedVisibility(visible = showLabelField) {
                Column(modifier = Modifier.bringIntoViewRequester(labelBringIntoViewRequester)) {
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = onLabelTextChange,
                        placeholder = { Text("e.g. Lightning sale") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LightningYellow,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = LightningYellow,
                        ),
                        trailingIcon = {
                            if (labelText.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        onLabelTextChange(labelText.trim())
                                        Toast.makeText(context, "Label will be saved with invoice", Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Text("Save", color = LightningYellow)
                                }
                            }
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Embed in invoice",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        SquareToggle(
                            checked = embedLabelInQr,
                            onCheckedChange = onEmbedLabelInQrChange,
                            checkedColor = LightningYellow,
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

private fun formatLiquidAmountForReceive(
    sats: ULong,
    useSats: Boolean,
): String {
    return if (useSats) {
        java.text.NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())
    } else {
        val lbtc = sats.toDouble() / 100_000_000.0
        String.format(Locale.US, "%.8f", lbtc)
    }
}

private fun liquidDisplayUnit(useSats: Boolean): String = if (useSats) "sats" else "BTC"


private fun formatLiquidReceiveAddress(address: String?): String {
    if (address == null) return "No wallet"
    val edgeCharacters = 10
    val minimumLengthToShorten = edgeCharacters * 2
    if (address.length <= minimumLengthToShorten) return address
    return "${address.take(edgeCharacters)}...${address.takeLast(edgeCharacters)}"
}
