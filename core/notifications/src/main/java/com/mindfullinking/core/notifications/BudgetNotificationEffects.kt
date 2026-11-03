package com.mindfullinking.core.notifications

import android.content.Context
import com.mindfullinking.core.database.NotificationEffect

/**
 * Performs notification work emitted by a committed [BudgetMutationStore]
 * transaction. Call this only for a successful mutation so failed Room
 * transactions cannot leave orphaned reminders behind.
 */
fun applyBudgetNotificationEffects(
    context: Context,
    effects: List<NotificationEffect>
) {
    effects.forEach { effect ->
        when (effect) {
            is NotificationEffect.ScheduleDeadline ->
                NotificationScheduler.scheduleDeadlineNotification(context, effect.link)
            is NotificationEffect.CancelDeadline ->
                NotificationScheduler.cancelDeadlineNotification(context, effect.linkId)
            is NotificationEffect.ScheduleOverageExpiry ->
                NotificationScheduler.scheduleOverageExpiryReminder(context, effect.expiresAt)
        }
    }
}
