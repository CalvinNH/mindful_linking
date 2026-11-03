package com.mindfullinking.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Segmented pill tab bar component.
 * Charcoal text on Signal Lime selected state; Charcoal text on Gray unselected state.
 */
@Composable
fun FluxTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FluxTheme.colors.background
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FluxTheme.shapes.pill)
            .background(containerColor)
            .padding(FluxTheme.spacing.xs)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) FluxTheme.colors.accentAction else Color.Transparent,
                    animationSpec = FluxTheme.motion.baseSpec(),
                    label = "tabBackgroundAnimation"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) FluxTheme.colors.contentOnAction else FluxTheme.colors.contentSecondary,
                    animationSpec = FluxTheme.motion.baseSpec(),
                    label = "tabTextAnimation"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = FluxTheme.spacing.touchTargetMin)
                        .clip(FluxTheme.shapes.pill)
                        .background(backgroundColor)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) }
                        )
                        .padding(vertical = FluxTheme.spacing.sm, horizontal = FluxTheme.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = FluxTheme.typography.labelLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}
