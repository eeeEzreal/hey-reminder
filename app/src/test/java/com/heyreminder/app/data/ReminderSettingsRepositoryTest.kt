package com.heyreminder.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderSettingsRepositoryTest {
    @Test
    fun `new repository exposes product defaults`() = runBlocking {
        val settings = createRepository().settings.first()

        assertEquals(ReminderSettings(), settings)
    }

    @Test
    fun `all settings persist through the shared preferences store`() = runBlocking {
        val repository = createRepository()

        repository.setReminderEnabled(false)
        repository.setMonitoringMode(MonitoringMode.WHITELIST)
        repository.setReminderMinutes(20)
        repository.setSnoozeMinutes(10)
        repository.setPauseMinutes(60)
        repository.setDebug30SecondReminderEnabled(true)

        val settings = repository.settings.first()
        assertFalse(settings.isReminderEnabled)
        assertEquals(MonitoringMode.WHITELIST, settings.monitoringMode)
        assertEquals(20, settings.reminderMinutes)
        assertEquals(10, settings.snoozeMinutes)
        assertEquals(60, settings.pauseMinutes)
        assertEquals(true, settings.isDebug30SecondReminderEnabled)
    }

    @Test
    fun `invalid persisted values fall back to safe defaults`() = runBlocking {
        val dataStore = InMemoryPreferencesDataStore(
            initialPreferences = androidx.datastore.preferences.core.preferencesOf(
                stringPreferencesKey("monitoring_mode") to "UNKNOWN",
                intPreferencesKey("reminder_minutes") to 0,
                intPreferencesKey("snooze_minutes") to -1,
                intPreferencesKey("pause_minutes") to 0,
            ),
        )

        assertEquals(ReminderSettings(), ReminderSettingsRepository(dataStore).settings.first())
    }

    @Test
    fun `legacy selected-only blacklist migrates to corrected whitelist`() = runBlocking {
        val dataStore = InMemoryPreferencesDataStore(
            initialPreferences = androidx.datastore.preferences.core.preferencesOf(
                ReminderSettingsRepository.MonitoringModeKey to MonitoringMode.BLACKLIST.name,
                AppSelectionRepository.SelectedPackagesKey to setOf("social"),
            ),
        )

        val settings = ReminderSettingsRepository(dataStore).settings.first()

        assertEquals(MonitoringMode.WHITELIST, settings.monitoringMode)
        assertEquals(
            ReminderSettingsRepository.CURRENT_MONITORING_MODE_VERSION,
            dataStore.data.first()[ReminderSettingsRepository.MonitoringModeSemanticsVersionKey],
        )
    }

    @Test
    fun `legacy exclusion whitelist migrates to corrected blacklist`() = runBlocking {
        val dataStore = InMemoryPreferencesDataStore(
            initialPreferences = androidx.datastore.preferences.core.preferencesOf(
                ReminderSettingsRepository.MonitoringModeKey to MonitoringMode.WHITELIST.name,
                AppSelectionRepository.SelectedPackagesKey to setOf("social"),
            ),
        )

        assertEquals(
            MonitoringMode.BLACKLIST,
            ReminderSettingsRepository(dataStore).settings.first().monitoringMode,
        )
    }

    private fun createRepository() = ReminderSettingsRepository(InMemoryPreferencesDataStore())

    private class InMemoryPreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            transform(state.value).also { updatedPreferences ->
                state.value = updatedPreferences
            }
        }
    }
}
