package com.mindfullinking.feature.now

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mindfullinking.core.domain.RecommendationEngine
import com.mindfullinking.core.domain.RecommendationResult
import com.mindfullinking.core.domain.ScoredRecommendation
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.ResolutionReason
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * UI phases for the Recommendation Auction Dialog.
 */
enum class AuctionPhase {
    TIME_SELECTION,
    ML_ANIMATION,
    RESULTS
}

/**
 * Duration options available in the ML Recommendation Auction:
 * 1 min, 3 min, 5 min, 10 min, 15 min, 20 min, 30 min, 45 min, 60 min, 90 min, 120+ min.
 */
val AUCTION_DURATION_OPTIONS = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)

fun formatDurationLabel(minutes: Int): String {
    return if (minutes >= 120) "120+ min" else "$minutes min"
}

/**
 * Bauhaus Recommendation Auction Dialog Component.
 *
 * State 1 (Time Selection): Modal card titled "Choose the amount of time you currently have available"
 * with single-select duration chips (1m, 3m, 5m, 10m, 15m, 20m, 30m, 45m, 60m, 90m, 120+m).
 * State 2 (Action CTA): "Run Auction" button (Signal Action Lime background #DAF163, Void Charcoal text #222124).
 * State 3 (ML Auction Animation): ~2-second vertical link-shuffling animation simulating background ML candidate analysis
 * displaying candidate titles rapidly flowing up/down with a charcoal/lime progress beam.
 * State 4 (Results Presentation): Displays top 3 recommended link cards scored by RecommendationEngine
 * with PRD 9.6 explanation badges, Open CTA, Resolve, Snooze, and Archive actions.
 * Includes "Exceeds available time" shortest link fallback state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecommendationAuctionDialog(
    activeLinks: List<Link>,
    onDismissRequest: () -> Unit,
    onOpenLink: (Link) -> Unit,
    onResolveLink: (Link, ResolutionReason) -> Unit,
    onSnoozeLink: (Link, Instant) -> Unit,
    onArchiveLink: (Link) -> Unit,
    onAuctionCompleted: (availableMinutes: Int, recommendedLinkIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    lastSessionRecommendedLinkIds: List<String> = emptyList(),
    previousSessionRecommendedLinkIds: List<String> = emptyList(),
    initialAvailableMinutes: Int = 15
) {
    var selectedMinutes by remember { mutableIntStateOf(initialAvailableMinutes) }
    var currentPhase by remember { mutableStateOf(AuctionPhase.TIME_SELECTION) }
    var recommendationResult by remember { mutableStateOf<RecommendationResult?>(null) }
    var currentLastSessionIds by remember(lastSessionRecommendedLinkIds) {
        mutableStateOf(lastSessionRecommendedLinkIds)
    }
    var currentPreviousSessionIds by remember(previousSessionRecommendedLinkIds) {
        mutableStateOf(previousSessionRecommendedLinkIds)
    }

    // Sub-dialog states for Resolve and Snooze pickers
    var linkToResolve by remember { mutableStateOf<Link?>(null) }
    var linkToSnooze by remember { mutableStateOf<Link?>(null) }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = FluxTheme.shapes.dialog,
            color = FluxPalette.Paper,
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Bar with Title & Close Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FluxBadge(
                        text = "ML RECOMMENDATION AUCTION",
                        variant = FluxBadgeVariant.Charcoal
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FluxPalette.Surface)
                            .clickable(onClick = onDismissRequest)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Close Recommendation Auction Dialog"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            style = FluxTheme.typography.labelLarge,
                            color = FluxPalette.Void
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = currentPhase,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "AuctionPhaseTransition"
                ) { phase ->
                    when (phase) {
                        AuctionPhase.TIME_SELECTION -> {
                            TimeSelectionContent(
                                selectedMinutes = selectedMinutes,
                                onSelectMinutes = { selectedMinutes = it },
                                onRunAuction = {
                                    currentPhase = AuctionPhase.ML_ANIMATION
                                }
                            )
                        }

                        AuctionPhase.ML_ANIMATION -> {
                            MlAuctionAnimationContent(
                                availableMinutes = selectedMinutes,
                                activeLinks = activeLinks,
                                onAnimationComplete = {
                                    val result = RecommendationEngine.recommend(
                                        activeLinks = activeLinks,
                                        availableMinutes = selectedMinutes,
                                        lastSessionRecommendedLinkIds = currentLastSessionIds,
                                        previousSessionRecommendedLinkIds = currentPreviousSessionIds,
                                        now = Instant.now()
                                    )
                                    recommendationResult = result
                                    val recommendedIds = result.recommendations.map { it.link.id }
                                    currentPreviousSessionIds = currentLastSessionIds
                                    currentLastSessionIds = recommendedIds
                                    onAuctionCompleted(selectedMinutes, recommendedIds)
                                    currentPhase = AuctionPhase.RESULTS
                                }
                            )
                        }

                        AuctionPhase.RESULTS -> {
                            val result = recommendationResult ?: RecommendationEngine.recommend(
                                activeLinks = activeLinks,
                                availableMinutes = selectedMinutes,
                                lastSessionRecommendedLinkIds = currentLastSessionIds,
                                previousSessionRecommendedLinkIds = currentPreviousSessionIds,
                                now = Instant.now()
                            )

                            ResultsPresentationContent(
                                availableMinutes = selectedMinutes,
                                result = result,
                                onOpenLink = onOpenLink,
                                onResolveClick = { linkToResolve = it },
                                onSnoozeClick = { linkToSnooze = it },
                                onArchiveLink = onArchiveLink,
                                onReRunAuction = {
                                    currentPhase = AuctionPhase.TIME_SELECTION
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Resolve Reason Sub-Dialog
    linkToResolve?.let { link ->
        FluxDialog(
            onDismissRequest = { linkToResolve = null },
            title = "Resolve Link",
            body = "Select resolution reason for \"${link.title ?: link.displayUrl}\":",
            primaryButtonText = "Cancel",
            onPrimaryClick = { linkToResolve = null },
            style = FluxDialogStyle.Paper
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionReason.entries.forEach { reason ->
                    val label = when (reason) {
                        ResolutionReason.COMPLETED -> "Completed — Finished reading/using"
                        ResolutionReason.APPLIED -> "Applied — Took action or extracted value"
                        ResolutionReason.NO_LONGER_RELEVANT -> "No Longer Relevant — Topic outdated"
                    }
                    FluxButton(
                        text = label,
                        onClick = {
                            onResolveLink(link, reason)
                            linkToResolve = null
                        },
                        variant = FluxButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Snooze Duration Sub-Dialog
    linkToSnooze?.let { link ->
        FluxDialog(
            onDismissRequest = { linkToSnooze = null },
            title = "Snooze Link",
            body = "Hide \"${link.title ?: link.displayUrl}\" from recommendations for:",
            primaryButtonText = "Cancel",
            onPrimaryClick = { linkToSnooze = null },
            style = FluxDialogStyle.Paper
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val snoozeOptions = listOf(
                    "1 Day" to Duration.ofDays(1),
                    "3 Days" to Duration.ofDays(3),
                    "7 Days (1 Week)" to Duration.ofDays(7)
                )

                snoozeOptions.forEach { (label, duration) ->
                    FluxButton(
                        text = label,
                        onClick = {
                            val snoozeUntil = Instant.now().plus(duration)
                            onSnoozeLink(link, snoozeUntil)
                            linkToSnooze = null
                        },
                        variant = FluxButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * State 1 & State 2: Time Selection & Run Auction Action CTA.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeSelectionContent(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    onRunAuction: () -> Unit
) {
    Column {
        Text(
            text = "Choose the amount of time you currently have available",
            style = FluxTheme.typography.titleLarge,
            color = FluxPalette.Void
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select your free window and run the background ML Auction to discover your top 3 recommended links.",
            style = FluxTheme.typography.bodyMedium,
            color = FluxPalette.CharcoalMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "AVAILABLE DURATION",
            style = FluxTheme.typography.labelSmall,
            color = FluxPalette.Void
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Single-select chips grid for 1m, 3m, 5m, 10m, 15m, 20m, 30m, 45m, 60m, 90m, 120+m
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AUCTION_DURATION_OPTIONS.forEach { mins ->
                val isSelected = selectedMinutes == mins
                val label = formatDurationLabel(mins)

                val chipBackground = if (isSelected) FluxPalette.Signal else FluxPalette.Paper
                val chipContentColor = FluxPalette.Void
                val chipBorder = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, FluxPalette.Void)
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, FluxPalette.Void.copy(alpha = 0.25f))
                }

                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable { onSelectMinutes(mins) }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Select $label duration"
                        },
                    shape = FluxTheme.shapes.button,
                    color = chipBackground,
                    contentColor = chipContentColor,
                    border = chipBorder
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = if (isSelected) FluxTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else FluxTheme.typography.labelMedium,
                            color = FluxPalette.Void
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // State 2: "Run Auction" CTA button (Signal Action Lime background, Void Charcoal text)
        FluxButton(
            text = "Run Auction",
            onClick = onRunAuction,
            variant = FluxButtonVariant.Primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
    }
}

/**
 * State 3: ML Auction Animation (~2-second link-shuffling animation simulating background ML candidate analysis).
 * Renders candidate titles flowing up/down with a charcoal/lime progress beam.
 */
