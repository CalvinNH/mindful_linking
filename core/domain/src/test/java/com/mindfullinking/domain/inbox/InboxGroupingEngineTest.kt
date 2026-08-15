package com.mindfullinking.domain.inbox

import com.mindfullinking.core.domain.InboxGroupingEngine
import com.mindfullinking.core.model.DeadlineSubgroup
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class InboxGroupingEngineTest {

    private val now: Instant = Instant.parse("2026-08-07T12:00:00Z")
    private val zoneId: ZoneId = ZoneId.of("UTC")

    private fun createLink(
        id: String,
        priority: Priority,
        deadlineAt: Instant? = null,
        savedAt: Instant = now.minus(5, ChronoUnit.DAYS),
        updatedAt: Instant = now.minus(1, ChronoUnit.DAYS),
        status: LinkStatus = LinkStatus.ACTIVE
    ): Link {
        return Link(
            id = id,
            canonicalUrl = "https://example.com/$id",
            displayUrl = "https://example.com/$id",
            priority = priority,
            estimatedDurationMinutes = 10,
            intent = IntentCategory.READ,
            deadlineAt = deadlineAt,
            savedAt = savedAt,
            updatedAt = updatedAt,
            status = status
        )
    }

    @Test
    fun testTwoLevelHierarchyPriority5DownTo1() {
        val linkP5 = createLink(id = "p5", priority = Priority.HIGHEST)
        val linkP3 = createLink(id = "p3", priority = Priority.MEDIUM)
        val linkP1 = createLink(id = "p1", priority = Priority.LOWEST)

        val hierarchy = InboxGroupingEngine.buildHierarchy(
            activeLinks = listOf(linkP3, linkP1, linkP5),
            now = now,
            zoneId = zoneId
        )

        assertEquals(3, hierarchy.size)
        assertEquals(5, hierarchy[0].priority)
        assertEquals(3, hierarchy[1].priority)
        assertEquals(1, hierarchy[2].priority)
    }

    @Test
    fun testDeadlineSubgroupsOrderingWithinPriority() {
        val overdueLink = createLink(id = "overdue", priority = Priority.HIGH, deadlineAt = now.minus(1, ChronoUnit.HOURS))
        val dueTodayLink = createLink(id = "today", priority = Priority.HIGH, deadlineAt = now.plus(2, ChronoUnit.HOURS))
        val due3dLink = createLink(id = "3d", priority = Priority.HIGH, deadlineAt = now.plus(2, ChronoUnit.DAYS))
        val noDeadlineLink = createLink(id = "no_dl", priority = Priority.HIGH, deadlineAt = null)

        val hierarchy = InboxGroupingEngine.buildHierarchy(
            activeLinks = listOf(noDeadlineLink, due3dLink, dueTodayLink, overdueLink),
            now = now,
            zoneId = zoneId
        )

        assertEquals(1, hierarchy.size)
        val subgroups = hierarchy[0].subgroups

        assertEquals(4, subgroups.size)
        assertEquals(DeadlineSubgroup.OVERDUE, subgroups[0].subgroup)
        assertEquals(DeadlineSubgroup.DUE_TODAY, subgroups[1].subgroup)
        assertEquals(DeadlineSubgroup.DUE_WITHIN_3D, subgroups[2].subgroup)
        assertEquals(DeadlineSubgroup.NO_DEADLINE, subgroups[3].subgroup)
    }

    @Test
    fun testThreeTierSubSortingWithinDeadlineSubgroup() {
        // Same priority (Priority 3), all NO_DEADLINE
        // Link 1: saved 10 days ago, updated 2 days ago
        // Link 2: saved 10 days ago, updated 1 hour ago (more recent edit)
        // Link 3: saved 20 days ago (oldest saved date)
        val link1 = createLink(id = "1", priority = Priority.MEDIUM, savedAt = now.minus(10, ChronoUnit.DAYS), updatedAt = now.minus(2, ChronoUnit.DAYS))
        val link2 = createLink(id = "2", priority = Priority.MEDIUM, savedAt = now.minus(10, ChronoUnit.DAYS), updatedAt = now.minus(1, ChronoUnit.HOURS))
        val link3 = createLink(id = "3", priority = Priority.MEDIUM, savedAt = now.minus(20, ChronoUnit.DAYS), updatedAt = now.minus(5, ChronoUnit.DAYS))

        val hierarchy = InboxGroupingEngine.buildHierarchy(
            activeLinks = listOf(link1, link2, link3),
            now = now,
            zoneId = zoneId
        )

        val links = hierarchy[0].subgroups[0].links
        assertEquals(3, links.size)

        // Tier 2 tie-breaker: Link 3 saved 20 days ago (oldest) comes first
        assertEquals("3", links[0].id)

        // Tier 3 tie-breaker: Link 2 updated 1 hour ago (more recent edit) comes before Link 1 updated 2 days ago
        assertEquals("2", links[1].id)
        assertEquals("1", links[2].id)
    }

    @Test
    fun testOnlyActiveLinksIncluded() {
        val activeLink = createLink(id = "active", priority = Priority.MEDIUM, status = LinkStatus.ACTIVE)
        val archivedLink = createLink(id = "archived", priority = Priority.MEDIUM, status = LinkStatus.ARCHIVED)
        val resolvedLink = createLink(id = "resolved", priority = Priority.MEDIUM, status = LinkStatus.RESOLVED)

        val hierarchy = InboxGroupingEngine.buildHierarchy(
            activeLinks = listOf(activeLink, archivedLink, resolvedLink),
            now = now,
            zoneId = zoneId
        )

        assertEquals(1, hierarchy.size)
        val links = hierarchy[0].subgroups[0].links
        assertEquals(1, links.size)
        assertEquals("active", links[0].id)
    }
}
