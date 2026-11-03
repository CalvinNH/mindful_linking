package com.mindfullinking.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel

/**
 * WorkManager worker for posting 24-hour temporary budget overage pre-expiry warning.
 */
class OverageExpiryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!NotificationScheduler.hasNotificationPermission(context)) {
            return Result.success()
        }

        val database = LinkLedgerDatabase.getInstance(context)
        val budgetStateEntity = database.budgetDao().getBudgetState() ?: return Result.success()
        val budgetState = budgetStateEntity.toDomainModel()

        if (budgetState.temporaryOverageCents <= 0 || budgetState.temporaryOverageExpiresAt == null) {
            return Result.success()
        }

        NotificationChannels.createNotificationChannels(context)

        val notificationTitle = "Temporary Overage Expiring Soon"
        val notificationText = "Your temporary budget overage will expire in 24 hours. Resolve or archive links to return to your 100-point budget."

        val mainActivityIntent = Intent().apply {
            setClassName(context.packageName, "com.mindfullinking.app.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.OVERAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText("Mindful Linking")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission denied safely suppressed
        }

        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 9001
        const val WORK_TAG = "overage_expiry_reminder"
        const val UNIQUE_WORK_NAME = "overage_expiry"
    }
}
