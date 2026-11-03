package com.mindfullinking.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import java.util.Locale

/**
 * 100.00-point Attention Budget Arc Gauge.
 * Charcoal structural frame with Signal Lime progress track and tabular numerical readouts.
 */
@Composable
fun BudgetGauge(
    usedPoints: Float,
    effectiveBudget: Float = 100.00f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 20.dp,
    isOnDark: Boolean = false
) {
    val progressFraction = (usedPoints / effectiveBudget).coerceIn(0f, 1.5f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = FluxTheme.motion.revealSpec(),
        label = "gaugeProgressAnimation"
    )

    val isOverBudget = usedPoints > effectiveBudget

    val trackColor = if (isOnDark) FluxPalette.CharcoalMuted.copy(alpha = 0.4f) else FluxPalette.Surface
    val gaugeColor = when {
        isOverBudget -> FluxPalette.Signal
        usedPoints > 85f -> FluxPalette.Lavender
        else -> FluxPalette.Signal
    }

    val usedFormatted = String.format(Locale.ROOT, "%.2f", usedPoints)
    val totalFormatted = String.format(Locale.ROOT, "%.0f", effectiveBudget)
    val accessibilityLabel = "Attention budget gauge: $usedFormatted out of $totalFormatted points used"

    Box(
        modifier = modifier
            .semantics { contentDescription = accessibilityLabel }
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = size.toPx() - strokePx
            val startAngle = 135f
            val sweepAngle = 270f

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress Arc
            drawArc(
                color = gaugeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress.coerceAtMost(1f),
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "ATTENTION BUDGET",
                style = FluxTheme.typography.labelSmall,
                color = if (isOnDark) FluxPalette.Paper.copy(alpha = 0.7f) else FluxPalette.CharcoalMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = usedFormatted,
                style = FluxTheme.typography.metricHero,
                color = if (isOnDark) FluxPalette.Paper else FluxPalette.Void
            )
            Text(
                text = "/ $totalFormatted PTS",
                style = FluxTheme.typography.labelMedium,
                color = if (isOnDark) FluxPalette.Paper.copy(alpha = 0.8f) else FluxPalette.CharcoalMuted
            )

            if (isOverBudget) {
                Spacer(modifier = Modifier.height(8.dp))
                FluxBadge(
                    text = "TEMPORARY OVERAGE",
                    variant = FluxBadgeVariant.Action
                )
            }
        }
    }
}
