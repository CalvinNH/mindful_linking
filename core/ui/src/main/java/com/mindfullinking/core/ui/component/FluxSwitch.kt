package com.mindfullinking.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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
    enabled: Boolean = true,
    label: String? = null
) {
    val colors = FluxTheme.colors
    val trackColor = if (checked) colors.accentAction else colors.background
    val thumbColor = if (checked) colors.contentOnAction else colors.contentSecondary
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) FluxTheme.spacing.switchThumb else FluxTheme.spacing.switchInset,
        animationSpec = FluxTheme.motion.baseSpec(),
        label = "switchThumbAnimation"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = FluxTheme.spacing.touchTargetMin, minHeight = FluxTheme.spacing.touchTargetMin)
            .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) })
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
                if (label != null) contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = FluxTheme.spacing.switchTrackWidth, height = FluxTheme.spacing.switchTrackHeight)
                .clip(FluxTheme.shapes.switchTrack)
                .background(trackColor)
                .padding(FluxTheme.spacing.switchInset)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(FluxTheme.spacing.switchThumb)
                    .clip(FluxTheme.shapes.switchThumb)
                    .background(thumbColor)
            )
        }
    }
}
