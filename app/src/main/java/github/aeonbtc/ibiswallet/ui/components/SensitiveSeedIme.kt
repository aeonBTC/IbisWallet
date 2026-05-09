package github.aeonbtc.ibiswallet.ui.components

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

private const val WRITING_TOOLS_API_LEVEL = 36

fun sensitiveSeedKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
): KeyboardOptions =
    KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = keyboardType,
    )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SensitiveSeedIme(content: @Composable () -> Unit) {
    val interceptor =
        remember {
            PlatformTextInputInterceptor { request, nextHandler ->
                val wrappedRequest =
                    PlatformTextInputMethodRequest { outAttributes ->
                        val inputConnection = request.createInputConnection(outAttributes)
                        applySensitiveSeedImeFlags(outAttributes)
                        inputConnection
                    }
                nextHandler.startInputMethod(wrappedRequest)
            }
        }

    InterceptPlatformTextInput(
        interceptor = interceptor,
        content = content,
    )
}

private fun applySensitiveSeedImeFlags(editorInfo: EditorInfo) {
    // Best-effort privacy hints; cooperative IMEs may still ignore them.
    editorInfo.inputType = editorInfo.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    editorInfo.imeOptions =
        editorInfo.imeOptions or
            EditorInfo.IME_FLAG_FORCE_ASCII or
            EditorInfo.IME_FLAG_NO_FULLSCREEN

    editorInfo.imeOptions =
        editorInfo.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

    if (Build.VERSION.SDK_INT >= WRITING_TOOLS_API_LEVEL) {
        editorInfo.isWritingToolsEnabled = false
    }
}
