package com.mindfullinking.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import java.util.Locale

sealed interface OverBudgetResolution {
    data class FreeUpLink(val linkId: String, val action: FreeUpAction) : OverBudgetResolution
    data class LowerPriority(val linkId: String, val newPriority: Priority) : OverBudgetResolution
    data class ReplaceLink(val linkIdToReplace: String) : OverBudgetResolution
    data class TemporaryOverage(val overagePoints: Int, val durationHours: Long) : OverBudgetResolution

    enum class FreeUpAction { RESOLVE, ARCHIVE, DELETE }
}

/**
 * Bauhaus Charcoal Modal triggered when an action exceeds the 100.00-point attention budget.
 * Offers 4 resolution options:
 * 1. Resolve, Archive, or Delete an existing active link
 * 2. Lower priority of an existing active link
 * 3. Replace a lower priority link
 * 4. Approve temporary overage (5-50 pts / 24h-30d expiry)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverBudgetModal(
    usedPoints: Float,
    effectiveBudget: Float,
    actionCostPoints: Float,
    activeLinks: List<Link>,
    onDismissRequest: () -> Unit,
    onResolutionSelected: (OverBudgetResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOption by remember { mutableIntStateOf(4) } // Default to temporary overage for quick resolution

    // Option 1 & 2 & 3 link selection state
    var selectedLinkId by remember { mutableStateOf(activeLinks.firstOrNull()?.id ?: "") }
    var selectedFreeUpAction by remember { mutableStateOf(OverBudgetResolution.FreeUpAction.ARCHIVE) }
    var selectedNewPriority by remember { mutableStateOf(Priority.LOW) }

    // Option 4 overage state
    var overagePts by remember { mutableIntStateOf(10) }
    var overageHours by remember { mutableLongStateOf(24L) }

    val resultingTotal = usedPoints + actionCostPoints

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Surface(
            shape = FluxTheme.shapes.dialog,
            color = FluxPalette.Void,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BUDGET EXCEEDED",
                        style = FluxTheme.typography.titleLarge,
                        color = FluxPalette.Paper
                    )
                    FluxBadge(
                        text = "+${String.format(Locale.ROOT, "%.2f", actionCostPoints)} PTS",
                        variant = FluxBadgeVariant.Action
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current & New Totals Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FluxPalette.CharcoalMuted.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Current Usage: ${String.format(Locale.ROOT, "%.2f", usedPoints)} / ${String.format(Locale.ROOT, "%.0f", effectiveBudget)} pts",
                            style = FluxTheme.typography.bodyMedium,
                            color = FluxPalette.Paper.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "New Total: ${String.format(Locale.ROOT, "%.2f", resultingTotal)} pts (Exceeds by ${String.format(Locale.ROOT, "%.2f", resultingTotal - effectiveBudget)} pts)",
                            style = FluxTheme.typography.labelMedium,
                            color = FluxPalette.Signal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose how to make room in your budget:",
                    style = FluxTheme.typography.labelLarge,
                    color = FluxPalette.Paper
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Options List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Option 1: Free up existing link
                    OptionCard(
                        title = "1. Resolve, Archive, or Delete Active Link",
                        isSelected = selectedOption == 1,
                        onSelect = { selectedOption = 1 }
                    ) {
                        if (activeLinks.isEmpty()) {
                            Text("No active links available to modify.", style = FluxTheme.typography.bodySmall, color = FluxPalette.Paper.copy(alpha = 0.7f))
                        } else {
                            Column {
                                DropdownLinkSelector(
                                    links = activeLinks,
                                    selectedLinkId = selectedLinkId,
                                    onLinkSelected = { selectedLinkId = it }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverBudgetResolution.FreeUpAction.entries.forEach { action ->
                                        FluxButton(
                                            text = action.name.lowercase().capitalize(),
                                            onClick = { selectedFreeUpAction = action },
                                            variant = if (selectedFreeUpAction == action) FluxButtonVariant.Primary else FluxButtonVariant.Dark,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Option 2: Lower Priority
                    OptionCard(
                        title = "2. Lower Priority of Existing Link",
                        isSelected = selectedOption == 2,
                        onSelect = { selectedOption = 2 }
                    ) {
                        if (activeLinks.isEmpty()) {
                            Text("No active links available.", style = FluxTheme.typography.bodySmall, color = FluxPalette.Paper.copy(alpha = 0.7f))
                        } else {
                            Column {
                                DropdownLinkSelector(
                                    links = activeLinks,
                                    selectedLinkId = selectedLinkId,
                                    onLinkSelected = { selectedLinkId = it }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Priority.entries.filter { it != Priority.HIGHEST }.forEach { p ->
                                        FluxButton(
                                            text = "P${p.value}",
                                            onClick = { selectedNewPriority = p },
                                            variant = if (selectedNewPriority == p) FluxButtonVariant.Primary else FluxButtonVariant.Dark,
                                            minWidth = 0.dp,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Option 3: Replace Link
                    OptionCard(
                        title = "3. Replace Lower Priority Link",
                        isSelected = selectedOption == 3,
                        onSelect = { selectedOption = 3 }
                    ) {
                        if (activeLinks.isEmpty()) {
                            Text("No active links available.", style = FluxTheme.typography.bodySmall, color = FluxPalette.Paper.copy(alpha = 0.7f))
                        } else {
                            DropdownLinkSelector(
                                links = activeLinks,
                                selectedLinkId = selectedLinkId,
                                onLinkSelected = { selectedLinkId = it }
                            )
                        }
                    }

                    // Option 4: Temporary Overage
                    OptionCard(
                        title = "4. Approve Temporary Overage",
                        isSelected = selectedOption == 4,
                        onSelect = { selectedOption = 4 }
                    ) {
                        Column {
                            Text("Select Overage Amount:", style = FluxTheme.typography.labelSmall, color = FluxPalette.Paper.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 20, 50).forEach { pts ->
                                    FluxButton(
                                        text = "+$pts pts",
                                        onClick = { overagePts = pts },
                                        variant = if (overagePts == pts) FluxButtonVariant.Primary else FluxButtonVariant.Dark,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Select Duration:", style = FluxTheme.typography.labelSmall, color = FluxPalette.Paper.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(24L to "24 Hours", 168L to "7 Days", 720L to "30 Days").forEach { (hrs, label) ->
                                    FluxButton(
                                        text = label,
                                        onClick = { overageHours = hrs },
                                        variant = if (overageHours == hrs) FluxButtonVariant.Primary else FluxButtonVariant.Dark,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FluxButton(
                        text = "Cancel",
                        onClick = onDismissRequest,
                        variant = FluxButtonVariant.Dark,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FluxButton(
                        text = "Confirm Choice",
                        onClick = {
                            val resolution = when (selectedOption) {
                                1 -> OverBudgetResolution.FreeUpLink(selectedLinkId, selectedFreeUpAction)
                                2 -> OverBudgetResolution.LowerPriority(selectedLinkId, selectedNewPriority)
                                3 -> OverBudgetResolution.ReplaceLink(selectedLinkId)
                                else -> OverBudgetResolution.TemporaryOverage(overagePts, overageHours)
                            }
                            onResolutionSelected(resolution)
                        },
                        variant = FluxButtonVariant.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit
) {
    val borderColor = if (isSelected) FluxPalette.Signal else FluxPalette.CharcoalMuted.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FluxPalette.Void)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = FluxPalette.Signal,
                        unselectedColor = FluxPalette.CharcoalMuted
                    )
                )
                Text(
                    text = title,
                    style = FluxTheme.typography.labelMedium,
                    color = if (isSelected) FluxPalette.Signal else FluxPalette.Paper
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.padding(start = 32.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DropdownLinkSelector(
    links: List<Link>,
    selectedLinkId: String,
    onLinkSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.take(4).forEach { link ->
            val isSelected = link.id == selectedLinkId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) FluxPalette.Signal.copy(alpha = 0.2f) else FluxPalette.CharcoalMuted.copy(alpha = 0.2f))
                    .clickable { onLinkSelected(link.id) }
                    .padding(8.dp)
            ) {
                Text(
                    text = link.title ?: link.displayUrl,
                    style = FluxTheme.typography.bodySmall,
                    color = if (isSelected) FluxPalette.Signal else FluxPalette.Paper,
                    maxLines = 1
                )
            }
        }
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
