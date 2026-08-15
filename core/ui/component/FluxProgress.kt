package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

enum class FluxProgressVariant {
    Action,   // Signal Lime progress track (#DAF163)
    Data,     // Lavender progress track (#BBB2F5)
    Charcoal  // Charcoal progress track (#222124)
}

/**
 * Custom Bauhaus progress indicator with rounded caps and controlled track height.
 */
@Composable
fun FluxProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    variant: FluxProgressVariant = FluxProgressVariant.Action,
    height: Dp = 10.dp,
    trackColor: Color = FluxPalette.Surface
) {
    val progressColor = when (variant) {
        FluxProgressVariant.Action -> FluxPalette.Signal
        FluxProgressVariant.Data -> FluxPalette.Lavender
        FluxProgressVariant.Charcoal -> FluxPalette.Void
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(FluxTheme.shapes.pill)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(FluxTheme.shapes.pill)
                .background(progressColor)
        )
    }
}
