package com.mindfullinking.feature.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.database.toEntity
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.InboxGroupingEngine
import com.mindfullinking.core.domain.InboxPrioritySection
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.model.ResolutionReason
import com.mindfullinking.core.notifications.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class InboxUiState(
    val prioritySections: List<InboxPrioritySection> = emptyList(),
    val totalActiveCount: Int = 0,
    val usedPoints: Float = 0f,
    val effectiveBudget: Float = 100f,
    val isLoading: Boolean = true
)

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LinkLedgerDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        observeInboxData()
    }

    private fun observeInboxData() {
        viewModelScope.launch {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()

            combine(
                linkDao.getActiveLinksFlow(),
                linkDao.getArchivedLinksFlow(),
                budgetDao.getBudgetStateFlow()
            ) { activeEntities, archivedEntities, budgetEntity ->
                val activeLinks = activeEntities.map { it.toDomainModel() }
                val archivedCount = archivedEntities.size
                val budgetState = budgetEntity?.toDomainModel() ?: BudgetState()

                val now = Instant.now()
                val zoneId = ZoneId.systemDefault()

                val sections = InboxGroupingEngine.buildHierarchy(activeLinks, now, zoneId)
                val usedCents = BudgetEngine.calculateUsedCents(activeLinks, archivedCount)
                val effectiveCents = BudgetEngine.calculateEffectiveBudgetCents(budgetState)

                InboxUiState(
                    prioritySections = sections,
                    totalActiveCount = activeLinks.size,
                    usedPoints = usedCents / 100.0f,
                    effectiveBudget = effectiveCents / 100.0f,
                    isLoading = false
                )
            }.collectLatest { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateLinkPriority(link: Link, newPriority: Priority) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val updated = link.copy(priority = newPriority, updatedAt = Instant.now())
            linkDao.upsertLink(updated.toEntity())
        }
    }

    fun updateLinkDuration(link: Link, newDurationMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val updated = link.copy(estimatedDurationMinutes = newDurationMinutes, updatedAt = Instant.now())
            linkDao.upsertLink(updated.toEntity())
        }
    }

    fun snoozeLink(link: Link, days: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val snoozedUntil = Instant.now().plus(days, ChronoUnit.DAYS)
            val updated = link.copy(snoozedUntil = snoozedUntil, updatedAt = Instant.now())

            linkDao.upsertLink(updated.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.SNOOZE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )
        }
    }

    fun resolveLink(link: Link, reason: ResolutionReason) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updated = link.copy(
                status = LinkStatus.RESOLVED,
                resolutionReason = reason,
                resolvedAt = Instant.now(),
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updated.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.RESOLVE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = reason.name
                )
            )

            NotificationScheduler.cancelDeadlineNotification(getApplication(), link.id)
        }
    }

    fun archiveLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updated = link.copy(
                status = LinkStatus.ARCHIVED,
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updated.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.ARCHIVE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )

            NotificationScheduler.cancelDeadlineNotification(getApplication(), link.id)
        }
    }

    fun deleteLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            linkDao.deleteLinkPermanently(link.id)
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.DELETE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )

            NotificationScheduler.cancelDeadlineNotification(getApplication(), link.id)
        }
    }
}
