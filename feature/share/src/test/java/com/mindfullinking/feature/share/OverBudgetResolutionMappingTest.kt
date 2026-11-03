package com.mindfullinking.feature.share

import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.ui.component.OverBudgetResolution
import org.junit.Assert.assertEquals
import org.junit.Test

class OverBudgetResolutionMappingTest {

    @Test
    fun `lowering the proposed link maps to the transaction-local resolution`() {
        val result = OverBudgetResolution.LowerPriority("proposal", Priority.LOWEST)
            .toBudgetResolutionFor("proposal")

        assertEquals(BudgetResolution.LowerProposedPriority(Priority.LOWEST), result)
    }

    @Test
    fun `all existing-link resolutions preserve their selected action`() {
        assertEquals(
            BudgetResolution.FreeUpLink("active", BudgetResolution.FreeUpAction.ARCHIVE),
            OverBudgetResolution.FreeUpLink("active", OverBudgetResolution.FreeUpAction.ARCHIVE)
                .toBudgetResolutionFor("proposal")
        )
        assertEquals(
            BudgetResolution.LowerPriority("active", Priority.LOW),
            OverBudgetResolution.LowerPriority("active", Priority.LOW)
                .toBudgetResolutionFor("proposal")
        )
        assertEquals(
            BudgetResolution.ReplaceLink("active"),
            OverBudgetResolution.ReplaceLink("active").toBudgetResolutionFor("proposal")
        )
        assertEquals(
            BudgetResolution.TemporaryOverage(5, 24),
            OverBudgetResolution.TemporaryOverage(5, 24).toBudgetResolutionFor("proposal")
        )
    }
}
