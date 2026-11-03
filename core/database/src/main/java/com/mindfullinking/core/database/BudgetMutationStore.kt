package com.mindfullinking.core.database

import androidx.room.withTransaction
import com.mindfullinking.core.domain.BudgetAction
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import java.time.Instant
import java.util.UUID

/** The outcome of a transaction that changes point-consuming link state. */
sealed interface BudgetMutationResult {
    /**
     * Notifications must be scheduled or cancelled only after this result is
     * returned. A failed transaction has no notification side effects.
     */
    data class Success(
        val link: Link,
        /** Apply only after Room has committed the transaction. */
        val notificationEffects: List<NotificationEffect> = emptyList()
    ) : BudgetMutationResult

    data object BudgetExceeded : BudgetMutationResult
    data object LinkNotFound : BudgetMutationResult
    data object LinkIsNotActive : BudgetMutationResult
    data object LinkIsNotArchived : BudgetMutationResult
    data object InvalidDuration : BudgetMutationResult
    data object InvalidResolution : BudgetMutationResult
}

sealed interface NotificationEffect {
    data class ScheduleDeadline(val link: Link) : NotificationEffect
    data class CancelDeadline(val linkId: String) : NotificationEffect
    data class ScheduleOverageExpiry(val expiresAt: Instant) : NotificationEffect
}

/**
 * The only persistence boundary for mutations that can increase attention
 * usage. It re-reads Room state inside one transaction, delegates all budget
 * arithmetic to [BudgetEngine], and commits lifecycle events with the link and
 * budget rows together.
 */
