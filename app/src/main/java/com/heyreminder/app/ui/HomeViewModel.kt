package com.heyreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.heyreminder.app.data.UsageAccessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val isReminderEnabled: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val modeLabel: String = "黑名单",
    val monitoredAppCount: Int = 0,
    val reminderMinutes: Int = 10,
) {
    val statusLabel: String
        get() = when {
            !isReminderEnabled -> "已关闭"
            !hasUsageAccess -> "等待授权"
            else -> "准备就绪"
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val usageAccessRepository = UsageAccessRepository(application)
    private val _uiState = MutableStateFlow(
        HomeUiState(hasUsageAccess = usageAccessRepository.hasUsageAccess()),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refreshUsageAccess() {
        _uiState.value = _uiState.value.copy(
            hasUsageAccess = usageAccessRepository.hasUsageAccess(),
        )
    }
}
