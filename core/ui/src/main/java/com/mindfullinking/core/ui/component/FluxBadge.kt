package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.mindfullinking.core.ui.theme.FluxTheme

enum class FluxBadgeVariant {
    Action,    // Signal Lime (#DAF163) + Void Charcoal (#222124)
    Data,      // Lavender (#BBB2F5) + Void Charcoal (#222124)
    Charcoal,  // Void Charcoal (#222124) + Paper White (#FFFFFF)
    Muted      // Surface Gray / Lavender Muted (#B1ADCC) + Void Charcoal (#222124)
}

/**
 * Pill-shaped badge component for priority levels, point metrics (0.25 pt), and status tags.
 */
@Composable
fun FluxBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: FluxBadgeVariant = FluxBadgeVariant.Action
) {
    val colors = FluxTheme.colors
    val containerColor = when (variant) {
        FluxBadgeVariant.Action -> colors.accentAction
        FluxBadgeVariant.Data -> colors.accentData
        FluxBadgeVariant.Charcoal -> colors.surfaceInverted
        FluxBadgeVariant.Muted -> colors.accentDataMuted
    }

    val contentColor = when (variant) {
        FluxBadgeVariant.Charcoal -> colors.contentOnInverted
        else -> colors.contentPrimary
    }

    Box(
        modifier = modifier
            .clip(FluxTheme.shapes.badge)
            .background(containerColor)
            .padding(horizontal = FluxTheme.spacing.badgeHorizontalPadding, vertical = FluxTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = FluxTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