class BudgetMutationStore(
    private val database: LinkLedgerDatabase
) {
    suspend fun updateActiveLinkPriority(
        linkId: String,
        newPriority: Priority,
        now: Instant = Instant.now()
    ): BudgetMutationResult = executeTransaction {
        val linkDao = database.linkDao()
        val existing = linkDao.getLinkById(linkId)?.toDomainModel()
            ?: abort(BudgetMutationResult.LinkNotFound)
        if (existing.status != LinkStatus.ACTIVE) {
            abort(BudgetMutationResult.LinkIsNotActive)
        }

        val state = currentBudgetState()
        val activeLinks = linkDao.getActiveLinks().map { it.toDomainModel() }
        val archivedCount = linkDao.getArchivedCount()
        val action = BudgetAction.UpdateActiveLinkPriority(existing.priority, newPriority)
        if (!BudgetEngine.canPerformAction(state, activeLinks, archivedCount, action, now)) {
            abort(BudgetMutationResult.BudgetExceeded)
        }

        val updated = existing.copy(priority = newPriority, updatedAt = now)
        linkDao.upsertLink(updated.toEntity())
        insertEvent(updated.id, LinkEventType.PRIORITY_CHANGED, now)
        BudgetMutationResult.Success(updated)
    }

    /**
     * Updates an active link's estimate only when it is one of the centrally
     * supported duration options. This keeps UI-only input validation from
     * becoming a persistence bypass.
     */
    suspend fun updateActiveLinkDuration(
        linkId: String,
        newDurationMinutes: Int,
        now: Instant = Instant.now()
    ): BudgetMutationResult = executeTransaction {
        if (!BudgetEngine.isValidEstimatedDurationMinutes(newDurationMinutes)) {
            abort(BudgetMutationResult.InvalidDuration)
        }

        val linkDao = database.linkDao()
        val existing = linkDao.getLinkById(linkId)?.toDomainModel()
            ?: abort(BudgetMutationResult.LinkNotFound)
        if (existing.status != LinkStatus.ACTIVE) {
            abort(BudgetMutationResult.LinkIsNotActive)
        }

        val updated = existing.copy(
            estimatedDurationMinutes = newDurationMinutes,
            updatedAt = now
        )
        linkDao.upsertLink(updated.toEntity())
        BudgetMutationResult.Success(updated)
    }

    /** Saves an active link, optionally applying a selected capacity resolution first. */
    suspend fun saveActiveLink(
        proposedLink: Link,
        resolution: BudgetResolution? = null,
        now: Instant = Instant.now()
    ): BudgetMutationResult = executeTransaction {
        if (!BudgetEngine.isValidEstimatedDurationMinutes(proposedLink.estimatedDurationMinutes)) {
            abort(BudgetMutationResult.InvalidDuration)
        }

        val linkDao = database.linkDao()
        val current = linkDao.getLinkById(proposedLink.id)?.toDomainModel()
        if (resolution?.targetLinkId() == proposedLink.id) {
            abort(BudgetMutationResult.InvalidResolution)
        }

        val resolvedProposal = when (resolution) {
            is BudgetResolution.LowerProposedPriority -> {
                if (resolution.newPriority.value >= proposedLink.priority.value) {
                    abort(BudgetMutationResult.InvalidResolution)
                }
                proposedLink.copy(priority = resolution.newPriority)
            }
            else -> proposedLink
        }
        val resolutionEffects = applyResolution(resolution, now)

        val activeLinks = linkDao.getActiveLinks().map { it.toDomainModel() }
        val archivedCount = linkDao.getArchivedCount()
        val state = currentBudgetState()
        val action = saveAction(current, resolvedProposal.priority)
        if (!BudgetEngine.canPerformAction(state, activeLinks, archivedCount, action, now)) {
            abort(BudgetMutationResult.BudgetExceeded)
        }

        val saved = resolvedProposal.copy(status = LinkStatus.ACTIVE, updatedAt = now)
        linkDao.upsertLink(saved.toEntity())
        insertEvent(saved.id, LinkEventType.SAVE, now)
        val deadlineEffect = when {
            saved.hasSchedulableDeadlineReminder() -> listOf(NotificationEffect.ScheduleDeadline(saved))
            current?.hasSchedulableDeadlineReminder() == true -> listOf(NotificationEffect.CancelDeadline(saved.id))
            else -> emptyList()
        }
        BudgetMutationResult.Success(
            link = saved,
            notificationEffects = resolutionEffects + deadlineEffect
        )
    }

    /** Restores an archived link, optionally applying a selected capacity resolution first. */
    suspend fun restoreArchivedLink(
        linkId: String,
        resolution: BudgetResolution? = null,
        now: Instant = Instant.now()
    ): BudgetMutationResult = executeTransaction {
        val linkDao = database.linkDao()
        val existing = linkDao.getLinkById(linkId)?.toDomainModel()
            ?: abort(BudgetMutationResult.LinkNotFound)
        if (existing.status != LinkStatus.ARCHIVED) {
            abort(BudgetMutationResult.LinkIsNotArchived)
        }
        if (resolution?.targetLinkId() == linkId) {
            abort(BudgetMutationResult.InvalidResolution)
        }

        val resolutionEffects = applyResolution(resolution, now)

        val activeLinks = linkDao.getActiveLinks().map { it.toDomainModel() }
        val archivedCount = linkDao.getArchivedCount()
        val state = currentBudgetState()
        if (!BudgetEngine.canPerformAction(
                state,
                activeLinks,
                archivedCount,
                BudgetAction.RestoreArchivedLink(existing.priority),
                now
            )
        ) {
            abort(BudgetMutationResult.BudgetExceeded)
        }

        val restored = existing.copy(status = LinkStatus.ACTIVE, updatedAt = now)
        linkDao.upsertLink(restored.toEntity())
        insertEvent(restored.id, LinkEventType.RESTORE, now)
        BudgetMutationResult.Success(
            link = restored,
            notificationEffects = resolutionEffects + if (restored.deadlineNotificationsEnabled) {
                listOf(NotificationEffect.ScheduleDeadline(restored))
            } else emptyList()
        )
    }

    /**
     * Applies the capacity resolution to the current transaction and returns
     * post-commit notification work. Rejections abort the surrounding Room
     * transaction, so no partial resolution is committed.
     */
    private suspend fun applyResolution(
        resolution: BudgetResolution?,
        now: Instant
    ): List<NotificationEffect> {
        if (resolution == null) return emptyList()

        val linkDao = database.linkDao()
        return when (resolution) {
            is BudgetResolution.FreeUpLink -> {
                val link = linkDao.getLinkById(resolution.linkId)?.toDomainModel()
                    ?: abort(BudgetMutationResult.LinkNotFound)
                if (link.status != LinkStatus.ACTIVE) abort(BudgetMutationResult.LinkIsNotActive)

                when (resolution.action) {
                    BudgetResolution.FreeUpAction.RESOLVE -> {
                        val updated = link.copy(
                            status = LinkStatus.RESOLVED,
                            resolvedAt = now,
                            updatedAt = now
                        )
                        linkDao.upsertLink(updated.toEntity())
                        insertEvent(updated.id, LinkEventType.RESOLVE, now)
                    }
                    BudgetResolution.FreeUpAction.ARCHIVE -> {
                        val updated = link.copy(status = LinkStatus.ARCHIVED, updatedAt = now)
                        linkDao.upsertLink(updated.toEntity())
                        insertEvent(updated.id, LinkEventType.ARCHIVE, now)
                    }
                    BudgetResolution.FreeUpAction.DELETE -> {
                        linkDao.deleteLinkPermanently(link.id)
                        insertEvent(link.id, LinkEventType.DELETE, now)
                    }
                }
                listOf(NotificationEffect.CancelDeadline(link.id))
            }
            is BudgetResolution.LowerPriority -> {
                val link = linkDao.getLinkById(resolution.linkId)?.toDomainModel()
                    ?: abort(BudgetMutationResult.LinkNotFound)
                if (link.status != LinkStatus.ACTIVE || resolution.newPriority.value >= link.priority.value) {
                    abort(BudgetMutationResult.InvalidResolution)
                }
                val updated = link.copy(priority = resolution.newPriority, updatedAt = now)
                linkDao.upsertLink(updated.toEntity())
                insertEvent(updated.id, LinkEventType.PRIORITY_CHANGED, now)
                emptyList()
            }
            is BudgetResolution.LowerProposedPriority -> emptyList()
            is BudgetResolution.ReplaceLink -> {
                val link = linkDao.getLinkById(resolution.linkIdToReplace)?.toDomainModel()
                    ?: abort(BudgetMutationResult.LinkNotFound)
                if (link.status != LinkStatus.ACTIVE) abort(BudgetMutationResult.LinkIsNotActive)
                linkDao.deleteLinkPermanently(link.id)
                insertEvent(link.id, LinkEventType.DELETE, now)
                listOf(NotificationEffect.CancelDeadline(link.id))
            }
            is BudgetResolution.TemporaryOverage -> {
                val updatedState = try {
                    BudgetEngine.applyOverage(currentBudgetState(), resolution.overagePoints, resolution.durationHours, now)
                } catch (_: IllegalArgumentException) {
                    abort(BudgetMutationResult.InvalidResolution)
                }
                database.budgetDao().upsertBudgetState(updatedState.toEntity())
                listOf(
                    NotificationEffect.ScheduleOverageExpiry(
                        requireNotNull(updatedState.temporaryOverageExpiresAt)
                    )
                )
            }
        }
    }

    private suspend fun currentBudgetState(): BudgetState =
        database.budgetDao().getBudgetState()?.toDomainModel() ?: BudgetState()

    private fun saveAction(current: Link?, proposedPriority: Priority): BudgetAction = when (current?.status) {
        LinkStatus.ACTIVE -> BudgetAction.UpdateActiveLinkPriority(current.priority, proposedPriority)
        LinkStatus.ARCHIVED -> BudgetAction.RestoreArchivedLink(proposedPriority)
        LinkStatus.RESOLVED, null -> BudgetAction.SaveActiveLink(proposedPriority)
    }

    private fun Link.hasSchedulableDeadlineReminder(): Boolean =
        status == LinkStatus.ACTIVE && deadlineNotificationsEnabled && deadlineAt != null

    private suspend fun insertEvent(linkId: String, type: LinkEventType, now: Instant) {
        database.linkEventDao().insertEvent(
            LinkEventEntity(
                id = UUID.randomUUID().toString(),
                linkId = linkId,
                eventType = type.name,
                timestampEpochMs = now.toEpochMilli(),
                details = null
            )
        )
    }

    private fun BudgetResolution.targetLinkId(): String? = when (this) {
        is BudgetResolution.FreeUpLink -> linkId
        is BudgetResolution.LowerPriority -> linkId
        is BudgetResolution.LowerProposedPriority -> null
        is BudgetResolution.ReplaceLink -> linkIdToReplace
        is BudgetResolution.TemporaryOverage -> null
    }

    private suspend fun executeTransaction(
        block: suspend () -> BudgetMutationResult
    ): BudgetMutationResult = try {
        database.withTransaction { block() }
    } catch (abort: MutationAbort) {
        abort.result
    }

    private fun abort(result: BudgetMutationResult): Nothing = throw MutationAbort(result)

    private class MutationAbort(val result: BudgetMutationResult) : RuntimeException()
}
