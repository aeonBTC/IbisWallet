package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

@Composable
fun rememberBringIntoViewRequesterOnExpand(
    expanded: Boolean,
    key: Any? = Unit,
    delayMs: Long = 180L,
): BringIntoViewRequester {
    val requester = remember(key) { BringIntoViewRequester() }

    LaunchedEffect(expanded, key) {
        if (!expanded) return@LaunchedEffect
        delay(delayMs)
        requester.bringIntoView()
    }

    return requester
}
