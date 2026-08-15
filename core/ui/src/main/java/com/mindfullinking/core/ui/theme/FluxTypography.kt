package com.mindfullinking.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Plus Jakarta Sans font family for contemporary Bauhaus visual hierarchy.
 * Default fallback to FontFamily.SansSerif ensures compilation without missing resources.
 */
val PlusJakartaSans = FontFamily.SansSerif

@Immutable
data class FluxTypography(
    val displayHero: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        color = FluxPalette.Void
    ),
    val displayTitle: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = FluxPalette.Void
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = FluxPalette.Void
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = FluxPalette.Void
    ),
    val titleSmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = FluxPalette.Void
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = FluxPalette.Void
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = FluxPalette.Void
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = FluxPalette.CharcoalMuted
    ),
    val labelLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = FluxPalette.Void
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = FluxPalette.Void
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = FluxPalette.CharcoalMuted
    ),
    val metricHero: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        fontFeatureSettings = "tnum",
        color = FluxPalette.Void
    ),
    val metricLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontFeatureSettings = "tnum",
        color = FluxPalette.Void
    )
)

val LocalFluxTypography = staticCompositionLocalOf { FluxTypography() }
