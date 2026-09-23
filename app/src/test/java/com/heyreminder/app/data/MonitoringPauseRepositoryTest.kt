package com.heyreminder.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringPauseRepositoryTest {
    @Test
    fun `pause deadline persists until explicitly cleared`() = runBlocking {
        val repository = MonitoringPauseRepository(InMemoryPreferencesDataStore())

        repository.pauseUntil(1_800_000L)
        assertEquals(1_800_000L, repository.currentPauseUntilEpochMillis())

        repository.clearPause()
        assertEquals(0L, repository.pauseUntilEpochMillis.first())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

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
