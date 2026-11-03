package com.mindfullinking.core.model

import java.time.Instant
import java.time.LocalDate
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
    val deadlineAt: Instant? = null,
    /** Calendar date for a date-only deadline, preserved across time-zone changes. */
    val deadlineDateOnly: LocalDate? = null,
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
        val deadlineDate = deadlineDateIn(zoneId)
        if (deadlineInstantIn(zoneId)?.isBefore(now) == true) {
            return DeadlineSubgroup.OVERDUE
        }

        val todayDate = now.atZone(zoneId).toLocalDate()

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

    /**
     * Returns the user-selected calendar date. Date-only links use their
     * persisted [deadlineDateOnly], never an Instant reinterpreted in a new
     * device time zone.
     */
    fun deadlineDateIn(zoneId: ZoneId): LocalDate? = when {
        deadlineAt == null -> null
        !deadlineHasTime && deadlineDateOnly != null -> deadlineDateOnly
        else -> deadlineAt.atZone(zoneId).toLocalDate()
    }

    /** Resolves a deadline to an instant for comparisons and scheduling. */
    fun deadlineInstantIn(zoneId: ZoneId): Instant? = when {
        deadlineAt == null -> null
        !deadlineHasTime && deadlineDateOnly != null -> deadlineDateOnly
            ?.atTime(23, 59, 59)
            ?.atZone(zoneId)
            ?.toInstant()
        else -> deadlineAt
    }

    companion object {
        val VALID_DURATION_MINUTES: List<Int> = DurationOption.VALID_MINUTES
    }
}
