package com.heyreminder.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderStatsRepositoryTest {
    @Test
    fun `records only explicit trigger calls in their local date buckets`() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val repository = createRepository(
            clock = Clock.fixed(Instant.parse("2026-09-21T02:00:00Z"), zone),
        )

        repository.recordTriggeredReminder(Instant.parse("2026-09-21T02:00:00Z"))
        repository.recordTriggeredReminder(Instant.parse("2026-09-21T15:59:59Z"))
        repository.recordTriggeredReminder(Instant.parse("2026-09-21T16:00:00Z"))

        val counts = repository.dailyCounts.first()
        assertEquals(2, counts[LocalDate.parse("2026-09-21")])
        assertEquals(1, counts[LocalDate.parse("2026-09-22")])
    }

    @Test
    fun `aggregates an inclusive date range for future week and month views`() {
        val counts = DailyReminderCounts(
            countsByDate = mapOf(
                LocalDate.parse("2026-09-20") to 2,
                LocalDate.parse("2026-09-21") to 3,
                LocalDate.parse("2026-09-22") to 4,
            ),
        )

        assertEquals(
            7,
            counts.totalBetween(
                fromInclusive = LocalDate.parse("2026-09-21"),
                toInclusive = LocalDate.parse("2026-09-22"),
            ),
        )
    }

    @Test
    fun `legacy notification fallback counts are not reported as successful overlays`() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val repository = ReminderStatsRepository(
            dataStore = InMemoryPreferencesDataStore(
                preferencesOf(
                    intPreferencesKey("reminder_count_2026-09-21") to 2,
                ),
            ),
            clock = Clock.fixed(Instant.parse("2026-09-21T02:00:00Z"), zone),
        )

        assertEquals(0, repository.dailyCounts.first()[LocalDate.parse("2026-09-21")])
    }

    private fun createRepository(clock: Clock): ReminderStatsRepository {
        return ReminderStatsRepository(
            dataStore = InMemoryPreferencesDataStore(),
            clock = clock,
        )
    }

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
