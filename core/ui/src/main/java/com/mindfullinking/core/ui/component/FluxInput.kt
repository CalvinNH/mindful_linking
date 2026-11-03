package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Custom Bauhaus text input wrapper.
 * White background, Charcoal text, 16dp rounded corners, minimum 48dp height.
 */
@Composable
fun FluxInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var isFocused by remember { mutableStateOf(false) }

    val colors = FluxTheme.colors
    val borderColor = when {
        errorMessage != null -> colors.statusDanger
        isFocused -> colors.contentPrimary
        else -> colors.border
    }
    val borderWidth = if (isFocused || errorMessage != null) FluxTheme.spacing.borderEmphasis else FluxTheme.spacing.borderThin

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = FluxTheme.typography.labelMedium, color = colors.contentPrimary)
            Spacer(modifier = Modifier.height(FluxTheme.spacing.xs))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FluxTheme.spacing.touchTargetMin)
                .onFocusChanged { isFocused = it.isFocused }
                .semantics {
                    contentDescription = label ?: placeholder
                    stateDescription = when {
                        !enabled -> "Disabled"
                        errorMessage != null -> errorMessage
                        else -> "Editable"
                    }
                },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = FluxTheme.typography.bodyLarge.copy(color = colors.contentPrimary),
            cursorBrush = SolidColor(colors.contentPrimary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FluxTheme.shapes.input)
                        .background(colors.surface)
                        .border(borderWidth, borderColor, FluxTheme.shapes.input)
                        .padding(horizontal = FluxTheme.spacing.lg, vertical = FluxTheme.spacing.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (leadingIcon != null) {
                            leadingIcon()
                            Spacer(modifier = Modifier.width(FluxTheme.spacing.md))
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, style = FluxTheme.typography.bodyLarge, color = colors.contentSecondary)
                            }
                            innerTextField()
                        }
                        if (trailingIcon != null) {
                            Spacer(modifier = Modifier.width(FluxTheme.spacing.md))
                            trailingIcon()
                        }
                    }
                }
            }
        )
        if (errorMessage != null) {
            Text(errorMessage, style = FluxTheme.typography.bodySmall, color = colors.statusDanger)
        }
    }
}
