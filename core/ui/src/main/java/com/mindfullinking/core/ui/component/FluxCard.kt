package com.mindfullinking.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

enum class FluxCardVariant {
    Paper,      // White card on gray surface (#FFFFFF)
    Charcoal,   // Dark hero card (#222124)
    Canvas,     // Pale environmental lime (#DCEA95)
    Signal,     // Vivid action lime (#DAF163)
    Lavender,   // Data emphasis lavender (#BBB2F5)
    Surface     // Neutral gray (#E1E1E1)
}

/**
 * Geometric Bauhaus card wrapper with distinct radii and internal padding.
 */
@Composable
fun FluxCard(
    modifier: Modifier = Modifier,
    variant: FluxCardVariant = FluxCardVariant.Paper,
    shape: Shape = FluxTheme.shapes.cardPrimary,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(FluxTheme.spacing.cardPaddingRegular),
    content: @Composable () -> Unit
) {
    val containerColor = when (variant) {
        FluxCardVariant.Paper -> FluxPalette.Paper
        FluxCardVariant.Charcoal -> FluxPalette.Void
        FluxCardVariant.Canvas -> FluxPalette.CanvasLime
        FluxCardVariant.Signal -> FluxPalette.Signal
        FluxCardVariant.Lavender -> FluxPalette.Lavender
        FluxCardVariant.Surface -> FluxPalette.Surface
    }

    val contentColor = when (variant) {
        FluxCardVariant.Charcoal -> FluxPalette.Paper
        else -> FluxPalette.Void
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
