package github.aeonbtc.ibiswallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primary Bitcoin Orange - muted for professional look
val BitcoinOrange = Color(0xFFCD7F32)
val BitcoinOrangeLight = Color(0xFFE09530)
val BitcoinOrangeDark = Color(0xFFB87310)

// Dark theme backgrounds
val StandardDarkBackground = Color(0xFF0D1117)
val StandardDarkSurface = Color(0xFF161B22)
val StandardDarkSurfaceVariant = Color(0xFF21262D)
val StandardDarkCard = Color(0xFF1C2128)

val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceVariant = Color(0xFF111111)
val AmoledCard = Color(0xFF000000)

@Immutable
data class IbisThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val card: Color,
)

val StandardIbisThemeColors =
    IbisThemeColors(
        background = StandardDarkBackground,
        surface = StandardDarkSurface,
        surfaceVariant = StandardDarkSurfaceVariant,
        card = StandardDarkCard,
    )

val AmoledIbisThemeColors =
    IbisThemeColors(
        background = AmoledBackground,
        surface = AmoledSurface,
        surfaceVariant = AmoledSurfaceVariant,
        card = AmoledCard,
    )

val LocalIbisThemeColors = staticCompositionLocalOf { StandardIbisThemeColors }

val DarkBackground: Color
    @Composable get() = LocalIbisThemeColors.current.background

val DarkSurface: Color
    @Composable get() = LocalIbisThemeColors.current.surface

val DarkSurfaceVariant: Color
    @Composable get() = LocalIbisThemeColors.current.surfaceVariant

val DarkCard: Color
    @Composable get() = LocalIbisThemeColors.current.card

// Accent colors - muted versions
val AccentTeal = Color(0xFF1F9E8F)
val AccentGreen = Color(0xFF2D9F5D)
val AccentRed = Color(0xFFC94A4A)
val AccentBlue = Color(0xFF3B82F6)

// Text colors
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)
val TextTertiary = Color(0xFF6E7681)

// Border and divider
val BorderColor = Color(0xFF676767)

// Status colors - muted for professional look
val SuccessGreen = Color(0xFF2D9F5D)
val WarningYellow = Color(0xFFD4A921)
val ErrorRed = Color(0xFFC94A4A)
val TorPurple = Color(0xFF9B59B6)

// Dedicated Lightning accent
val LightningYellow = Color(0xFFEEB311)

// Layer 2 - Liquid Network branding
val LiquidTeal = Color(0xFF1F9E8F)
val SparkPurple = Color(0xFF583DA1)

// Drawer menu icon accent - muted orange
val DrawerIconColor = Color(0xFFCD7F32)
