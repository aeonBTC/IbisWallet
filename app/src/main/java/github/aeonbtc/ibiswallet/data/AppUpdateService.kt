package github.aeonbtc.ibiswallet.data

import github.aeonbtc.ibiswallet.util.AppVersion
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.stringWithLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class AppUpdateService(
    private val client: OkHttpClient = defaultClient(),
    private val releasesUrl: String = GITHUB_RELEASES_API_URL,
) {
    suspend fun fetchLatestRelease(): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(releasesUrl)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", USER_AGENT)
                        .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${it.code}: ${it.message}"))
                    }

                    val body = it.body.stringWithLimit(InputLimits.MEDIUM_JSON_BYTES)
                    if (body.isBlank()) {
                        return@withContext Result.failure(Exception("Empty update response"))
                    }

                    Result.success(parseLatestRelease(body))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    companion object {
        private const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/aeonBTC/IbisWallet/releases"
        private const val USER_AGENT = "IbisWallet-UpdateCheck"
        private const val TIMEOUT_SECONDS = 15L

        fun parseLatestRelease(json: String): AppReleaseInfo? {
            val releases = JSONArray(json)

            return buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue

                    val tagName = release.optString("tag_name").trim()
                    val version = AppVersion.parse(tagName) ?: continue

                    val htmlUrl = release.optString("html_url").trim().ifBlank { DEFAULT_RELEASES_PAGE_URL }
                    add(
                        AppReleaseInfo(
                            versionName = tagName,
                            version = version,
                            htmlUrl = htmlUrl,
                        ),
                    )
                }
            }.maxByOrNull { it.version }
        }

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        const val DEFAULT_RELEASES_PAGE_URL = "https://github.com/aeonBTC/IbisWallet/releases"
    }
}

data class AppReleaseInfo(
    val versionName: String,
    val version: AppVersion,
    val htmlUrl: String,
)
