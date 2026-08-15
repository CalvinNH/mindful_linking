package com.mindfullinking.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Metric stat block for displaying point budget counts, time estimates, and link metrics.
 */
@Composable
fun FluxStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    isOnDark: Boolean = false,
    accentColor: Color? = null
) {
    val labelColor = if (isOnDark) FluxPalette.Paper.copy(alpha = 0.7f) else FluxPalette.CharcoalMuted
    val valueColor = accentColor ?: (if (isOnDark) FluxPalette.Paper else FluxPalette.Void)

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = FluxTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (unit != null) "$value $unit" else value,
            style = FluxTheme.typography.metricLarge,
            color = valueColor
        )
    }
}
