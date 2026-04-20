package github.aeonbtc.ibiswallet.ui.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sparrowwallet.hummingbird.UREncoder
import com.sparrowwallet.hummingbird.registry.CryptoPSBT
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.Bbqr
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import github.aeonbtc.ibiswallet.util.resolveQrVersion
import kotlinx.coroutines.delay

/**
 * Animated QR code component that displays BC-UR encoded data as cycling QR frames.
 * Used for exporting PSBTs/PSETs to hardware wallets via animated QR codes.
 *
 * Uses `ur:crypto-psbt` framing which carries both Bitcoin PSBT and Liquid PSET bytes
 * (Jade and other hardware wallets accept both through the same UR type).
 *
 * For small payloads that fit in a single QR frame, displays a static QR code.
 * For larger payloads, precomputes a stable UR cycle and loops through it.
 *
 * Optimized for hardware wallet cameras (SeedSigner, Coldcard, Keystone, Jade):
 * - Slower cadence for Jade and other lower-FPS cameras
 * - Moderate adaptive fragment sizing to balance frame count vs QR density
 * - Stable ordered part loop instead of an aggressive changing stream
 * - Optional pause/step controls for scanners that miss fast transitions
 * - Explicit error correction level L (fountain codes handle redundancy)
 * - Thick white border for contrast
 * - Screen brightness boost and keep-screen-on
 * - Tap to enlarge for full-screen display
 */
enum class AnimatedQrExportProfile {
    BITCOIN_PSBT,
    LIQUID_PSET,
}

data class AnimatedQrEncodingDiagnostics(
    val payloadSizeBytes: Int,
    val fragmentSize: Int,
    val totalParts: Int,
    val density: SecureStorage.QrDensity,
    val exportProfile: AnimatedQrExportProfile,
)

data class AnimatedQrEncodingPlan(
    val qrParts: List<String>,
    val diagnostics: AnimatedQrEncodingDiagnostics,
)

@Composable
fun rememberAnimatedQrEncodingPlan(
    dataBase64: String,
    density: SecureStorage.QrDensity,
    exportProfile: AnimatedQrExportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
): AnimatedQrEncodingPlan {
    return remember(dataBase64, density, exportProfile) {
        val dataBytes = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT)
        buildAnimatedQrEncodingPlan(
            dataBytes = dataBytes,
            density = density,
            exportProfile = exportProfile,
        )
    }
}

