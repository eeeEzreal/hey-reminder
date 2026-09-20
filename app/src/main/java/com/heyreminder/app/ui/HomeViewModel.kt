package com.heyreminder.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val isReminderEnabled: Boolean = true,
    val modeLabel: String = "黑名单",
    val monitoredAppCount: Int = 0,
    val reminderMinutes: Int = 10,
) {
    val statusLabel: String
        get() = if (isReminderEnabled) "等待授权" else "已关闭"
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}

