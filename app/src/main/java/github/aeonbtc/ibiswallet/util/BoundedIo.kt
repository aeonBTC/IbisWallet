package github.aeonbtc.ibiswallet.util

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object InputLimits {
    const val SMALL_JSON_BYTES: Int = 256 * 1024
    const val MEDIUM_JSON_BYTES: Int = 1024 * 1024
    const val BACKUP_FILE_BYTES: Int = 16 * 1024 * 1024
    const val TX_FILE_BYTES: Int = 8 * 1024 * 1024
    const val QR_PAYLOAD_BYTES: Int = 8 * 1024 * 1024
    const val QR_PART_CHARS: Int = 12 * 1024
    const val QR_PARTS: Int = 500
}

fun ResponseBody.stringWithLimit(maxBytes: Int): String {
    val declaredLength = contentLength()
    if (declaredLength > maxBytes) {
        throw IOException("Response body exceeds ${maxBytes} bytes")
    }
    return byteStream().readBytesWithLimit(maxBytes).toString(Charsets.UTF_8)
}

fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val out = ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("Input exceeds ${maxBytes} bytes")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
