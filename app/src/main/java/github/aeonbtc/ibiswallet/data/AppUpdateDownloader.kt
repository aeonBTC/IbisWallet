package github.aeonbtc.ibiswallet.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Only GitHub release/download hosts are reachable over https. The check is
 * applied to every request in the redirect chain, so a 302 can never move the
 * download to an arbitrary host or to cleartext.
 */
internal fun isAllowedUpdateDownloadUrl(url: HttpUrl): Boolean {
    if (!url.scheme.equals("https", ignoreCase = true)) return false
    val host = url.host.lowercase()
    return host == "github.com" ||
        host == "www.github.com" ||
        host.endsWith(".githubusercontent.com")
}

internal data class ContentRange(
    val start: Long,
    val end: Long,
    val total: Long?,
)

internal fun parseContentRange(header: String?): ContentRange? {
    val match =
        CONTENT_RANGE_REGEX.matchEntire(header?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val totalRaw = match.groupValues[3]
    val total = if (totalRaw == "*") null else totalRaw.toLongOrNull() ?: return null
    if (end < start || (total != null && end >= total)) return null
    return ContentRange(start = start, end = end, total = total)
}

private val CONTENT_RANGE_REGEX = Regex("""^bytes\s+(\d+)-(\d+)/(\d+|\*)$""")

private class ApkTooLargeException : IOException("APK too large")

/**
 * Downloads a release APK over a direct clearnet connection (never Tor) and
 * streams it to [destFile] with progress callbacks. An existing partial file is
 * resumed with an HTTP Range request; a failed transfer keeps the partial bytes
 * so a later attempt continues instead of restarting the whole APK.
 *
 * The transfer runs as one blocking call inside the coroutine, so [download]
 * only returns once the file is no longer being written (a cancelled download
 * can never overlap the next one on the same file).
 */
class AppUpdateDownloader(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun download(
        apkUrl: String,
        destFile: File,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(transfer(apkUrl, destFile, expectedBytes, onProgress))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private suspend fun transfer(
        apkUrl: String,
        destFile: File,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File =
        coroutineScope {
            var resumeFrom = if (destFile.isFile) destFile.length() else 0L
            if (expectedBytes > 0L && resumeFrom > expectedBytes) {
                destFile.delete()
                resumeFrom = 0L
            }
            if (expectedBytes > 0L && resumeFrom == expectedBytes) {
                onProgress(resumeFrom, expectedBytes)
                return@coroutineScope destFile
            }
            // identity keeps Content-Length and Range offsets byte-exact.
            val request =
                Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
                    .get()
                    .build()
            val call = client.newCall(request)
            val canceller =
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
            try {
                call.execute().use { response ->
                    receive(response, destFile, resumeFrom, expectedBytes, onProgress)
                }
            } finally {
                canceller.cancel()
            }
        }

    private suspend fun receive(
        response: Response,
        destFile: File,
        resumeFrom: Long,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File {
        if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
            destFile.delete()
            throw IOException("HTTP ${response.code}")
        }
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

        val isPartial = response.code == HTTP_PARTIAL_CONTENT
        val startOffset = if (isPartial) resumeFrom else 0L
        val append = isPartial && resumeFrom > 0L
        val total =
            if (isPartial) {
                partialTotal(response, destFile, resumeFrom, expectedBytes)
            } else {
                response.body.contentLength()
            }
        if (total <= 0L) throw IOException("Unknown download size")
        if (total > MAX_APK_BYTES) {
            destFile.delete()
            throw ApkTooLargeException()
        }
        if (append && destFile.length() != startOffset) {
            destFile.delete()
            throw IOException("Partial download changed")
        }
        ensureFreeSpace(destFile, total - startOffset)

        try {
            response.body.byteStream().use { input ->
                FileOutputStream(destFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = startOffset
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        written += read
                        if (written > MAX_APK_BYTES) throw ApkTooLargeException()
                        output.write(buffer, 0, read)
                        onProgress(written, total)
                    }
                    output.flush()
                }
            }
        } catch (tooLarge: ApkTooLargeException) {
            destFile.delete()
            throw tooLarge
        }
        if (destFile.length() != total) throw IOException("Truncated download")
        return destFile
    }

    /** Validates a 206 against the requested offset and returns the full asset size. */
    private fun partialTotal(
        response: Response,
        destFile: File,
        resumeFrom: Long,
        expectedBytes: Long,
    ): Long {
        val range = parseContentRange(response.header("Content-Range"))
        val headerTotal = range?.total
        if (range == null || range.start != resumeFrom || (headerTotal == null && expectedBytes <= 0L)) {
            destFile.delete()
            throw IOException("Invalid resume response")
        }
        if (headerTotal != null && expectedBytes > 0L && headerTotal != expectedBytes) {
            destFile.delete()
            throw IOException("APK size changed")
        }
        return headerTotal ?: expectedBytes
    }

    /** A shortage keeps the partial file so the user can free space and resume. */
    private fun ensureFreeSpace(destFile: File, remainingBytes: Long) {
        val parent = destFile.parentFile ?: return
        parent.mkdirs()
        val usableSpace = parent.usableSpace
        if (usableSpace > 0L && usableSpace < remainingBytes + FREE_SPACE_MARGIN_BYTES) {
            throw IOException("Not enough storage")
        }
    }

    companion object {
        private const val USER_AGENT = "IbisWallet-UpdateDownload"
        private const val BUFFER_SIZE = 32 * 1024
        private const val FREE_SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val MAX_APK_BYTES = 500L * 1024L * 1024L

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    val request = chain.request()
                    if (!isAllowedUpdateDownloadUrl(request.url)) {
                        throw IOException("Blocked update download host: ${request.url.host}")
                    }
                    chain.proceed(request)
                }
                .connectTimeout(15L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.SECONDS)
                .writeTimeout(30L, TimeUnit.SECONDS)
                .build()
    }
}
