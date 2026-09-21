package com.heyreminder.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `enabled reminder waits when usage access is missing`() {
        assertEquals("等待授权", HomeUiState(isReminderEnabled = true).statusLabel)
    }

    @Test
    fun `enabled reminder is ready when usage access is granted`() {
        assertEquals(
            "准备就绪",
            HomeUiState(isReminderEnabled = true, hasUsageAccess = true).statusLabel,
        )
    }

    @Test
    fun `disabled reminder reports closed`() {
        assertEquals(
            "已关闭",
            HomeUiState(isReminderEnabled = false, hasUsageAccess = true).statusLabel,
        )
    }
}
