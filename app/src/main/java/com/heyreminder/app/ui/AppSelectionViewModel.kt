package com.heyreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heyreminder.app.data.AppSelectionRepository
import com.heyreminder.app.data.InstalledApp
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.ReminderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val apps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val monitoringMode: MonitoringMode = MonitoringMode.BLACKLIST,
) {
    val selectedCount: Int
        get() = selectedPackages.size
}

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppSelectionRepository(application)
    private val settingsRepository = ReminderSettingsRepository(application)
    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.selectedPackages.collect { selectedPackages ->
                _uiState.update { state ->
                    state.copy(selectedPackages = selectedPackages)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(monitoringMode = settings.monitoringMode)
                }
            }
        }
        loadApps()
    }

    fun toggleApp(packageName: String) {
        val shouldSelect = packageName !in _uiState.value.selectedPackages
        _uiState.update { state ->
            state.copy(
                selectedPackages = if (shouldSelect) {
                    state.selectedPackages + packageName
                } else {
                    state.selectedPackages - packageName
                },
            )
        }
        viewModelScope.launch {
            repository.setPackageSelected(packageName, shouldSelect)
        }
    }

    fun retryLoading() {
        loadApps()
    }

    private fun loadApps() {
        _uiState.update { state ->
            state.copy(isLoading = true, loadFailed = false)
        }
        viewModelScope.launch {
            runCatching { repository.loadInstalledApps() }
                .onSuccess { apps ->
                    _uiState.update { state ->
                        state.copy(
                            apps = apps,
                            isLoading = false,
                            loadFailed = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isLoading = false, loadFailed = true)
                    }
                }
        }
    }
}
