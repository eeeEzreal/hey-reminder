package com.heyreminder.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.OverlayPermissionRepository
import com.heyreminder.app.data.ReminderSettings
import com.heyreminder.app.data.ReminderSettingsRepository
import com.heyreminder.app.monitor.OverlayReminderPresenter
import com.heyreminder.app.monitor.ReminderConditionReachedEvent
import com.heyreminder.app.monitor.ReminderVisualLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TestReminderResult {
    SHOWN,
    NEEDS_OVERLAY_PERMISSION,
    FAILED,
}

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val testReminderResult: TestReminderResult? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderSettingsRepository(application)
    private val overlayPermissionRepository = OverlayPermissionRepository(application)
    private val overlayPresenter = OverlayReminderPresenter(application)
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

    fun showTestStrongReminder(): TestReminderResult {
        if (!overlayPermissionRepository.hasOverlayPermission()) {
            val result = TestReminderResult.NEEDS_OVERLAY_PERMISSION
            _uiState.update { state -> state.copy(testReminderResult = result) }
            return result
        }
        val now = SystemClock.elapsedRealtime()
        val settings = _uiState.value.settings
        val shown = overlayPresenter.show(
            event = ReminderConditionReachedEvent(
                packageName = getApplication<Application>().packageName,
                sessionStartedAtElapsedMillis = now - settings.reminderMinutes * 60_000L,
                reminderDueAtElapsedMillis = now,
                continuousDurationMillis = settings.reminderMinutes * 60_000L,
            ),
            level = ReminderVisualLevel.FIRST,
            settings = settings,
            onAction = {},
        )
        val result = if (shown) TestReminderResult.SHOWN else TestReminderResult.FAILED
        _uiState.update { state -> state.copy(testReminderResult = result) }
        return result
    }

    override fun onCleared() {
        overlayPresenter.dismiss()
        super.onCleared()
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
