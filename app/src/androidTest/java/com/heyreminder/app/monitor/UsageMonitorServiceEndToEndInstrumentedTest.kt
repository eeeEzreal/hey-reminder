package com.heyreminder.app.monitor

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.heyreminder.app.data.AppSelectionRepository
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.ReminderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageMonitorServiceEndToEndInstrumentedTest {
    @Test(timeout = 90_000L)
    fun continuousExternalAppUseKeepsRealOverlayVisible() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val settingsRepository = ReminderSettingsRepository(context)
        val appSelectionRepository = AppSelectionRepository(context)

        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} GET_USAGE_STATS allow",
        )).use { stream -> stream.readBytes() }
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
        )).use { stream -> stream.readBytes() }
        settingsRepository.setMonitoringMode(MonitoringMode.BLACKLIST)
        appSelectionRepository.selectedPackages.first().forEach { packageName ->
            appSelectionRepository.setPackageSelected(packageName, false)
        }
        settingsRepository.setReminderMinutes(1)
        settingsRepository.setDebug30SecondReminderEnabled(true)
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

            val externalAppIntent = requireNotNull(
                context.packageManager.getLaunchIntentForPackage(EXTERNAL_APP_PACKAGE),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(externalAppIntent)

            assertTrue(
                "real monitor service did not show an overlay after thirty continuous seconds",
                waitUntil(timeoutMillis = 45_000L) {
                    OverlayReminderPresenter.isAnyOverlayVisible
                },
            )
            assertFalse(
                "notification fallback must not be posted after a successful Overlay",
                notificationManager.activeNotifications.any { notification ->
                    notification.id == REMINDER_NOTIFICATION_ID
                },
            )

            // Catch the OEM-sensitive failure where SystemUI or window focus immediately ended
            // the session and removed an Overlay that had technically been added successfully.
            Thread.sleep(5_000L)
            assertTrue(
                "overlay disappeared without any user action",
                OverlayReminderPresenter.isAnyOverlayVisible,
            )
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "screencap -p /sdcard/Download/hey-phase91-overlay-api35.png",
                ),
            ).use { stream -> stream.readBytes() }
            Unit
        } finally {
            UsageMonitorService.stop(context)
            waitUntil(timeoutMillis = 5_000L) {
                notificationManager.activeNotifications.none { notification ->
                    notification.id == MONITOR_NOTIFICATION_ID
                }
            }
            Thread.sleep(250L)
            notificationManager.cancelAll()
            settingsRepository.setDebug30SecondReminderEnabled(false)
            settingsRepository.setReminderMinutes(10)
        }
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
        const val EXTERNAL_APP_PACKAGE = "com.android.chrome"
    }
}
