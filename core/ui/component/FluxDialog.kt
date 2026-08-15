package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

enum class FluxDialogStyle {
    Charcoal, // Dark modal with white text, lime primary action (for Over-budget modal)
    Paper     // Light modal with charcoal text
}

/**
 * Bauhaus Modal Dialog wrapper.
 * Strictly enforces Charcoal text on Lime action buttons and White text ONLY on Charcoal surfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluxDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    style: FluxDialogStyle = FluxDialogStyle.Charcoal,
    content: (@Composable () -> Unit)? = null
) {
    val containerColor = if (style == FluxDialogStyle.Charcoal) FluxPalette.Void else FluxPalette.Paper
    val titleColor = if (style == FluxDialogStyle.Charcoal) FluxPalette.Paper else FluxPalette.Void
    val bodyColor = if (style == FluxDialogStyle.Charcoal) FluxPalette.Paper.copy(alpha = 0.85f) else FluxPalette.CharcoalMuted

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Surface(
            shape = FluxTheme.shapes.dialog,
            color = containerColor,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = FluxTheme.typography.titleLarge,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = body,
                    style = FluxTheme.typography.bodyMedium,
                    color = bodyColor
                )

                if (content != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (secondaryButtonText != null && onSecondaryClick != null) {
                        FluxButton(
                            text = secondaryButtonText,
                            onClick = onSecondaryClick,
                            variant = if (style == FluxDialogStyle.Charcoal) FluxButtonVariant.Dark else FluxButtonVariant.Ghost,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    FluxButton(
                        text = primaryButtonText,
                        onClick = onPrimaryClick,
                        variant = if (style == FluxDialogStyle.Charcoal) FluxButtonVariant.Primary else FluxButtonVariant.Primary
                    )
                }
            }
        }
    }
}
