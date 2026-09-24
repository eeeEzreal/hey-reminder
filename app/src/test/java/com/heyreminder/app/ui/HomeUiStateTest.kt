package com.heyreminder.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `enabled reminder waits when usage access is missing`() {
        assertEquals("等待授权", HomeUiState(isReminderEnabled = true).statusLabel)
    }

    @Test
    fun `enabled reminder reports active monitoring when everything is ready`() {
        assertEquals(
            "监控运行中",
            HomeUiState(
                isReminderEnabled = true,
                hasUsageAccess = true,
                hasNotificationPermission = true,
                hasOverlayPermission = true,
                monitoredAppCount = 1,
            ).statusLabel,
        )
    }

    @Test
    fun `zero monitored apps is never reported as ready`() {
        assertEquals(
            "没有监控 App",
            HomeUiState(
                isReminderEnabled = true,
                hasUsageAccess = true,
                hasNotificationPermission = true,
                hasOverlayPermission = true,
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
    fun `missing overlay permission is never reported as ready`() {
        assertEquals(
            "强提醒权限未开启",
            HomeUiState(
                isReminderEnabled = true,
                hasUsageAccess = true,
                hasNotificationPermission = true,
                monitoredAppCount = 1,
            ).statusLabel,
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
                hasOverlayPermission = true,
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
                hasOverlayPermission = true,
                isTemporarilyPaused = true,
                monitoredAppCount = 1,
            ).statusLabel,
        )
    }
}
