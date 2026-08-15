package com.mindfullinking.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for ACTION_BOOT_COMPLETED and ACTION_MY_PACKAGE_REPLACED.
 * Queries active links and budget state to reschedule all pending deadline and overage alarms.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAllNotifications(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescheduleAllNotifications(context: Context) {
        val database = LinkLedgerDatabase.getInstance(context)

        // Reschedule active deadline notifications
        val activeLinkEntities = database.linkDao().getActiveLinks()
        for (entity in activeLinkEntities) {
            val link = entity.toDomainModel()
            if (link.deadlineNotificationsEnabled && link.deadlineAt != null) {
                NotificationScheduler.scheduleDeadlineNotification(context, link)
            }
        }

        // Reschedule overage expiry reminder if active
        val budgetStateEntity = database.budgetDao().getBudgetState()
        if (budgetStateEntity != null) {
            val budgetState = budgetStateEntity.toDomainModel()
            val expiresAt = budgetState.temporaryOverageExpiresAt
            if (budgetState.temporaryOverageCents > 0 && expiresAt != null) {
                NotificationScheduler.scheduleOverageExpiryReminder(context, expiresAt)
            }
        }
    }
}
