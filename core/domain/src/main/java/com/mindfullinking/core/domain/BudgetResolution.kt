package com.mindfullinking.core.domain

import com.mindfullinking.core.model.Priority

/**
 * A user-selected resolution for an action that would otherwise exceed the
 * attention budget. This is deliberately UI-independent so persistence can
 * apply the resolution and the requested lifecycle transition atomically.
 */
sealed interface BudgetResolution {
    data class FreeUpLink(val linkId: String, val action: FreeUpAction) : BudgetResolution
    data class LowerPriority(val linkId: String, val newPriority: Priority) : BudgetResolution
    /** Lowers the not-yet-persisted link that is being saved in this transaction. */
    data class LowerProposedPriority(val newPriority: Priority) : BudgetResolution
    data class ReplaceLink(val linkIdToReplace: String) : BudgetResolution
    data class TemporaryOverage(val overagePoints: Int, val durationHours: Long) : BudgetResolution

    enum class FreeUpAction {
        RESOLVE,
        ARCHIVE,
        DELETE
    }
}
