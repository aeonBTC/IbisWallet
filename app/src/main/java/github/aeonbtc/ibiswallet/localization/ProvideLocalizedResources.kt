package github.aeonbtc.ibiswallet.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

/**
 * Re-applies [LocalConfiguration] and [LocalResources] for [LocalAppLocale].
 *
 * Compose [androidx.compose.ui.window.Dialog] windows do not reliably inherit the Activity's
 * localized context; `stringResource()` reads [LocalResources], which must match the locale or UI
 * stays in default (English) while unrelated controls may still pick up translations. Keep
 * [LocalContext] as the Activity context so click handlers can safely launch intents.
 */
@Composable
fun ProvideLocalizedResources(content: @Composable () -> Unit) {
    val appLocale = LocalAppLocale.current
    val baseContext = LocalContext.current.applicationContext
    val localizedContext =
        remember(appLocale, baseContext) {
            AppLocale.createLocalizedContext(baseContext, appLocale)
        }
    val localizedResources = localizedContext.resources
    CompositionLocalProvider(
        LocalAppLocale provides appLocale,
        LocalConfiguration provides localizedResources.configuration,
        LocalResources provides localizedResources,
    ) {
        content()
    }
}
