package com.mindfullinking.feature.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPermissionCoordinatorTest {
    @Test
    fun android13WithoutPermission_requestsPermissionBeforeEnablingReminder() {
        val plan = reminderEnablementPlan(
            notificationPermissionGranted = false,
            notificationPermissionRequestSupported = true,
            exactAlarmsAvailable = true
        )

        assertTrue(plan.requestNotificationPermission)
        assertTrue(plan.explanation!!.contains("permission"))
    }

    @Test
    fun deniedPermissionOnOlderPlatform_keepsReminderOff() {
        val plan = reminderEnablementPlan(
            notificationPermissionGranted = false,
            notificationPermissionRequestSupported = false,
            exactAlarmsAvailable = true
        )

        assertFalse(plan.requestNotificationPermission)
        assertTrue(plan.explanation!!.contains("stays off"))
    }

    @Test
    fun unavailableExactAlarms_explainsWorkManagerFallback() {
        val plan = reminderEnablementPlan(
            notificationPermissionGranted = true,
            notificationPermissionRequestSupported = true,
            exactAlarmsAvailable = false
        )

        assertFalse(plan.requestNotificationPermission)
        assertEquals(
            "Reminders are on. Your device does not allow exact alarms, so delivery may be delayed slightly.",
            plan.explanation
        )
    }
}
