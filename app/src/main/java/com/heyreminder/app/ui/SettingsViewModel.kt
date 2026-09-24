package com.heyreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.ReminderSettings
import com.heyreminder.app.data.ReminderSettingsRepository
import com.heyreminder.app.monitor.UsageReminderNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TestReminderResult {
    SENT,
    UNAVAILABLE,
}

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val testReminderResult: TestReminderResult? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderSettingsRepository(application)
    private val reminderNotifier = UsageReminderNotifier(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { state -> state.copy(settings = settings) }
            }
        }
    }

    fun setReminderEnabled(enabled: Boolean) = update {
        repository.setReminderEnabled(enabled)
    }

    fun setMonitoringMode(mode: MonitoringMode) = update {
        repository.setMonitoringMode(mode)
    }

    fun setReminderMinutes(minutes: Int) = update {
        repository.setReminderMinutes(minutes)
    }

    fun setSnoozeMinutes(minutes: Int) = update {
        repository.setSnoozeMinutes(minutes)
    }

    fun setPauseMinutes(minutes: Int) = update {
        repository.setPauseMinutes(minutes)
    }

    fun sendTestReminder() {
        val result = if (reminderNotifier.showTestReminder()) {
            TestReminderResult.SENT
        } else {
            TestReminderResult.UNAVAILABLE
        }
        _uiState.update { state -> state.copy(testReminderResult = result) }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
