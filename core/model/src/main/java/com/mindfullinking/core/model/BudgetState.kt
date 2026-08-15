package com.mindfullinking.core.model

import java.time.Instant

/**
 * Scaled integer attention budget state representation.
 * Points are scaled by 100 to integer cents to eliminate floating point rounding errors:
 * - 100.00 pts = 10000 cents
 * - 1.00 pt = 100 cents
 * - 0.25 pt = 25 cents
 */
data class BudgetState(
    val standardBudgetCents: Int = STANDARD_BUDGET_CENTS,
    val temporaryOverageCents: Int = 0,
    val temporaryOverageExpiresAt: Instant? = null,
    val lastOverageReminderAt: Instant? = null
) {
    val standardBudgetPoints: Double
        get() = standardBudgetCents / 100.0

    val temporaryOveragePoints: Double
        get() = temporaryOverageCents / 100.0

    val effectiveBudgetCents: Int
        get() = standardBudgetCents + temporaryOverageCents

    val effectiveBudgetPoints: Double
        get() = effectiveBudgetCents / 100.0

    fun isOverageActive(now: Instant): Boolean {
        val expiresAt = temporaryOverageExpiresAt ?: return false
        return temporaryOverageCents > 0 && now.isBefore(expiresAt)
    }

    companion object {
        const val STANDARD_BUDGET_CENTS: Int = 10000 // 100.00 points
        const val ARCHIVED_LINK_COST_CENTS: Int = 25   // 0.25 points
    }
}
