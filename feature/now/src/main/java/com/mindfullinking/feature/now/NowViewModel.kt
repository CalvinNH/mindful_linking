package com.mindfullinking.feature.now

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.database.toEntity
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.RecommendationEngine
import com.mindfullinking.core.domain.RecommendationResult
import com.mindfullinking.core.domain.ScoredRecommendation
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.ResolutionReason
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
import java.util.UUID

data class NowUiState(
    val usedPoints: Float = 0f,
    val effectiveBudget: Float = 100f,
    val isOverageActive: Boolean = false,
    val overageExpiresAt: Instant? = null,
    val availableMinutes: Int = 15,
    val selectedIntentFilter: IntentCategory? = null,
    val recommendationResult: RecommendationResult = RecommendationResult(emptyList()),
    val activeLinks: List<Link> = emptyList(),
    val activeLinksCount: Int = 0,
    val isLoading: Boolean = true
)

class NowViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LinkLedgerDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(NowUiState())
    val uiState: StateFlow<NowUiState> = _uiState.asStateFlow()

    private val _availableMinutes = MutableStateFlow(15)
    private val _selectedIntentFilter = MutableStateFlow<IntentCategory?>(null)

    private var lastSessionRecommendedIds: List<String> = emptyList()
    private var previousSessionRecommendedIds: List<String> = emptyList()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()

            combine(
                linkDao.getActiveLinksFlow(),
                linkDao.getArchivedLinksFlow(),
                budgetDao.getBudgetStateFlow(),
                _availableMinutes,
                _selectedIntentFilter
            ) { activeEntities, archivedEntities, budgetEntity, minutes, intentFilter ->
                val activeLinks = activeEntities.map { it.toDomainModel() }
                val archivedCount = archivedEntities.size
                val budgetState = budgetEntity?.toDomainModel() ?: BudgetState()

                val usedCents = BudgetEngine.calculateUsedCents(activeLinks, archivedCount)
                val effectiveCents = BudgetEngine.calculateEffectiveBudgetCents(budgetState)

                val result = RecommendationEngine.recommend(
                    activeLinks = activeLinks,
                    availableMinutes = minutes,
                    intentFilter = intentFilter,
                    lastSessionRecommendedLinkIds = lastSessionRecommendedIds,
                    previousSessionRecommendedLinkIds = previousSessionRecommendedIds,
                    now = Instant.now(),
                    zoneId = ZoneId.systemDefault()
                )

                // Log session if recommendations found
                if (result.recommendations.isNotEmpty()) {
                    val recommendedIds = result.recommendations.map { it.link.id }
                    previousSessionRecommendedIds = lastSessionRecommendedIds
                    lastSessionRecommendedIds = recommendedIds
                }

                NowUiState(
                    usedPoints = usedCents / 100.0f,
                    effectiveBudget = effectiveCents / 100.0f,
                    isOverageActive = budgetState.isOverageActive(Instant.now()),
                    overageExpiresAt = budgetState.temporaryOverageExpiresAt,
                    availableMinutes = minutes,
                    selectedIntentFilter = intentFilter,
                    recommendationResult = result,
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

    fun setIntentFilter(intent: IntentCategory?) {
        _selectedIntentFilter.value = intent
    }

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
