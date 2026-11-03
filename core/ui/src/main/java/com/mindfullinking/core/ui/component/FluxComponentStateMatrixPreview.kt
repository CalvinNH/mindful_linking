package com.mindfullinking.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Design-time state matrix and screenshot baseline. It deliberately keeps both schemes in one
 * source so visual review catches a light-only component before feature code can depend on it.
 */
@Preview(name = "Flux controls — light", showBackground = true, backgroundColor = 0xFFE1E1E1)
@Composable
private fun FluxControlsLightPreview() = FluxControlsStateMatrix(darkTheme = false)

@Preview(name = "Flux controls — dark", showBackground = true, backgroundColor = 0xFF222124)
@Composable
private fun FluxControlsDarkPreview() = FluxControlsStateMatrix(darkTheme = true)

@Composable
private fun FluxControlsStateMatrix(darkTheme: Boolean) {
    FluxTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.padding(FluxTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md)
        ) {
            FluxButton(text = "Save link", onClick = {}, modifier = Modifier.fillMaxWidth())
            FluxButton(text = "Dark structural action", onClick = {}, variant = FluxButtonVariant.Dark, modifier = Modifier.fillMaxWidth())
            FluxButton(text = "Saving", onClick = {}, isLoading = true, modifier = Modifier.fillMaxWidth())
            FluxButton(text = "Unavailable", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            FluxInput(value = "", onValueChange = {}, label = "Link title", placeholder = "A clear title")
            FluxInput(value = "", onValueChange = {}, label = "Passphrase", placeholder = "Required", errorMessage = "A passphrase is required")
            FluxSwitch(checked = true, onCheckedChange = {}, label = "Deadline reminders")
            FluxTabs(tabs = listOf("Now", "Inbox"), selectedIndex = 0, onTabSelected = {})
            FluxCard(variant = FluxCardVariant.Charcoal) {
                Column(verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.sm)) {
                    FluxBadge(text = "ACTION", variant = FluxBadgeVariant.Action)
                    FluxBadge(text = "CHARCOAL", variant = FluxBadgeVariant.Charcoal)
                }
            }
        }
    }
}
