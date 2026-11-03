package com.mindfullinking.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mindfullinking.core.ui.theme.FluxTheme
import com.mindfullinking.core.ui.theme.FluxColors
import com.mindfullinking.core.ui.theme.FluxPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-test scaffold for accessibility and screenshot baselines. Screenshot capture can be
 * added by the host test runner without altering the semantic assertions below.
 */
@RunWith(AndroidJUnit4::class)
class FluxAccessibilityScaffoldTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun labelled_controls_expose_their_talkback_names() {
        composeRule.setContent {
            FluxTheme {
                FluxInput(value = "", onValueChange = {}, label = "Backup passphrase")
                FluxSwitch(checked = false, onCheckedChange = {}, label = "Deadline reminders")
                FluxButton(text = "Save link", onClick = {})
            }
        }

        composeRule.onNodeWithContentDescription("Backup passphrase").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Deadline reminders").assertIsDisplayed()
        composeRule.onNodeWithText("Save link").assertIsDisplayed()
    }

    @Test
    fun dark_structural_roles_keep_paper_foreground_on_charcoal() {
        val colors = FluxColors.dark()

        assertEquals(FluxPalette.Void, colors.surfaceInverted)
        assertEquals(FluxPalette.Paper, colors.contentOnInverted)
    }
}
