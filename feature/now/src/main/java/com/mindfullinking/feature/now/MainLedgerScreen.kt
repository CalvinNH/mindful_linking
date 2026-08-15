package com.mindfullinking.feature.now

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindfullinking.core.domain.InboxDeadlineSubgroupSection
import com.mindfullinking.core.model.DeadlineSubgroup
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.model.ResolutionReason
import com.mindfullinking.core.ui.chart.BudgetGauge
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxEmptyState
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import com.mindfullinking.feature.inbox.InboxViewModel
import java.util.Locale

@Composable
fun MainLedgerScreen(
    nowViewModel: NowViewModel = viewModel(),
    inboxViewModel: InboxViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val nowUiState by nowViewModel.uiState.collectAsState()
    val inboxUiState by inboxViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAuctionDialog by remember { mutableStateOf(false) }
    var resolveTargetLink by remember { mutableStateOf<Link?>(null) }
    var editTargetLink by remember { mutableStateOf<Link?>(null) }

    val priorityExpandedMap = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            put(5, true); put(4, true); put(3, true); put(2, true); put(1, true)
        }
    }

    if (showAuctionDialog) {
        RecommendationAuctionDialog(
            activeLinks = nowUiState.activeLinks,
            initialAvailableMinutes = nowUiState.availableMinutes,
            onDismissRequest = { showAuctionDialog = false },
            onOpenLink = { link ->
                nowViewModel.openLink(link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl))
                context.startActivity(intent)
            },
            onResolveLink = { link, reason ->
                inboxViewModel.resolveLink(link, reason)
            },
            onSnoozeLink = { link, snoozeUntil ->
                nowViewModel.snoozeLink(link, snoozeUntil)
            },
            onArchiveLink = { link ->
                inboxViewModel.archiveLink(link)
            },
            onSkipLink = { link ->
                nowViewModel.skipLink(link)
            }
        )
    }

    // Resolve Reason Dialog
    if (resolveTargetLink != null) {
        val target = resolveTargetLink!!
        FluxDialog(
            onDismissRequest = { resolveTargetLink = null },
            title = "Resolve Link",
            body = "Select resolution reason for: ${target.title ?: target.displayUrl}",
            primaryButtonText = "Cancel",
            onPrimaryClick = { resolveTargetLink = null },
            style = FluxDialogStyle.Paper
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionReason.entries.forEach { reason ->
                    FluxButton(
                        text = reason.name.replace("_", " "),
                        onClick = {
                            inboxViewModel.resolveLink(target, reason)
                            resolveTargetLink = null
                        },
                        variant = FluxButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Quick Edit Priority / Duration Dialog
    if (editTargetLink != null) {
        val target = editTargetLink!!
        FluxDialog(
            onDismissRequest = { editTargetLink = null },
            title = "Edit Link Details",
            body = target.title ?: target.displayUrl,
            primaryButtonText = "Done",
            onPrimaryClick = { editTargetLink = null },
            style = FluxDialogStyle.Paper
        ) {
            Column {
                Text(text = "Priority Level", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Priority.entries.forEach { p ->
                        val isSelected = target.priority == p
                        FluxButton(
                            text = "P${p.value}",
                            onClick = { inboxViewModel.updateLinkPriority(target, p) },
                            variant = if (isSelected) FluxButtonVariant.Primary else FluxButtonVariant.Outlined,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Estimated Duration", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10, 15, 30, 45, 60, 90, 120).forEach { mins ->
                        val isSelected = target.estimatedDurationMinutes == mins
                        FluxButton(
                            text = "${mins}m",
                            onClick = { inboxViewModel.updateLinkDuration(target, mins) },
                            variant = if (isSelected) FluxButtonVariant.Data else FluxButtonVariant.Outlined,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FluxPalette.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sticky / Hero Card: Budget Gauge & "I have time available now" CTA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FluxCard(
                    variant = FluxCardVariant.Paper,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BudgetGauge(
                            usedPoints = inboxUiState.usedPoints,
                            effectiveBudget = inboxUiState.effectiveBudget,
                            size = 180.dp
                        )

                        if (nowUiState.isOverageActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FluxBadge(
                                text = "TEMPORARY OVERAGE ACTIVE",
                                variant = FluxBadgeVariant.Action
                            )
                        }
                    }
                }

                FluxCard(
                    variant = FluxCardVariant.Charcoal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ATTENTION BUDGET RECOMMENDATION",
                            style = FluxTheme.typography.labelSmall,
                            color = FluxPalette.Signal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ready for a focused link break?",
                            style = FluxTheme.typography.titleMedium,
                            color = FluxPalette.Paper
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FluxButton(
                            text = "I have time available now",
                            onClick = { showAuctionDialog = true },
                            variant = FluxButtonVariant.Primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Open time recommendation auction modal" }
                        )
                    }
                }
            }
        }

        // Section Title: Inbox Queue
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "INBOX QUEUE",
                        style = FluxTheme.typography.titleLarge,
                        color = FluxPalette.Void
                    )
                    Text(
                        text = "${inboxUiState.totalActiveCount} Active Links (${String.format(Locale.ROOT, "%.2f", inboxUiState.usedPoints)} / ${String.format(Locale.ROOT, "%.0f", inboxUiState.effectiveBudget)} pts used)",
                        style = FluxTheme.typography.bodySmall,
                        color = FluxPalette.CharcoalMuted
                    )
                }
                FluxBadge(
                    text = "PRIORITY 5 → 1",
                    variant = FluxBadgeVariant.Charcoal
                )
            }
        }

        if (inboxUiState.prioritySections.isEmpty()) {
            item {
                FluxEmptyState(
                    title = "Inbox is empty",
                    body = "No active links in your attention budget queue. Share a link from any app to get started.",
                    actionText = "Refresh Queue",
                    onActionClick = {}
                )
            }
        } else {
            items(inboxUiState.prioritySections, key = { it.priority }) { prioritySection ->
                val isExpanded = priorityExpandedMap[prioritySection.priority] ?: true

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Level 1 Header: Priority Group (5 down to 1)
                    PriorityHeaderRow(
                        priority = prioritySection.priority,
                        linkCount = prioritySection.subgroups.sumOf { it.links.size },
                        isExpanded = isExpanded,
                        onToggle = { priorityExpandedMap[prioritySection.priority] = !isExpanded }
                    )

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // Level 2 Subgroups: 6 Deadline Subgroups
                            prioritySection.subgroups.forEach { subgroupSection ->
                                DeadlineSubgroupBlock(
                                    subgroupSection = subgroupSection,
                                    onOpenLink = { link ->
                                        nowViewModel.openLink(link)
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl))
                                        context.startActivity(intent)
                                    },
                                    onEdit = { editTargetLink = it },
                                    onSnooze = { link -> inboxViewModel.snoozeLink(link, 1) },
                                    onResolve = { resolveTargetLink = it },
                                    onArchive = { link -> inboxViewModel.archiveLink(link) },
                                    onDelete = { link -> inboxViewModel.deleteLink(link) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityHeaderRow(
    priority: Int,
    linkCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FluxPalette.Void)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Priority level $priority group, contains $linkCount links, ${if (isExpanded) "expanded" else "collapsed"}" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluxBadge(
                    text = "PRIORITY $priority",
                    variant = FluxBadgeVariant.Action
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$priority.00 pts each ($linkCount links)",
                    style = FluxTheme.typography.labelMedium,
                    color = FluxPalette.Paper
                )
            }
            Text(
                text = if (isExpanded) "▲" else "▼",
                style = FluxTheme.typography.labelMedium,
                color = FluxPalette.Signal
            )
        }
    }
}

@Composable
private fun DeadlineSubgroupBlock(
    subgroupSection: InboxDeadlineSubgroupSection,
    onOpenLink: (Link) -> Unit,
    onEdit: (Link) -> Unit,
    onSnooze: (Link) -> Unit,
    onResolve: (Link) -> Unit,
    onArchive: (Link) -> Unit,
    onDelete: (Link) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = subgroupSection.subgroup.name.replace("_", " "),
                style = FluxTheme.typography.labelSmall,
                color = if (subgroupSection.subgroup == DeadlineSubgroup.OVERDUE) FluxPalette.Void else FluxPalette.CharcoalMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            FluxBadge(
                text = "${subgroupSection.links.size}",
                variant = if (subgroupSection.subgroup == DeadlineSubgroup.OVERDUE) FluxBadgeVariant.Action else FluxBadgeVariant.Muted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subgroupSection.links.forEach { link ->
                LedgerLinkCard(
                    link = link,
                    onOpenLink = { onOpenLink(link) },
                    onEdit = { onEdit(link) },
                    onSnooze = { onSnooze(link) },
                    onResolve = { onResolve(link) },
                    onArchive = { onArchive(link) },
                    onDelete = { onDelete(link) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerLinkCard(
    link: Link,
    onOpenLink: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onResolve: () -> Unit,
    onArchive: () -> Unit,
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
                    FluxBadge(text = "${link.estimatedDurationMinutes}m", variant = FluxBadgeVariant.Muted)
                    if (link.skipCount > 0) {
                        FluxBadge(text = "${link.skipCount} skips", variant = FluxBadgeVariant.Muted)
                    }
                }
                FluxButton(
                    text = "Edit",
                    onClick = onEdit,
                    variant = FluxButtonVariant.Ghost,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayTitle = link.title ?: link.displayUrl
            Text(
                text = displayTitle,
                style = FluxTheme.typography.titleMedium,
                color = FluxPalette.Void,
                maxLines = 2,
                modifier = Modifier.clickable(onClick = onOpenLink)
            )

            if (!link.title.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = link.displayUrl,
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.CharcoalMuted,
                    maxLines = 1
                )
            }

            val noteText = link.note
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FluxButton(
                    text = "Open",
                    onClick = onOpenLink,
                    variant = FluxButtonVariant.Primary,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
                FluxButton(
                    text = "Snooze",
                    onClick = onSnooze,
                    variant = FluxButtonVariant.Secondary,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
                FluxButton(
                    text = "Resolve",
                    onClick = onResolve,
                    variant = FluxButtonVariant.Data,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
                FluxButton(
                    text = "Archive",
                    onClick = onArchive,
                    variant = FluxButtonVariant.Outlined,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
                FluxButton(
                    text = "Delete",
                    onClick = onDelete,
                    variant = FluxButtonVariant.Ghost,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
