package com.mindfullinking.feature.archive

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxEmptyState
import com.mindfullinking.core.ui.component.FluxInput
import com.mindfullinking.core.ui.component.OverBudgetModal
import com.mindfullinking.core.ui.component.OverBudgetResolution
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import java.util.Locale

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var restoreTargetLink by remember { mutableStateOf<Link?>(null) }
    var showOverBudgetModal by remember { mutableStateOf(false) }

    if (showOverBudgetModal && restoreTargetLink != null) {
        val target = restoreTargetLink!!
        val actionCost = (target.priority.value * 1.0f) - 0.25f
        OverBudgetModal(
            usedPoints = uiState.usedPoints,
            effectiveBudget = uiState.effectiveBudget,
            actionCostPoints = actionCost,
            activeLinks = uiState.activeLinks,
            onDismissRequest = {
                showOverBudgetModal = false
                restoreTargetLink = null
            },
            onResolutionSelected = { resolution ->
                showOverBudgetModal = false
                viewModel.restoreLink(target, resolution.toBudgetResolution())
                restoreTargetLink = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FluxPalette.Surface)
            .padding(16.dp)
    ) {
        // Header Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column {
                Text(
                    text = "REFERENCE ARCHIVE",
                    style = FluxTheme.typography.titleLarge,
                    color = FluxPalette.Void
                )
                Text(
                    text = "${uiState.archivedLinks.size} Retained Links (0.25 pt each)",
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.CharcoalMuted
                )
            }
            FluxBadge(
                text = "0.25 PT COST",
                variant = FluxBadgeVariant.Data
            )
        }

        // Search Field
        FluxInput(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = "Search archived reference links...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.filteredLinks.isEmpty()) {
            FluxEmptyState(
                title = if (uiState.searchQuery.isBlank()) "Archive is empty" else "No matching links",
                body = if (uiState.searchQuery.isBlank()) "Archived links consume only 0.25 points each and serve as your permanent local reference collection." else "No archived reference links match your search filter."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredLinks, key = { it.id }) { link ->
                    ArchiveLinkCard(
                        link = link,
                        onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl))
                            context.startActivity(intent)
                        },
                        onRestore = {
                            if (viewModel.canRestoreLink(link)) {
                                viewModel.restoreLink(link)
                            } else {
                                restoreTargetLink = link
                                showOverBudgetModal = true
                            }
                        },
                        onDelete = { viewModel.deleteLinkPermanently(link) }
                    )
                }
            }
        }
    }
}

private fun OverBudgetResolution.toBudgetResolution(): BudgetResolution = when (this) {
    is OverBudgetResolution.FreeUpLink -> BudgetResolution.FreeUpLink(
        linkId = linkId,
        action = when (action) {
            OverBudgetResolution.FreeUpAction.RESOLVE -> BudgetResolution.FreeUpAction.RESOLVE
            OverBudgetResolution.FreeUpAction.ARCHIVE -> BudgetResolution.FreeUpAction.ARCHIVE
            OverBudgetResolution.FreeUpAction.DELETE -> BudgetResolution.FreeUpAction.DELETE
        }
    )
    is OverBudgetResolution.LowerPriority -> BudgetResolution.LowerPriority(linkId, newPriority)
    is OverBudgetResolution.ReplaceLink -> BudgetResolution.ReplaceLink(linkIdToReplace)
    is OverBudgetResolution.TemporaryOverage -> BudgetResolution.TemporaryOverage(overagePoints, durationHours)
}

@Composable
private fun ArchiveLinkCard(
    link: Link,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    FluxCard(
        variant = FluxCardVariant.Paper,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FluxBadge(text = "0.25 pt", variant = FluxBadgeVariant.Data)
                    FluxBadge(text = "Originally P${link.priority.value}", variant = FluxBadgeVariant.Muted)
                }
                FluxButton(
                    text = "Delete (Releases 0.25pt)",
                    onClick = onDelete,
                    variant = FluxButtonVariant.Outlined,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val titleText = link.title
            val noteText = link.note

            val displayTitle = if (!titleText.isNullOrBlank()) titleText else link.displayUrl
            Text(
                text = displayTitle,
                style = FluxTheme.typography.titleMedium,
                color = FluxPalette.Void,
                maxLines = 2,
                modifier = Modifier.clickable(onClick = onOpen)
            )

            if (!titleText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = link.displayUrl,
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.CharcoalMuted,
                    maxLines = 1
                )
            }

            if (!noteText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = noteText,
                    style = FluxTheme.typography.bodyMedium,
                    color = FluxPalette.Void,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FluxButton(
                    text = "Open Link",
                    onClick = onOpen,
                    variant = FluxButtonVariant.Outlined,
                    modifier = Modifier.padding(end = 8.dp)
                )
                FluxButton(
                    text = "Restore to Queue",
                    onClick = onRestore,
                    variant = FluxButtonVariant.Primary
                )
            }
        }
    }
}
