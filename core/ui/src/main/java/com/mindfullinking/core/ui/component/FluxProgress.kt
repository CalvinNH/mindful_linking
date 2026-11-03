package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
    height: Dp = FluxTheme.spacing.progressHeight,
    trackColor: Color = FluxTheme.colors.background,
    label: String = "Progress"
) {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val progressColor = when (variant) {
        FluxProgressVariant.Action -> FluxTheme.colors.accentAction
        FluxProgressVariant.Data -> FluxTheme.colors.accentData
        FluxProgressVariant.Charcoal -> FluxTheme.colors.surfaceInverted
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(FluxTheme.shapes.pill)
            .background(trackColor)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(normalizedProgress, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(normalizedProgress)
                .clip(FluxTheme.shapes.pill)
                .background(progressColor)
        )
    }
}
