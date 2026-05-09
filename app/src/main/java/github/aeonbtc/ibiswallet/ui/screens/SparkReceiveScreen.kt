package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.SparkReceiveKind
import github.aeonbtc.ibiswallet.data.model.SparkReceiveState
import github.aeonbtc.ibiswallet.ui.components.AmountLabel
import github.aeonbtc.ibiswallet.ui.components.ReceiveActionButton
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TextTertiary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import androidx.compose.material3.Text

@Composable
fun SparkReceiveScreen(
    receiveState: SparkReceiveState,
    denomination: String,
    btcPrice: Double? = null,
    fiatCurrency: String = SecureStorage.DEFAULT_PRICE_CURRENCY,
    privacyMode: Boolean,
    onReceive: (SparkReceiveKind, Long?, String, Boolean) -> Unit,
    onSaveAddressLabel: (String, String) -> Unit = { _, _ -> },
    onResetReceive: () -> Unit,
    onToggleDenomination: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    var receiveTab by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var showAmountField by remember { mutableStateOf(false) }
    var showLabelField by remember { mutableStateOf(false) }
    var embedLabelInQr by remember { mutableStateOf(false) }
    var isUsdMode by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val amountBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAmountField, "spark_receive_amount")
    val labelBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showLabelField, "spark_receive_label")

    val activeKind =
        when (receiveTab) {
            1 -> SparkReceiveKind.BOLT11_INVOICE
            2 -> SparkReceiveKind.BITCOIN_ADDRESS
            else -> SparkReceiveKind.SPARK_ADDRESS
        }
    val amountSats =
        remember(amountText, useSats, isUsdMode, btcPrice) {
            if (amountText.isEmpty()) {
                null
            } else if (isUsdMode && btcPrice != null && btcPrice > 0) {
                amountText.toDoubleOrNull()?.let { usd ->
                    ((usd / btcPrice) * 100_000_000).toLong()
                }
            } else if (useSats) {
                amountText.filter { it.isDigit() }.toLongOrNull()
            } else {
                amountText.toDoubleOrNull()?.let { btc ->
                    (btc * 100_000_000).toLong()
                }
            }
        }
    val requestedAmountSats = amountSats?.takeIf { showAmountField && it > 0 }
    val embeddedLabel = descriptionText.trim().takeIf { showLabelField && embedLabelInQr && it.isNotBlank() }
    val isLightningMode = activeKind == SparkReceiveKind.BOLT11_INVOICE
    val requestKind =
        when {
            activeKind == SparkReceiveKind.SPARK_ADDRESS &&
                (requestedAmountSats != null || embeddedLabel != null) -> SparkReceiveKind.SPARK_INVOICE
            else -> activeKind
        }
    val ready = receiveState as? SparkReceiveState.Ready
    val baseRequestText = ready?.takeIf { it.kind == requestKind }?.paymentRequest
    val requestText =
        when (activeKind) {
            SparkReceiveKind.BITCOIN_ADDRESS ->
                buildSparkBitcoinRequest(
                    address = baseRequestText,
                    amountSats = requestedAmountSats,
                    label = embeddedLabel,
                )
            else -> baseRequestText
        }
    val currentQrBitmap = qrBitmap
    val displayText =
        when (activeKind) {
            SparkReceiveKind.BITCOIN_ADDRESS ->
                if (privacyMode && requestText != null) {
                    "****"
                } else {
                    formatSparkReceiveText(baseRequestText)
                }
            else ->
                when {
                    privacyMode && requestText != null -> "****"
                    requestText != null -> formatSparkReceiveText(requestText)
                    else -> "No request generated"
                }
        }
    val screenTitle =
        when (activeKind) {
            SparkReceiveKind.SPARK_ADDRESS -> "Receive Spark"
            SparkReceiveKind.BOLT11_INVOICE -> "Receive Lightning"
            SparkReceiveKind.BITCOIN_ADDRESS -> "Receive Bitcoin"
            SparkReceiveKind.SPARK_INVOICE -> "Receive Spark"
        }
    val lightningRequestDescription = descriptionText.trim().takeIf { showLabelField && embedLabelInQr && it.isNotBlank() }.orEmpty()
    val lightningAmountSats = amountSats?.takeIf { isLightningMode && it > 0 }
    val isLightningReady = isLightningMode && requestText != null
    val isLightningLoading = isLightningMode && receiveState is SparkReceiveState.Loading
    val lightningError = if (isLightningMode && receiveState is SparkReceiveState.Error) receiveState.message else null
    val canGenerateNewRequest =
        receiveState !is SparkReceiveState.Loading &&
            requestKind != SparkReceiveKind.SPARK_ADDRESS
    val copySparkRequest: () -> Unit = {
        requestText?.let {
            SecureClipboard.copyAndScheduleClear(context, it)
            Toast.makeText(context, "Request copied", Toast.LENGTH_SHORT).show()
        }
    }
    val shareRequestChooserTitle = stringResource(R.string.loc_ebbd9745)
    val labelTargetRequest = baseRequestText ?: requestText
    // Match the other receive screens by showing a default request immediately, and
    // switch Spark QR generation to invoice mode when amount/label must be embedded.
    LaunchedEffect(requestKind, requestedAmountSats, embeddedLabel) {
        when (requestKind) {
            SparkReceiveKind.BOLT11_INVOICE -> Unit
            SparkReceiveKind.BITCOIN_ADDRESS,
            SparkReceiveKind.SPARK_ADDRESS,
            -> onReceive(requestKind, null, "", false)
            SparkReceiveKind.SPARK_INVOICE -> onReceive(requestKind, requestedAmountSats, embeddedLabel.orEmpty(), false)
        }
    }

    LaunchedEffect(requestText, privacyMode) {
        qrBitmap =
            requestText
                ?.takeUnless { privacyMode }
                ?.let { content ->
                    withContext(Dispatchers.Default) {
                        generateQrBitmap(content)
                    }
                }
    }

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        text = stringResource(R.string.loc_e1041b50),
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
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SparkReceiveTab("Spark", receiveTab == 0, SparkPurple, Modifier.weight(1f)) {
                        receiveTab = 0
                        onResetReceive()
                    }
                    SparkReceiveTab("Lightning", receiveTab == 1, LightningYellow, Modifier.weight(1f)) {
                        receiveTab = 1
                        onResetReceive()
                    }
                    SparkReceiveTab("On-chain", receiveTab == 2, BitcoinOrange, Modifier.weight(1f)) {
                        receiveTab = 2
                        onResetReceive()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLightningMode && !isLightningReady) {
                    when {
                        isLightningLoading -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = LightningYellow,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.loc_de610209), color = TextSecondary)
                        }

                        else -> {
                            lightningError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SparkLightningInvoiceForm(
                                amountText = amountText,
                                amountInSats = amountSats,
                                useSats = useSats,
                                isUsdMode = isUsdMode,
                                btcPrice = btcPrice,
                                fiatCurrency = fiatCurrency,
                                privacyMode = privacyMode,
                                labelText = descriptionText,
                                showLabelField = showLabelField,
                                embedLabelInInvoice = embedLabelInQr,
                                onAmountTextChange = { amountText = it },
                                onUsdModeChange = {
                                    amountText =
                                        convertSparkAmountForUsdToggle(
                                            currentText = amountText,
                                            currentAmountSats = amountSats,
                                            currentlyUsdMode = isUsdMode,
                                            useSats = useSats,
                                            btcPrice = btcPrice ?: 0.0,
                                        )
                                    isUsdMode = it
                                },
                                onShowLabelFieldChange = { showLabelField = it },
                                onLabelTextChange = { descriptionText = it },
                                onEmbedLabelInInvoiceChange = { embedLabelInQr = it },
                                onToggleDenomination = onToggleDenomination,
                            )
                        }
                    }
                } else if (isLightningMode && isLightningReady) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_5fd82ed8),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(252.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .combinedClickable(
                                        enabled = true,
                                        onClick = { showEnlargedQr = true },
                                        onLongClick = copySparkRequest,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (currentQrBitmap != null) {
                                    Image(
                                        bitmap = currentQrBitmap.asImageBitmap(),
                                        contentDescription = "Lightning Invoice QR",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = LightningYellow,
                                        strokeWidth = 3.dp,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatSparkInvoicePreview(requestText),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .weight(1f, fill = false)
                                            .clickable(onClick = copySparkRequest),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.loc_a1329beb),
                                    tint = LightningYellow,
                                    modifier =
                                        Modifier
                                            .size(18.dp)
                                            .clickable(onClick = copySparkRequest),
                                )
                            }

                            lightningAmountSats?.let { amount ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(R.string.loc_890d7574),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatSparkAmountForReceive(amount, useSats)} ${sparkDisplayUnit(useSats)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SparkPurple,
                                        fontWeight = FontWeight.SemiBold,
                                    )

                                    descriptionText.trim().takeIf { it.isNotBlank() }?.let { label ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.loc_cf667fec),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
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
                } else if (qrBitmap != null && requestText != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .combinedClickable(
                                enabled = true,
                                onClick = { showEnlargedQr = true },
                                onLongClick = copySparkRequest,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Receive QR",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (receiveState is SparkReceiveState.Loading) {
                            CircularProgressIndicator(color = SparkPurple)
                        } else {
                            Text(
                                text =
                                    when {
                                        privacyMode -> "Hidden"
                                        activeKind == SparkReceiveKind.BOLT11_INVOICE -> "Generate request"
                                        else -> "Generating..."
                                    },
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                if (!isLightningMode) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (displayText != "No request generated") MaterialTheme.colorScheme.onBackground else TextSecondary,
                        maxLines = if (activeKind == SparkReceiveKind.BOLT11_INVOICE) 1 else 2,
                        overflow = if (activeKind == SparkReceiveKind.BOLT11_INVOICE) TextOverflow.Ellipsis else TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = requestText != null, onClick = copySparkRequest),
                        textAlign = TextAlign.Center,
                    )

                    receiveState.errorMessage()?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!isLightningMode) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReceiveActionButton(
                            text = stringResource(R.string.loc_ed8814bc),
                            icon = Icons.Default.ContentCopy,
                            tint = SparkPurple,
                            enabled = requestText != null,
                            onClick = copySparkRequest,
                            iconSize = 17.dp,
                        )
                        ReceiveActionButton(
                            text = stringResource(R.string.loc_53ae02a5),
                            icon = Icons.Default.Refresh,
                            tint = SparkPurple,
                            enabled = canGenerateNewRequest,
                            onClick = { onReceive(requestKind, requestedAmountSats, embeddedLabel.orEmpty(), true) },
                            iconSize = 20.dp,
                        )
                        ReceiveActionButton(
                            text = stringResource(R.string.loc_2ec7b25e),
                            icon = Icons.Default.Share,
                            tint = SparkPurple,
                            enabled = requestText != null,
                            onClick = {
                                requestText?.let {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, it)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            shareRequestChooserTitle,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
                if (!isLightningMode) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            checkedColor = SparkPurple,
                        )
                    }

                    AnimatedVisibility(visible = showAmountField) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(amountBringIntoViewRequester),
                        ) {
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
                                if (btcPrice != null && btcPrice > 0) {
                                    Card(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    amountText =
                                                        convertSparkAmountForUsdToggle(
                                                            currentText = amountText,
                                                            currentAmountSats = amountSats,
                                                            currentlyUsdMode = isUsdMode,
                                                            useSats = useSats,
                                                            btcPrice = btcPrice,
                                                        )
                                                    isUsdMode = !isUsdMode
                                                },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    if (isUsdMode) {
                                                        SparkPurple.copy(alpha = 0.15f)
                                                    } else {
                                                        DarkSurface
                                                    },
                                            ),
                                        border = BorderStroke(1.dp, if (isUsdMode) SparkPurple else BorderColor),
                                    ) {
                                        Text(
                                            text = fiatCurrency,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isUsdMode) SparkPurple else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }

                            val conversionText =
                                if (amountText.isNotEmpty() && amountSats != null && amountSats > 0 && btcPrice != null && btcPrice > 0) {
                                    if (privacyMode) {
                                        "≈ ****"
                                    } else if (isUsdMode) {
                                        "≈ ${formatSparkAmountForReceive(amountSats, useSats)} ${sparkDisplayUnit(useSats)}"
                                    } else {
                                        val usdValue = (amountSats / 100_000_000.0) * btcPrice
                                        "≈ ${formatSparkFiat(usdValue, fiatCurrency)}"
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
                                                color = SparkPurple,
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
                                colors = sparkTextFieldColors(),
                            )
                        }
                    }

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
                            text = stringResource(R.string.loc_cf667fec),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        SquareToggle(
                            checked = showLabelField,
                            onCheckedChange = { showLabelField = it },
                            checkedColor = SparkPurple,
                        )
                    }

                    AnimatedVisibility(visible = showLabelField) {
                        Column(modifier = Modifier.bringIntoViewRequester(labelBringIntoViewRequester)) {
                            OutlinedTextField(
                                value = descriptionText,
                                onValueChange = { descriptionText = it },
                                placeholder = { Text(stringResource(R.string.loc_9873e592), color = TextSecondary.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = sparkTextFieldColors(),
                                trailingIcon = {
                                    if (descriptionText.isNotBlank() && labelTargetRequest != null) {
                                        TextButton(
                                            onClick = {
                                                onSaveAddressLabel(labelTargetRequest, descriptionText)
                                                Toast.makeText(context, "Label saved", Toast.LENGTH_SHORT).show()
                                            },
                                        ) {
                                            Text(stringResource(R.string.loc_f55495e0), color = SparkPurple)
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
                                    color = TextSecondary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                SquareToggle(
                                    checked = embedLabelInQr,
                                    onCheckedChange = { embedLabelInQr = it },
                                    checkedColor = SparkPurple,
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
        }

        if (isLightningMode && isLightningReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    amountText = ""
                    descriptionText = ""
                    showLabelField = false
                    embedLabelInQr = false
                    isUsdMode = false
                    onResetReceive()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightningYellow,
                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.loc_777771dd),
                    color = DarkSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (isLightningMode && !isLightningReady && !isLightningLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    amountSats?.let { amount ->
                        onReceive(SparkReceiveKind.BOLT11_INVOICE, amount, lightningRequestDescription, true)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                enabled = amountSats?.let { it > 0 } == true,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightningYellow,
                    disabledContainerColor = LightningYellow.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.loc_91a0293c),
                    color = DarkSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SparkReceiveTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val inactiveTint = selectedColor.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedColor else inactiveTint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun sparkTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SparkPurple,
        unfocusedBorderColor = BorderColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = SparkPurple,
    )

@Composable
private fun SparkLightningInvoiceForm(
    amountText: String,
    amountInSats: Long?,
    useSats: Boolean,
    isUsdMode: Boolean,
    btcPrice: Double?,
    fiatCurrency: String,
    privacyMode: Boolean,
    labelText: String,
    showLabelField: Boolean,
    embedLabelInInvoice: Boolean,
    onAmountTextChange: (String) -> Unit,
    onUsdModeChange: (Boolean) -> Unit,
    onShowLabelFieldChange: (Boolean) -> Unit,
    onLabelTextChange: (String) -> Unit,
    onEmbedLabelInInvoiceChange: (Boolean) -> Unit,
    onToggleDenomination: () -> Unit,
) {
    val context = LocalContext.current
    val labelBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showLabelField, "spark_lightning_invoice_label")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.loc_c5f4423b),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
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
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUsdModeChange(!isUsdMode) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUsdMode) SparkPurple.copy(alpha = 0.15f) else DarkCard,
                        ),
                        border = BorderStroke(1.dp, if (isUsdMode) SparkPurple else BorderColor),
                    ) {
                        Text(
                            text = fiatCurrency,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUsdMode) SparkPurple else TextSecondary,
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
                        "≈ ${formatSparkAmountForReceive(amountInSats, useSats)} ${sparkDisplayUnit(useSats)}"
                    } else {
                        val usdValue = (amountInSats / 100_000_000.0) * btcPrice
                        "≈ ${formatSparkFiat(usdValue, fiatCurrency)}"
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
                                color = SparkPurple,
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
                    focusedBorderColor = SparkPurple,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = SparkPurple,
                ),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShowLabelFieldChange(!showLabelField) }
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.loc_cf667fec),
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
                        placeholder = { Text(stringResource(R.string.loc_1c8c54ce)) },
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
                                    Text(stringResource(R.string.loc_f55495e0), color = LightningYellow)
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
                            text = stringResource(R.string.loc_982772df),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        SquareToggle(
                            checked = embedLabelInInvoice,
                            onCheckedChange = onEmbedLabelInInvoiceChange,
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

private fun convertSparkAmountForUsdToggle(
    currentText: String,
    currentAmountSats: Long?,
    currentlyUsdMode: Boolean,
    useSats: Boolean,
    btcPrice: Double,
): String {
    if (currentText.isBlank()) return ""
    val sats = currentAmountSats ?: return ""
    return if (!currentlyUsdMode) {
        val usdValue = (sats / 100_000_000.0) * btcPrice
        String.format(Locale.US, "%.2f", usdValue)
    } else if (useSats) {
        sats.toString()
    } else {
        String.format(Locale.US, "%.8f", sats / 100_000_000.0)
    }
}

private fun formatSparkAmountForReceive(
    sats: Long,
    useSats: Boolean,
): String {
    return if (useSats) {
        NumberFormat.getNumberInstance(Locale.US).format(sats)
    } else {
        String.format(Locale.US, "%.8f", sats / 100_000_000.0)
    }
}

private fun sparkDisplayUnit(useSats: Boolean): String = if (useSats) "sats" else "BTC"

private fun formatSparkFiat(
    amount: Double,
    fiatCurrency: String,
): String = "${fiatCurrency.uppercase(Locale.US)} ${String.format(Locale.US, "%.2f", amount)}"

private fun formatSparkReceiveText(requestText: String?): String {
    if (requestText == null) return "No request generated"
    val edgeCharacters = 10
    val minimumLengthToShorten = edgeCharacters * 2
    if (requestText.length <= minimumLengthToShorten) return requestText
    return "${requestText.take(edgeCharacters)}...${requestText.takeLast(edgeCharacters)}"
}

private fun formatSparkInvoicePreview(invoice: String?): String {
    if (invoice == null) return "No request generated"
    val edgeCharacters = 8
    val minimumLengthToShorten = edgeCharacters * 2
    if (invoice.length <= minimumLengthToShorten) return invoice
    return "${invoice.take(edgeCharacters)}...${invoice.takeLast(edgeCharacters)}"
}

private fun buildSparkBitcoinRequest(
    address: String?,
    amountSats: Long?,
    label: String?,
): String? {
    val baseAddress = address ?: return null
    if (amountSats == null && label == null) return baseAddress

    val params = mutableListOf<String>()
    amountSats?.let {
        val btcAmount = it.toDouble() / 100_000_000.0
        params += "amount=${String.format(Locale.US, "%.8f", btcAmount)}"
    }
    label?.let {
        params += "label=${URLEncoder.encode(it, "UTF-8")}"
    }
    return "bitcoin:$baseAddress?${params.joinToString("&")}"
}

private fun SparkReceiveState.errorMessage(): String? =
    (this as? SparkReceiveState.Error)?.message
