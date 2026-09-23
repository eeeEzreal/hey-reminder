package com.heyreminder.app.monitor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal data class ReminderActionCommand(
    val action: ReminderActionType,
    val target: ReminderActionTarget,
)

internal object ReminderNotificationActionContract {
    private const val EXTRA_PACKAGE_NAME = "reminder_package_name"
    private const val EXTRA_SESSION_STARTED_AT = "reminder_session_started_at"
    private const val EXTRA_REMINDER_DUE_AT = "reminder_due_at"

    fun createPendingIntent(
        context: Context,
        event: ReminderConditionReachedEvent,
        action: ReminderActionType,
    ): PendingIntent = PendingIntent.getService(
        context,
        requestCode(action),
        Intent(context, UsageMonitorService::class.java)
            .setAction(intentAction(action))
            .putExtra(EXTRA_PACKAGE_NAME, event.packageName)
            .putExtra(EXTRA_SESSION_STARTED_AT, event.sessionStartedAtElapsedMillis)
            .putExtra(EXTRA_REMINDER_DUE_AT, event.reminderDueAtElapsedMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun parse(intent: Intent?): ReminderActionCommand? {
        val sourceIntent = intent ?: return null
        val action = ReminderActionType.entries
            .firstOrNull { candidate -> intentAction(candidate) == sourceIntent.action }
            ?: return null
        val packageName = sourceIntent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (
            !sourceIntent.hasExtra(EXTRA_SESSION_STARTED_AT) ||
            !sourceIntent.hasExtra(EXTRA_REMINDER_DUE_AT)
        ) {
            return null
        }

        return ReminderActionCommand(
            action = action,
            target = ReminderActionTarget(
                packageName = packageName,
                sessionStartedAtElapsedMillis = sourceIntent.getLongExtra(
                    EXTRA_SESSION_STARTED_AT,
                    0L,
                ),
                reminderDueAtElapsedMillis = sourceIntent.getLongExtra(
                    EXTRA_REMINDER_DUE_AT,
                    0L,
                ),
            ),
        )
    }

    private fun intentAction(action: ReminderActionType): String = when (action) {
        ReminderActionType.ACKNOWLEDGE -> "com.heyreminder.app.action.ACKNOWLEDGE_REMINDER"
        ReminderActionType.SNOOZE -> "com.heyreminder.app.action.SNOOZE_REMINDER"
        ReminderActionType.PAUSE -> "com.heyreminder.app.action.PAUSE_REMINDERS"
    }

    private fun requestCode(action: ReminderActionType): Int = when (action) {
        ReminderActionType.ACKNOWLEDGE -> 2101
        ReminderActionType.SNOOZE -> 2102
        ReminderActionType.PAUSE -> 2103
    }
}
