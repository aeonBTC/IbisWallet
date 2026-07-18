package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sparrowwallet.hummingbird.ResultType
import com.sparrowwallet.hummingbird.URDecoder
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.util.Bbqr
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.UrAccountParser

/**
 * Animated QR scanner dialog that supports both single-frame and multi-frame BC-UR QR codes.
 * Used for scanning signed PSBTs or raw transactions from hardware wallets.
 */
@Composable
fun AnimatedQrScannerDialog(
    onDataReceived: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CameraPermissionGate(onDismiss = onDismiss) { lifecycleOwner ->
        val urDecoder = remember { URDecoder() }
        val bbqrJoiner = remember { Bbqr.ContinuousJoiner() }
        var scanProgress by remember { mutableFloatStateOf(0f) }
        var isComplete by remember { mutableStateOf(false) }

        QrScannerDialogShell(
            onDismiss = onDismiss,
            scanProgress = scanProgress,
            showProgress = scanProgress > 0f && !isComplete,
        ) {
            QrCameraPreview(
                lifecycleOwner = lifecycleOwner,
                pauseScanning = isComplete,
                onFrameScanned = { scannedText ->
                    handleScannedQrData(
                        scannedText = scannedText,
                        urDecoder = urDecoder,
                        bbqrJoiner = bbqrJoiner,
                        onProgress = { progress -> scanProgress = progress },
                        onComplete = { decodedData ->
                            isComplete = true
                            onDataReceived(decodedData)
                        },
                    )
                },
            )
        }
    }
}

/**
 * QR scanner dialog for the wallet import screen.
 */
@Composable
fun ImportQrScannerDialog(
    preferredAddressType: AddressType = AddressType.SEGWIT,
    sequentialScan: Boolean = false,
    @Suppress("UNUSED_PARAMETER") scanHint: String? = null,
    scanError: String? = null,
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CameraPermissionGate(onDismiss = onDismiss) { lifecycleOwner ->
        val urDecoder = remember { URDecoder() }
        var scanProgress by remember { mutableFloatStateOf(0f) }
        var isComplete by remember { mutableStateOf(false) }
        var internalError by remember { mutableStateOf<String?>(null) }

        QrScannerDialogShell(
            onDismiss = onDismiss,
            scanProgress = scanProgress,
            showProgress = scanProgress > 0f && !isComplete,
            errorMessage = scanError ?: internalError,
        ) {
            QrCameraPreview(
                lifecycleOwner = lifecycleOwner,
                pauseScanning = isComplete && !sequentialScan,
                onFrameScanned = { scannedText ->
                    handleImportQrData(
                        scannedText = scannedText,
                        urDecoder = urDecoder,
                        preferredAddressType = preferredAddressType,
                        onProgress = { progress ->
                            scanProgress = progress
                            internalError = null
                        },
                        onComplete = { decodedData ->
                            if (!sequentialScan) {
                                isComplete = true
                            }
                            onCodeScanned(decodedData)
                        },
                        onError = { msg -> internalError = msg },
                    )
                },
            )
        }
    }
}

/**
 * QR scanner dialog for BIP 329 label import.
 */
@Composable
fun LabelsQrScannerDialog(
    onLabelsScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CameraPermissionGate(onDismiss = onDismiss) { lifecycleOwner ->
        val bbqrJoiner = remember { github.aeonbtc.ibiswallet.util.Bbqr.ContinuousJoiner() }
        var scanProgress by remember { mutableFloatStateOf(0f) }
        var isComplete by remember { mutableStateOf(false) }

        QrScannerDialogShell(
            onDismiss = onDismiss,
            scanProgress = scanProgress,
            showProgress = scanProgress > 0f && !isComplete,
        ) {
            QrCameraPreview(
                lifecycleOwner = lifecycleOwner,
                pauseScanning = isComplete,
                onFrameScanned = { scannedText ->
                    handleLabelsQrData(
                        scannedText = scannedText,
                        bbqrJoiner = bbqrJoiner,
                        onProgress = { progress, _ -> scanProgress = progress },
                        onComplete = { decodedContent ->
                            isComplete = true
                            onLabelsScanned(decodedContent)
                        },
                    )
                },
            )
        }
    }
}

