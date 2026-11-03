package com.mindfullinking.feature.settings

/** Ensures irreversible storage deletion commits before notification effects run. */
internal class DataWipeExecutor(
    private val clearPersistedData: suspend () -> Unit,
    private val cancelNotifications: suspend () -> Unit,
    private val clearPreferences: suspend () -> Unit
) {
    suspend fun wipe() {
        clearPersistedData()
        cancelNotifications()
        clearPreferences()
    }
}
