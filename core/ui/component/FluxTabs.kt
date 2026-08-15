package com.mindfullinking.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
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
    containerColor: Color = FluxPalette.Surface
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FluxTheme.shapes.pill)
            .background(containerColor)
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) FluxPalette.Signal else Color.Transparent,
                    animationSpec = FluxTheme.motion.baseSpec(),
                    label = "tabBackgroundAnimation"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) FluxPalette.Void else FluxPalette.CharcoalMuted,
                    animationSpec = FluxTheme.motion.baseSpec(),
                    label = "tabTextAnimation"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(FluxTheme.shapes.pill)
                        .background(backgroundColor)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
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
