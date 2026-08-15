package com.mindfullinking.core.domain

import com.mindfullinking.core.model.DeadlineSubgroup
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Scored recommendation item wrapping a Link.
 */
data class ScoredRecommendation(
    val link: Link,
    val baseScore: Double,
    val finalScore: Double,
    val explanation: String,
    val isReconsideration: Boolean,
    val exceedsTime: Boolean
)

/**
 * Result returned by the recommendation engine.
 */
data class RecommendationResult(
    val recommendations: List<ScoredRecommendation>,
    val isFallbackShortestLinks: Boolean = false
)

/**
 * Deterministic 6-factor recommendation scoring engine.
 * Implements PRD Section 9 & Implementation Plan:
 * 1. PRD 9.5 hard consecutive session exclusion filter with <3 candidate bypass.
 * 2. 6-factor base score equation:
 *    base_score = 0.40 * priority_score + 0.22 * deadline_score + 0.18 * duration_fit_score +
 *                 0.12 * age_score + 0.08 * skip_score - 0.20 * exposure_penalty
 * 3. Subgroup diversity penalty (-0.15 for matching priority + deadline subgroup unless <3 subgroups exist). 24h deadline override.
 * 4. 5-skip reconsideration flag.
 * 5. Deterministic explanation generator (PRD 9.6).
 * 6. Shortest-link fallback state when no links fit available time window.
 */
object RecommendationEngine {

    private data class Candidate(
        val link: Link,
        val baseScore: Double,
        val priorityScore: Double,
        val deadlineScore: Double,
        val durationFitScore: Double,
        val ageScore: Double,
        val skipScore: Double,
        val exposurePenalty: Double,
        val daysSinceSaved: Double,
        val isImminentDeadline: Boolean,
        val isReconsideration: Boolean,
        val subgroupKey: SubgroupKey
    )

    private data class SubgroupKey(
        val priority: Priority,
        val subgroup: DeadlineSubgroup
    )

