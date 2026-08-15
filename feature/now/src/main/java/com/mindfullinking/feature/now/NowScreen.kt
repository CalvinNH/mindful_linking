package com.mindfullinking.feature.now

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mindfullinking.core.domain.ScoredRecommendation
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.ui.chart.BudgetGauge
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxEmptyState
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowScreen(
    onNavigateToInbox: () -> Unit,
    viewModel: NowViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    var showAuctionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FluxPalette.Surface)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header Section with Hero Card & Budget Gauge
        FluxCard(
            variant = FluxCardVariant.Paper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                BudgetGauge(
                    usedPoints = uiState.usedPoints,
                    effectiveBudget = uiState.effectiveBudget,
                    size = 200.dp
                )

                if (uiState.isOverageActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FluxBadge(
                        text = "TEMPORARY OVERAGE ACTIVE",
                        variant = FluxBadgeVariant.Action
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // "I have time available now" Hero Action Button Card
        FluxCard(
            variant = FluxCardVariant.Signal,
            onClick = { showAuctionDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "I HAVE TIME AVAILABLE NOW",
                        style = FluxTheme.typography.titleMedium,
                        color = FluxPalette.Void
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Run background ML auction for your available time window",
                        style = FluxTheme.typography.bodySmall,
                        color = FluxPalette.Void
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FluxButton(
                    text = "Run Auction",
                    onClick = { showAuctionDialog = true },
                    variant = FluxButtonVariant.Dark
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // "Use a Link" Time Selector Section
        Text(
            text = "USE A LINK — AVAILABLE TIME",
            style = FluxTheme.typography.labelLarge,
            color = FluxPalette.Void
        )
        Spacer(modifier = Modifier.height(8.dp))

        val durationOptions = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(durationOptions) { mins ->
                val isSelected = uiState.availableMinutes == mins
                val label = if (mins >= 120) "120+m" else "${mins}m"
                FluxButton(
                    text = label,
                    onClick = { viewModel.setAvailableMinutes(mins) },
                    variant = if (isSelected) FluxButtonVariant.Primary else FluxButtonVariant.Secondary,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.semantics { contentDescription = "Select $mins minutes available time" }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Intent Category Filter Chips
        Text(
            text = "INTENT FILTER",
            style = FluxTheme.typography.labelSmall,
            color = FluxPalette.CharcoalMuted
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val filters = listOf<IntentCategory?>(null) + IntentCategory.entries
            filters.forEach { cat ->
                val isSelected = uiState.selectedIntentFilter == cat
                val label = cat?.name ?: "ALL"
                FluxButton(
                    text = label,
                    onClick = { viewModel.setIntentFilter(cat) },
                    variant = if (isSelected) FluxButtonVariant.Data else FluxButtonVariant.Ghost,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recommendation Results Carousel / Cards
        val recResult = uiState.recommendationResult

        if (recResult.recommendations.isEmpty()) {
            FluxEmptyState(
                title = "No links in queue",
                body = "You have no active links matching your current filters. Share links via your browser or add them in Inbox.",
                actionText = "Go to Inbox",
                onActionClick = onNavigateToInbox
            )
        } else if (recResult.isFallbackShortestLinks) {
            // Fallback State: No links fit available time window
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FluxCard(
                    variant = FluxCardVariant.Canvas,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Exceeds available time",
                            style = FluxTheme.typography.titleMedium,
                            color = FluxPalette.Void
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No active links fit your ${uiState.availableMinutes}-minute window. Here are your shortest links:",
                            style = FluxTheme.typography.bodyMedium,
                            color = FluxPalette.Void
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FluxButton(
                            text = "Go to Inbox",
                            onClick = onNavigateToInbox,
                            variant = FluxButtonVariant.Primary
                        )
                    }
                }

                recResult.recommendations.forEach { rec ->
                    RecommendationCard(
                        recommendation = rec,
                        onOpen = {
                            viewModel.openLink(rec.link)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rec.link.canonicalUrl))
                            context.startActivity(intent)
                        },
                        onSkip = { viewModel.skipLink(rec.link) }
                    )
                }
            }
        } else {
            // Normal Recommendation Carousel (up to 3 cards)
            Text(
                text = "RECOMMENDED FOR YOU",
                style = FluxTheme.typography.labelLarge,
                color = FluxPalette.Void
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                recResult.recommendations.forEach { rec ->
                    RecommendationCard(
                        recommendation = rec,
                        onOpen = {
                            viewModel.openLink(rec.link)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rec.link.canonicalUrl))
                            context.startActivity(intent)
                        },
                        onSkip = { viewModel.skipLink(rec.link) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showAuctionDialog) {
        RecommendationAuctionDialog(
            activeLinks = uiState.activeLinks,
            initialAvailableMinutes = uiState.availableMinutes,
            onDismissRequest = { showAuctionDialog = false },
            onOpenLink = { link ->
                viewModel.openLink(link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.canonicalUrl))
                context.startActivity(intent)
            },
            onResolveLink = { link, reason ->
                viewModel.resolveLink(link, reason)
            },
            onSnoozeLink = { link, snoozeUntil ->
                viewModel.snoozeLink(link, snoozeUntil)
            },
            onArchiveLink = { link ->
                viewModel.archiveLink(link)
            },
            onSkipLink = { link ->
                viewModel.skipLink(link)
            }
        )
    }
}

@Composable
fun RecommendationCard(
    recommendation: ScoredRecommendation,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val link = recommendation.link
    val is5SkipReconsidered = link.skipCount >= 5

    FluxCard(
        variant = if (is5SkipReconsidered) FluxCardVariant.Canvas else FluxCardVariant.Paper,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // PRD 9.6 Explanation Badge & Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                FluxBadge(
                    text = recommendation.explanation,
                    variant = if (is5SkipReconsidered) FluxBadgeVariant.Charcoal else FluxBadgeVariant.Action
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FluxBadge(text = "P${link.priority.value}", variant = FluxBadgeVariant.Data)
                    FluxBadge(text = "${link.estimatedDurationMinutes}m", variant = FluxBadgeVariant.Muted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title & URL
            val titleText = link.title
            val noteText = link.note

            Text(
                text = titleText ?: link.displayUrl,
                style = FluxTheme.typography.titleMedium,
                color = FluxPalette.Void,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = link.displayUrl,
                style = FluxTheme.typography.bodySmall,
                color = FluxPalette.CharcoalMuted,
                maxLines = 1
            )

            if (!noteText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = noteText,
                    style = FluxTheme.typography.bodyMedium,
                    color = FluxPalette.Void,
                    maxLines = 2
                )
            }

            if (is5SkipReconsidered) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "5 skips reached — decide whether to keep or archive in Inbox",
                    style = FluxTheme.typography.labelSmall,
                    color = FluxPalette.Void
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // "Skip" button disabled on 5-skip reconsidered cards
                FluxButton(
                    text = "Skip",
                    onClick = onSkip,
                    enabled = !is5SkipReconsidered,
                    variant = FluxButtonVariant.Outlined,
                    modifier = Modifier.padding(end = 8.dp)
                )
                FluxButton(
                    text = "Open Link",
                    onClick = onOpen,
                    variant = FluxButtonVariant.Primary
                )
            }
        }
    }
}
