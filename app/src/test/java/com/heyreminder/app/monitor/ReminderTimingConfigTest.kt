package com.heyreminder.app.monitor

import com.heyreminder.app.data.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimingConfigTest {
    @Test
    fun `persisted minute settings convert to core timing values`() {
        val timing = ReminderSettings(
            reminderMinutes = 20,
            snoozeMinutes = 10,
            pauseMinutes = 60,
        ).toReminderTimingConfig()

        assertEquals(20L * 60L * 1_000L, timing.reminderIntervalMillis)
        assertEquals(10L * 60L * 1_000L, timing.snoozeIntervalMillis)
        assertEquals(60L * 60L * 1_000L, timing.pauseDurationMillis)
    }
}
