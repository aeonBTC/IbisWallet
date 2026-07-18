package github.aeonbtc.ibiswallet.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
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
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var hasZoom by remember { mutableStateOf(false) }
    val currentCameraControl by rememberUpdatedState(cameraControl)
    val currentZoomRatio by rememberUpdatedState(zoomRatio)
    val currentPauseScanning by rememberUpdatedState(pauseScanning)

    DisposableEffect(lifecycleOwner, singleShot) {
        val scanned = if (singleShot) AtomicBoolean(false) else null
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var zoomStateLiveData: androidx.lifecycle.LiveData<androidx.camera.core.ZoomState>? = null
        val zoomObserver =
            Observer<androidx.camera.core.ZoomState> { zoomState ->
                minZoomRatio = zoomState.minZoomRatio
                maxZoomRatio = zoomState.maxZoomRatio
                zoomRatio = zoomState.zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                hasZoom = zoomState.maxZoomRatio > zoomState.minZoomRatio
            }

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
                            if (currentPauseScanning) {
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
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer,
                )
                cameraControl = camera.cameraControl
                zoomStateLiveData = camera.cameraInfo.zoomState
                zoomStateLiveData?.observe(lifecycleOwner, zoomObserver)
            } catch (_: Exception) {
                cameraControl = null
                hasZoom = false
                // Camera binding failed
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            zoomStateLiveData?.removeObserver(zoomObserver)
            cameraControl = null
            hasZoom = false
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Cleanup failed
            }
            analyzerExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier.clipToBounds(),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(hasZoom, minZoomRatio, maxZoomRatio) {
                        if (hasZoom) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                val targetZoom =
                                    (currentZoomRatio * zoomChange)
                                        .coerceIn(minZoomRatio, maxZoomRatio)
                                currentCameraControl?.setZoomRatio(targetZoom)
                            }
                        }
                    },
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
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val width = imageProxy.width
        val height = imageProxy.height
        // Dense connection QRs (clnrest/lndconnect with large cert bundles) need
        // TRY_HARDER + HybridBinarizer; GlobalHistogram alone often fails on phone cameras.
        val pixels = IntArray(width * height)
        val row = ByteArray(rowStride)
        var out = 0
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            buffer.get(row, 0, rowStride.coerceAtMost(buffer.remaining()))
            var x = 0
            while (x < width) {
                val luminance = row[x * pixelStride].toInt() and 0xFF
                pixels[out++] = (0xFF shl 24) or (luminance shl 16) or (luminance shl 8) or luminance
                x++
            }
        }

        val result = decodeQrFromPixels(pixels, width, height) ?: return
        val text = result.text.trim()
        if (text.isBlank()) return
        val deliverResult = {
            onFrameScanned(text)
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

private fun decodeQrFromPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
): Result? {
    val hints =
        mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
    val reader = MultiFormatReader().apply { setHints(hints) }
    val source = RGBLuminanceSource(width, height, pixels)
    val attempts =
        listOf(
            { BinaryBitmap(HybridBinarizer(source)) },
            { BinaryBitmap(GlobalHistogramBinarizer(source)) },
            { BinaryBitmap(HybridBinarizer(source.invert())) },
            { BinaryBitmap(GlobalHistogramBinarizer(source.invert())) },
        )
    for (build in attempts) {
        runCatching {
            reader.reset()
            return reader.decodeWithState(build())
        }
    }
    return null
}
