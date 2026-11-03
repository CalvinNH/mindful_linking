package com.mindfullinking.feature.share

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A share-intake deadline ready for persistence in the domain model. */
internal data class DeadlineSelection(
    val deadlineAt: Instant,
    val deadlineDateOnly: LocalDate?,
    val deadlineHasTime: Boolean
)

/**
 * Date-only selections retain their [LocalDate] as the source of truth.
 * [deadlineAt] is retained for compatibility with existing sorting/storage,
 * while schedulers resolve the date against the user's current time zone.
 */
internal fun deadlineSelection(
    date: LocalDate?,
    time: LocalTime?,
    zoneId: ZoneId
): DeadlineSelection? = date?.let { selectedDate ->
    if (time == null) {
        DeadlineSelection(
            deadlineAt = selectedDate.atStartOfDay(zoneId).toInstant(),
            deadlineDateOnly = selectedDate,
            deadlineHasTime = false
        )
    } else {
        DeadlineSelection(
            deadlineAt = LocalDateTime.of(selectedDate, time).atZone(zoneId).toInstant(),
            deadlineDateOnly = null,
            deadlineHasTime = true
        )
    }
}
