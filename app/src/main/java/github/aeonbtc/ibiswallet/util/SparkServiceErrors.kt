package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.R

/**
 * Maps Breez Spark SDK / transport failures to short user-facing copy.
 * Raw HTML, gRPC metadata, and CloudFront bodies must never reach the UI.
 */
object SparkServiceErrors {
    interface Localizer {
        fun get(resId: Int): String
    }

    fun mapFailure(
        localizer: Localizer,
        error: Throwable,
        fallback: String,
    ): String {
        if (error.isTransactionInsufficientFundsError()) {
            return localizer.get(R.string.loc_534e1eb2)
        }

        val details = collectErrorText(error)
        if (details.isBlank()) {
            return fallback.ifBlank { localizer.get(R.string.spark_error_generic) }
        }

        val lower = details.lowercase()
        return when {
            isGeoOrCdnBlocked(lower) -> localizer.get(R.string.spark_error_unavailable)
            isConnectionFailure(lower) -> localizer.get(R.string.spark_error_connection)
            isRawTransportPayload(details, lower) -> localizer.get(R.string.spark_error_connection)
            else -> sanitizeDetail(details) ?: localizer.get(R.string.spark_error_generic)
        }
    }

    fun collectErrorText(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { throwable ->
                throwable.message?.trim()?.takeIf { message -> message.isNotEmpty() }
            }
            .distinct()
            .joinToString(separator = " | ")

    private fun isGeoOrCdnBlocked(lower: String): Boolean =
        lower.contains("cloudfront") ||
            lower.contains("block access from your country") ||
            lower.contains("not available in your country") ||
            lower.contains("geo") && lower.contains("block") ||
            lower.contains("403 error") ||
            lower.contains(">403 ") ||
            lower.contains("http status: 403") ||
            lower.contains("status code: 403")

    private fun isConnectionFailure(lower: String): Boolean =
        lower.contains("permissiondenied") ||
            lower.contains("permission denied") ||
            lower.contains("unauthenticated") ||
            lower.contains("unavailable") ||
            lower.contains("deadline exceeded") ||
            lower.contains("connection error") ||
            lower.contains("connection refused") ||
            lower.contains("connection reset") ||
            lower.contains("connection timed out") ||
            lower.contains("connect timed out") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("network error") ||
            lower.contains("network is unreachable") ||
            lower.contains("failed to connect") ||
            lower.contains("unable to resolve") ||
            lower.contains("unknownhost") ||
            lower.contains("no address associated") ||
            lower.contains("broken pipe") ||
            lower.contains("sslhandshake") ||
            lower.contains("ssl error") ||
            lower.contains("tls handshake") ||
            lower.contains("certificate") ||
            lower.contains("service connection error")

    private fun isRawTransportPayload(
        details: String,
        lower: String,
    ): Boolean =
        details.length > 220 ||
            lower.contains("<!doctype") ||
            lower.contains("<html") ||
            lower.contains("<head") ||
            lower.contains("<body") ||
            lower.contains("metadatamap") ||
            lower.contains("content-type") ||
            lower.contains("application/grpc") ||
            lower.contains("awselb") ||
            details.count { it == '\n' } >= 2

    private fun sanitizeDetail(details: String): String? {
        val firstLine =
            details
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.isNotEmpty() &&
                        !line.contains("Exception", ignoreCase = true) &&
                        !line.startsWith("at ", ignoreCase = true) &&
                        !line.startsWith("<") &&
                        !line.contains("<!")
                }
                ?: return null
        if (isRawTransportPayload(firstLine, firstLine.lowercase())) return null
        return firstLine.take(160).takeIf { it.isNotBlank() }
    }
}
