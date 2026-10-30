package github.aeonbtc.ibiswallet.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Secure clipboard utility for sensitive data.
 * - Marks clipboard content as sensitive on Android 13+ (hides from clipboard preview)
 * - Auto-clears clipboard after 30 seconds
 */
object SecureClipboard {
    private const val CLEAR_DELAY_MS = 30_000L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clearJob: kotlinx.coroutines.Job? = null

    /**
     * Copy sensitive text to clipboard with auto-clear.
     * On Android 13+, marks the content as sensitive to hide from clipboard preview.
     */
    fun copyAndScheduleClear(
        context: Context,
        text: String,
    ) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Keep the clip label generic too; labels can be exposed by clipboard UIs.
        val clipData = ClipData.newPlainText("Text", text)

        // Mark as sensitive on Android 13+ to hide from clipboard preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clipData.description.extras =
                PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
        }

        clipboardManager.setPrimaryClip(clipData)

        // Cancel any previous clear job and schedule a new one
        clearJob?.cancel()
        clearJob =
            scope.launch {
                delay(CLEAR_DELAY_MS)
                clearNow(context)
            }
    }

    /**
     * Read the primary clip as trimmed text for the in-field paste buttons.
     * Returns null when the clipboard is empty, non-text, or unavailable.
     * Never log the returned value; it may be a seed, key, or invoice.
     */
    fun readText(context: Context): String? =
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) {
                null
            } else {
                clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }

    /** Immediately clear the primary clipboard clip, if possible. */
    fun clearNow(context: Context) {
        clearJob?.cancel()
        clearJob = null
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
            // Ignore - clipboard service may be unavailable
        }
    }
}
