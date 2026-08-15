package com.mindfullinking.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Handles scheduling, rescheduling, and cancellation of deadline reminders and overage warnings.
 */
object NotificationScheduler {

    const val DEADLINE_CHANNEL_ID = "deadline_reminders"
    const val OVERAGE_CHANNEL_ID = "overage_reminders"

    private val REMINDER_TYPES = listOf(
        DeadlineWorker.REMINDER_TYPE_7D,
        DeadlineWorker.REMINDER_TYPE_3D,
        DeadlineWorker.REMINDER_TYPE_1D,
        DeadlineWorker.REMINDER_TYPE_2H,
        DeadlineWorker.REMINDER_TYPE_EXACT
    )

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            
            val deadlineChannel = NotificationChannel(
                DEADLINE_CHANNEL_ID,
                "Deadline Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for link deadlines"
            }
            
            val overageChannel = NotificationChannel(
                OVERAGE_CHANNEL_ID,
                "Attention Budget Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for budget overage expiration"
            }

            notificationManager.createNotificationChannel(deadlineChannel)
            notificationManager.createNotificationChannel(overageChannel)
        }
    }

    fun checkNotificationPermission(context: Context): Boolean {
        return hasNotificationPermission(context)
    }

    /**
     * Converts date-only deadlines to 23:59:59 local time in [zoneId], returning target Instant.
     * If time is specified (deadlineHasTime == true), returns original deadlineAt.
     */
    fun calculateDeadlineInstant(
        deadlineAt: Instant,
        deadlineHasTime: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Instant {
        if (deadlineHasTime) {
            return deadlineAt
        }
        val localDate = deadlineAt.atZone(zoneId).toLocalDate()
        return localDate.atTime(23, 59, 59).atZone(zoneId).toInstant()
    }

    /**
     * Checks POST_NOTIFICATIONS permission for Android 13+ (API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if exact alarm scheduling is permitted on Android 12+ (API 31+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun scheduleDeadlineNotification(context: Context, link: Link, zoneId: ZoneId = ZoneId.systemDefault()) {
        scheduleDeadlineNotifications(context, link, zoneId)
    }

    /**
     * Schedules deadline reminders at 7d, 3d, 1d, 2h (if time specified), and exact instant.
     * Safe permission guard: if POST_NOTIFICATIONS is denied, suppresses system notification
     * without altering link deadline data.
     */
    fun scheduleDeadlineNotifications(
        context: Context,
        link: Link,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        // Cancel existing pending notifications for this link first
        cancelDeadlineNotifications(context, link.id)

        if (!hasNotificationPermission(context)) {
            // Suppress system notifications safely without modifying link data
            return
        }

        if (!link.deadlineNotificationsEnabled || link.deadlineAt == null || link.status != LinkStatus.ACTIVE) {
            return
        }

        val targetInstant = calculateDeadlineInstant(link.deadlineAt!!, link.deadlineHasTime, zoneId)
        val now = Instant.now()

        val remindersToSchedule = mutableMapOf<String, Instant>()
        remindersToSchedule[DeadlineWorker.REMINDER_TYPE_7D] = targetInstant.minus(7, ChronoUnit.DAYS)
        remindersToSchedule[DeadlineWorker.REMINDER_TYPE_3D] = targetInstant.minus(3, ChronoUnit.DAYS)
        remindersToSchedule[DeadlineWorker.REMINDER_TYPE_1D] = targetInstant.minus(1, ChronoUnit.DAYS)
        if (link.deadlineHasTime) {
            remindersToSchedule[DeadlineWorker.REMINDER_TYPE_2H] = targetInstant.minus(2, ChronoUnit.HOURS)
        }
        remindersToSchedule[DeadlineWorker.REMINDER_TYPE_EXACT] = targetInstant

        val allowExact = canScheduleExactAlarms(context)

        for ((reminderType, triggerInstant) in remindersToSchedule) {
            if (triggerInstant.isAfter(now)) {
                if (allowExact) {
                    scheduleExactAlarm(context, link, reminderType, triggerInstant)
                } else {
                    scheduleWorkManagerFallback(context, link, reminderType, triggerInstant)
                }
            }
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        link: Link,
        reminderType: String,
        triggerInstant: Instant
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DeadlineAlarmReceiver::class.java).apply {
            putExtra(DeadlineAlarmReceiver.EXTRA_LINK_ID, link.id)
            putExtra(DeadlineAlarmReceiver.EXTRA_REMINDER_TYPE, reminderType)
            putExtra(DeadlineAlarmReceiver.EXTRA_LINK_TITLE, link.title)
        }

        val requestCode = (link.id.hashCode() * 31 + reminderType.hashCode())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerInstant.toEpochMilli(),
            pendingIntent
        )
    }

    private fun scheduleWorkManagerFallback(
        context: Context,
        link: Link,
        reminderType: String,
        triggerInstant: Instant
    ) {
        val delayMs = Duration.between(Instant.now(), triggerInstant).toMillis().coerceAtLeast(0)
        val workData = workDataOf(
            DeadlineWorker.KEY_LINK_ID to link.id,
            DeadlineWorker.KEY_REMINDER_TYPE to reminderType,
            DeadlineWorker.KEY_LINK_TITLE to link.title
        )

        val workRequest = OneTimeWorkRequestBuilder<DeadlineWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .addTag("deadline_${link.id}")
            .build()

        val uniqueWorkName = "deadline_${link.id}_$reminderType"
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Enqueues a WorkManager worker 24 hours prior to temporary overage expiration.
     */
    fun scheduleOverageExpiryReminder(context: Context, expiresAt: Instant) {
        cancelOverageExpiryReminder(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val reminderInstant = expiresAt.minus(24, ChronoUnit.HOURS)
        val delayMs = Duration.between(Instant.now(), reminderInstant).toMillis()

        if (delayMs > 0) {
            val workRequest = OneTimeWorkRequestBuilder<OverageExpiryWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(OverageExpiryWorker.WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                OverageExpiryWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    fun cancelDeadlineNotification(context: Context, linkId: String) {
        cancelDeadlineNotifications(context, linkId)
    }

    /**
     * Immediate notification cancellation on link resolution, archive, or deletion.
     */
    fun cancelDeadlineNotifications(context: Context, linkId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        for (reminderType in REMINDER_TYPES) {
            val intent = Intent(context, DeadlineAlarmReceiver::class.java)
            val requestCode = (linkId.hashCode() * 31 + reminderType.hashCode())
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            val uniqueWorkName = "deadline_${linkId}_$reminderType"
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        }

        WorkManager.getInstance(context).cancelAllWorkByTag("deadline_$linkId")
    }

    /**
     * Cancels pending 24-hour overage expiry reminder.
     */
    fun cancelOverageExpiryReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(OverageExpiryWorker.UNIQUE_WORK_NAME)
        WorkManager.getInstance(context).cancelAllWorkByTag(OverageExpiryWorker.WORK_TAG)
    }
}
