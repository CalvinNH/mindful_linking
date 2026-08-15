package com.mindfullinking.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.model.LinkStatus

/**
 * WorkManager worker for posting deadline reminder notifications.
 */
class DeadlineWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!NotificationScheduler.hasNotificationPermission(context)) {
            return Result.success()
        }

        val linkId = inputData.getString(KEY_LINK_ID)
        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: REMINDER_TYPE_EXACT
        val inputTitle = inputData.getString(KEY_LINK_TITLE)

        var displayTitle = inputTitle
        var displayUrl: String? = null

        if (!linkId.isNullOrEmpty()) {
            val database = LinkLedgerDatabase.getInstance(context)
            val linkEntity = database.linkDao().getLinkById(linkId) ?: return Result.success()
            val link = linkEntity.toDomainModel()

            if (link.status != LinkStatus.ACTIVE || !link.deadlineNotificationsEnabled) {
                return Result.success()
            }
            displayTitle = link.title
            displayUrl = link.displayUrl
        }

        NotificationChannels.createNotificationChannels(context)

        val headerText = when (reminderType) {
            REMINDER_TYPE_7D -> "Deadline in 7 Days"
            REMINDER_TYPE_3D -> "Deadline in 3 Days"
            REMINDER_TYPE_1D -> "Deadline Tomorrow"
            REMINDER_TYPE_2H -> "Deadline in 2 Hours"
            else -> "Deadline Reached"
        }

        val linkText = displayTitle?.ifBlank { null } ?: displayUrl ?: "Saved Link"
        val notificationText = "$headerText: $linkText"

        val mainActivityIntent = Intent().apply {
            setClassName(context.packageName, "com.mindfullinking.app.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (linkId ?: "deadline").hashCode(),
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.DEADLINE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(headerText)
            .setContentText(linkText)
            .setSubText("Mindful Linking")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationId = (linkId.hashCode() * 31 + reminderType.hashCode())
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission denied safely suppressed
        }

        return Result.success()
    }

    companion object {
        const val KEY_LINK_ID = "link_id"
        const val KEY_REMINDER_TYPE = "reminder_type"
        const val KEY_LINK_TITLE = "link_title"

        const val REMINDER_TYPE_7D = "7d"
        const val REMINDER_TYPE_3D = "3d"
        const val REMINDER_TYPE_1D = "1d"
        const val REMINDER_TYPE_2H = "2h"
        const val REMINDER_TYPE_EXACT = "exact"
    }
}
