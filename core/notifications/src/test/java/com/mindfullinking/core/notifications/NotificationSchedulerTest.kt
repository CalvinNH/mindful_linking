package com.mindfullinking.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationSchedulerTest {

    @Test
    fun channelAliases_matchTheChannelsUsedByNotificationWorkers() {
        assertEquals(NotificationChannels.DEADLINE_CHANNEL_ID, NotificationScheduler.DEADLINE_CHANNEL_ID)
        assertEquals(NotificationChannels.OVERAGE_CHANNEL_ID, NotificationScheduler.OVERAGE_CHANNEL_ID)
    }

    @Test
    fun calculateDeadlineInstant_whenHasTime_returnsExactInstant() {
        val exactInstant = Instant.parse("2026-08-15T14:30:00Z")
        val result = NotificationScheduler.calculateDeadlineInstant(
            deadlineAt = exactInstant,
            deadlineHasTime = true,
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(exactInstant, result)
    }

    @Test
    fun calculateDeadlineInstant_whenDateOnly_anchorsToLocal235959() {
        val utcNoon = Instant.parse("2026-08-15T12:00:00Z")
        val zoneId = ZoneId.of("America/New_York")

        // 2026-08-15 12:00 UTC is 2026-08-15 08:00 EDT (EDT is UTC-4)
        val result = NotificationScheduler.calculateDeadlineInstant(
            deadlineAt = utcNoon,
            deadlineHasTime = false,
            zoneId = zoneId
        )

        val resultZoned = result.atZone(zoneId)
        assertEquals(LocalDate.of(2026, 8, 15), resultZoned.toLocalDate())
        assertEquals(23, resultZoned.hour)
        assertEquals(59, resultZoned.minute)
        assertEquals(59, resultZoned.second)
    }

    @Test
    fun calculateDeadlineInstant_timezoneChange_recalculatesUtcEpochMs() {
        val baseInstant = Instant.parse("2026-08-15T12:00:00Z")

        val nyZone = ZoneId.of("America/New_York") // UTC-4 in August
        val tokyoZone = ZoneId.of("Asia/Tokyo")     // UTC+9

        val nyResult = NotificationScheduler.calculateDeadlineInstant(
            deadlineAt = baseInstant,
            deadlineHasTime = false,
            zoneId = nyZone
        )

        val tokyoResult = NotificationScheduler.calculateDeadlineInstant(
            deadlineAt = baseInstant,
            deadlineHasTime = false,
            zoneId = tokyoZone
        )

        // 2026-08-15 23:59:59 in NY is 2026-08-16 03:59:59 UTC
        // 2026-08-15 23:59:59 in Tokyo is 2026-08-15 14:59:59 UTC
        assertEquals(Instant.parse("2026-08-16T03:59:59Z"), nyResult)
        assertEquals(Instant.parse("2026-08-15T14:59:59Z"), tokyoResult)
    }

    @Test
    fun calculateDeadlineInstant_dateOnlyKeepsCalendarDayAcrossKolkataToNewYork() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val newYork = ZoneId.of("America/New_York")
        val selectedDate = LocalDate.of(2026, 8, 25)
        val instantStoredByOlderVersions = selectedDate.atStartOfDay(kolkata).toInstant()

        val result = NotificationScheduler.calculateDeadlineInstant(
            deadlineAt = instantStoredByOlderVersions,
            deadlineHasTime = false,
            deadlineDateOnly = selectedDate,
            zoneId = newYork
        )

        assertEquals(Instant.parse("2026-08-26T03:59:59Z"), result)
        assertEquals(selectedDate, result.atZone(newYork).toLocalDate())
    }
}
