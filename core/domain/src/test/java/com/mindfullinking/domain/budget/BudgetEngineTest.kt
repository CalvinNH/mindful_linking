package com.mindfullinking.domain.budget

import com.mindfullinking.core.domain.BudgetAction
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.CreatedFrom
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.MetadataFetchStatus
import com.mindfullinking.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class BudgetEngineTest {

    private fun createDummyLink(
        id: String = "1",
        priority: Priority = Priority.MEDIUM,
        status: LinkStatus = LinkStatus.ACTIVE
    ): Link {
        return Link(
            id = id,
            canonicalUrl = "https://example.com/$id",
            displayUrl = "https://example.com/$id",
            priority = priority,
            estimatedDurationMinutes = 10,
            intent = IntentCategory.READ,
            status = status,
            savedAt = Instant.now()
        )
    }

    @Test
    fun testUsedCentsCalculation() {
        val activeLinks = listOf(
            createDummyLink(id = "1", priority = Priority.HIGHEST), // 500 cents
            createDummyLink(id = "2", priority = Priority.HIGH),    // 400 cents
            createDummyLink(id = "3", priority = Priority.LOW)      // 200 cents
        )
        val archivedCount = 4 // 4 * 25 = 100 cents

        val used = BudgetEngine.calculateUsedCents(activeLinks, archivedCount)
        assertEquals(1200, used) // 12.00 pts
    }

    @Test
    fun testIncrementalCosts() {
        // Save active link Priority 5: +500 cents
        assertEquals(500, BudgetEngine.calculateIncrementalCostCents(BudgetAction.SaveActiveLink(Priority.HIGHEST)))

        // Restore archived link Priority 4: 400 - 25 = +375 cents
        assertEquals(375, BudgetEngine.calculateIncrementalCostCents(BudgetAction.RestoreArchivedLink(Priority.HIGH)))

        // Archive active link Priority 5: 25 - 500 = -475 cents
        assertEquals(-475, BudgetEngine.calculateIncrementalCostCents(BudgetAction.ArchiveActiveLink(Priority.HIGHEST)))

        // Delete archived link: -25 cents
        assertEquals(-25, BudgetEngine.calculateIncrementalCostCents(BudgetAction.DeleteArchivedLink))

        // Delete active link Priority 3: -300 cents
        assertEquals(-300, BudgetEngine.calculateIncrementalCostCents(BudgetAction.DeleteActiveLink(Priority.MEDIUM)))

        // Update priority Priority 2 -> Priority 5: (5 - 2) * 100 = +300 cents
        assertEquals(
            300,
            BudgetEngine.calculateIncrementalCostCents(
                BudgetAction.UpdateActiveLinkPriority(oldPriority = Priority.LOW, newPriority = Priority.HIGHEST)
            )
        )
    }

    @Test
    fun testCanPerformActionStandardBudget() {
        val budgetState = BudgetState() // 10,000 cents
        val now = Instant.now()

        // 19 active links at Priority 5 = 9,500 cents
        val activeLinks = (1..19).map { createDummyLink(id = "$it", priority = Priority.HIGHEST) }

        // Can save Priority 5 (500 cents): 9,500 + 500 = 10,000 <= 10,000 -> true
        assertTrue(
            BudgetEngine.canPerformAction(
                budgetState = budgetState,
                activeLinks = activeLinks,
                archivedLinkCount = 0,
                action = BudgetAction.SaveActiveLink(Priority.HIGHEST),
                now = now
            )
        )

        // 20 active links at Priority 5 = 10,000 cents
        val fullLinks = (1..20).map { createDummyLink(id = "$it", priority = Priority.HIGHEST) }

        // Cannot save another Priority 1 (100 cents): 10,000 + 100 > 10,000 -> false
        assertFalse(
            BudgetEngine.canPerformAction(
                budgetState = budgetState,
                activeLinks = fullLinks,
                archivedLinkCount = 0,
                action = BudgetAction.SaveActiveLink(Priority.LOWEST),
                now = now
            )
        )
    }

    @Test
    fun testTemporaryOverageAndExpiryLock() {
        val now = Instant.now()
        val budgetState = BudgetState()

        // Apply 20 points overage (2,000 cents) for 24 hours
        val overageState = BudgetEngine.applyOverage(budgetState, overagePoints = 20, durationHours = 24, now = now)

        assertEquals(12000, BudgetEngine.calculateEffectiveBudgetCents(overageState, now))
        assertTrue(overageState.isOverageActive(now))

        // Fill budget to 11,500 cents
        val activeLinks = (1..23).map { createDummyLink(id = "$it", priority = Priority.HIGHEST) }

        // While overage is active, can save Priority 5 link (11,500 + 500 = 12,000)
        assertTrue(
            BudgetEngine.canPerformAction(
                budgetState = overageState,
                activeLinks = activeLinks,
                archivedLinkCount = 0,
                action = BudgetAction.SaveActiveLink(Priority.HIGHEST),
                now = now
            )
        )

        // After overage expires (25 hours later)
        val futureTime = now.plus(25, ChronoUnit.HOURS)
        assertFalse(overageState.isOverageActive(futureTime))
        assertEquals(10000, BudgetEngine.calculateEffectiveBudgetCents(overageState, futureTime))

        // Budget is now locked because 11,500 > 10,000
        assertTrue(BudgetEngine.isBudgetLocked(overageState, activeLinks, 0, futureTime))

        // Cannot save new links
        assertFalse(
            BudgetEngine.canPerformAction(
                budgetState = overageState,
                activeLinks = activeLinks,
                archivedLinkCount = 0,
                action = BudgetAction.SaveActiveLink(Priority.LOWEST),
                now = futureTime
            )
        )

        // CAN perform actions that decrease budget (e.g. archiving or resolving links)
        assertTrue(
            BudgetEngine.canPerformAction(
                budgetState = overageState,
                activeLinks = activeLinks,
                archivedLinkCount = 0,
                action = BudgetAction.ArchiveActiveLink(Priority.HIGHEST),
                now = futureTime
            )
        )
    }
}
