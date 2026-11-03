package com.mindfullinking.feature.share

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineSelectionTest {
    @Test
    fun dateOnly_keepsLocalDateAsTheDurableSourceOfTruth() {
        val selectedDate = LocalDate.of(2026, 8, 25)
        val result = deadlineSelection(selectedDate, null, ZoneId.of("Asia/Kolkata"))!!

        assertEquals(selectedDate, result.deadlineDateOnly)
        assertFalse(result.deadlineHasTime)
        assertEquals("2026-08-24T18:30:00Z", result.deadlineAt.toString())
    }

    @Test
    fun timedDeadline_persistsExactInstantWithoutDateOnlyValue() {
        val result = deadlineSelection(
            LocalDate.of(2026, 8, 25),
            LocalTime.of(9, 30),
            ZoneId.of("Asia/Kolkata")
        )!!

        assertTrue(result.deadlineHasTime)
        assertNull(result.deadlineDateOnly)
        assertEquals("2026-08-25T04:00:00Z", result.deadlineAt.toString())
    }
}
