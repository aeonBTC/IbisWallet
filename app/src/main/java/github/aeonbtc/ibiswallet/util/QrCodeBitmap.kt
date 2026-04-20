package github.aeonbtc.ibiswallet.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

fun generateQrBitmap(
    content: String,
    forcedVersion: Int? = null,
    cropToContent: Boolean = false,
): Bitmap? {
    return try {
        val size = 512
        val qrCodeWriter = QRCodeWriter()
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        )
        forcedVersion?.let { hints[EncodeHintType.QR_VERSION] = it }
        val bitMatrix =
            qrCodeWriter.encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints,
            )

        val contentBounds =
            if (cropToContent) {
                findContentBounds(bitMatrix)
            } else {
                null
            }

        val sourceLeft = contentBounds?.left ?: 0
        val sourceTop = contentBounds?.top ?: 0
        val sourceWidth = contentBounds?.width ?: size
        val sourceHeight = contentBounds?.height ?: size
        val bitmap = createBitmap(size, size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val sourceX = sourceLeft + (x * sourceWidth) / size
                val sourceY = sourceTop + (y * sourceHeight) / size
                bitmap[x, y] =
                    if (bitMatrix[sourceX, sourceY]) Color.Black.toArgb() else Color.White.toArgb()
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

private fun findContentBounds(bitMatrix: com.google.zxing.common.BitMatrix): ContentBounds? {
    var minX = bitMatrix.width
    var minY = bitMatrix.height
    var maxX = -1
    var maxY = -1

    for (x in 0 until bitMatrix.width) {
        for (y in 0 until bitMatrix.height) {
            if (!bitMatrix[x, y]) continue
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }

    if (maxX < minX || maxY < minY) {
        return null
    }

    return ContentBounds(
        left = minX,
        top = minY,
        width = maxX - minX + 1,
        height = maxY - minY + 1,
    )
}

private data class ContentBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

fun resolveQrVersion(content: String): Int? {
    return try {
        val hints =
            mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            )
        Encoder.encode(content, ErrorCorrectionLevel.L, hints).version.versionNumber
    } catch (_: Exception) {
        null
    }
}