@Composable
fun AnimatedQrCode(
    encodingPlan: AnimatedQrEncodingPlan,
    modifier: Modifier = Modifier,
    qrSize: Dp = 280.dp,
    brightness: Float? = null,
    playbackSpeed: SecureStorage.QrPlaybackSpeed = SecureStorage.QrPlaybackSpeed.MEDIUM,
) {
    val context = LocalContext.current
    var showEnlarged by remember { mutableStateOf(false) }
    val qrParts = encodingPlan.qrParts
    val totalParts = encodingPlan.diagnostics.totalParts
    val forcedQrVersion =
        remember(qrParts) {
            qrParts.maxOfOrNull { part -> resolveQrVersion(part.uppercase()) ?: 1 }
        }
    val effectiveFrameDelayMs =
        remember(totalParts, playbackSpeed) {
            resolveUrFrameDelay(
                totalParts = totalParts,
                playbackSpeed = playbackSpeed,
            )
        }
    var partIndex by remember(qrParts) { mutableIntStateOf(0) }
    val currentPartIndex = clampAnimatedPartIndex(partIndex, totalParts)

    // Only animate if there are multiple parts
    val isAnimated = totalParts > 1
    val currentPart = qrParts[currentPartIndex]

    if (isAnimated) {
        LaunchedEffect(qrParts, effectiveFrameDelayMs) {
            partIndex = clampAnimatedPartIndex(partIndex, totalParts)
            while (true) {
                delay(effectiveFrameDelayMs)
                partIndex = (partIndex + 1) % totalParts
            }
        }
    }

    val qrBitmap =
        remember(currentPart, forcedQrVersion) {
            generateQrBitmap(
                content = currentPart.uppercase(),
                forcedVersion = forcedQrVersion,
                cropToContent = true,
            )
        }

    // Keep the screen on while QR is displayed and apply the user-selected brightness if provided.
    DisposableEffect(brightness) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        window?.let {
            val params = it.attributes
            brightness?.let { selectedBrightness ->
                params.screenBrightness = selectedBrightness.coerceIn(0f, 1f)
            }
            it.attributes = params
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.let {
                val params = it.attributes
                params.screenBrightness = originalBrightness
                it.attributes = params
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val enlargeQr = { showEnlarged = true }
    val dismissEnlarged = { showEnlarged = false }
    if (showEnlarged) {
        Dialog(
            onDismissRequest = dismissEnlarged,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = dismissEnlarged),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isAnimated) {
                        Text(
                            text = "Part ${currentPartIndex + 1} of $totalParts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Enlarged PSBT QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap anywhere to close",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isAnimated) {
            Text(
                text = "Part ${currentPartIndex + 1} of $totalParts",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // White background box with padding for contrast against dark card
        Box(
            modifier =
                Modifier
                    .size(qrSize + 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(onClick = enlargeQr)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PSBT QR Code - Tap to enlarge",
                    modifier = Modifier.size(qrSize),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap QR to enlarge",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

internal fun buildAnimatedQrEncodingPlan(
    dataBytes: ByteArray,
    density: SecureStorage.QrDensity,
    exportProfile: AnimatedQrExportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
): AnimatedQrEncodingPlan {
    val fragmentSize =
        resolveUrFragmentSize(
            payloadSize = dataBytes.size,
            density = density,
            exportProfile = exportProfile,
        )

    // CryptoPSBT wraps arbitrary bytes as ur:crypto-psbt — works for both PSBT and PSET.
    // Keep the UR encoder shared, but separate the sizing policy per export profile.
    val cryptoPsbt = CryptoPSBT(dataBytes)
    val ur = cryptoPsbt.toUR()
    val encoder = UREncoder(ur, fragmentSize, 10, 0)
    val qrParts = List(encoder.seqLen) { encoder.nextPart() }

    return AnimatedQrEncodingPlan(
        qrParts = qrParts,
        diagnostics =
            AnimatedQrEncodingDiagnostics(
                payloadSizeBytes = dataBytes.size,
                fragmentSize = fragmentSize,
                totalParts = qrParts.size,
                density = density,
                exportProfile = exportProfile,
            ),
    )
}

internal fun resolveUrFragmentSize(
    payloadSize: Int,
    density: SecureStorage.QrDensity,
    exportProfile: AnimatedQrExportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
): Int {
    return when (exportProfile) {
        AnimatedQrExportProfile.BITCOIN_PSBT -> resolveBitcoinPsbtFragmentSize(payloadSize, density)
        AnimatedQrExportProfile.LIQUID_PSET -> resolveLiquidPsetFragmentSize(payloadSize, density)
    }
}

private fun resolveBitcoinPsbtFragmentSize(
    payloadSize: Int,
    density: SecureStorage.QrDensity,
): Int {
    return when (density) {
        SecureStorage.QrDensity.LOW ->
            when {
                payloadSize >= 12_000 -> 280
                payloadSize >= 6_000 -> 240
                payloadSize >= 2_000 -> 220
                else -> 180
            }

        SecureStorage.QrDensity.MEDIUM ->
            when {
                payloadSize >= 12_000 -> 360
                payloadSize >= 6_000 -> 320
                payloadSize >= 2_000 -> 280
                else -> 220
            }

        SecureStorage.QrDensity.HIGH ->
            when {
                payloadSize >= 12_000 -> 440
                payloadSize >= 6_000 -> 400
                payloadSize >= 2_000 -> 340
                else -> 280
            }
    }
}

private fun resolveLiquidPsetFragmentSize(
    payloadSize: Int,
    density: SecureStorage.QrDensity,
): Int {
    return when (density) {
        SecureStorage.QrDensity.LOW ->
            when {
                payloadSize >= 12_000 -> 180
                payloadSize >= 6_000 -> 140
                payloadSize >= 2_000 -> 120
                else -> 100
            }

        SecureStorage.QrDensity.MEDIUM ->
            when {
                payloadSize >= 12_000 -> 240
                payloadSize >= 6_000 -> 220
                payloadSize >= 2_000 -> 180
                else -> 140
            }

        SecureStorage.QrDensity.HIGH ->
            when {
                payloadSize >= 12_000 -> 300
                payloadSize >= 6_000 -> 260
                payloadSize >= 2_000 -> 220
                else -> 180
            }
    }
}

private fun resolveUrFrameDelay(
    totalParts: Int,
    playbackSpeed: SecureStorage.QrPlaybackSpeed,
): Long {
    val baseDelay =
        when {
            totalParts >= 60 -> 1200L
            totalParts >= 20 -> 900L
            else -> 650L
        }

    return when (playbackSpeed) {
        SecureStorage.QrPlaybackSpeed.SLOW -> (baseDelay * 1.25f).toLong()
        SecureStorage.QrPlaybackSpeed.MEDIUM -> baseDelay
        SecureStorage.QrPlaybackSpeed.FAST -> (baseDelay * 0.65f).toLong()
    }.coerceAtLeast(300L)
}

/**
 * Animated QR code component for generic byte data, encoded as BBQr.
 * Used for exporting labels, PSBTs, and other byte payloads via animated QR codes.
 *
 * For small data that fits in a single QR frame, displays a static QR code.
 * For larger data, splits into deterministic BBQr parts and cycles through them.
 *
 * BBQr advantages over BC-UR for labels:
 * - Zlib compression (~30-45% smaller for JSONL text)
 * - More data per QR frame (~1,062 bytes vs 120)
 * - Deterministic progress (no fountain code overhead)
 * - Interoperable with Sparrow, LabelBase, Nunchuk, Coldcard Q
 */
@Composable
fun AnimatedQrCodeBytes(
    data: ByteArray,
    modifier: Modifier = Modifier,
    qrSize: Dp = 280.dp,
    frameDelayMs: Long = 250L,
    fileType: Char = Bbqr.FILE_TYPE_JSON,
    brightness: Float? = null,
    minVersion: Int = 5,
    maxVersion: Int = 40,
) {
    val context = LocalContext.current
    var showEnlarged by remember { mutableStateOf(false) }

    // Split data into BBQr parts with Zlib compression
    val bbqrSplit =
        remember(data, fileType, minVersion, maxVersion) {
            Bbqr.split(
                data = data,
                fileType = fileType,
                minVersion = minVersion,
                maxVersion = maxVersion,
            )
        }
    val bbqrParts = bbqrSplit.parts
    val forcedQrVersion = bbqrSplit.version

    val totalParts = bbqrParts.size
    var partIndex by remember(bbqrParts) { mutableIntStateOf(0) }
    val currentPartIndex = clampAnimatedPartIndex(partIndex, totalParts)

    val isAnimated = totalParts > 1

    if (isAnimated) {
        LaunchedEffect(bbqrParts, frameDelayMs) {
            partIndex = clampAnimatedPartIndex(partIndex, totalParts)
            while (true) {
                delay(frameDelayMs)
                partIndex = (partIndex + 1) % totalParts
            }
        }
    }

    val currentPart = bbqrParts[currentPartIndex]

    val qrBitmap =
        remember(currentPart, forcedQrVersion) {
            generateQrBitmap(
                content = currentPart,
                forcedVersion = forcedQrVersion,
                cropToContent = true,
            )
        }

    // Keep the screen on while QR is displayed and apply user-selected brightness if provided.
    DisposableEffect(brightness) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        window?.let {
            val params = it.attributes
            brightness?.let { selectedBrightness ->
                params.screenBrightness = selectedBrightness.coerceIn(0f, 1f)
            }
            it.attributes = params
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.let {
                val params = it.attributes
                params.screenBrightness = originalBrightness
                it.attributes = params
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val enlargeQr = { showEnlarged = true }
    val dismissEnlarged = { showEnlarged = false }
    if (showEnlarged) {
        Dialog(
            onDismissRequest = dismissEnlarged,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(onClick = dismissEnlarged),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isAnimated) {
                        Text(
                            text = "Part ${currentPartIndex + 1} of $totalParts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(360.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Enlarged QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap anywhere to close",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isAnimated) {
            Text(
                text = "Part ${currentPartIndex + 1} of $totalParts",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
            modifier =
                Modifier
                    .size(qrSize + 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(onClick = enlargeQr)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code - Tap to enlarge",
                    modifier = Modifier.size(qrSize),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap QR to enlarge",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

internal fun clampAnimatedPartIndex(
    partIndex: Int,
    totalParts: Int,
): Int {
    if (totalParts <= 0) return 0
    return partIndex.coerceIn(0, totalParts - 1)
}

