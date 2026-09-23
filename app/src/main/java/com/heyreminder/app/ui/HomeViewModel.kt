package com.heyreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heyreminder.app.data.AppSelectionRepository
import com.heyreminder.app.data.DailyReminderCounts
import com.heyreminder.app.data.NotificationPermissionRepository
import com.heyreminder.app.data.MonitoringPauseRepository
import com.heyreminder.app.data.ReminderStatsRepository
import com.heyreminder.app.data.UsageAccessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isReminderEnabled: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isTemporarilyPaused: Boolean = false,
    val todayReminderCount: Int = 0,
    val modeLabel: String = "黑名单",
    val monitoredAppCount: Int = 0,
    val reminderMinutes: Int = 10,
) {
    val statusLabel: String
        get() = when {
            !isReminderEnabled -> "已关闭"
            !hasUsageAccess -> "等待授权"
            !hasNotificationPermission -> "等待通知权限"
            isTemporarilyPaused -> "已暂停"
            else -> "准备就绪"
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val usageAccessRepository = UsageAccessRepository(application)
    private val reminderStatsRepository = ReminderStatsRepository(application)
    private val notificationPermissionRepository = NotificationPermissionRepository(application)
    private val monitoringPauseRepository = MonitoringPauseRepository(application)
    private val appSelectionRepository = AppSelectionRepository(application)
    private var dailyReminderCounts = DailyReminderCounts()
    private val _uiState = MutableStateFlow(
        HomeUiState(
            hasUsageAccess = usageAccessRepository.hasUsageAccess(),
            hasNotificationPermission =
                notificationPermissionRepository.hasNotificationPermission(),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reminderStatsRepository.dailyCounts.collect { counts ->
                dailyReminderCounts = counts
                refreshTodayReminderCount()
            }
        }
        viewModelScope.launch {
            appSelectionRepository.selectedPackages.collect { selectedPackages ->
                _uiState.update { state ->
                    state.copy(monitoredAppCount = selectedPackages.size)
                }
            }
        }
        viewModelScope.launch {
            monitoringPauseRepository.pauseUntilEpochMillis.collect { pauseUntilEpochMillis ->
                _uiState.update { state ->
                    state.copy(
                        isTemporarilyPaused = pauseUntilEpochMillis > System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun refreshUsageAccess() {
        _uiState.update { state ->
            state.copy(
                hasUsageAccess = usageAccessRepository.hasUsageAccess(),
                hasNotificationPermission =
                    notificationPermissionRepository.hasNotificationPermission(),
                todayReminderCount = dailyReminderCounts[reminderStatsRepository.currentDate()],
            )
        }
    }

    private fun refreshTodayReminderCount() {
        _uiState.update { state ->
            state.copy(
                todayReminderCount = dailyReminderCounts[reminderStatsRepository.currentDate()],
            )
        }
    }
}
