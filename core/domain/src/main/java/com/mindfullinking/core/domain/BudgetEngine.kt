package com.mindfullinking.core.domain

import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Incremental domain actions that affect the point budget.
 */
sealed interface BudgetAction {
    data class SaveActiveLink(val priority: Priority) : BudgetAction
    data class RestoreArchivedLink(val priority: Priority) : BudgetAction
    data class ArchiveActiveLink(val priority: Priority) : BudgetAction
    data object DeleteArchivedLink : BudgetAction
    data class DeleteActiveLink(val priority: Priority) : BudgetAction
    data class ResolveActiveLink(val priority: Priority) : BudgetAction
    data class UpdateActiveLinkPriority(val oldPriority: Priority, val newPriority: Priority) : BudgetAction
}

/**
 * Scaled integer attention budget calculation engine.
 * Implements PRD Section 6 point economics:
 * - Standard budget = 100.00 points = 10,000 cents.
 * - Active link priority 1..5 = 100..500 cents.
 * - Archived link cost = 0.25 points = 25 cents.
 * - All computations use scaled integer cents to prevent floating-point rounding errors (resolving H-5).
 */
object BudgetEngine {

    const val STANDARD_BUDGET_CENTS: Int = BudgetState.STANDARD_BUDGET_CENTS // 10,000 cents
    const val ARCHIVED_LINK_COST_CENTS: Int = BudgetState.ARCHIVED_LINK_COST_CENTS // 25 cents
    val ALLOWED_OVERAGE_DURATION_HOURS: Set<Long> = setOf(24L, 7L * 24L, 30L * 24L)

    /**
     * Intake and quick-edit controls use a fixed duration set. Keep the
     * validation at the mutation boundary as well so malformed or stale UI
     * input can never be persisted.
     */
    fun isValidEstimatedDurationMinutes(minutes: Int): Boolean =
        minutes in Link.VALID_DURATION_MINUTES

    /**
     * Computes the total used budget in cents across all active links and retained archived links.
     */
    fun calculateUsedCents(activeLinks: List<Link>, archivedLinkCount: Int): Int {
        val activeCents = activeLinks
            .filter { it.status == LinkStatus.ACTIVE }
            .sumOf { it.priority.value * 100 }
        val archivedCents = archivedLinkCount.coerceAtLeast(0) * ARCHIVED_LINK_COST_CENTS
        return activeCents + archivedCents
    }

    /**
     * Computes effective budget in cents considering active temporary overage expiration.
     */
    fun calculateEffectiveBudgetCents(budgetState: BudgetState, now: Instant = Instant.now()): Int {
        return if (budgetState.isOverageActive(now)) {
            budgetState.standardBudgetCents + budgetState.temporaryOverageCents
        } else {
            budgetState.standardBudgetCents
        }
    }

    /**
     * Computes available budget in cents (effective budget - used budget). Can be negative if over-budget.
     */
    fun calculateAvailableCents(
        budgetState: BudgetState,
        activeLinks: List<Link>,
        archivedLinkCount: Int,
        now: Instant = Instant.now()
    ): Int {
        return calculateEffectiveBudgetCents(budgetState, now) - calculateUsedCents(activeLinks, archivedLinkCount)
    }

    /**
     * Computes incremental cost in cents for a given domain budget action.
     * - Save active link (+Priority * 100)
     * - Restore archived link (+Priority * 100 - 25)
     * - Archive active link (25 - Priority * 100)
     * - Delete archived link (-25)
     * - Delete active link (-Priority * 100)
     * - Resolve active link (-Priority * 100)
     * - Update priority ((new - old) * 100)
     */
    fun calculateIncrementalCostCents(action: BudgetAction): Int {
        return when (action) {
            is BudgetAction.SaveActiveLink -> action.priority.value * 100
            is BudgetAction.RestoreArchivedLink -> (action.priority.value * 100) - ARCHIVED_LINK_COST_CENTS
            is BudgetAction.ArchiveActiveLink -> ARCHIVED_LINK_COST_CENTS - (action.priority.value * 100)
            is BudgetAction.DeleteArchivedLink -> -ARCHIVED_LINK_COST_CENTS
            is BudgetAction.DeleteActiveLink -> -(action.priority.value * 100)
            is BudgetAction.ResolveActiveLink -> -(action.priority.value * 100)
            is BudgetAction.UpdateActiveLinkPriority -> (action.newPriority.value - action.oldPriority.value) * 100
        }
    }

    /**
     * Checks if a proposed action can be performed within the current effective budget.
     */
    fun canPerformAction(
        budgetState: BudgetState,
        activeLinks: List<Link>,
        archivedLinkCount: Int,
        action: BudgetAction,
        now: Instant = Instant.now()
    ): Boolean {
        val incrementalCost = calculateIncrementalCostCents(action)
        if (incrementalCost <= 0) {
            // Actions that reduce or maintain point usage (archive, delete, resolve, lower priority) are always permitted
            return true
        }
        val used = calculateUsedCents(activeLinks, archivedLinkCount)
        val effectiveBudget = calculateEffectiveBudgetCents(budgetState, now)
        return (used + incrementalCost) <= effectiveBudget
    }

    /**
     * Checks if the budget is in a locked state (used budget exceeds effective budget).
     * Occurs when a temporary overage expires while used budget > standard budget.
     */
    fun isBudgetLocked(
        budgetState: BudgetState,
        activeLinks: List<Link>,
        archivedLinkCount: Int,
        now: Instant = Instant.now()
    ): Boolean {
        val used = calculateUsedCents(activeLinks, archivedLinkCount)
        val effective = calculateEffectiveBudgetCents(budgetState, now)
        return used > effective
    }

    /**
     * Applies a temporary overage to the budget state.
     * Allowed overage point additions: 5, 10, 20, up to 50 points (500 to 5000 cents).
     * Expiry options: 24h, 7d, 30d (expressed in hours).
     */
    fun applyOverage(
        budgetState: BudgetState,
        overagePoints: Int,
        durationHours: Long,
        now: Instant = Instant.now()
    ): BudgetState {
        require(overagePoints in 1..50) { "Overage points must be between 1 and 50 points" }
        require(durationHours in ALLOWED_OVERAGE_DURATION_HOURS) {
            "Overage duration must be 24 hours, 7 days, or 30 days"
        }

        val overageCents = overagePoints * 100
        val expiresAt = now.plus(durationHours, ChronoUnit.HOURS)

        return budgetState.copy(
            temporaryOverageCents = overageCents,
            temporaryOverageExpiresAt = expiresAt,
            lastOverageReminderAt = null
        )
    }

    /**
     * Clears any active temporary overage.
     */
    fun clearOverage(budgetState: BudgetState): BudgetState {
        return budgetState.copy(
            temporaryOverageCents = 0,
            temporaryOverageExpiresAt = null,
            lastOverageReminderAt = null
        )
    }
}
