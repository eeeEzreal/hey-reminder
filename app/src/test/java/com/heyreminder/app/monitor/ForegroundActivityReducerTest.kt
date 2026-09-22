package com.heyreminder.app.monitor

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundActivityReducerTest {
    @Test
    fun `the most recently resumed app is treated as foreground`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.first", "FirstActivity", atMillis = 1_000L)
        reducer.resume("com.example.second", "SecondActivity", atMillis = 2_000L)

        assertEquals("com.example.second", reducer.currentPackage())
    }

    @Test
    fun `pausing an old activity does not clear a newer activity in the same app`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.app", "FirstActivity", atMillis = 1_000L)
        reducer.resume("com.example.app", "SecondActivity", atMillis = 2_000L)
        reducer.pause("com.example.app", "FirstActivity", atMillis = 3_000L)

        assertEquals("com.example.app", reducer.currentPackage())
    }

    @Test
    fun `pausing the current activity clears foreground state`() {
        val reducer = ForegroundActivityReducer()

        reducer.resume("com.example.app", "MainActivity", atMillis = 1_000L)
        reducer.pause("com.example.app", "MainActivity", atMillis = 2_000L)

        assertNull(reducer.currentPackage())
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
