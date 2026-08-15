package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.theme.FluxPalette
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
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = if (isFocused) FluxPalette.Void else FluxPalette.Surface
    val borderWidth = if (isFocused) 2.dp else 1.dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FluxTheme.spacing.touchTargetMin)
            .onFocusChanged { isFocused = it.isFocused },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = FluxTheme.typography.bodyLarge.copy(color = FluxPalette.Void),
        cursorBrush = SolidColor(FluxPalette.Void),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FluxTheme.shapes.input)
                    .background(FluxPalette.Paper)
                    .border(borderWidth, borderColor, FluxTheme.shapes.input)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = FluxTheme.typography.bodyLarge,
                                color = FluxPalette.CharcoalMuted
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        trailingIcon()
                    }
                }
            }
        }
    )
}
