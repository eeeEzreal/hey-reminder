package com.heyreminder.app.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageReminderNotifierInstrumentedTest {
    @Test
    fun postsExpectedReminderToAndroidNotificationCenter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()

        try {
            val triggered = UsageReminderNotifier(context).show(
                ReminderConditionReachedEvent(
                    packageName = context.packageName,
                    continuousDurationMillis = 10L * 60L * 1_000L,
                ),
            )

            assertTrue(triggered)
            val postedNotification = notificationManager.activeNotifications
                .firstOrNull { notification ->
                    notification.notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString() == "Hey!"
                }
            assertNotNull(postedNotification)
            assertEquals(
                "你已经连续使用 Hey! 10 分钟了。",
                postedNotification?.notification?.extras
                    ?.getCharSequence(Notification.EXTRA_TEXT)
                    ?.toString(),
            )
            assertEquals("usage_reminders", postedNotification?.notification?.channelId)
        } finally {
            notificationManager.cancelAll()
        }
    }
}
