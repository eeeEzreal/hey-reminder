package com.heyreminder.app.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.heyreminder.app.MainActivity
import com.heyreminder.app.R
import kotlin.math.max

internal fun interface ReminderNotificationGateway {
    fun show(event: ReminderConditionReachedEvent): Boolean
}

internal class ReminderNotificationCoordinator(
    private val notificationGateway: ReminderNotificationGateway,
    private val recordTriggeredReminder: suspend () -> Unit,
) {
    suspend fun onReminderConditionReached(event: ReminderConditionReachedEvent): Boolean {
        if (!notificationGateway.show(event)) return false

        recordTriggeredReminder()
        return true
    }
}

internal class UsageReminderNotifier(
    context: Context,
    timingConfig: ReminderTimingConfig = DEFAULT_REMINDER_TIMING,
) : ReminderNotificationGateway {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    @Volatile
    private var timingConfig = timingConfig

    init {
        createNotificationChannel()
    }

    override fun show(event: ReminderConditionReachedEvent): Boolean {
        if (!canPostReminderNotification()) return false

        val appName = resolveAppName(event.packageName)
        val durationMinutes = max(1L, event.continuousDurationMillis / MILLIS_PER_MINUTE)
        val openAppIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            REMINDER_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.reminder_notification_text,
                    appName,
                    durationMinutes,
                ),
            )
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_notification,
                applicationContext.getString(R.string.reminder_action_acknowledge),
                ReminderNotificationActionContract.createPendingIntent(
                    applicationContext,
                    event,
                    ReminderActionType.ACKNOWLEDGE,
                ),
            )
            .addAction(
                R.drawable.ic_notification,
                applicationContext.getString(
                    R.string.reminder_action_snooze,
                    timingConfig.snoozeIntervalMillis.toWholeMinutes(),
                ),
                ReminderNotificationActionContract.createPendingIntent(
                    applicationContext,
                    event,
                    ReminderActionType.SNOOZE,
                ),
            )
            .addAction(
                R.drawable.ic_notification,
                applicationContext.getString(
                    R.string.reminder_action_pause,
                    timingConfig.pauseDurationMillis.toWholeMinutes(),
                ),
                ReminderNotificationActionContract.createPendingIntent(
                    applicationContext,
                    event,
                    ReminderActionType.PAUSE,
                ),
            )
            .build()

        return runCatching {
            notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun dismiss() {
        notificationManager.cancel(REMINDER_NOTIFICATION_ID)
    }

    fun updateTimingConfig(timingConfig: ReminderTimingConfig) {
        this.timingConfig = timingConfig
    }

    private fun canPostReminderNotification(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!notificationManager.areNotificationsEnabled()) return false

        return notificationManager
            .getNotificationChannel(REMINDER_CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun resolveAppName(packageName: String): String = runCatching {
        val applicationInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
        applicationContext.packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault(packageName)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val REMINDER_CHANNEL_ID = "usage_reminders"
        const val REMINDER_NOTIFICATION_ID = 2001
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

private fun Long.toWholeMinutes(): Long = max(1L, this / 60_000L)
