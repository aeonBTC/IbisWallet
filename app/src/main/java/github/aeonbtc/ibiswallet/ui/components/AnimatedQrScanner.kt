package github.aeonbtc.ibiswallet.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.sparrowwallet.hummingbird.ResultType
import com.sparrowwallet.hummingbird.URDecoder
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.UrAccountParser
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraPreview

/**
 * Animated QR scanner dialog that supports both single-frame and multi-frame BC-UR QR codes.
 * Used for scanning signed PSBTs or raw transactions from hardware wallets.
 *
 * Handles three input formats:
 * 1. BC-UR animated QR (ur:crypto-psbt/...) - accumulates frames, shows progress
 * 2. Single-frame base64 PSBT
 * 3. Single-frame raw transaction hex
 *
 * @param onDataReceived Called with the decoded data (base64 PSBT or hex raw tx)
 * @param onDismiss Called when the dialog is dismissed
 */
@Composable
fun AnimatedQrScannerDialog(
    onDataReceived: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermission by remember {
        mutableIntStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA),
        )
    }
    val cameraPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                cameraPermission = android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                onDismiss()
            }
        }

    // Request permission immediately if not granted (LaunchedEffect(Unit) runs once)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        // UR decoder accumulates multi-frame scans
        val urDecoder = remember { URDecoder() }
        var scanProgress by remember { mutableFloatStateOf(0f) }
        var isComplete by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Header
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Scan Signed Transaction",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }

                    // Camera preview
                    QrCameraPreview(
                        lifecycleOwner = lifecycleOwner,
                        isComplete = isComplete,
                        onFrameScanned = { scannedText ->
                            handleScannedQrData(
                                scannedText = scannedText,
                                urDecoder = urDecoder,
                                onProgress = { progress ->
                                    scanProgress = progress
                                },
                                onComplete = { decodedData ->
                                    isComplete = true
                                    onDataReceived(decodedData)
                                },
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(horizontal = 16.dp),
                    )

                    // Progress indicator for multi-frame scanning
                    if (scanProgress > 0f && !isComplete) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BitcoinOrange,
                                trackColor = BitcoinOrange.copy(alpha = 0.15f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scanning: ${(scanProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * QR scanner dialog for the wallet import screen. Supports:
 * 1. Single-frame plain text (xpub, mnemonic, descriptor, SeedQR digits)
 * 2. Single-frame UR (ur:crypto-hdkey, ur:crypto-output)
 * 3. Multi-frame animated UR (ur:crypto-account, ur:account-descriptor)
 * 4. Any single-frame content is passed through for the caller to parse
 *
 * @param preferredAddressType The address type selected on the import screen,
 *   used to pick the matching descriptor from crypto-account bundles
 * @param onCodeScanned Called with the decoded text (key-origin string, descriptor, mnemonic, etc.)
 * @param onDismiss Called when the dialog is dismissed
 */
@Composable
fun ImportQrScannerDialog(
    preferredAddressType: AddressType = AddressType.SEGWIT,
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermission by remember {
        mutableIntStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA),
        )
    }
    val cameraPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                cameraPermission = android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                onDismiss()
            }
        }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        val urDecoder = remember { URDecoder() }
        var scanProgress by remember { mutableFloatStateOf(0f) }
        var isComplete by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Header
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Scan QR Code",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }

                    // Camera preview
                    QrCameraPreview(
                        lifecycleOwner = lifecycleOwner,
                        isComplete = isComplete,
                        onFrameScanned = { scannedText ->
                            handleImportQrData(
                                scannedText = scannedText,
                                urDecoder = urDecoder,
                                preferredAddressType = preferredAddressType,
                                onProgress = { progress ->
                                    scanProgress = progress
                                },
                                onComplete = { decodedData ->
                                    isComplete = true
                                    onCodeScanned(decodedData)
                                },
                                onError = { msg ->
                                    errorMessage = msg
                                },
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(horizontal = 16.dp),
                    )

                    // Progress indicator for multi-frame scanning
                    if (scanProgress > 0f && !isComplete) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BitcoinOrange,
                                trackColor = BitcoinOrange.copy(alpha = 0.15f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scanning: ${(scanProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }

                    // Error message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Shared camera preview composable with QR code scanning.
 * Extracted to its own @Composable function to resolve UiComposable applier inference.
 */
@Composable
private fun QrCameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isComplete: Boolean,
    onFrameScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview =
                            CameraPreview.Builder().build().also {
                                it.setSurfaceProvider(this@apply.surfaceProvider)
                            }

                        val imageAnalyzer =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        if (isComplete) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }

                                        val buffer = imageProxy.planes[0].buffer
                                        val data = ByteArray(buffer.remaining())
                                        buffer.get(data)

                                        val width = imageProxy.width
                                        val height = imageProxy.height
                                        val pixels = IntArray(width * height)

                                        for (i in data.indices) {
                                            val y = data[i].toInt() and 0xFF
                                            pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
                                        }

                                        val source = RGBLuminanceSource(width, height, pixels)
                                        val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))

                                        try {
                                            val result = MultiFormatReader().decode(binaryBitmap)
                                            onFrameScanned(result.text)
                                        } catch (_: Exception) {
                                            // No QR code found in this frame
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer,
                            )
                        } catch (_: Exception) {
                            // Camera binding failed
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Process a scanned QR frame for wallet import. Handles:
 * - BC-UR multi-frame (crypto-account, account-descriptor) with progress
 * - BC-UR single-frame (crypto-hdkey, hdkey, crypto-output, output-descriptor)
 * - Plain text pass-through (xpub, mnemonic, descriptor, SeedQR)
 */
private fun handleImportQrData(
    scannedText: String,
    urDecoder: URDecoder,
    preferredAddressType: AddressType,
    onProgress: (Float) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val trimmed = scannedText.trim()

    // Check if it's a BC-UR part (case-insensitive)
    if (trimmed.lowercase().startsWith("ur:")) {
        try {
            urDecoder.receivePart(trimmed)
            val progress = urDecoder.estimatedPercentComplete
            onProgress(progress.toFloat())

            if (urDecoder.result != null) {
                val result = urDecoder.result
                if (result.type == ResultType.SUCCESS) {
                    val ur = result.ur
                    val urType = ur.type

                    // Check for PSBT type -- reject on import screen
                    if (urType == "crypto-psbt" || urType == "psbt") {
                        onError("This is a PSBT QR, not a wallet key. Use the PSBT screen to scan signed transactions.")
                        return
                    }

                    // Parse wallet-related UR types
                    val parsed = UrAccountParser.parseUr(ur, preferredAddressType)
                    if (parsed != null) {
                        onComplete(parsed.keyMaterial)
                    } else {
                        onError("Could not extract key data from ${ur.type} QR code")
                    }
                } else {
                    onError("Failed to decode UR QR code")
                }
            }
        } catch (_: Exception) {
            // Invalid UR part, ignore (might be a partial frame)
        }
        return
    }

    // Not a UR -- pass through as plain text (xpub, mnemonic, SeedQR digits, descriptor, JSON, etc.)
    if (trimmed.isNotEmpty()) {
        onComplete(trimmed)
    }
}

/**
 * Process a single scanned QR frame. Handles BC-UR multi-frame,
 * single-frame base64 PSBT, and raw hex transactions.
 */
private fun handleScannedQrData(
    scannedText: String,
    urDecoder: URDecoder,
    onProgress: (Float) -> Unit,
    onComplete: (String) -> Unit,
) {
    val trimmed = scannedText.trim()

    // Check if it's a BC-UR part (case-insensitive)
    if (trimmed.lowercase().startsWith("ur:")) {
        try {
            urDecoder.receivePart(trimmed)
            val progress = urDecoder.estimatedPercentComplete
            onProgress(progress.toFloat())

            if (urDecoder.result != null) {
                val result = urDecoder.result
                if (result.type == ResultType.SUCCESS) {
                    val ur = result.ur
                    // The UR toBytes() decodes CBOR wrapper and returns the raw payload
                    // For crypto-psbt, this is the raw PSBT bytes
                    val psbtBytes = ur.toBytes()
                    val psbtBase64 =
                        android.util.Base64.encodeToString(
                            psbtBytes,
                            android.util.Base64.NO_WRAP,
                        )
                    onComplete(psbtBase64)
                }
            }
        } catch (_: Exception) {
            // Invalid UR part, ignore
        }
        return
    }

    // Not a UR - check if it's a plain base64 PSBT or raw hex transaction
    // Base64 PSBT typically starts with "cHNidP" (base64 of "psbt\xff")
    val isLikelyBase64Psbt = trimmed.startsWith("cHNidP")
    val isLikelyHex =
        trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
            trimmed.length % 2 == 0 && trimmed.length > 20

    if (isLikelyBase64Psbt || isLikelyHex) {
        onComplete(trimmed)
    }
}
