package com.mindfullinking.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Manages notification channel setup for Link Ledger.
 */
object NotificationChannels {
    const val DEADLINE_CHANNEL_ID = "deadline_reminders"
    const val OVERAGE_CHANNEL_ID = "overage_warnings"

    /**
     * Ensures required notification channels exist on API 26+.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val deadlineChannel = NotificationChannel(
                DEADLINE_CHANNEL_ID,
                "Deadline Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming link deadlines"
                enableVibration(true)
            }

            val overageChannel = NotificationChannel(
                OVERAGE_CHANNEL_ID,
                "Overage Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for expiring temporary budget overages"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(deadlineChannel)
            notificationManager.createNotificationChannel(overageChannel)
        }
    }
}
