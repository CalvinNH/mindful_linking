package com.mindfullinking.core.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Recommendation Mark Hero Card.
 * Displays the top recommended link with deterministic factor badges and primary action CTA.
 */
@Composable
fun RecommendationMark(
    title: String,
    url: String,
    explanation: String,
    priority: Int,
    durationMinutes: Int,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipClick: (() -> Unit)? = null,
    isReconsiderFlagged: Boolean = false
) {
    val accessibilityDescription = "Recommended link: $title. $explanation. Priority $priority, estimated $durationMinutes minutes."

    FluxCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityDescription },
        variant = FluxCardVariant.Paper,
        shape = FluxTheme.shapes.cardPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxBadge(
                        text = "PRIORITY $priority",
                        variant = FluxBadgeVariant.Action
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FluxBadge(
                        text = "${durationMinutes}M",
                        variant = FluxBadgeVariant.Data
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = FluxTheme.typography.titleLarge,
                color = FluxPalette.Void
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = url,
                style = FluxTheme.typography.bodySmall,
                color = FluxPalette.CharcoalMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "WHY: $explanation",
                style = FluxTheme.typography.labelMedium,
                color = FluxPalette.Void
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSkipClick != null) {
                    FluxButton(
                        text = if (isReconsiderFlagged) "RECONSIDER" else "SKIP",
                        onClick = onSkipClick,
                        variant = FluxButtonVariant.Ghost,
                        enabled = !isReconsiderFlagged,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                FluxButton(
                    text = "OPEN LINK",
                    onClick = onOpenClick,
                    variant = FluxButtonVariant.Primary
                )
            }
        }
    }
}
