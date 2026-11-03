package com.mindfullinking.feature.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.datastore.UserPreferencesRepository
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.notifications.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun exportEncryptedBackup(uri: Uri, context: Context, passphrase: CharArray, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = db.withTransaction {
                    BackupSnapshot(
                        exportedAtEpochMs = System.currentTimeMillis(),
                        links = db.linkDao().getAllLinks(),
                        budgetState = db.budgetDao().getBudgetState(),
                        events = db.linkEventDao().getAllEvents(),
                        sessions = db.recommendationSessionDao().getAllSessions()
                    )
                }
                val encryptedBackup = EncryptedBackupCodec.encrypt(
                    snapshot,
                    passphrase
                )

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(encryptedBackup)
                } ?: error("Unable to open the selected export location.")

                withContext(Dispatchers.Main) {
                    onComplete(true, "Encrypted backup exported. Keep its passphrase safe.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Export failed: ${e.localizedMessage}")
                }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun performPermanentDataWipe(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkIds = db.linkDao().getAllLinks().map { it.id }
            DataWipeExecutor(
                clearPersistedData = { db.clearAllUserData() },
                cancelNotifications = {
                    NotificationScheduler.cancelAllForDataWipe(getApplication(), linkIds)
                },
                clearPreferences = { prefsRepository.clearAllPreferences() }
            ).wipe()

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun restoreEncryptedBackup(uri: Uri, context: Context, passphrase: CharArray, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val container = context.contentResolver.openInputStream(uri)?.use(EncryptedBackupCodec::readContainer)
                    ?: error("Unable to open the selected backup.")
                ValidatedBackupRestorer { snapshot ->
                    // Only after decryption and validation are successful do we replace local data.
                    val priorLinkIds = db.linkDao().getAllLinks().map { it.id }
                    db.replaceAllUserData(snapshot.links, snapshot.budgetState, snapshot.events, snapshot.sessions)

                    // Side effects occur only after Room commits. A failed restore leaves old alarms intact.
                    NotificationScheduler.cancelAllForDataWipe(getApplication(), priorLinkIds)
                    snapshot.restoredReminderPlan().let { plan ->
                        plan.deadlineLinks.forEach {
                            NotificationScheduler.scheduleDeadlineNotification(getApplication(), it)
                        }
                        plan.overageExpiresAt?.let {
                            NotificationScheduler.scheduleOverageExpiryReminder(getApplication(), it)
                        }
                    }
                }.restore(container, passphrase)
                withContext(Dispatchers.Main) { onComplete(true, "Encrypted backup restored.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, "Restore failed: ${e.localizedMessage}") }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }
}