@Composable
private fun MlAuctionAnimationContent(
    availableMinutes: Int,
    activeLinks: List<Link>,
    onAnimationComplete: () -> Unit
) {
    val progressAnim = remember { Animatable(0f) }
    var currentTickerIndex by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Initializing ML candidate engine...") }

    // Sample candidate pool for ticker animation
    val candidateTitles = remember(activeLinks) {
        if (activeLinks.isNotEmpty()) {
            activeLinks.map { it.title ?: it.displayUrl }
        } else {
            listOf(
                "Designing Scalable Mobile Systems",
                "Jetpack Compose Performance Tuning",
                "Kotlin Coroutines Deep Dive",
                "Bauhaus Product Design Language",
                "Offline-First Data Architecture",
                "Room Database Indexing Strategies",
                "Mindful Reading & Focus Management",
                "Algorithmic Recommendation Systems",
                "Understanding Android AlarmManager",
                "Zero-Cloud Local Security Patterns"
            )
        }
    }

    LaunchedEffect(Unit) {
        // Status text update loop
        launch {
            val statusMessages = listOf(
                "Filtering active eligible candidate pool...",
                "Evaluating priority and deadline urgency weights...",
                "Scoring duration fit for ${availableMinutes}m window...",
                "Applying subgroup diversity penalties...",
                "Finalizing top 3 ML recommendation matrix..."
            )
            for (msg in statusMessages) {
                statusText = msg
                delay(380)
            }
        }

        // Fast ticker shuffle loop
        launch {
            val totalSteps = 16
            for (step in 0 until totalSteps) {
                currentTickerIndex = (currentTickerIndex + 1) % candidateTitles.size
                delay(120)
            }
        }

        // Progress beam animation (2 seconds)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )

        onAnimationComplete()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "RUNNING ML AUCTION",
            style = FluxTheme.typography.titleMedium,
            color = FluxPalette.Void
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusText,
            style = FluxTheme.typography.bodySmall,
            color = FluxPalette.CharcoalMuted,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Charcoal / Lime Progress Indicator Beam
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(FluxPalette.Void)
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressAnim.value)
                    .clip(RoundedCornerShape(50))
                    .background(FluxPalette.Signal)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Vertical Link-Shuffling Animation Surface (Simulating ML candidate analysis)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(FluxTheme.shapes.cardPrimary)
                .background(FluxPalette.Void)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val title1 = candidateTitles[currentTickerIndex % candidateTitles.size]
                val title2 = candidateTitles[(currentTickerIndex + 1) % candidateTitles.size]
                val title3 = candidateTitles[(currentTickerIndex + 2) % candidateTitles.size]

                // Shuffling candidate cards flowing up/down
                CandidateTickerCard(
                    title = title1,
                    score = String.format("%.2f", 0.85 + (currentTickerIndex % 15) * 0.01),
                    alpha = 0.5f
                )
                CandidateTickerCard(
                    title = title2,
                    score = String.format("%.2f", 0.92 + (currentTickerIndex % 8) * 0.01),
                    alpha = 1.0f,
                    isHighlighted = true
                )
                CandidateTickerCard(
                    title = title3,
                    score = String.format("%.2f", 0.78 + (currentTickerIndex % 20) * 0.01),
                    alpha = 0.5f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Analyzing duration fit for ${availableMinutes} minutes...",
            style = FluxTheme.typography.labelSmall,
            color = FluxPalette.Void
        )
    }
}

