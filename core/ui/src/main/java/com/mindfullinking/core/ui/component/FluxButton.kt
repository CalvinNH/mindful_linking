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
import com.mindfullinking.core.ui.theme.FluxTheme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

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
    minWidth: Dp = FluxTheme.spacing.buttonMinWidth,
    contentPadding: PaddingValues = PaddingValues(horizontal = FluxTheme.spacing.xxl, vertical = FluxTheme.spacing.md),
    content: @Composable RowScope.() -> Unit
) {
    val colors = FluxTheme.colors
    val containerColor = when (variant) {
        FluxButtonVariant.Primary -> colors.accentAction
        FluxButtonVariant.Secondary -> colors.surface
        FluxButtonVariant.Dark -> colors.surfaceInverted
        FluxButtonVariant.Data -> colors.accentData
        FluxButtonVariant.Outlined -> Color.Transparent
        FluxButtonVariant.Ghost -> Color.Transparent
    }

    val contentColor = when (variant) {
        FluxButtonVariant.Primary -> colors.contentOnAction
        FluxButtonVariant.Secondary -> colors.contentPrimary
        FluxButtonVariant.Dark -> colors.contentOnInverted
        FluxButtonVariant.Data -> colors.contentOnData
        FluxButtonVariant.Outlined -> colors.contentPrimary
        FluxButtonVariant.Ghost -> colors.contentPrimary
    }

    val disabledContainerColor = when (variant) {
        FluxButtonVariant.Dark -> colors.contentSecondary.copy(alpha = 0.5f)
        FluxButtonVariant.Outlined, FluxButtonVariant.Ghost -> Color.Transparent
        else -> colors.background
    }

    val disabledContentColor = when (variant) {
        FluxButtonVariant.Dark -> colors.contentOnInverted.copy(alpha = 0.5f)
        else -> colors.contentSecondary
    }

    val border = if (variant == FluxButtonVariant.Outlined) {
        BorderStroke(FluxTheme.spacing.borderThin, if (enabled) colors.contentPrimary else colors.contentSecondary)
    } else null

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = FluxTheme.spacing.touchTargetMin)
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
                    modifier = Modifier.size(FluxTheme.spacing.iconMedium),
                    color = contentColor,
                    strokeWidth = FluxTheme.spacing.borderEmphasis
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
    minWidth: Dp = FluxTheme.spacing.buttonMinWidth,
    textStyle: TextStyle? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = FluxTheme.spacing.xxl, vertical = FluxTheme.spacing.md)
) {
    FluxButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        isLoading = isLoading,
        shape = shape,
        minWidth = minWidth,
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = textStyle ?: FluxTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = when (variant) {
                FluxButtonVariant.Dark -> FluxTheme.colors.contentOnInverted
                FluxButtonVariant.Primary -> FluxTheme.colors.contentOnAction
                FluxButtonVariant.Data -> FluxTheme.colors.contentOnData
                else -> FluxTheme.colors.contentPrimary
            }
        )
    }
}
