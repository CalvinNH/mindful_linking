package com.mindfullinking.core.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.model.LinkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for handling exact alarms scheduled via AlarmManager.
 */
class DeadlineAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationScheduler.hasNotificationPermission(context)) {
            return
        }

        val linkId = intent.getStringExtra(EXTRA_LINK_ID) ?: return
        val reminderType = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: DeadlineWorker.REMINDER_TYPE_EXACT
        val extraTitle = intent.getStringExtra(EXTRA_LINK_TITLE)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = LinkLedgerDatabase.getInstance(context)
                val linkEntity = database.linkDao().getLinkById(linkId)
                if (linkEntity != null) {
                    val link = linkEntity.toDomainModel()
                    if (link.status == LinkStatus.ACTIVE && link.deadlineNotificationsEnabled) {
                        postNotification(
                            context = context,
                            linkId = linkId,
                            reminderType = reminderType,
                            title = link.title ?: extraTitle,
                            displayUrl = link.displayUrl
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(
        context: Context,
        linkId: String,
        reminderType: String,
        title: String?,
        displayUrl: String?
    ) {
        NotificationChannels.createNotificationChannels(context)

        val headerText = when (reminderType) {
            DeadlineWorker.REMINDER_TYPE_7D -> "Deadline in 7 Days"
            DeadlineWorker.REMINDER_TYPE_3D -> "Deadline in 3 Days"
            DeadlineWorker.REMINDER_TYPE_1D -> "Deadline Tomorrow"
            DeadlineWorker.REMINDER_TYPE_2H -> "Deadline in 2 Hours"
            else -> "Deadline Reached"
        }

        val linkText = title?.ifBlank { null } ?: displayUrl ?: "Saved Link"
        val notificationText = "$headerText: $linkText"

        val mainActivityIntent = Intent().apply {
            setClassName(context.packageName, "com.mindfullinking.app.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            linkId.hashCode(),
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
            // Suppressed if permission denied
        }
    }

    companion object {
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_LINK_TITLE = "extra_link_title"
    }
}
