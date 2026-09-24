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
import com.heyreminder.app.data.REMINDER_NOTIFICATION_CHANNEL_ID
import kotlin.math.max

internal class UsageReminderNotifier(
    context: Context,
    timingConfig: ReminderTimingConfig = DEFAULT_REMINDER_TIMING,
) {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    @Volatile
    private var timingConfig = timingConfig

    init {
        createNotificationChannel()
    }

    fun show(event: ReminderConditionReachedEvent): Boolean {
        return show(event, ReminderVisualLevel.FIRST)
    }

    fun show(
        event: ReminderConditionReachedEvent,
        level: ReminderVisualLevel,
    ): Boolean {
        if (!canPostReminderNotification()) return false

        val appName = resolveAppName(event.packageName)
        val durationMinutes = max(1L, event.continuousDurationMillis / MILLIS_PER_MINUTE)
        val openAppIntent = createOpenAppIntent(REMINDER_NOTIFICATION_ID)
        val title = applicationContext.getString(
            if (level == ReminderVisualLevel.ESCALATED) {
                R.string.reminder_notification_escalated_title
            } else {
                R.string.reminder_notification_title
            },
        )
        val message = applicationContext.getString(
            if (level == ReminderVisualLevel.ESCALATED) {
                R.string.reminder_notification_escalated_text
            } else {
                R.string.reminder_notification_text
            },
            appName,
            durationMinutes,
        )
        val expandedMessage = applicationContext.getString(
            R.string.reminder_notification_expanded_text,
            appName,
            durationMinutes,
        )
        val notification = NotificationCompat.Builder(
            applicationContext,
            REMINDER_NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(expandedMessage)
                    .setSummaryText(appName),
            )
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

        return postNotification(REMINDER_NOTIFICATION_ID, notification)
    }

    fun showTestReminder(): Boolean {
        if (!canPostReminderNotification()) return false

        val title = applicationContext.getString(R.string.test_reminder_notification_title)
        val message = applicationContext.getString(R.string.test_reminder_notification_text)
        val notification = NotificationCompat.Builder(
            applicationContext,
            REMINDER_NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createOpenAppIntent(TEST_REMINDER_NOTIFICATION_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return postNotification(TEST_REMINDER_NOTIFICATION_ID, notification)
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
            .getNotificationChannel(REMINDER_NOTIFICATION_CHANNEL_ID)
            ?.importance
            ?.let { importance -> importance >= NotificationManager.IMPORTANCE_HIGH }
            ?: false
    }

    private fun createOpenAppIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        requestCode,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun postNotification(
        notificationId: Int,
        notification: android.app.Notification,
    ): Boolean = runCatching {
        notificationManager.notify(notificationId, notification)
        true
    }.getOrDefault(false)

    private fun resolveAppName(packageName: String): String = runCatching {
        val applicationInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
        applicationContext.packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault(packageName)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            REMINDER_NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.reminder_channel_description)
            enableVibration(true)
            vibrationPattern = REMINDER_VIBRATION_PATTERN
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val REMINDER_NOTIFICATION_ID = 2001
        const val TEST_REMINDER_NOTIFICATION_ID = 2002
        const val MILLIS_PER_MINUTE = 60_000L
        val REMINDER_VIBRATION_PATTERN = longArrayOf(0L, 300L, 180L, 500L)
    }
}

private fun Long.toWholeMinutes(): Long = max(1L, this / 60_000L)
