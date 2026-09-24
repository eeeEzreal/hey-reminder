package com.heyreminder.app.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

internal const val REMINDER_NOTIFICATION_CHANNEL_ID = "prominent_usage_reminders"

class NotificationPermissionRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean {
        if (!hasRuntimePermission() || !notificationManager.areNotificationsEnabled()) {
            return false
        }
        val channel = notificationManager.getNotificationChannel(
            REMINDER_NOTIFICATION_CHANNEL_ID,
        ) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun createNotificationSettingsIntent(): Intent {
        val channel = notificationManager.getNotificationChannel(
            REMINDER_NOTIFICATION_CHANNEL_ID,
        )
        return if (channel != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, REMINDER_NOTIFICATION_CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
        }
    }
}
