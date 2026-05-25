package github.aeonbtc.ibiswallet.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import kotlin.math.min

private fun darkColorSchemeFor(themeColors: IbisThemeColors): ColorScheme =
    darkColorScheme(
        primary = BitcoinOrange,
        onPrimary = themeColors.background,
        primaryContainer = BitcoinOrangeDark,
        onPrimaryContainer = TextPrimary,
        secondary = AccentTeal,
        onSecondary = themeColors.background,
        secondaryContainer = themeColors.surfaceVariant,
        onSecondaryContainer = TextPrimary,
        tertiary = AccentGreen,
        onTertiary = themeColors.background,
        tertiaryContainer = themeColors.surfaceVariant,
        onTertiaryContainer = TextPrimary,
        error = ErrorRed,
        onError = TextPrimary,
        errorContainer = ErrorRed.copy(alpha = 0.2f),
        onErrorContainer = ErrorRed,
        background = themeColors.background,
        onBackground = TextPrimary,
        surface = themeColors.surface,
        onSurface = TextPrimary,
        surfaceVariant = themeColors.surfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = BorderColor,
        outlineVariant = BorderColor.copy(alpha = 0.5f),
        inverseSurface = TextPrimary,
        inverseOnSurface = themeColors.background,
        inversePrimary = BitcoinOrangeDark,
    )

@Composable
@Suppress("DEPRECATION")
fun IbisWalletTheme(
    themeMode: String = SecureStorage.THEME_MODE_DARK,
    content: @Composable () -> Unit,
) {
    val themeColors =
        if (themeMode == SecureStorage.THEME_MODE_AMOLED) {
            AmoledIbisThemeColors
        } else {
            StandardIbisThemeColors
        }
    val colorScheme = darkColorSchemeFor(themeColors)
    val density = LocalDensity.current
    val clampedDensity =
        Density(
            density = density.density,
            fontScale = min(density.fontScale, 1.15f),
        )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = themeColors.background.toArgb()
            window.navigationBarColor = themeColors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(
        LocalDensity provides clampedDensity,
        LocalIbisThemeColors provides themeColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
