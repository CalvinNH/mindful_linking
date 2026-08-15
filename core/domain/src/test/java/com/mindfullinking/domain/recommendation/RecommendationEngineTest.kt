package com.mindfullinking.domain.recommendation

import com.mindfullinking.core.domain.RecommendationEngine
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class RecommendationEngineTest {

    private val now: Instant = Instant.parse("2026-08-07T12:00:00Z")
    private val zoneId: ZoneId = ZoneId.of("UTC")

    private fun createLink(
        id: String,
        priority: Priority = Priority.MEDIUM,
        duration: Int = 15,
        deadlineAt: Instant? = null,
        skipCount: Int = 0,
        savedAt: Instant = now.minus(5, ChronoUnit.DAYS),
        intent: IntentCategory = IntentCategory.READ,
        status: LinkStatus = LinkStatus.ACTIVE
    ): Link {
        return Link(
            id = id,
            canonicalUrl = "https://example.com/$id",
            displayUrl = "https://example.com/$id",
            priority = priority,
            estimatedDurationMinutes = duration,
            intent = intent,
            deadlineAt = deadlineAt,
            savedAt = savedAt,
            skipCount = skipCount,
            status = status
        )
    }

    @Test
    fun testHardConsecutiveSessionExclusionFilter() {
        // Create 4 candidates (so total eligible >= 3)
        val link1 = createLink(id = "1")
        val link2 = createLink(id = "2")
        val link3 = createLink(id = "3")
        val link4 = createLink(id = "4")

        // Link 1 was recommended last session, has no imminent deadline
        val result = RecommendationEngine.recommend(
            activeLinks = listOf(link1, link2, link3, link4),
            availableMinutes = 30,
            lastSessionRecommendedLinkIds = listOf("1"),
            now = now,
            zoneId = zoneId
        )

        // Link 1 should be excluded by consecutive session filter
        assertFalse(result.recommendations.any { it.link.id == "1" })
        assertEquals(3, result.recommendations.size)
    }

    @Test
    fun testConsecutiveSessionExclusionBypassedIfImminentDeadline() {
        // Link 1 was recommended last session, but is due in 10 hours
        val imminentLink = createLink(id = "1", deadlineAt = now.plus(10, ChronoUnit.HOURS))
        val link2 = createLink(id = "2")
        val link3 = createLink(id = "3")
        val link4 = createLink(id = "4")

        val result = RecommendationEngine.recommend(
            activeLinks = listOf(imminentLink, link2, link3, link4),
            availableMinutes = 30,
            lastSessionRecommendedLinkIds = listOf("1"),
            now = now,
            zoneId = zoneId
        )

        // Imminent link SHOULD be included despite last session recommendation
        assertTrue(result.recommendations.any { it.link.id == "1" })
    }

    @Test
    fun testConsecutiveSessionExclusionBypassedIfLessThanThreeCandidates() {
        // Only 2 candidate links exist
        val link1 = createLink(id = "1")
        val link2 = createLink(id = "2")

        val result = RecommendationEngine.recommend(
            activeLinks = listOf(link1, link2),
            availableMinutes = 30,
            lastSessionRecommendedLinkIds = listOf("1"),
            now = now,
            zoneId = zoneId
        )

        // Link 1 is included because total candidate pool < 3
        assertEquals(2, result.recommendations.size)
        assertTrue(result.recommendations.any { it.link.id == "1" })
    }

    @Test
    fun testFiveSkipReconsiderationFlag() {
        val linkWith5Skips = createLink(id = "1", skipCount = 5)
        val linkNormal = createLink(id = "2", skipCount = 0)

        val result = RecommendationEngine.recommend(
            activeLinks = listOf(linkWith5Skips, linkNormal),
            availableMinutes = 30,
            now = now,
            zoneId = zoneId
        )

        val rec = result.recommendations.first { it.link.id == "1" }
        assertTrue(rec.isReconsideration)
        assertEquals("You have skipped this several times; decide whether to keep it.", rec.explanation)
    }

    @Test
    fun testNoLinksFitShortestLinkFallbackState() {
        // All active links exceed available minutes (e.g. available = 10, links = 30, 45, 60 mins)
        val link1 = createLink(id = "1", duration = 30, priority = Priority.HIGH)
        val link2 = createLink(id = "2", duration = 45, priority = Priority.MEDIUM)
        val link3 = createLink(id = "3", duration = 60, priority = Priority.HIGHEST)

        val result = RecommendationEngine.recommend(
            activeLinks = listOf(link1, link2, link3),
            availableMinutes = 10,
            now = now,
            zoneId = zoneId
        )

        assertTrue(result.isFallbackShortestLinks)
        assertEquals(3, result.recommendations.size)
        assertTrue(result.recommendations.all { it.exceedsTime })
        assertEquals("Exceeds available time", result.recommendations.first().explanation)
        // Sorted shortest duration first (30 mins = link1)
        assertEquals("1", result.recommendations.first().link.id)
    }

    @Test
    fun testDeterministicExplanations() {
        val deadlineLink = createLink(id = "1", deadlineAt = now.plus(12, ChronoUnit.HOURS))
        val priority5Link = createLink(id = "2", priority = Priority.HIGHEST, duration = 15)

        val result = RecommendationEngine.recommend(
            activeLinks = listOf(deadlineLink, priority5Link),
            availableMinutes = 15,
            now = now,
            zoneId = zoneId
        )

        val rec1 = result.recommendations.first { it.link.id == "1" }
        assertEquals("Deadline is tomorrow.", rec1.explanation)

        val rec2 = result.recommendations.first { it.link.id == "2" }
        assertEquals("Priority 5 and fits your 15 minutes.", rec2.explanation)
    }

    @Test
    fun testOneAndThreeMinuteDurationRecommendation() {
        val link1m = createLink(id = "1m", duration = 1, priority = Priority.HIGH)
        val link3m = createLink(id = "3m", duration = 3, priority = Priority.HIGH)
        val link5m = createLink(id = "5m", duration = 5, priority = Priority.MEDIUM)
        val link10m = createLink(id = "10m", duration = 10, priority = Priority.LOW)

        // Available time = 1 minute
        val result1m = RecommendationEngine.recommend(
            activeLinks = listOf(link1m, link3m, link5m, link10m),
            availableMinutes = 1,
            now = now,
            zoneId = zoneId
        )

        assertFalse(result1m.isFallbackShortestLinks)
        assertEquals(1, result1m.recommendations.size)
        assertEquals("1m", result1m.recommendations.first().link.id)
        assertFalse(result1m.recommendations.first().exceedsTime)

        // Available time = 3 minutes
        val result3m = RecommendationEngine.recommend(
            activeLinks = listOf(link1m, link3m, link5m, link10m),
            availableMinutes = 3,
            now = now,
            zoneId = zoneId
        )

        assertFalse(result3m.isFallbackShortestLinks)
        assertEquals(2, result3m.recommendations.size)
        assertTrue(result3m.recommendations.any { it.link.id == "1m" })
        assertTrue(result3m.recommendations.any { it.link.id == "3m" })
    }

    @Test
    fun testIntentCategoryFilterRemoved() {
        val readLink = createLink(id = "read", intent = IntentCategory.READ, priority = Priority.HIGH)
        val watchLink = createLink(id = "watch", intent = IntentCategory.WATCH, priority = Priority.MEDIUM)
        val actLink = createLink(id = "act", intent = IntentCategory.ACT, priority = Priority.LOW)

        // Request recommendations with WATCH intent filter
        val result = RecommendationEngine.recommend(
            activeLinks = listOf(readLink, watchLink, actLink),
            availableMinutes = 15,
            intentFilter = IntentCategory.WATCH,
            now = now,
            zoneId = zoneId
        )

        // Intent filter should be ignored and all eligible links returned
        assertEquals(3, result.recommendations.size)
        assertTrue(result.recommendations.any { it.link.id == "read" })
        assertTrue(result.recommendations.any { it.link.id == "watch" })
        assertTrue(result.recommendations.any { it.link.id == "act" })
    }
}
