package github.aeonbtc.ibiswallet.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import kotlin.math.min

private val DarkColorScheme =
    darkColorScheme(
        primary = BitcoinOrange,
        onPrimary = DarkBackground,
        primaryContainer = BitcoinOrangeDark,
        onPrimaryContainer = TextPrimary,
        secondary = AccentTeal,
        onSecondary = DarkBackground,
        secondaryContainer = DarkSurfaceVariant,
        onSecondaryContainer = TextPrimary,
        tertiary = AccentGreen,
        onTertiary = DarkBackground,
        tertiaryContainer = DarkSurfaceVariant,
        onTertiaryContainer = TextPrimary,
        error = ErrorRed,
        onError = TextPrimary,
        errorContainer = ErrorRed.copy(alpha = 0.2f),
        onErrorContainer = ErrorRed,
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = BorderColor,
        outlineVariant = BorderColor.copy(alpha = 0.5f),
        inverseSurface = TextPrimary,
        inverseOnSurface = DarkBackground,
        inversePrimary = BitcoinOrangeDark,
    )

@Composable
fun IbisWalletTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme // Always use dark theme for this app
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalDensity provides clampedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
