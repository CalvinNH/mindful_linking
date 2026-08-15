package com.mindfullinking.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Domain model representing a link item in Link Ledger.
 */
data class Link(
    val id: String,
    val canonicalUrl: String,
    val displayUrl: String,
    val title: String? = null,
    val note: String? = null,
    val priority: Priority,
    val estimatedDurationMinutes: Int,
    val intent: IntentCategory = IntentCategory.READ,
    val deadlineAt: Instant? = null,
    val deadlineHasTime: Boolean = false,
    val deadlineNotificationsEnabled: Boolean = false,
    val status: LinkStatus = LinkStatus.ACTIVE,
    val resolutionReason: ResolutionReason? = null,
    val savedAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val lastOpenedAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val skipCount: Int = 0,
    val lastSkippedAt: Instant? = null,
    val recommendationCount: Int = 0,
    val lastRecommendedAt: Instant? = null,
    val createdFrom: CreatedFrom = CreatedFrom.MANUAL,
    val metadataFetchStatus: MetadataFetchStatus = MetadataFetchStatus.DISABLED,
    val updatedAt: Instant = Instant.now()
) {
    /**
     * Determines the inbox deadline subgroup based on current time and local time zone.
     */
    fun determineSubgroup(now: Instant, zoneId: ZoneId): DeadlineSubgroup {
        val deadline = deadlineAt ?: return DeadlineSubgroup.NO_DEADLINE
        if (deadline.isBefore(now)) {
            return DeadlineSubgroup.OVERDUE
        }

        val todayDate = now.atZone(zoneId).toLocalDate()
        val deadlineDate = deadline.atZone(zoneId).toLocalDate()

        if (deadlineDate == todayDate) {
            return DeadlineSubgroup.DUE_TODAY
        }

        val daysDifference = ChronoUnit.DAYS.between(todayDate, deadlineDate)
        return when {
            daysDifference in 1..3 -> DeadlineSubgroup.DUE_WITHIN_3D
            daysDifference in 4..7 -> DeadlineSubgroup.DUE_WITHIN_7D
            else -> DeadlineSubgroup.DUE_LATER
        }
    }

    companion object {
        val VALID_DURATION_MINUTES: List<Int> = DurationOption.VALID_MINUTES
    }
}
