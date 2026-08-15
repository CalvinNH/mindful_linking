package com.mindfullinking.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Root Bauhaus design system theme wrapper.
 */
@Composable
fun FluxTheme(
    colors: FluxColors = FluxColors(),
    typography: FluxTypography = FluxTypography(),
    shapes: FluxShapes = FluxShapes(),
    spacing: FluxSpacing = FluxSpacing(),
    motion: FluxMotion = FluxMotion(),
    content: @Composable () -> Unit
) {
    val materialColorScheme = lightColorScheme(
        primary = colors.accentAction,
        onPrimary = colors.contentOnAction,
        surface = colors.surface,
        onSurface = colors.contentPrimary,
        background = colors.background,
        onBackground = colors.contentPrimary,
        secondary = colors.accentData,
        onSecondary = colors.contentOnData,
        error = colors.statusDanger,
        onError = FluxPalette.Paper
    )

    CompositionLocalProvider(
        LocalFluxColors provides colors,
        LocalFluxTypography provides typography,
        LocalFluxShapes provides shapes,
        LocalFluxSpacing provides spacing,
        LocalFluxMotion provides motion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}

object FluxTheme {
    val colors: FluxColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFluxColors.current

    val typography: FluxTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalFluxTypography.current

    val shapes: FluxShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalFluxShapes.current

    val spacing: FluxSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalFluxSpacing.current

    val motion: FluxMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalFluxMotion.current
}
