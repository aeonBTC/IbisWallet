package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import github.aeonbtc.ibiswallet.ui.theme.*
import java.util.concurrent.Executors

/**
 * QR Scanner Dialog - provides camera-based QR code scanning with dialog wrapper
 */
@Composable
fun QrScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        ) 
    }
    var permissionRequested by remember { mutableStateOf(false) }
    
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraPermission = android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // Permission denied, dismiss the dialog
            onDismiss()
        }
    }
    
    // Request permission immediately if not granted
    LaunchedEffect(Unit) {
        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED && !permissionRequested) {
            permissionRequested = true
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
    if (cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scan QR Code",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                    
                    // Camera preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                androidx.camera.view.PreviewView(ctx).apply {
                                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                    
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        
                                        val preview = CameraPreview.Builder().build().also {
                                            it.setSurfaceProvider(this@apply.surfaceProvider)
                                        }
                                        
                                        val imageAnalyzer = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also {
                                                it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
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
                                                    val binaryBitmap = BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
                                                    
                                                    try {
                                                        val result = MultiFormatReader().decode(binaryBitmap)
                                                        onCodeScanned(result.text)
                                                    } catch (e: Exception) {
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
                                                imageAnalyzer
                                            )
                                        } catch (e: Exception) {
                                            // Camera binding failed
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
