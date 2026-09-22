package com.heyreminder.app.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousUsageTrackerTest {
    private val tracker = ContinuousUsageTracker(
        reminderThresholdMillis = TEN_MINUTES_MILLIS,
    )
    private val selectedPackages = setOf(CHROME_PACKAGE, TIKTOK_PACKAGE)

    @Test
    fun `entering a selected app starts a new session at zero`() {
        val update = tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = CHROME_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 1_000L,
        )

        assertEquals(CHROME_PACKAGE, update.state.packageName)
        assertEquals(1_000L, update.state.startedAtElapsedMillis)
        assertFalse(update.state.reminderConditionReached)
        assertNull(update.event)
    }

    @Test
    fun `remaining in a selected app keeps the original session start`() {
        val started = startSession(CHROME_PACKAGE, nowElapsedMillis = 1_000L)

        val update = tracker.update(
            previousState = started,
            foregroundPackage = CHROME_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 1_000L + TEN_MINUTES_MILLIS - 1L,
        )

        assertEquals(started, update.state)
        assertNull(update.event)
    }

    @Test
    fun `leaving before the threshold clears the session`() {
        val started = startSession(CHROME_PACKAGE, nowElapsedMillis = 1_000L)

        val update = tracker.update(
            previousState = started,
            foregroundPackage = null,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 5_000L,
        )

        assertFalse(update.state.isActive)
        assertNull(update.event)
    }

    @Test
    fun `reentering after leaving starts again from zero`() {
        val started = startSession(CHROME_PACKAGE, nowElapsedMillis = 1_000L)
        val left = tracker.update(
            previousState = started,
            foregroundPackage = "com.android.launcher3",
            selectedPackages = selectedPackages,
            nowElapsedMillis = 6_000L,
        ).state

        val reentered = tracker.update(
            previousState = left,
            foregroundPackage = CHROME_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 10_000L,
        )

        assertEquals(CHROME_PACKAGE, reentered.state.packageName)
        assertEquals(10_000L, reentered.state.startedAtElapsedMillis)
        assertFalse(reentered.state.reminderConditionReached)
    }

    @Test
    fun `switching between selected apps resets the old session and starts the new one`() {
        val chromeSession = startSession(CHROME_PACKAGE, nowElapsedMillis = 1_000L)

        val update = tracker.update(
            previousState = chromeSession,
            foregroundPackage = TIKTOK_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 9_000L,
        )

        assertEquals(TIKTOK_PACKAGE, update.state.packageName)
        assertEquals(9_000L, update.state.startedAtElapsedMillis)
        assertFalse(update.state.reminderConditionReached)
        assertNull(update.event)
    }

    @Test
    fun `reaching ten minutes emits exactly one reminder condition event`() {
        val started = startSession(CHROME_PACKAGE, nowElapsedMillis = 1_000L)

        val reached = tracker.update(
            previousState = started,
            foregroundPackage = CHROME_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 1_000L + TEN_MINUTES_MILLIS,
        )
        val nextPoll = tracker.update(
            previousState = reached.state,
            foregroundPackage = CHROME_PACKAGE,
            selectedPackages = selectedPackages,
            nowElapsedMillis = 1_000L + TEN_MINUTES_MILLIS + 5_000L,
        )

        assertTrue(reached.state.reminderConditionReached)
        assertEquals(CHROME_PACKAGE, reached.event?.packageName)
        assertEquals(TEN_MINUTES_MILLIS, reached.event?.continuousDurationMillis)
        assertNull(nextPoll.event)
    }

    @Test
    fun `an unselected app never starts a session`() {
        val update = tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = "com.android.settings",
            selectedPackages = selectedPackages,
            nowElapsedMillis = 1_000L,
        )

        assertFalse(update.state.isActive)
        assertNull(update.event)
    }

    private fun startSession(packageName: String, nowElapsedMillis: Long): ContinuousUsageState {
        return tracker.update(
            previousState = ContinuousUsageState(),
            foregroundPackage = packageName,
            selectedPackages = selectedPackages,
            nowElapsedMillis = nowElapsedMillis,
        ).state
    }

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val TIKTOK_PACKAGE = "com.example.tiktok"
        const val TEN_MINUTES_MILLIS = 10L * 60L * 1_000L
    }
}
