package com.heyreminder.app.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderActionReducerTest {
    private val timingConfig = ReminderTimingConfig(
        reminderIntervalMillis = TEN_MINUTES,
        snoozeIntervalMillis = FIVE_MINUTES,
        pauseDurationMillis = THIRTY_MINUTES,
    )
    private val tracker = ContinuousUsageTracker(timingConfig.reminderIntervalMillis)
    private val reducer = ReminderActionReducer(tracker, timingConfig)
    private val reachedState = ContinuousUsageState(
        packageName = PACKAGE_NAME,
        startedAtElapsedMillis = 1_000L,
        nextReminderAtElapsedMillis = 1_000L + TEN_MINUTES,
        reminderConditionReached = true,
    )
    private val target = ReminderActionTarget(
        packageName = PACKAGE_NAME,
        sessionStartedAtElapsedMillis = 1_000L,
        reminderDueAtElapsedMillis = 1_000L + TEN_MINUTES,
    )

    @Test
    fun `acknowledge schedules the next full reminder interval`() {
        val result = reduce(ReminderActionType.ACKNOWLEDGE)!!

        assertEquals(NOW_ELAPSED + TEN_MINUTES, result.usageState.nextReminderAtElapsedMillis)
        assertFalse(result.usageState.reminderConditionReached)
        assertNull(result.pauseUntilEpochMillis)
    }

    @Test
    fun `snooze schedules the configured shorter interval`() {
        val result = reduce(ReminderActionType.SNOOZE)!!

        assertEquals(NOW_ELAPSED + FIVE_MINUTES, result.usageState.nextReminderAtElapsedMillis)
        assertFalse(result.usageState.reminderConditionReached)
        assertNull(result.pauseUntilEpochMillis)
    }

    @Test
    fun `pause clears the session and returns a persistent wall clock deadline`() {
        val result = reduce(ReminderActionType.PAUSE)!!

        assertFalse(result.usageState.isActive)
        assertEquals(NOW_EPOCH + THIRTY_MINUTES, result.pauseUntilEpochMillis)
    }

    @Test
    fun `stale notification action is rejected`() {
        val staleTarget = target.copy(reminderDueAtElapsedMillis = target.reminderDueAtElapsedMillis - 1L)

        val result = reducer.reduce(
            previousState = reachedState,
            action = ReminderActionType.PAUSE,
            target = staleTarget,
            nowElapsedMillis = NOW_ELAPSED,
            nowEpochMillis = NOW_EPOCH,
        )

        assertNull(result)
    }

    private fun reduce(action: ReminderActionType): ReminderActionResult? = reducer.reduce(
        previousState = reachedState,
        action = action,
        target = target,
        nowElapsedMillis = NOW_ELAPSED,
        nowEpochMillis = NOW_EPOCH,
    )

    private companion object {
        const val PACKAGE_NAME = "com.example.target"
        const val NOW_ELAPSED = 700_000L
        const val NOW_EPOCH = 1_790_154_531_000L
        const val FIVE_MINUTES = 5L * 60L * 1_000L
        const val TEN_MINUTES = 10L * 60L * 1_000L
        const val THIRTY_MINUTES = 30L * 60L * 1_000L
    }
}
