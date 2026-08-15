package com.mindfullinking.feature.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.database.toEntity
import com.mindfullinking.core.domain.BudgetAction
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

data class ArchiveUiState(
    val archivedLinks: List<Link> = emptyList(),
    val filteredLinks: List<Link> = emptyList(),
    val searchQuery: String = "",
    val activeLinks: List<Link> = emptyList(),
    val budgetState: BudgetState = BudgetState(),
    val usedPoints: Float = 0f,
    val effectiveBudget: Float = 100f,
    val isLoading: Boolean = true
)

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LinkLedgerDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeArchiveData()
    }

    private fun observeArchiveData() {
        viewModelScope.launch {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()

            combine(
                linkDao.getArchivedLinksFlow(),
                linkDao.getActiveLinksFlow(),
                budgetDao.getBudgetStateFlow(),
                _searchQuery
            ) { archivedEntities, activeEntities, budgetEntity, query ->
                val archivedLinks = archivedEntities.map { it.toDomainModel() }
                val activeLinks = activeEntities.map { it.toDomainModel() }
                val budgetState = budgetEntity?.toDomainModel() ?: BudgetState()

                val filtered = if (query.isBlank()) {
                    archivedLinks
                } else {
                    val q = query.trim().lowercase()
                    archivedLinks.filter { link ->
                        (link.title?.lowercase()?.contains(q) == true) ||
                                link.displayUrl.lowercase().contains(q) ||
                                (link.note?.lowercase()?.contains(q) == true) ||
                                link.canonicalUrl.lowercase().contains(q)
                    }
                }

                val usedCents = BudgetEngine.calculateUsedCents(activeLinks, archivedLinks.size)
                val effectiveCents = BudgetEngine.calculateEffectiveBudgetCents(budgetState)

                ArchiveUiState(
                    archivedLinks = archivedLinks,
                    filteredLinks = filtered,
                    searchQuery = query,
                    activeLinks = activeLinks,
                    budgetState = budgetState,
                    usedPoints = usedCents / 100.0f,
                    effectiveBudget = effectiveCents / 100.0f,
                    isLoading = false
                )
            }.collectLatest { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun canRestoreLink(link: Link): Boolean {
        val state = _uiState.value
        return BudgetEngine.canPerformAction(
            budgetState = state.budgetState,
            activeLinks = state.activeLinks,
            archivedLinkCount = state.archivedLinks.size,
            action = BudgetAction.RestoreArchivedLink(link.priority)
        )
    }

    fun restoreLink(link: Link) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val updated = link.copy(
                status = LinkStatus.ACTIVE,
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(updated.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = link.id,
                    eventType = LinkEventType.RESTORE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )
        }
    }

    fun deleteLinkPermanently(link: Link) {
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
        }
    }

    fun applyOverage(overagePoints: Int, durationHours: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val budgetDao = db.budgetDao()
            val updatedState = BudgetEngine.applyOverage(_uiState.value.budgetState, overagePoints, durationHours)
            budgetDao.upsertBudgetState(updatedState.toEntity())
        }
    }
}
