package com.mindfullinking.core.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.ui.chart.BudgetGauge
import com.mindfullinking.core.ui.chart.RecommendationMark
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxEmptyState
import com.mindfullinking.core.ui.component.FluxInput
import com.mindfullinking.core.ui.component.FluxProgress
import com.mindfullinking.core.ui.component.FluxProgressVariant
import com.mindfullinking.core.ui.component.FluxStat
import com.mindfullinking.core.ui.component.FluxSwitch
import com.mindfullinking.core.ui.component.FluxTabs
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

/**
 * Interactive Design System Gallery showcasing all Bauhaus Flux components, swatches, typography, and dialogs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemGallery() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var switchState by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    var showOverBudgetDialog by remember { mutableStateOf(false) }
    var demoGaugePoints by remember { mutableStateOf(78.25f) }

    FluxTheme {
        Scaffold(
            containerColor = FluxPalette.Surface
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "BAUHAUS DESIGN SYSTEM",
                    style = FluxTheme.typography.labelSmall,
                    color = FluxPalette.CharcoalMuted
                )
                Text(
                    text = "Gallery & Tokens",
                    style = FluxTheme.typography.displayHero,
                    color = FluxPalette.Void
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Section 1: Measured Palette Swatches
                GallerySectionHeader("1. Measured Reference Palette")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PaletteSwatch("Void", "#222124", FluxPalette.Void, isDark = true)
                    PaletteSwatch("Paper", "#FFFFFF", FluxPalette.Paper)
                    PaletteSwatch("Surface", "#E1E1E1", FluxPalette.Surface)
                    PaletteSwatch("CanvasLime", "#DCEA95", FluxPalette.CanvasLime)
                    PaletteSwatch("Signal", "#DAF163", FluxPalette.Signal)
                    PaletteSwatch("Lavender", "#BBB2F5", FluxPalette.Lavender)
                    PaletteSwatch("MutedLav", "#B1ADCC", FluxPalette.LavenderMuted)
                    PaletteSwatch("CharcoalMuted", "#4C4C49", FluxPalette.CharcoalMuted, isDark = true)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section 2: Typography Scale
                GallerySectionHeader("2. Typography Scale (Plus Jakarta Sans)")
                FluxCard(variant = FluxCardVariant.Paper) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Display Hero 36sp", style = FluxTheme.typography.displayHero)
                        Text("Display Title 28sp", style = FluxTheme.typography.displayTitle)
                        Text("Title Large 22sp", style = FluxTheme.typography.titleLarge)
                        Text("Title Medium 18sp", style = FluxTheme.typography.titleMedium)
                        Text("Body Large 16sp", style = FluxTheme.typography.bodyLarge)
                        Text("Body Medium 14sp", style = FluxTheme.typography.bodyMedium)
                        Text("LABEL SMALL METADATA TRACKING", style = FluxTheme.typography.labelSmall)
                        Text("Metric Hero: 100.00 PTS", style = FluxTheme.typography.metricHero)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section 3: Flux Buttons
                GallerySectionHeader("3. Flux Buttons (Strict Contrast Rules)")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FluxButton(text = "PRIMARY (LIME)", onClick = {}, variant = FluxButtonVariant.Primary)
                    FluxButton(text = "SECONDARY (WHITE)", onClick = {}, variant = FluxButtonVariant.Secondary)
                    FluxButton(text = "DARK (CHARCOAL)", onClick = {}, variant = FluxButtonVariant.Dark)
                    FluxButton(text = "DATA (LAVENDER)", onClick = {}, variant = FluxButtonVariant.Data)
                    FluxButton(text = "OUTLINED", onClick = {}, variant = FluxButtonVariant.Outlined)
                    FluxButton(text = "DISABLED", onClick = {}, enabled = false)
                    FluxButton(text = "LOADING", onClick = {}, isLoading = true)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section 4: Flux Cards
                GallerySectionHeader("4. Flux Cards & Radii")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FluxCard(variant = FluxCardVariant.Paper) {
                        Text("Paper Card (24dp radius)", style = FluxTheme.typography.titleMedium)
                    }
                    FluxCard(variant = FluxCardVariant.Charcoal) {
                        Text("Charcoal Hero Card (24dp radius)", style = FluxTheme.typography.titleMedium, color = FluxPalette.Paper)
                    }
                    FluxCard(variant = FluxCardVariant.Canvas) {
                        Text("Environmental Canvas Lime Card", style = FluxTheme.typography.titleMedium)
                    }
                    FluxCard(variant = FluxCardVariant.Lavender) {
                        Text("Data Lavender Card", style = FluxTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section 5: Badges & Progress & Stats
                GallerySectionHeader("5. Badges, Progress & Stats")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluxBadge(text = "PRIORITY 5", variant = FluxBadgeVariant.Action)
                    FluxBadge(text = "0.25 PT", variant = FluxBadgeVariant.Data)
                    FluxBadge(text = "CHARCOAL", variant = FluxBadgeVariant.Charcoal)
                    FluxBadge(text = "MUTED", variant = FluxBadgeVariant.Muted)
                }
                Spacer(modifier = Modifier.height(16.dp))
                FluxProgress(progress = 0.65f, variant = FluxProgressVariant.Action)
                Spacer(modifier = Modifier.height(8.dp))
                FluxProgress(progress = 0.35f, variant = FluxProgressVariant.Data)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    FluxStat(label = "Active Budget", value = "78.25", unit = "PTS")
                    FluxStat(label = "Archived Links", value = "16", unit = "ITEMS")
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section 6: Controls (Tabs, Switch, Input)
                GallerySectionHeader("6. Controls (Tabs, Switch, Input)")
                FluxTabs(
                    tabs = listOf("NOW", "INBOX", "ARCHIVE"),
                    selectedIndex = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxSwitch(checked = switchState, onCheckedChange = { switchState = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = if (switchState) "Notifications Enabled" else "Notifications Disabled", style = FluxTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                FluxInput(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = "Search or paste link URL..."
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Section 7: Budget Gauge & Recommendation Card
                GallerySectionHeader("7. Budget Gauge & Recommendation Hero")
                FluxCard(variant = FluxCardVariant.Charcoal) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BudgetGauge(usedPoints = demoGaugePoints, isOnDark = true)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FluxButton("45 PTS", onClick = { demoGaugePoints = 45.00f }, variant = FluxButtonVariant.Secondary)
                            FluxButton("78 PTS", onClick = { demoGaugePoints = 78.25f }, variant = FluxButtonVariant.Secondary)
                            FluxButton("105 PTS", onClick = { demoGaugePoints = 105.00f }, variant = FluxButtonVariant.Primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                RecommendationMark(
                    title = "Deep Dive into Compose Performance Optimization",
                    url = "https://developer.android.com/jetpack/compose/performance",
                    explanation = "Priority 5 and fits your 15 minutes",
                    priority = 5,
                    durationMinutes = 15,
                    onOpenClick = {},
                    onSkipClick = {}
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Section 8: Over-Budget Modal & Empty State
                GallerySectionHeader("8. Over-Budget Modal & Empty State")
                FluxButton(
                    text = "TRIGGER OVER-BUDGET MODAL",
                    onClick = { showOverBudgetDialog = true },
                    variant = FluxButtonVariant.Dark,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                FluxEmptyState(
                    title = "Your Link Ledger is Clean",
                    body = "Share articles or URLs from any Android app to start building your deliberate leisure queue.",
                    actionText = "ADD A LINK",
                    onActionClick = {}
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showOverBudgetDialog) {
            FluxDialog(
                onDismissRequest = { showOverBudgetDialog = false },
                title = "Attention Budget Exceeded",
                body = "Adding this link increases your total active budget above 100.00 points (Currently 105.00 pts). Approve temporary overage or resolve existing links to continue.",
                primaryButtonText = "APPROVE 24H OVERAGE",
                onPrimaryClick = { showOverBudgetDialog = false },
                secondaryButtonText = "MANAGE LINKS",
                onSecondaryClick = { showOverBudgetDialog = false },
                style = FluxDialogStyle.Charcoal
            )
        }
    }
}

@Composable
private fun GallerySectionHeader(title: String) {
    Text(
        text = title,
        style = FluxTheme.typography.titleMedium,
        color = FluxPalette.Void,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun PaletteSwatch(
    name: String,
    hex: String,
    color: Color,
    isDark: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(FluxTheme.shapes.cardCompact)
                .background(color)
                .border(1.dp, FluxPalette.CharcoalMuted.copy(alpha = 0.2f), FluxTheme.shapes.cardCompact)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = name, style = FluxTheme.typography.labelSmall)
        Text(text = hex, style = FluxTheme.typography.bodySmall)
    }
}
