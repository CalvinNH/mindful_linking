package com.mindfullinking.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * BroadcastReceiver for ACTION_TIMEZONE_CHANGED.
 * Recalculates local 23:59 anchors for date-only deadlines and updates scheduled notifications.
 */
class TimeZoneChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recalculateAndReschedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun recalculateAndReschedule(context: Context) {
        val database = LinkLedgerDatabase.getInstance(context)
        val currentZoneId = ZoneId.systemDefault()

        val activeLinkEntities = database.linkDao().getActiveLinks()
        for (entity in activeLinkEntities) {
            val link = entity.toDomainModel()
            if (link.deadlineNotificationsEnabled && link.deadlineAt != null) {
                NotificationScheduler.scheduleDeadlineNotifications(context, link, currentZoneId)
            }
        }
    }
}