@Composable
private fun CandidateTickerCard(
    title: String,
    score: String,
    alpha: Float,
    isHighlighted: Boolean = false
) {
    Surface(
        shape = FluxTheme.shapes.badge,
        color = if (isHighlighted) FluxPalette.Signal.copy(alpha = alpha) else FluxPalette.CharcoalMuted.copy(alpha = 0.4f * alpha),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = FluxTheme.typography.bodySmall,
                color = FluxPalette.Void,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FluxBadge(
                text = "Score $score",
                variant = if (isHighlighted) FluxBadgeVariant.Charcoal else FluxBadgeVariant.Muted
            )
        }
    }
}

/**
 * State 4: Results Presentation.
 * Displays top 3 recommended link cards scored by RecommendationEngine with PRD 9.6 explanation badges,
 * Open CTA, Resolve, Snooze, and Archive actions.
 * Includes "Exceeds available time" shortest link fallback state.
 */
@Composable
private fun ResultsPresentationContent(
    availableMinutes: Int,
    result: RecommendationResult,
    onOpenLink: (Link) -> Unit,
    onResolveClick: (Link) -> Unit,
    onSnoozeClick: (Link) -> Unit,
    onArchiveLink: (Link) -> Unit,
    onReRunAuction: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (result.isFallbackShortestLinks) "SHORTEST LINKS FALLBACK" else "RECOMMENDED FOR YOU",
                style = FluxTheme.typography.labelLarge,
                color = FluxPalette.Void
            )

            FluxButton(
                text = "Re-run",
                onClick = onReRunAuction,
                variant = FluxButtonVariant.Ghost,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (result.recommendations.isEmpty()) {
            FluxCard(
                variant = FluxCardVariant.Surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Active Links Available",
                        style = FluxTheme.typography.titleMedium,
                        color = FluxPalette.Void
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You currently have no active links in your queue. Save or share links to run the ML recommendation auction.",
                        style = FluxTheme.typography.bodyMedium,
                        color = FluxPalette.CharcoalMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // "Exceeds available time" shortest link fallback banner
            if (result.isFallbackShortestLinks) {
                FluxCard(
                    variant = FluxCardVariant.Canvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FluxBadge(
                                text = "Exceeds available time",
                                variant = FluxBadgeVariant.Charcoal
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No active links fit your ${availableMinutes}-minute window. Surfacing your shortest active links as a fallback:",
                            style = FluxTheme.typography.bodySmall,
                            color = FluxPalette.Void
                        )
                    }
                }
            }

            // Top 3 Scored Recommendation Cards
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                result.recommendations.forEach { rec ->
                    AuctionRecommendationCard(
                        recommendation = rec,
                        onOpen = { onOpenLink(rec.link) },
                        onResolve = { onResolveClick(rec.link) },
                        onSnooze = { onSnoozeClick(rec.link) },
                        onArchive = { onArchiveLink(rec.link) }
                    )
                }
            }
        }
    }
}

