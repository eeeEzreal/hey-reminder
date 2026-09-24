package com.heyreminder.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class MonitoringMode {
    BLACKLIST,
    WHITELIST,
}

data class ReminderSettings(
    val isReminderEnabled: Boolean = true,
    val monitoringMode: MonitoringMode = MonitoringMode.BLACKLIST,
    val reminderMinutes: Int = 10,
    val snoozeMinutes: Int = 5,
    val pauseMinutes: Int = 30,
)

class ReminderSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.userPreferencesDataStore)

    val settings: Flow<ReminderSettings> = flow {
        migrateLegacyMonitoringMode()
        emitAll(
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map(::toReminderSettings),
        )
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ReminderEnabledKey] = enabled
        }
    }

    suspend fun setMonitoringMode(mode: MonitoringMode) {
        dataStore.edit { preferences ->
            preferences[MonitoringModeKey] = mode.name
            preferences[MonitoringModeSemanticsVersionKey] = CURRENT_MONITORING_MODE_VERSION
        }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        setPositiveMinutes(ReminderMinutesKey, minutes)
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        setPositiveMinutes(SnoozeMinutesKey, minutes)
    }

    suspend fun setPauseMinutes(minutes: Int) {
        setPositiveMinutes(PauseMinutesKey, minutes)
    }

    private suspend fun setPositiveMinutes(
        key: Preferences.Key<Int>,
        minutes: Int,
    ) {
        require(minutes > 0) { "minutes must be positive" }
        dataStore.edit { preferences ->
            preferences[key] = minutes
        }
    }

    private suspend fun migrateLegacyMonitoringMode() {
        dataStore.edit { preferences ->
            if (
                preferences[MonitoringModeSemanticsVersionKey] ==
                CURRENT_MONITORING_MODE_VERSION
            ) {
                return@edit
            }

            val selectedPackages = preferences[AppSelectionRepository.SelectedPackagesKey]
                .orEmpty()
            val legacyMode = preferences[MonitoringModeKey]
                ?.let { storedMode ->
                    MonitoringMode.entries.firstOrNull { mode -> mode.name == storedMode }
                }
                ?: MonitoringMode.BLACKLIST
            val correctedMode = when (legacyMode) {
                // The old implementation used BLACKLIST to mean "only these apps".
                // Keep existing non-empty selections monitored after correcting the labels.
                MonitoringMode.BLACKLIST -> if (selectedPackages.isEmpty()) {
                    MonitoringMode.BLACKLIST
                } else {
                    MonitoringMode.WHITELIST
                }

                // The old implementation used WHITELIST to mean "all except these apps".
                MonitoringMode.WHITELIST -> MonitoringMode.BLACKLIST
            }
            preferences[MonitoringModeKey] = correctedMode.name
            preferences[MonitoringModeSemanticsVersionKey] = CURRENT_MONITORING_MODE_VERSION
        }
    }

    companion object {
        internal val ReminderEnabledKey = booleanPreferencesKey("reminder_enabled")
        internal val MonitoringModeKey = stringPreferencesKey("monitoring_mode")
        internal val MonitoringModeSemanticsVersionKey =
            intPreferencesKey("monitoring_mode_semantics_version")
        internal val ReminderMinutesKey = intPreferencesKey("reminder_minutes")
        internal val SnoozeMinutesKey = intPreferencesKey("snooze_minutes")
        internal val PauseMinutesKey = intPreferencesKey("pause_minutes")
        internal const val CURRENT_MONITORING_MODE_VERSION = 2
    }
}

private fun toReminderSettings(preferences: Preferences): ReminderSettings {
    val defaults = ReminderSettings()
    return ReminderSettings(
        isReminderEnabled = preferences[ReminderSettingsRepository.ReminderEnabledKey]
            ?: defaults.isReminderEnabled,
        monitoringMode = preferences[ReminderSettingsRepository.MonitoringModeKey]
            ?.let { storedMode ->
                MonitoringMode.entries.firstOrNull { mode -> mode.name == storedMode }
            }
            ?: defaults.monitoringMode,
        reminderMinutes = preferences[ReminderSettingsRepository.ReminderMinutesKey]
            .positiveOr(defaults.reminderMinutes),
        snoozeMinutes = preferences[ReminderSettingsRepository.SnoozeMinutesKey]
            .positiveOr(defaults.snoozeMinutes),
        pauseMinutes = preferences[ReminderSettingsRepository.PauseMinutesKey]
            .positiveOr(defaults.pauseMinutes),
    )
}

private fun Int?.positiveOr(defaultValue: Int): Int =
    this?.takeIf { value -> value > 0 } ?: defaultValue
