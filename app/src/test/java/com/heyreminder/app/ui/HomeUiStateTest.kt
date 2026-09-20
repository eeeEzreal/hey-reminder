package com.heyreminder.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `enabled reminder waits for permission during phase one`() {
        assertEquals("等待授权", HomeUiState(isReminderEnabled = true).statusLabel)
    }

    @Test
    fun `disabled reminder reports closed`() {
        assertEquals("已关闭", HomeUiState(isReminderEnabled = false).statusLabel)
    }
}

