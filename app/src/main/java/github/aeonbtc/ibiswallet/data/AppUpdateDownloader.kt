package github.aeonbtc.ibiswallet.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

/**
 * Downloads a release APK over a direct clearnet connection (never Tor) and
 * streams it to [destFile] with progress callbacks. Partial files are deleted
 * on failure or cancellation.
 */
class AppUpdateDownloader(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun download(
        apkUrl: String,
        destFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> {
        val request =
            Request.Builder()
                .url(apkUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        val call = client.newCall(request)
        return try {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(
                        object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: IOException) {
                                destFile.delete()
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(e))
                                }
                            }

                            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                response.use {
                                    if (!it.isSuccessful) {
                                        destFile.delete()
                                        if (continuation.isActive) {
                                            continuation.resume(Result.failure(IOException("HTTP ${it.code}")))
                                        }
                                        return
                                    }
                                    val total = it.body.contentLength()
                                    if (total <= 0L) {
                                        destFile.delete()
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                Result.failure(IOException("Unknown download size")),
                                            )
                                        }
                                        return
                                    }
                                    if (total > MAX_APK_BYTES) {
                                        destFile.delete()
                                        if (continuation.isActive) {
                                            continuation.resume(Result.failure(IOException("APK too large")))
                                        }
                                        return
                                    }
                                    val usableSpace = destFile.parentFile?.usableSpace ?: 0L
                                    if (usableSpace > 0L && usableSpace < total + FREE_SPACE_MARGIN_BYTES) {
                                        destFile.delete()
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                Result.failure(IOException("Not enough storage")),
                                            )
                                        }
                                        return
                                    }
                                    try {
                                        destFile.parentFile?.mkdirs()
                                        it.body.byteStream().use { input ->
                                            destFile.outputStream().use { output ->
                                                val buffer = ByteArray(BUFFER_SIZE)
                                                var downloaded = 0L
                                                while (true) {
                                                    val read = input.read(buffer)
                                                    if (read == -1) break
                                                    downloaded += read
                                                    if (downloaded > MAX_APK_BYTES) {
                                                        throw IOException("APK too large")
                                                    }
                                                    output.write(buffer, 0, read)
                                                    onProgress(downloaded, total)
                                                }
                                                output.flush()
                                            }
                                        }
                                        if (destFile.length() != total) {
                                            throw IOException("Truncated download")
                                        }
                                        if (continuation.isActive) {
                                            continuation.resume(Result.success(destFile))
                                        }
                                    } catch (error: Exception) {
                                        destFile.delete()
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(error)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            destFile.delete()
            throw cancelled
        } catch (error: Exception) {
            destFile.delete()
            Result.failure(error)
        }
    }

    companion object {
        private const val USER_AGENT = "IbisWallet-UpdateDownload"
        private const val BUFFER_SIZE = 32 * 1024
        private const val FREE_SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
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
