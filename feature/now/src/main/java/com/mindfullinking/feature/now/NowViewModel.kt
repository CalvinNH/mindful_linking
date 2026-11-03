package com.mindfullinking.feature.now

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.RecommendationSessionEntity
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.database.toEntity
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.RecommendationResult
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.RecommendationSession
import com.mindfullinking.core.model.RecommendationSessionHistory
import com.mindfullinking.core.model.ResolutionReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class NowUiState(
    val usedPoints: Float = 0f,
    val effectiveBudget: Float = 100f,
    val isOverageActive: Boolean = false,
    val overageExpiresAt: Instant? = null,
    val availableMinutes: Int = 15,
    val recommendationResult: RecommendationResult = RecommendationResult(emptyList()),
    val lastSessionRecommendedLinkIds: List<String> = emptyList(),
    val previousSessionRecommendedLinkIds: List<String> = emptyList(),
    val activeLinks: List<Link> = emptyList(),
    val activeLinksCount: Int = 0,
    val isLoading: Boolean = true
)

class NowViewModel @JvmOverloads constructor(
    application: Application,
    private val db: LinkLedgerDatabase = LinkLedgerDatabase.getInstance(application),
    private val persistCompletedSession: suspend (RecommendationSessionEntity) -> Unit = {
        session -> db.recommendationSessionDao().insertCompletedSession(session)
    }
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NowUiState())
    val uiState: StateFlow<NowUiState> = _uiState.asStateFlow()

    private val _availableMinutes = MutableStateFlow(15)
    private val _durableRecommendationSessions = MutableStateFlow<List<RecommendationSession>>(emptyList())
    private val _pendingRecommendationSessions = MutableStateFlow<Map<String, RecommendationSession>>(emptyMap())
    private val completedAuctionWriteMutex = Mutex()
    private val latestSessionStartedAtEpochMs = AtomicLong(Long.MIN_VALUE)

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            db.recommendationSessionDao().getSessionsFlow().collectLatest { sessionEntities ->
                _durableRecommendationSessions.value = sessionEntities
                    .map { it.toDomainModel() }
                    .sortedBySessionOrder()
            }
        }

        viewModelScope.launch {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()
            val sessionHistorySource = combine(
                _durableRecommendationSessions,
                _pendingRecommendationSessions
            ) { durableSessions, pendingSessions ->
                historyFrom(durableSessions, pendingSessions.values)
            }

            combine(
                linkDao.getActiveLinksFlow(),
                linkDao.getArchivedLinksFlow(),
                budgetDao.getBudgetStateFlow(),
                _availableMinutes,
                sessionHistorySource
            ) { activeEntities, archivedEntities, budgetEntity, minutes, sessionHistory ->
                val activeLinks = activeEntities.map { it.toDomainModel() }
                val archivedCount = archivedEntities.size
                val budgetState = budgetEntity?.toDomainModel() ?: BudgetState()

                val usedCents = BudgetEngine.calculateUsedCents(activeLinks, archivedCount)
                val effectiveCents = BudgetEngine.calculateEffectiveBudgetCents(budgetState)

                NowUiState(
                    usedPoints = usedCents / 100.0f,
                    effectiveBudget = effectiveCents / 100.0f,
                    isOverageActive = budgetState.isOverageActive(Instant.now()),
                    overageExpiresAt = budgetState.temporaryOverageExpiresAt,
                    availableMinutes = minutes,
                    lastSessionRecommendedLinkIds = sessionHistory.lastRecommendedLinkIds,
                    previousSessionRecommendedLinkIds = sessionHistory.previousRecommendedLinkIds,
                    activeLinks = activeLinks,
                    activeLinksCount = activeLinks.size,
                    isLoading = false
                )
            }.collectLatest { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setAvailableMinutes(minutes: Int) {
        _availableMinutes.value = minutes
    }

    /** Persists history only for an auction that reached its completed-results phase. */
    fun recordCompletedAuction(availableMinutes: Int, recommendedLinkIds: List<String>) {
        val session = RecommendationSession(
            id = UUID.randomUUID().toString(),
            availableMinutes = availableMinutes,
            startedAt = nextSessionStartedAt(),
            recommendedLinkIds = recommendedLinkIds
        )
        _pendingRecommendationSessions.update { it + (session.id to session) }
        updateUiHistoryFromSessions()

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                completedAuctionWriteMutex.withLock {
                    persistCompletedSession(session.toEntity())
                    _durableRecommendationSessions.value = db.recommendationSessionDao()
                        .getMostRecentSessions(limit = 2)
                        .map { it.toDomainModel() }
                        .sortedBySessionOrder()
                }
            }
            _pendingRecommendationSessions.update { it - session.id }
            updateUiHistoryFromSessions()
        }
    }

    /** Preserves completion order even when two user actions land in the same millisecond. */
    private fun nextSessionStartedAt(): Instant {
        val nowEpochMs = Instant.now().toEpochMilli()
        val timestamp = latestSessionStartedAtEpochMs.updateAndGet { previous ->
            maxOf(nowEpochMs, previous + 1)
        }
        return Instant.ofEpochMilli(timestamp)
    }

    private fun updateUiHistoryFromSessions() {
        val history = historyFrom(
            durableSessions = _durableRecommendationSessions.value,
            pendingSessions = _pendingRecommendationSessions.value.values
        )
        _uiState.update {
            it.copy(
                lastSessionRecommendedLinkIds = history.lastRecommendedLinkIds,
                previousSessionRecommendedLinkIds = history.previousRecommendedLinkIds
            )
        }
    }

    private fun historyFrom(
        durableSessions: List<RecommendationSession>,
        pendingSessions: Collection<RecommendationSession>
    ): RecommendationSessionHistory = RecommendationSessionHistory.fromSessions(
        (durableSessions + pendingSessions)
            .distinctBy { it.id }
            .sortedBySessionOrder()
            .take(2)
    )

    private fun List<RecommendationSession>.sortedBySessionOrder(): List<RecommendationSession> =
        sortedWith(compareByDescending<RecommendationSession> { it.startedAt }.thenByDescending { it.id })

    fun skipLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updatedLink = link.copy(
                skipCount = link.skipCount + 1,
                lastSkippedAt = Instant.now(),
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updatedLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.SKIP.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )
        }
    }

    fun openLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updatedLink = link.copy(
                lastOpenedAt = Instant.now(),
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updatedLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.OPEN.name,
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

            val updatedLink = link.copy(
                status = com.mindfullinking.core.model.LinkStatus.RESOLVED,
                resolutionReason = reason,
                resolvedAt = Instant.now(),
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updatedLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.RESOLVE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = reason.name
                )
            )
        }
    }

    fun snoozeLink(link: Link, snoozeUntil: Instant) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updatedLink = link.copy(
                snoozedUntil = snoozeUntil,
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updatedLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.SNOOZE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = snoozeUntil.toString()
                )
            )
        }
    }

    fun archiveLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updatedLink = link.copy(
                status = com.mindfullinking.core.model.LinkStatus.ARCHIVED,
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updatedLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.ARCHIVE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )
        }
    }
}
