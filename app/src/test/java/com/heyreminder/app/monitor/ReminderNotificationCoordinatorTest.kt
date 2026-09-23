package com.heyreminder.app.monitor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationCoordinatorTest {
    private val event = ReminderConditionReachedEvent(
        packageName = "com.example.target",
        sessionStartedAtElapsedMillis = 1_000L,
        reminderDueAtElapsedMillis = 601_000L,
        continuousDurationMillis = 10L * 60L * 1_000L,
    )

    @Test
    fun `successful notification records one triggered reminder`() = runBlocking {
        var recordedCount = 0
        val coordinator = ReminderNotificationCoordinator(
            notificationGateway = ReminderNotificationGateway { true },
            recordTriggeredReminder = { recordedCount += 1 },
        )

        val triggered = coordinator.onReminderConditionReached(event)

        assertTrue(triggered)
        assertEquals(1, recordedCount)
    }

    @Test
    fun `failed notification does not record a triggered reminder`() = runBlocking {
        var recordedCount = 0
        val coordinator = ReminderNotificationCoordinator(
            notificationGateway = ReminderNotificationGateway { false },
            recordTriggeredReminder = { recordedCount += 1 },
        )

        val triggered = coordinator.onReminderConditionReached(event)

        assertFalse(triggered)
        assertEquals(0, recordedCount)
    }
}
