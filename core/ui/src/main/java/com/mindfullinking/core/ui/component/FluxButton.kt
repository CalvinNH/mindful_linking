package com.mindfullinking.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

enum class FluxButtonVariant {
    Primary,    // Signal Lime background (#DAF163) + Void Charcoal text (#222124)
    Secondary,  // Paper White background (#FFFFFF) + Void Charcoal text (#222124)
    Dark,       // Void Charcoal background (#222124) + Paper White text (#FFFFFF)
    Data,       // Lavender background (#BBB2F5) + Void Charcoal text (#222124)
    Outlined,   // Transparent + Void Charcoal border & text
    Ghost       // Transparent + Void Charcoal text
}

/**
 * Bauhaus FluxButton wrapping Material3 Button primitive.
 * Enforces foreground/background contrast compliance: Charcoal text on lime/white/lavender, White text ONLY on charcoal.
 * Enforces minimum 48dp touch target.
 */
@Composable
fun FluxButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FluxButtonVariant = FluxButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape = FluxTheme.shapes.button,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val containerColor = when (variant) {
        FluxButtonVariant.Primary -> FluxPalette.Signal
        FluxButtonVariant.Secondary -> FluxPalette.Paper
        FluxButtonVariant.Dark -> FluxPalette.Void
        FluxButtonVariant.Data -> FluxPalette.Lavender
        FluxButtonVariant.Outlined -> Color.Transparent
        FluxButtonVariant.Ghost -> Color.Transparent
    }

    val contentColor = when (variant) {
        FluxButtonVariant.Primary -> FluxPalette.Void
        FluxButtonVariant.Secondary -> FluxPalette.Void
        FluxButtonVariant.Dark -> FluxPalette.Paper
        FluxButtonVariant.Data -> FluxPalette.Void
        FluxButtonVariant.Outlined -> FluxPalette.Void
        FluxButtonVariant.Ghost -> FluxPalette.Void
    }

    val disabledContainerColor = when (variant) {
        FluxButtonVariant.Dark -> FluxPalette.CharcoalMuted.copy(alpha = 0.5f)
        FluxButtonVariant.Outlined, FluxButtonVariant.Ghost -> Color.Transparent
        else -> FluxPalette.Surface
    }

    val disabledContentColor = when (variant) {
        FluxButtonVariant.Dark -> FluxPalette.Paper.copy(alpha = 0.5f)
        else -> FluxPalette.CharcoalMuted
    }

    val border = if (variant == FluxButtonVariant.Outlined) {
        BorderStroke(1.5.dp, if (enabled) FluxPalette.Void else FluxPalette.CharcoalMuted)
    } else null

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = FluxTheme.spacing.touchTargetMin)
            .semantics { role = Role.Button },
        enabled = enabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        ),
        border = border,
        contentPadding = contentPadding
    ) {
        if (isLoading) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.5.dp
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Text convenience overload for FluxButton.
 */
@Composable
fun FluxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FluxButtonVariant = FluxButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shape: Shape = FluxTheme.shapes.button,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
) {
    FluxButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        isLoading = isLoading,
        shape = shape,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = FluxTheme.typography.labelLarge,
            color = if (variant == FluxButtonVariant.Dark) FluxPalette.Paper else FluxPalette.Void
        )
    }
}