private fun handleImportQrData(
    scannedText: String,
    urDecoder: URDecoder,
    preferredAddressType: AddressType,
    onProgress: (Float) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val trimmed = scannedText.trim()

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

                    if (urType == "crypto-psbt" || urType == "psbt") {
                        onError(
                            "This is a PSBT QR, not a wallet key. Use the PSBT screen to scan signed transactions.",
                        )
                        return
                    }

                    try {
                        val parsed = UrAccountParser.parseUr(ur, preferredAddressType)
                        if (parsed != null) {
                            onComplete(parsed.keyMaterial)
                        } else {
                            onError("Could not extract key data from ${ur.type} QR code")
                        }
                    } catch (e: IllegalArgumentException) {
                        onError(e.message ?: "Unsupported wallet type")
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

    if (trimmed.isNotEmpty()) {
        onComplete(trimmed)
    }
}

private fun handleLabelsQrData(
    scannedText: String,
    bbqrJoiner: github.aeonbtc.ibiswallet.util.Bbqr.ContinuousJoiner,
    onProgress: (Float, String) -> Unit,
    onComplete: (String) -> Unit,
) {
    val trimmed = scannedText.trim()

    if (github.aeonbtc.ibiswallet.util.Bbqr.isBbqrPart(trimmed)) {
        try {
            bbqrJoiner.addPart(trimmed)
            val progress = bbqrJoiner.progress
            val received = bbqrJoiner.partsReceived
            val total = bbqrJoiner.partsTotal
            val status = if (total > 0) "Scanned $received of $total" else ""
            onProgress(progress, status)

            if (bbqrJoiner.isComplete) {
                val result = bbqrJoiner.result!!
                val content = String(result.data, Charsets.UTF_8)
                onComplete(content)
            }
        } catch (_: Exception) {
            // Invalid BBQr part, ignore
        }
        return
    }

    if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) {
        onComplete(trimmed)
    }
}

internal fun handleScannedQrData(
    scannedText: String,
    urDecoder: URDecoder,
    bbqrJoiner: Bbqr.ContinuousJoiner,
    onProgress: (Float) -> Unit,
    onComplete: (String) -> Unit,
) {
    val trimmed = scannedText.trim()

    if (Bbqr.isBbqrPart(trimmed)) {
        val accepted =
            try {
                bbqrJoiner.addPart(trimmed)
            } catch (_: Exception) {
                false
            }
        if (!accepted) return

        onProgress(bbqrJoiner.progress)
        val result = bbqrJoiner.result ?: return
        if (result.data.size > InputLimits.QR_PAYLOAD_BYTES) return

        when (result.fileType) {
            Bbqr.FILE_TYPE_PSBT -> {
                onComplete(
                    android.util.Base64.encodeToString(
                        result.data,
                        android.util.Base64.NO_WRAP,
                    ),
                )
            }
            Bbqr.FILE_TYPE_TXN -> onComplete(result.data.toHexString())
        }
        return
    }

    if (trimmed.lowercase().startsWith("ur:")) {
        try {
            urDecoder.receivePart(trimmed)
            val progress = urDecoder.estimatedPercentComplete
            onProgress(progress.toFloat())

            if (urDecoder.result != null) {
                val result = urDecoder.result
                if (result.type == ResultType.SUCCESS) {
                    val ur = result.ur
                    val psbtBytes = ur.toBytes()
                    if (psbtBytes.size > InputLimits.QR_PAYLOAD_BYTES) {
                        return
                    }
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

    val isLikelyBase64Psbt = trimmed.startsWith("cHNidP") || trimmed.startsWith("cHNl")
    val isLikelyHex =
        trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
            trimmed.length % 2 == 0 && trimmed.length > 20

    if (isLikelyBase64Psbt || isLikelyHex) {
        onComplete(trimmed)
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }
