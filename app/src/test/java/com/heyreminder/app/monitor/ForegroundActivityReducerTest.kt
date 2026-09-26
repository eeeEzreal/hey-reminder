package com.heyreminder.app.monitor

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundActivityReducerTest {
    @Test
    fun `the most recently resumed app is confirmed as foreground`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.first", "FirstActivity", atMillis = 1_000L)
        reducer.resume("com.example.second", "SecondActivity", atMillis = 2_000L)

        val detection = reducer.currentDetection(nowMillis = 2_000L)
        assertEquals("com.example.second", detection.packageName)
        assertEquals(ForegroundDetectionStatus.CONFIRMED, detection.status)
    }

    @Test
    fun `pausing an old activity does not clear a newer activity in the same app`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.app", "FirstActivity", atMillis = 1_000L)
        reducer.resume("com.example.app", "SecondActivity", atMillis = 2_000L)
        reducer.pause("com.example.app", "FirstActivity", atMillis = 3_000L)

        assertEquals(
            "com.example.app",
            reducer.currentDetection(nowMillis = 3_000L).packageName,
        )
    }

    @Test
    fun `a lone pause is a short activity transition before becoming no foreground`() {
        val reducer = ForegroundActivityReducer(transitionGraceMillis = 3_000L)

        reducer.resume("com.example.app", "MainActivity", atMillis = 1_000L)
        reducer.pause("com.example.app", "MainActivity", atMillis = 2_000L)

        val duringGap = reducer.currentDetection(nowMillis = 4_999L)
        val afterGap = reducer.currentDetection(nowMillis = 5_001L)

        assertEquals("com.example.app", duringGap.packageName)
        assertEquals(ForegroundDetectionStatus.ACTIVITY_TRANSITION, duringGap.status)
        assertNull(afterGap.packageName)
        assertEquals(ForegroundDetectionStatus.NO_FOREGROUND_APP, afterGap.status)
    }

    @Test
    fun `a delayed resume in the same app restores confirmed foreground`() {
        val reducer = ForegroundActivityReducer(transitionGraceMillis = 3_000L)

        reducer.resume("com.example.app", "FirstActivity", atMillis = 1_000L)
        reducer.pause("com.example.app", "FirstActivity", atMillis = 2_000L)
        assertEquals(
            ForegroundDetectionStatus.ACTIVITY_TRANSITION,
            reducer.currentDetection(nowMillis = 2_500L).status,
        )
        reducer.resume("com.example.app", "SecondActivity", atMillis = 2_800L)

        val detection = reducer.currentDetection(nowMillis = 3_000L)
        assertEquals("com.example.app", detection.packageName)
        assertEquals(ForegroundDetectionStatus.CONFIRMED, detection.status)
    }

    @Test
    fun `resuming another app switches immediately during a transition gap`() {
        val reducer = ForegroundActivityReducer(transitionGraceMillis = 3_000L)

        reducer.resume("com.example.first", "FirstActivity", atMillis = 1_000L)
        reducer.pause("com.example.first", "FirstActivity", atMillis = 2_000L)
        reducer.resume("com.example.second", "SecondActivity", atMillis = 2_100L)

        val detection = reducer.currentDetection(nowMillis = 2_100L)
        assertEquals("com.example.second", detection.packageName)
        assertEquals(ForegroundDetectionStatus.CONFIRMED, detection.status)
    }

    @Test
    fun `overlapping queries cannot replay an older pause over a newer resume`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.first", "FirstActivity", atMillis = 1_000L)
        reducer.pause("com.example.first", "FirstActivity", atMillis = 2_000L)
        reducer.resume("com.example.second", "SecondActivity", atMillis = 3_000L)
        reducer.pause("com.example.first", "FirstActivity", atMillis = 2_000L)

        assertEquals(
            "com.example.second",
            reducer.currentDetection(nowMillis = 3_000L).packageName,
        )
    }

    @Test
    fun `screen lock reset clears foreground immediately`() {
        val reducer = ForegroundActivityReducer()
        reducer.resume("com.example.app", "MainActivity", atMillis = 1_000L)

        reducer.clear()

        val detection = reducer.currentDetection(nowMillis = 1_001L)
        assertNull(detection.packageName)
        assertEquals(ForegroundDetectionStatus.NO_FOREGROUND_APP, detection.status)
    }

    private fun ForegroundActivityReducer.resume(
        packageName: String,
        className: String,
        atMillis: Long,
    ) {
        accept(
            ForegroundActivityEvent(
                eventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = packageName,
                className = className,
                timestampMillis = atMillis,
            ),
        )
    }

    private fun ForegroundActivityReducer.pause(
        packageName: String,
        className: String,
        atMillis: Long,
    ) {
        accept(
            ForegroundActivityEvent(
                eventType = UsageEvents.Event.ACTIVITY_PAUSED,
                packageName = packageName,
                className = className,
                timestampMillis = atMillis,
            ),
        )
    }
}