/**
 * Single Recommendation Card within the Auction Results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuctionRecommendationCard(
    recommendation: ScoredRecommendation,
    onOpen: () -> Unit,
    onResolve: () -> Unit,
    onSnooze: () -> Unit,
    onArchive: () -> Unit
) {
    val link = recommendation.link
    val is5SkipReconsidered = link.skipCount >= 5

    FluxCard(
        variant = if (is5SkipReconsidered) FluxCardVariant.Canvas else FluxCardVariant.Paper,
        border = androidx.compose.foundation.BorderStroke(1.dp, FluxPalette.Void.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Row: PRD 9.6 Explanation Badge & Metadata Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluxBadge(
                    text = recommendation.explanation,
                    variant = if (is5SkipReconsidered) FluxBadgeVariant.Charcoal else FluxBadgeVariant.Action
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FluxBadge(
                        text = "P${link.priority.value}",
                        variant = FluxBadgeVariant.Data
                    )
                    FluxBadge(
                        text = "${link.estimatedDurationMinutes}m",
                        variant = FluxBadgeVariant.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Link Title & URL
            Text(
                text = link.title ?: link.displayUrl,
                style = FluxTheme.typography.titleMedium,
                color = FluxPalette.Void,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = link.displayUrl,
                style = FluxTheme.typography.bodySmall,
                color = FluxPalette.CharcoalMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val noteText = link.note
            if (!noteText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = noteText,
                    style = FluxTheme.typography.bodyMedium,
                    color = FluxPalette.Void,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (is5SkipReconsidered) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reconsidered (5 skips reached): decide whether to keep, snooze, resolve, or archive.",
                    style = FluxTheme.typography.labelSmall,
                    color = FluxPalette.Void
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action CTAs Row (Open CTA, Resolve, Snooze, Archive)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Primary Open CTA
                FluxButton(
                    text = "Open Link",
                    onClick = onOpen,
                    variant = FluxButtonVariant.Primary,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                )

                // Resolve CTA
                FluxButton(
                    text = "Resolve",
                    onClick = onResolve,
                    variant = FluxButtonVariant.Outlined,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )

                // Snooze CTA
                FluxButton(
                    text = "Snooze",
                    onClick = onSnooze,
                    variant = FluxButtonVariant.Outlined,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )

                // Archive CTA
                FluxButton(
                    text = "Archive",
                    onClick = onArchive,
                    variant = FluxButtonVariant.Outlined,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
