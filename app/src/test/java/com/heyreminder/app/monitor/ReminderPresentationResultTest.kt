package com.heyreminder.app.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationResultTest {
    @Test
    fun `notification fallback cannot masquerade as successful overlay presentation`() {
        val result = ReminderPresentationResult(
            overlayShown = false,
            notificationShown = true,
            overlayFailureReason = "permission denied",
        )

        assertFalse(result.wasPresented)
        assertTrue(shouldShowNotificationFallback(overlayShown = false))
    }

    @Test
    fun `successful overlay suppresses notification fallback`() {
        val result = ReminderPresentationResult(
            overlayShown = true,
            notificationShown = false,
        )

        assertTrue(result.wasPresented)
        assertFalse(shouldShowNotificationFallback(overlayShown = true))
    }
}