    fun recommend(
        activeLinks: List<Link>,
        availableMinutes: Int,
        @Suppress("UNUSED_PARAMETER") intentFilter: IntentCategory? = null,
        lastSessionRecommendedLinkIds: List<String> = emptyList(),
        previousSessionRecommendedLinkIds: List<String> = emptyList(),
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RecommendationResult {
        // Step 1: Base eligibility filter
        val activeNonSnoozed = activeLinks.filter { link ->
            link.status == LinkStatus.ACTIVE &&
                    (link.snoozedUntil == null || !now.isBefore(link.snoozedUntil))
        }

        if (activeNonSnoozed.isEmpty()) {
            return RecommendationResult(recommendations = emptyList(), isFallbackShortestLinks = false)
        }

        // Step 2: Duration fit eligibility (or 24h imminent deadline override)
        val durationEligible = activeNonSnoozed.filter { link ->
            val isImminent = isDueWithin24Hours(link, now)
            link.estimatedDurationMinutes <= availableMinutes || isImminent
        }

        // Step 3: Check for "No Links Fit" fallback state (PRD 7.5 line 255 & M-1)
        if (durationEligible.isEmpty()) {
            val fallbackShortest = activeNonSnoozed
                .sortedWith(
                    compareBy<Link> { it.estimatedDurationMinutes }
                        .thenByDescending { it.priority.value }
                        .thenBy { it.savedAt }
                )
                .take(3)
                .map { link ->
                    ScoredRecommendation(
                        link = link,
                        baseScore = 0.0,
                        finalScore = 0.0,
                        explanation = "Exceeds available time",
                        isReconsideration = link.skipCount >= 5,
                        exceedsTime = true
                    )
                }
            return RecommendationResult(
                recommendations = fallbackShortest,
                isFallbackShortestLinks = true
            )
        }

        // Step 4: Hard Consecutive Session Exclusion Filter (PRD 9.5 & H-1)
        val totalCandidatesCount = durationEligible.size
        val candidatesAfterExclusion = durationEligible.filter { link ->
            val shownLastSession = lastSessionRecommendedLinkIds.contains(link.id)
            val isImminent = isDueWithin24Hours(link, now)

            // Bypass exclusion if imminent deadline (<=24h) or total eligible candidates < 3
            if (!shownLastSession || isImminent || totalCandidatesCount < 3) {
                true
            } else {
                false
            }
        }

        val pool = candidatesAfterExclusion.ifEmpty { durationEligible }

        // Step 5: Score Candidates
        val candidates = pool.map { link ->
            scoreCandidate(
                link = link,
                availableMinutes = availableMinutes,
                lastSessionRecommendedLinkIds = lastSessionRecommendedLinkIds,
                previousSessionRecommendedLinkIds = previousSessionRecommendedLinkIds,
                now = now,
                zoneId = zoneId
            )
        }

        // Step 6: Subgroup Diversity Selection
        val distinctSubgroupsCount = candidates.map { it.subgroupKey }.distinct().size
        val applyDiversityPenalty = distinctSubgroupsCount >= 3

        val selectedCandidates = mutableListOf<Candidate>()
        val selectedSubgroupKeys = mutableSetOf<SubgroupKey>()
        val remaining = candidates.toMutableList()

        val selectedRecommendations = mutableListOf<ScoredRecommendation>()

        while (selectedRecommendations.size < 3 && remaining.isNotEmpty()) {
            // Find best remaining candidate
            var bestCandidate: Candidate? = null
            var bestFinalScore = -Double.MAX_VALUE
            var bestDiversityApplied = false

            for (candidate in remaining) {
                val sharesSubgroup = selectedSubgroupKeys.contains(candidate.subgroupKey)
                val diversityApplies = applyDiversityPenalty && sharesSubgroup && !candidate.isImminentDeadline
                val penalty = if (diversityApplies) 0.15 else 0.0
                val finalScore = candidate.baseScore - penalty

                if (finalScore > bestFinalScore) {
                    bestFinalScore = finalScore
                    bestCandidate = candidate
                    bestDiversityApplied = diversityApplies
                } else if (abs(finalScore - bestFinalScore) < 1e-9 && bestCandidate != null) {
                    // Tie-breaker: higher priority, then earlier saved date
                    if (candidate.link.priority.value > bestCandidate.link.priority.value ||
                        (candidate.link.priority.value == bestCandidate.link.priority.value &&
                                candidate.link.savedAt.isBefore(bestCandidate.link.savedAt))
                    ) {
                        bestFinalScore = finalScore
                        bestCandidate = candidate
                        bestDiversityApplied = diversityApplies
                    }
                }
            }

            if (bestCandidate == null) break

            remaining.remove(bestCandidate)
            selectedCandidates.add(bestCandidate)
            selectedSubgroupKeys.add(bestCandidate.subgroupKey)

            val explanation = generateExplanation(
                candidate = bestCandidate,
                availableMinutes = availableMinutes,
                wasDiversityPenaltyApplied = bestDiversityApplied
            )

            val scored = ScoredRecommendation(
                link = bestCandidate.link,
                baseScore = bestCandidate.baseScore,
                finalScore = bestFinalScore,
                explanation = explanation,
                isReconsideration = bestCandidate.isReconsideration,
                exceedsTime = bestCandidate.link.estimatedDurationMinutes > availableMinutes
            )

            selectedRecommendations.add(scored)
        }

        return RecommendationResult(
            recommendations = selectedRecommendations,
            isFallbackShortestLinks = false
        )
    }

    private fun isDueWithin24Hours(link: Link, now: Instant): Boolean {
        val deadline = link.deadlineAt ?: return false
        if (deadline.isBefore(now)) return true
        val hoursRemaining = ChronoUnit.MILLIS.between(now, deadline) / 3600000.0
        return hoursRemaining <= 24.0
    }

    private fun scoreCandidate(
        link: Link,
        availableMinutes: Int,
        lastSessionRecommendedLinkIds: List<String>,
        previousSessionRecommendedLinkIds: List<String>,
        now: Instant,
        zoneId: ZoneId
    ): Candidate {
        // Priority score (0.20 to 1.00)
        val priorityScore = link.priority.value / 5.0

        // Duration fit score (0.0 to 1.0)
        val durationDiff = abs(availableMinutes - link.estimatedDurationMinutes).toDouble()
        val durationDenominator = max(availableMinutes, 1).toDouble()
        val durationFitScore = max(0.0, 1.0 - (durationDiff / durationDenominator))

        // Age score (0.0 to 1.0)
        val millisSinceSaved = max(0L, ChronoUnit.MILLIS.between(link.savedAt, now))
        val daysSinceSaved = millisSinceSaved / 86400000.0
        val ageScore = min(1.0, daysSinceSaved / 30.0)

        // Deadline urgency score
        val isImminent: Boolean
        val deadlineScore: Double
        if (link.deadlineAt == null) {
            deadlineScore = 0.00
            isImminent = false
        } else {
            val millisRemaining = ChronoUnit.MILLIS.between(now, link.deadlineAt)
            val hoursRemaining = millisRemaining / 3600000.0
            isImminent = hoursRemaining <= 24.0

            deadlineScore = when {
                hoursRemaining <= 24.0 -> 1.00 // Overdue or <= 24h
                hoursRemaining in 24.0..96.0 -> 0.80 // 1-3 days
                hoursRemaining in 96.0..192.0 -> 0.50 // 4-7 days
                hoursRemaining in 192.0..336.0 -> 0.25 // 8-14 days
                else -> 0.10 // > 14 days
            }
        }

        // Skip adjustment score
        val isReconsideration = link.skipCount >= 5
        val skipScore = when {
            link.skipCount == 0 -> 0.00
            link.skipCount in 1..2 -> 0.05
            link.skipCount in 3..4 -> 0.10
            else -> -0.20
        }

        // Exposure penalty
        val exposurePenalty = when {
            lastSessionRecommendedLinkIds.contains(link.id) -> 1.00
            previousSessionRecommendedLinkIds.contains(link.id) -> 0.50
            else -> 0.00
        }

        val baseScore = (0.40 * priorityScore) +
                (0.22 * deadlineScore) +
                (0.18 * durationFitScore) +
                (0.12 * ageScore) +
                (0.08 * skipScore) -
                (0.20 * exposurePenalty)

        val subgroupKey = SubgroupKey(
            priority = link.priority,
            subgroup = link.determineSubgroup(now, zoneId)
        )

        return Candidate(
            link = link,
            baseScore = baseScore,
            priorityScore = priorityScore,
            deadlineScore = deadlineScore,
            durationFitScore = durationFitScore,
            ageScore = ageScore,
            skipScore = skipScore,
            exposurePenalty = exposurePenalty,
            daysSinceSaved = daysSinceSaved,
            isImminentDeadline = isImminent,
            isReconsideration = isReconsideration,
            subgroupKey = subgroupKey
        )
    }

    /**
     * Deterministic explanation generator matching PRD 9.6 tie-breaker hierarchy:
     * 1. Deadline urgency (if deadlineScore >= 0.80) -> "Deadline is tomorrow."
     * 2. Priority (if priority 5) -> "Priority 5 and fits your X minutes."
     * 3. Duration fit (if durationFitScore >= 0.90) -> "Fits your available time."
     * 4. Age (if saved >= 14 days ago) -> "Saved X days ago."
     * 5. Neglect / Reconsideration (if skipCount >= 5) -> "You have skipped this several times; decide whether to keep it."
     * 6. Group diversity (if diversity penalty was applied) -> "Chosen to vary your current queue."
     */
    private fun generateExplanation(
        candidate: Candidate,
        @Suppress("UNUSED_PARAMETER") availableMinutes: Int,
        wasDiversityPenaltyApplied: Boolean
    ): String {
        val link = candidate.link

        return when {
            candidate.deadlineScore >= 0.80 -> "Deadline is tomorrow."
            link.priority == Priority.HIGHEST -> "Priority 5 and fits your ${link.estimatedDurationMinutes} minutes."
            candidate.durationFitScore >= 0.90 -> "Fits your available time."
            candidate.daysSinceSaved >= 14.0 -> "Saved ${candidate.daysSinceSaved.toInt()} days ago."
            candidate.isReconsideration -> "You have skipped this several times; decide whether to keep it."
            wasDiversityPenaltyApplied -> "Chosen to vary your current queue."
            else -> "Fits your available time."
        }
    }
}
