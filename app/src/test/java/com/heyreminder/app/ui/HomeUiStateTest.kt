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
            HomeUiState(
                isReminderEnabled = true,
                hasUsageAccess = true,
                hasNotificationPermission = true,
            ).statusLabel,
        )
    }

    @Test
    fun `enabled reminder waits when notification permission is missing`() {
        assertEquals(
            "等待通知权限",
            HomeUiState(isReminderEnabled = true, hasUsageAccess = true).statusLabel,
        )
    }

    @Test
    fun `today reminder count starts at zero`() {
        assertEquals(0, HomeUiState().todayReminderCount)
    }

    @Test
    fun `disabled reminder reports closed`() {
        assertEquals(
            "已关闭",
            HomeUiState(
                isReminderEnabled = false,
                hasUsageAccess = true,
                hasNotificationPermission = true,
            ).statusLabel,
        )
    }

    @Test
    fun `temporary pause is visible when permissions are ready`() {
        assertEquals(
            "已暂停",
            HomeUiState(
                hasUsageAccess = true,
                hasNotificationPermission = true,
                isTemporarilyPaused = true,
            ).statusLabel,
        )
    }
}
