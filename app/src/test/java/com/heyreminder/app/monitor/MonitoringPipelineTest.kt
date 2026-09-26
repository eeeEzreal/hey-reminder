package com.heyreminder.app.monitor

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPipelineTest {
    @Test
    fun `split pause and resume events do not reset the session before thirty second SHOW`() {
        val reducer = ForegroundActivityReducer(transitionGraceMillis = 3_000L)
        val tracker = ContinuousUsageTracker(reminderIntervalMillis = 30_000L)
        val engine = ReminderEngine()
        val monitoredPackages = setOf(TARGET_PACKAGE)

        reducer.accept(event(UsageEvents.Event.ACTIVITY_RESUMED, "FirstActivity", 1_000L))
        var usage = tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = reducer.currentDetection(1_000L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 10_000L,
        ).state

        reducer.accept(event(UsageEvents.Event.ACTIVITY_PAUSED, "FirstActivity", 10_000L))
        usage = tracker.update(
            previousState = usage,
            foregroundPackage = reducer.currentDetection(10_500L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 19_500L,
        ).state
        reducer.accept(event(UsageEvents.Event.ACTIVITY_RESUMED, "SecondActivity", 11_000L))

        val reached = tracker.update(
            previousState = usage,
            foregroundPackage = reducer.currentDetection(31_000L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 40_000L,
        )
        val effect = engine.onReminderDue(requireNotNull(reached.event), 40_000L)

        assertEquals(10_000L, reached.state.startedAtElapsedMillis)
        assertTrue(reached.state.reminderConditionReached)
        assertTrue(effect is ReminderEngineEffect.ShowReminder)
        assertEquals(ReminderVisualLevel.FIRST, (effect as ReminderEngineEffect.ShowReminder).level)
    }

    @Test
    fun `an unresolved pause past grace ends the session`() {
        val reducer = ForegroundActivityReducer(transitionGraceMillis = 3_000L)
        val tracker = ContinuousUsageTracker(reminderIntervalMillis = 30_000L)
        val monitoredPackages = setOf(TARGET_PACKAGE)

        reducer.accept(event(UsageEvents.Event.ACTIVITY_RESUMED, "MainActivity", 1_000L))
        val started = tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = reducer.currentDetection(1_000L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 10_000L,
        ).state
        reducer.accept(event(UsageEvents.Event.ACTIVITY_PAUSED, "MainActivity", 2_000L))
        val ended = tracker.update(
            previousState = started,
            foregroundPackage = reducer.currentDetection(5_001L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 14_001L,
        )

        assertFalse(ended.state.isActive)
        assertNull(ended.event)
    }

    @Test
    fun `switching to another monitored app starts a new session immediately`() {
        val reducer = ForegroundActivityReducer()
        val tracker = ContinuousUsageTracker(reminderIntervalMillis = 30_000L)
        val monitoredPackages = setOf(TARGET_PACKAGE, SECOND_PACKAGE)

        reducer.accept(event(UsageEvents.Event.ACTIVITY_RESUMED, "MainActivity", 1_000L))
        val first = tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = reducer.currentDetection(1_000L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 10_000L,
        ).state
        reducer.accept(
            ForegroundActivityEvent(
                eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = SECOND_PACKAGE,
                className = "OtherActivity",
                timestampMillis = 2_000L,
            ),
        )
        val switched = tracker.update(
            previousState = first,
            foregroundPackage = reducer.currentDetection(2_000L).packageName,
            monitoredPackages = monitoredPackages,
            nowElapsedMillis = 11_000L,
        ).state

        assertEquals(SECOND_PACKAGE, switched.packageName)
        assertEquals(11_000L, switched.startedAtElapsedMillis)
    }

    private fun event(type: Int, className: String, atMillis: Long) =
        ForegroundActivityEvent(
            eventType = type,
            packageName = TARGET_PACKAGE,
            className = className,
            timestampMillis = atMillis,
        )

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        const val SECOND_PACKAGE = "com.example.second"
    }
}
