package github.aeonbtc.ibiswallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage

private val OpenRundeFontFamily =
    FontFamily(
        Font(R.font.open_runde_regular, FontWeight.Normal),
        Font(R.font.open_runde_medium, FontWeight.Medium),
        Font(R.font.open_runde_semibold, FontWeight.SemiBold),
        Font(R.font.open_runde_bold, FontWeight.Bold),
    )

private val AtkinsonHyperlegibleFontFamily =
    FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Medium),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.SemiBold),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )

private fun fontFamilyFor(typeface: String): FontFamily =
    when (typeface) {
        SecureStorage.TYPEFACE_ATKINSON_HYPERLEGIBLE -> AtkinsonHyperlegibleFontFamily
        SecureStorage.TYPEFACE_OPEN_RUNDE -> OpenRundeFontFamily
        else -> FontFamily.SansSerif
    }

fun ibisTypography(typeface: String) =
    fontFamilyFor(typeface).let { appFontFamily ->
    val sizeScale = if (typeface == SecureStorage.TYPEFACE_ATKINSON_HYPERLEGIBLE) 1.04f else 1f

    Typography(
        displaySmall =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (31 * sizeScale).sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (28 * sizeScale).sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (24 * sizeScale).sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (22 * sizeScale).sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (16 * sizeScale).sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * sizeScale).sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = (16 * sizeScale).sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = (14 * sizeScale).sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = (13 * sizeScale).sp,
                lineHeight = 17.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * sizeScale).sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (13 * sizeScale).sp,
                lineHeight = 17.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = (12 * sizeScale).sp,
                lineHeight = 17.sp,
                letterSpacing = 0.5.sp,
            ),
    )
    }
