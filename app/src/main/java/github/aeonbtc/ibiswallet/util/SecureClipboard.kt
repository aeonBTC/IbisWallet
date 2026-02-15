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
        label: String,
        text: String,
    ) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Use generic label on Android < 13 to avoid leaking content type
        // to clipboard managers. On 13+ the EXTRA_IS_SENSITIVE flag hides previews.
        val safeLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) label else "Text"
        val clipData = ClipData.newPlainText(safeLabel, text)

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
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (_: Exception) {
                    // Ignore - app may have been killed
                }
            }
    }
}
