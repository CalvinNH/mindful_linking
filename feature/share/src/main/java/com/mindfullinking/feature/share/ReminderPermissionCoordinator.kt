package com.mindfullinking.feature.share

/**
 * Presentation policy for enabling a link deadline reminder.
 *
 * Keeping this decision independent of Android framework classes makes the
 * permission path deterministic and testable. Scheduling itself remains in
 * :core:notifications, after the Room mutation commits.
 */
internal data class ReminderEnablementPlan(
    val requestNotificationPermission: Boolean,
    val explanation: String?
)

internal fun reminderEnablementPlan(
    notificationPermissionGranted: Boolean,
    notificationPermissionRequestSupported: Boolean,
    exactAlarmsAvailable: Boolean
): ReminderEnablementPlan = when {
    !notificationPermissionGranted && notificationPermissionRequestSupported -> ReminderEnablementPlan(
        requestNotificationPermission = true,
        explanation = "Android will ask for permission before reminders can be enabled."
    )
    !notificationPermissionGranted -> ReminderEnablementPlan(
        requestNotificationPermission = false,
        explanation = "Notifications are disabled for Mindful Linking, so this reminder stays off."
    )
    !exactAlarmsAvailable -> ReminderEnablementPlan(
        requestNotificationPermission = false,
        explanation = "Reminders are on. Your device does not allow exact alarms, so delivery may be delayed slightly."
    )
    else -> ReminderEnablementPlan(
        requestNotificationPermission = false,
        explanation = null
    )
}
