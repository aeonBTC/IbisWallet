package github.aeonbtc.ibiswallet.data.boltz

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig

internal enum class BoltzTraceLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

internal data class BoltzTraceContext(
    val operation: String,
    val swapId: String? = null,
    val requestKey: String? = null,
    val backend: String? = null,
    val viaTor: Boolean? = null,
    val session: String? = null,
    val cache: String? = null,
    val attempt: Int? = null,
    val source: String? = null,
)

internal fun boltzTraceStart(): Long = System.nanoTime()

internal fun boltzElapsedMs(startedAtNs: Long): Long {
    return ((System.nanoTime() - startedAtNs) / 1_000_000L).coerceAtLeast(0L)
}

internal fun logBoltzTrace(
    stage: String,
    context: BoltzTraceContext,
    vararg extras: Pair<String, Any?>,
) {
    logBoltzTrace(
        stage = stage,
        context = context,
        level = BoltzTraceLevel.INFO,
        throwable = null,
        extras = extras,
    )
}

internal fun logBoltzTrace(
    stage: String,
    context: BoltzTraceContext,
    level: BoltzTraceLevel,
    vararg extras: Pair<String, Any?>,
) {
    logBoltzTrace(
        stage = stage,
        context = context,
        level = level,
        throwable = null,
        extras = extras,
    )
}

internal fun logBoltzTrace(
    stage: String,
    context: BoltzTraceContext,
    level: BoltzTraceLevel = BoltzTraceLevel.INFO,
    throwable: Throwable? = null,
    vararg extras: Pair<String, Any?>,
) {
    if (!BuildConfig.DEBUG) return
    if (level == BoltzTraceLevel.DEBUG || level == BoltzTraceLevel.INFO) {
        if (!isVerboseBoltzTracingEnabled()) return
    }

    val fields =
        buildList {
            add("operation=${context.operation}")
            add("stage=$stage")
            addField("swapId", context.swapId)
            addField("requestKey", context.requestKey)
            addField("backend", context.backend)
            addField("viaTor", context.viaTor)
            addField("session", context.session)
            addField("cache", context.cache)
            addField("attempt", context.attempt)
            addField("source", context.source)
            extras.forEach { (key, value) -> addField(key, value) }
        }.joinToString(" ")

    runCatching {
        when (level) {
            BoltzTraceLevel.DEBUG ->
                if (throwable != null) {
                    Log.d(BOLTZ_LOG_TAG, fields, throwable)
                } else {
                    Log.d(BOLTZ_LOG_TAG, fields)
                }

            BoltzTraceLevel.INFO ->
                if (throwable != null) {
                    Log.i(BOLTZ_LOG_TAG, fields, throwable)
                } else {
                    Log.i(BOLTZ_LOG_TAG, fields)
                }

            BoltzTraceLevel.WARN ->
                if (throwable != null) {
                    Log.w(BOLTZ_LOG_TAG, fields, throwable)
                } else {
                    Log.w(BOLTZ_LOG_TAG, fields)
                }

            BoltzTraceLevel.ERROR ->
                if (throwable != null) {
                    Log.e(BOLTZ_LOG_TAG, fields, throwable)
                } else {
                    Log.e(BOLTZ_LOG_TAG, fields)
                }
        }
    }.getOrElse {
        println("$BOLTZ_LOG_TAG $fields")
        throwable?.printStackTrace()
    }
}

private fun MutableList<String>.addField(key: String, value: Any?) {
    when (value) {
        null -> Unit
        is String -> if (value.isNotBlank()) add("$key=$value")
        else -> add("$key=$value")
    }
}

private fun isVerboseBoltzTracingEnabled(): Boolean {
    return runCatching { Log.isLoggable(BOLTZ_LOG_TAG, Log.VERBOSE) }.getOrDefault(false)
}

private const val BOLTZ_LOG_TAG = "BoltzDebug"
