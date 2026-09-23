package com.heyreminder.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val MONITORING_STATE_STORE_NAME = "monitoring_state"

private val Context.monitoringStateDataStore by preferencesDataStore(
    name = MONITORING_STATE_STORE_NAME,
)

class MonitoringPauseRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.monitoringStateDataStore)

    val pauseUntilEpochMillis: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            (preferences[PAUSE_UNTIL_EPOCH_MILLIS] ?: 0L).coerceAtLeast(0L)
        }

    suspend fun currentPauseUntilEpochMillis(): Long = pauseUntilEpochMillis.first()

    suspend fun pauseUntil(epochMillis: Long) {
        require(epochMillis > 0L) { "epochMillis must be positive" }
        dataStore.edit { preferences ->
            preferences[PAUSE_UNTIL_EPOCH_MILLIS] = epochMillis
        }
    }

    suspend fun clearPause() {
        dataStore.edit { preferences ->
            preferences.remove(PAUSE_UNTIL_EPOCH_MILLIS)
        }
    }

    private companion object {
        val PAUSE_UNTIL_EPOCH_MILLIS = longPreferencesKey("pause_until_epoch_millis")
    }
}
