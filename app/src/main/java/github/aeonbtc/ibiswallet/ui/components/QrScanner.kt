package github.aeonbtc.ibiswallet.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview

/**
 * QR Scanner Dialog - provides camera-based QR code scanning with dialog wrapper
 */
@Composable
fun QrScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
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
        ) { isGranted: Boolean ->
            if (isGranted) {
                cameraPermission = android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                // Permission denied, dismiss the dialog
                onDismiss()
            }
        }

    // Request permission immediately if not granted
    LaunchedEffect(Unit) {
        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    if (cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
                    CameraPreviewBox(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        onCodeScanned = onCodeScanned,
                        lifecycleOwner = lifecycleOwner,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewBox(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit,
    lifecycleOwner: LifecycleOwner,
) {
    val context = LocalContext.current
    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val scanned = AtomicBoolean(false)
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
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            processFrame(imageProxy, scanned, mainHandler, onCodeScanned)
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
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Cleanup failed
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun processFrame(
    imageProxy: androidx.camera.core.ImageProxy,
    scanned: AtomicBoolean,
    mainHandler: android.os.Handler,
    onCodeScanned: (String) -> Unit,
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
        val binaryBitmap =
            BinaryBitmap(
                com.google.zxing.common.GlobalHistogramBinarizer(source),
            )

        val result = MultiFormatReader().decode(binaryBitmap)
        if (scanned.compareAndSet(false, true)) {
            mainHandler.post { onCodeScanned(result.text) }
        }
    } catch (_: Exception) {
        // No QR code found in this frame
    } finally {
        imageProxy.close()
    }
}
