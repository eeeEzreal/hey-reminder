package com.heyreminder.app.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.heyreminder.app.data.AppSelectionRepository
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.ReminderSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageMonitorServiceEndToEndInstrumentedTest {
    @Test(timeout = 90_000L)
    fun continuousExternalAppUsePostsReminderThroughRealService() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val settingsRepository = ReminderSettingsRepository(context)
        val appSelectionRepository = AppSelectionRepository(context)

        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} GET_USAGE_STATS allow",
        ).close()
        settingsRepository.setMonitoringMode(MonitoringMode.BLACKLIST)
        appSelectionRepository.selectedPackages.first().forEach { packageName ->
            appSelectionRepository.setPackageSelected(packageName, false)
        }
        settingsRepository.setReminderMinutes(1)
        settingsRepository.setReminderEnabled(true)
        notificationManager.cancelAll()

        try {
            UsageMonitorService.start(context)
            check(
                waitUntil(timeoutMillis = 10_000L) {
                    notificationManager.activeNotifications.any { notification ->
                        notification.id == MONITOR_NOTIFICATION_ID
                    }
                },
            ) { "monitor foreground service did not start" }

            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            val reminder = waitForReminder(notificationManager)
            assertNotNull(
                "real monitor service did not post a reminder after one continuous minute",
                reminder,
            )
        } finally {
            UsageMonitorService.stop(context)
            waitUntil(timeoutMillis = 5_000L) {
                notificationManager.activeNotifications.none { notification ->
                    notification.id == MONITOR_NOTIFICATION_ID
                }
            }
            Thread.sleep(250L)
            notificationManager.cancelAll()
            settingsRepository.setReminderMinutes(10)
        }
    }

    private fun waitForReminder(
        notificationManager: NotificationManager,
    ): android.service.notification.StatusBarNotification? {
        var reminder: android.service.notification.StatusBarNotification? = null
        waitUntil(timeoutMillis = 75_000L) {
            reminder = notificationManager.activeNotifications.firstOrNull { notification ->
                notification.id == REMINDER_NOTIFICATION_ID &&
                    notification.notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString() == "Hey! 该休息一下了"
            }
            reminder != null
        }
        return reminder
    }

    private fun waitUntil(
        timeoutMillis: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(250L)
        }
        return condition()
    }

    private companion object {
        const val MONITOR_NOTIFICATION_ID = 1001
        const val REMINDER_NOTIFICATION_ID = 2001
    }
}
