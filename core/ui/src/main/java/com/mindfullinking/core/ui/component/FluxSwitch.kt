package com.mindfullinking.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Custom Bauhaus Switch component with 48dp minimum touch target.
 * Checked track: Signal Lime (#DAF163), Thumb: Void Charcoal (#222124).
 */
@Composable
fun FluxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor = if (checked) FluxPalette.Signal else FluxPalette.Surface
    val thumbColor = if (checked) FluxPalette.Void else FluxPalette.CharcoalMuted
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = FluxTheme.motion.baseSpec(),
        label = "switchThumbAnimation"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = FluxTheme.spacing.touchTargetMin, minHeight = FluxTheme.spacing.touchTargetMin)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .semantics { role = Role.Switch },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 28.dp)
                .clip(FluxTheme.shapes.switchTrack)
                .background(trackColor)
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(FluxTheme.shapes.switchThumb)
                    .background(thumbColor)
            )
        }
    }
}
