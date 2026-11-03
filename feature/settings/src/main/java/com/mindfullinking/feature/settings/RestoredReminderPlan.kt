package com.mindfullinking.feature.settings

import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.model.Link
import java.time.Instant

/** Post-commit reminder work required after a validated backup replacement. */
internal data class RestoredReminderPlan(
    val deadlineLinks: List<Link>,
    val overageExpiresAt: Instant?
)

internal fun BackupSnapshot.restoredReminderPlan(): RestoredReminderPlan = RestoredReminderPlan(
    deadlineLinks = links.map { it.toDomainModel() }
        .filter { it.deadlineNotificationsEnabled && it.deadlineAt != null },
    overageExpiresAt = budgetState
        ?.takeIf { it.temporaryOverageCents > 0 }
        ?.temporaryOverageExpiresAtEpochMs
        ?.let(Instant::ofEpochMilli)
)
