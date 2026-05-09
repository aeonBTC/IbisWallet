package github.aeonbtc.ibiswallet.util

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig

/**
 * Central logging policy for wallet-sensitive code.
 *
 * Debug builds keep detailed diagnostics. Release builds only emit caller-provided
 * static messages and never include throwables or interpolated runtime values.
 */
object SecureLog {
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) {
            Log.d(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }
    }

    fun i(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        releaseMessage: String? = null,
    ) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.i(tag, message, throwable)
            } else {
                Log.i(tag, message)
            }
        } else {
            releaseMessage?.let { Log.i(tag, it) }
        }
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        releaseMessage: String? = null,
    ) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        } else {
            releaseMessage?.let { Log.w(tag, it) }
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        releaseMessage: String? = null,
    ) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } else {
            releaseMessage?.let { Log.e(tag, it) }
        }
    }
}
