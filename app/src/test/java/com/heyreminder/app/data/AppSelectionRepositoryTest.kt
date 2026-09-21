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

class AppSelectionRepositoryTest {
    @Test
    fun `selected packages are added and removed explicitly`() = runBlocking {
        val repository = createRepository()

        repository.setPackageSelected("com.example.one", isSelected = true)
        repository.setPackageSelected("com.example.two", isSelected = true)
        assertEquals(
            setOf("com.example.one", "com.example.two"),
            repository.selectedPackages.first(),
        )

        repository.setPackageSelected("com.example.one", isSelected = false)
        assertEquals(
            setOf("com.example.two"),
            repository.selectedPackages.first(),
        )
    }

    @Test
    fun `installed app catalog can be loaded independently from selection`() = runBlocking {
        val apps = listOf(
            InstalledApp(
                packageName = "com.example.app",
                displayName = "Example",
                icon = null,
            ),
        )
        val repository = AppSelectionRepository(
            dataStore = InMemoryPreferencesDataStore(),
            installedAppsLoader = { apps },
        )

        assertEquals(apps, repository.loadInstalledApps())
        assertEquals(emptySet<String>(), repository.selectedPackages.first())
    }

    private fun createRepository(): AppSelectionRepository = AppSelectionRepository(
        dataStore = InMemoryPreferencesDataStore(),
        installedAppsLoader = { emptyList() },
    )

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
