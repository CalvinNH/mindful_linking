package com.mindfullinking.core.domain

import com.mindfullinking.core.model.DeadlineSubgroup
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Priority section in the 2-level Inbox hierarchy (Priority 5 down to 1).
 */
data class InboxPrioritySection(
    val priority: Int,
    val subgroups: List<InboxDeadlineSubgroupSection>
)

/**
 * Deadline subgroup section within a Priority section.
 */
data class InboxDeadlineSubgroupSection(
    val subgroup: DeadlineSubgroup,
    val links: List<Link>
)

/**
 * Constructs 2-level Inbox hierarchy (PRD 7.4 & Implementation Plan):
 * 1. Level 1: Priority sections (5 down to 1).
 * 2. Level 2: 6 Deadline Subgroups (OVERDUE, DUE_TODAY, DUE_WITHIN_3D, DUE_WITHIN_7D, DUE_LATER, NO_DEADLINE).
 * 3. 3-tier tie-breaker sub-sorting:
 *    - Tier 1: Earliest deadline (deadlineAt ascending, nulls last)
 *    - Tier 2: Oldest saved date (savedAt ascending)
 *    - Tier 3: Most recent edit date (updatedAt descending)
 *
 * STRICT GUARANTEE: Recommendation scores are NEVER stored or exposed in the Inbox.
 */
object InboxGroupingEngine {

    fun buildHierarchy(
        activeLinks: List<Link>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<InboxPrioritySection> {
        val activeOnly = activeLinks.filter { it.status == LinkStatus.ACTIVE }

        return activeOnly.groupBy { it.priority.value }
            .toSortedMap(Comparator.reverseOrder())
            .map { (priorityValue, linksInPriority) ->
                val subgroupSections = linksInPriority.groupBy { it.determineSubgroup(now, zoneId) }
                    .toSortedMap(compareBy { it.displayOrder })
                    .map { (subgroup, subgroupLinks) ->
                        val sortedLinks = subgroupLinks.sortedWith(
                            compareBy<Link> { it.deadlineAt ?: Instant.MAX }
                                .thenBy { it.savedAt }
                                .thenByDescending { it.updatedAt }
                        )
                        InboxDeadlineSubgroupSection(subgroup, sortedLinks)
                    }
                InboxPrioritySection(priorityValue, subgroupSections)
            }
    }
}
