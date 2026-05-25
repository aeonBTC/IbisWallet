package github.aeonbtc.ibiswallet.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview

/**
 * QR Scanner Dialog — large centered camera preview with a close control only.
 */
@Composable
fun QrScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CameraPermissionGate(onDismiss = onDismiss) { lifecycleOwner ->
        QrScannerDialogShell(onDismiss = onDismiss) {
            QrCameraPreview(
                lifecycleOwner = lifecycleOwner,
                onFrameScanned = onCodeScanned,
                singleShot = true,
            )
        }
    }
}

@Composable
internal fun CameraPermissionGate(
    onDismiss: () -> Unit,
    content: @Composable (LifecycleOwner) -> Unit,
) {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        content(lifecycleOwner)
    }
}

@Composable
internal fun QrScannerDialogShell(
    onDismiss: () -> Unit,
    scanProgress: Float = 0f,
    showProgress: Boolean = false,
    errorMessage: String? = null,
    cameraContent: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 40.dp)
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
            ) {
                cameraContent()

                IconButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.loc_51bac044),
                        tint = Color.White,
                    )
                }

                if (showProgress || errorMessage != null) {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (showProgress) {
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BitcoinOrange,
                                trackColor = BitcoinOrange.copy(alpha = 0.25f),
                            )
                        }
                        if (errorMessage != null) {
                            androidx.compose.material3.Text(
                                text = errorMessage,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = if (showProgress) 8.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Camera preview with QR decoding. Fills the parent — use inside [QrScannerDialogShell].
 */
@Composable
internal fun QrCameraPreview(
    lifecycleOwner: LifecycleOwner,
    onFrameScanned: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    pauseScanning: Boolean = false,
    singleShot: Boolean = false,
) {
    val context = LocalContext.current
    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(lifecycleOwner, singleShot) {
        val scanned = if (singleShot) AtomicBoolean(false) else null
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview =
                CameraPreview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analyzerExecutor) { imageProxy ->
                            if (pauseScanning) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            decodeQrFrame(
                                imageProxy = imageProxy,
                                scanned = scanned,
                                mainHandler = mainHandler,
                                onFrameScanned = onFrameScanned,
                            )
                        }
                    }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer,
                )
            } catch (_: Exception) {
                // Camera binding failed
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Cleanup failed
            }
            analyzerExecutor.shutdown()
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun decodeQrFrame(
    imageProxy: androidx.camera.core.ImageProxy,
    scanned: AtomicBoolean?,
    mainHandler: android.os.Handler,
    onFrameScanned: (String) -> Unit,
) {
    try {
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
        val result = MultiFormatReader().decode(binaryBitmap)
        val deliverResult = {
            onFrameScanned(result.text)
        }
        when {
            scanned == null -> deliverResult()
            scanned.compareAndSet(false, true) -> mainHandler.post(deliverResult)
        }
    } catch (_: Exception) {
        // No QR code found in this frame
    } finally {
        imageProxy.close()
    }
}
