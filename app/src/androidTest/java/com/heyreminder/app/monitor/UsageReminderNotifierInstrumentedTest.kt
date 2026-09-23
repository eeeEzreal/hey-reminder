package com.heyreminder.app.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
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
            val notifier = UsageReminderNotifier(context)
            val triggered = notifier.show(
                ReminderConditionReachedEvent(
                    packageName = context.packageName,
                    sessionStartedAtElapsedMillis = 1_000L,
                    reminderDueAtElapsedMillis = 601_000L,
                    continuousDurationMillis = 10L * 60L * 1_000L,
                ),
            )

            assertTrue(triggered)
            val postedNotification = notificationManager.activeNotifications
                .firstOrNull { notification ->
                    notification.notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString() == "Hey! 该休息一下了"
                }
            assertNotNull(postedNotification)
            assertEquals(
                "你已经连续使用 Hey! 10 分钟了。",
                postedNotification?.notification?.extras
                    ?.getCharSequence(Notification.EXTRA_TEXT)
                    ?.toString(),
            )
            assertEquals(
                "prominent_usage_reminders",
                postedNotification?.notification?.channelId,
            )
            assertEquals(
                "你已经连续使用 Hey! 10 分钟了。放下手机，起来活动一下吧。",
                postedNotification?.notification?.extras
                    ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?.toString(),
            )
            assertEquals(Notification.CATEGORY_REMINDER, postedNotification?.notification?.category)
            assertEquals(Notification.VISIBILITY_PUBLIC, postedNotification?.notification?.visibility)
            val reminderChannel = notificationManager.getNotificationChannel(
                "prominent_usage_reminders",
            )
            assertEquals(NotificationManager.IMPORTANCE_HIGH, reminderChannel.importance)
            assertTrue(reminderChannel.shouldVibrate())
            assertEquals(
                listOf("收到", "再给我 5 分钟", "暂停 30 分钟"),
                postedNotification?.notification?.actions?.map { action ->
                    action.title.toString()
                },
            )

            notifier.updateTimingConfig(
                ReminderTimingConfig(
                    reminderIntervalMillis = 20L * 60L * 1_000L,
                    snoozeIntervalMillis = 10L * 60L * 1_000L,
                    pauseDurationMillis = 60L * 60L * 1_000L,
                ),
            )
            assertTrue(
                notifier.show(
                    ReminderConditionReachedEvent(
                        packageName = context.packageName,
                        sessionStartedAtElapsedMillis = 1_000L,
                        reminderDueAtElapsedMillis = 1_201_000L,
                        continuousDurationMillis = 20L * 60L * 1_000L,
                    ),
                ),
            )
            val updatedNotification = notificationManager.activeNotifications
                .first { notification ->
                    notification.notification.channelId == "prominent_usage_reminders"
                }
            assertEquals(
                listOf("收到", "再给我 10 分钟", "暂停 60 分钟"),
                updatedNotification.notification.actions.map { action ->
                    action.title.toString()
                },
            )
        } finally {
            notificationManager.cancelAll()
        }
    }

    @Test
    fun eachReminderActionRoutesBackToTheMonitorServiceAndDismissesTheReminder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifier = UsageReminderNotifier(context)
        val event = ReminderConditionReachedEvent(
            packageName = context.packageName,
            sessionStartedAtElapsedMillis = 1_000L,
            reminderDueAtElapsedMillis = 601_000L,
            continuousDurationMillis = 10L * 60L * 1_000L,
        )

        try {
            repeat(3) { actionIndex ->
                assertTrue(notifier.show(event))
                val reminder = notificationManager.activeNotifications.first { notification ->
                    notification.notification.channelId == "prominent_usage_reminders"
                }

                reminder.notification.actions[actionIndex].actionIntent.send()

                assertTrue(
                    waitUntil {
                        notificationManager.activeNotifications.none { notification ->
                            notification.notification.channelId == "prominent_usage_reminders"
                        }
                    },
                )
            }
        } finally {
            context.stopService(Intent(context, UsageMonitorService::class.java))
            notificationManager.cancelAll()
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(20) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return false
    }
}
