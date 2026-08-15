package com.mindfullinking.feature.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.datastore.UserPreferencesRepository
import com.mindfullinking.core.model.LinkEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

data class EventCounts(
    val saveCount: Int = 0,
    val archiveCount: Int = 0,
    val resolveCount: Int = 0,
    val restoreCount: Int = 0,
    val deleteCount: Int = 0,
    val skipCount: Int = 0,
    val openCount: Int = 0
)

data class SettingsUiState(
    val metadataFetchEnabled: Boolean = false,
    val analyticsOptIn: Boolean = false,
    val eventCounts: EventCounts = EventCounts(),
    val exportSuccessMessage: String? = null,
    val isLoading: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepository = UserPreferencesRepository(application)
    private val db = LinkLedgerDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            val eventDao = db.linkEventDao()

            combine(
                prefsRepository.metadataFetchEnabled,
                prefsRepository.analyticsOptIn,
                eventDao.getAllEventsFlow()
            ) { metadataEnabled, analyticsOptIn, events ->
                val counts = EventCounts(
                    saveCount = events.count { it.eventType == LinkEventType.SAVE.name },
                    archiveCount = events.count { it.eventType == LinkEventType.ARCHIVE.name },
                    resolveCount = events.count { it.eventType == LinkEventType.RESOLVE.name },
                    restoreCount = events.count { it.eventType == LinkEventType.RESTORE.name },
                    deleteCount = events.count { it.eventType == LinkEventType.DELETE.name },
                    skipCount = events.count { it.eventType == LinkEventType.SKIP.name },
                    openCount = events.count { it.eventType == LinkEventType.OPEN.name }
                )

                SettingsUiState(
                    metadataFetchEnabled = metadataEnabled,
                    analyticsOptIn = analyticsOptIn,
                    eventCounts = counts,
                    isLoading = false
                )
            }.collectLatest { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setMetadataFetchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setMetadataFetchEnabled(enabled)
        }
    }

    fun setAnalyticsOptIn(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAnalyticsOptIn(enabled)
        }
    }

    fun exportEncryptedBackup(uri: Uri, context: Context, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val linkDao = db.linkDao()
                val budgetDao = db.budgetDao()
                val eventDao = db.linkEventDao()

                val links = linkDao.getAllLinks().map { it.toDomainModel() }
                val budgetState = budgetDao.getBudgetState()?.toDomainModel()
                val events = eventDao.getAllEvents()

                val rootJson = JSONObject().apply {
                    put("version", 1)
                    put("exportedAt", System.currentTimeMillis())
                    
                    val linksArray = JSONArray()
                    links.forEach { link ->
                        linksArray.put(JSONObject().apply {
                            put("id", link.id)
                            put("canonicalUrl", link.canonicalUrl)
                            put("displayUrl", link.displayUrl)
                            put("title", link.title)
                            put("note", link.note)
                            put("priority", link.priority.value)
                            put("estimatedDurationMinutes", link.estimatedDurationMinutes)
                            put("intent", link.intent.name)
                            put("status", link.status.name)
                            put("savedAt", link.savedAt.toEpochMilli())
                        })
                    }
                    put("links", linksArray)

                    if (budgetState != null) {
                        put("budgetState", JSONObject().apply {
                            put("standardBudgetCents", budgetState.standardBudgetCents)
                            put("temporaryOverageCents", budgetState.temporaryOverageCents)
                        })
                    }

                    put("eventCount", events.size)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(rootJson.toString(2))
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete(true, "Database snapshot successfully exported.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun performPermanentDataWipe(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()
            val eventDao = db.linkEventDao()

            linkDao.deleteAllLinks()
            budgetDao.resetBudgetState()
            eventDao.deleteAllEvents()
            prefsRepository.clearAllPreferences()

            WorkManager.getInstance(getApplication()).cancelAllWork()

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
