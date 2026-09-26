package com.heyreminder.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val REMINDER_STATS_STORE_NAME = "reminder_stats"
// Phase 9.1: older buckets counted notification fallback as success. Keep them untouched but do not
// mix them with the new, stricter metric that counts only a successfully displayed Overlay.
private const val DAILY_COUNT_KEY_PREFIX = "overlay_reminder_count_"

private val Context.reminderStatsDataStore by preferencesDataStore(
    name = REMINDER_STATS_STORE_NAME,
)

data class DailyReminderCounts(
    val countsByDate: Map<LocalDate, Int> = emptyMap(),
) {
    operator fun get(date: LocalDate): Int = countsByDate[date] ?: 0

    fun totalBetween(fromInclusive: LocalDate, toInclusive: LocalDate): Int {
        require(!toInclusive.isBefore(fromInclusive)) {
            "toInclusive must not be before fromInclusive"
        }
        return countsByDate
            .filterKeys { date -> date in fromInclusive..toInclusive }
            .values
            .sum()
    }
}

class ReminderStatsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    constructor(
        context: Context,
        clock: Clock = Clock.systemDefaultZone(),
    ) : this(
        dataStore = context.applicationContext.reminderStatsDataStore,
        clock = clock,
    )

    val dailyCounts: Flow<DailyReminderCounts> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(Preferences::toDailyReminderCounts)

    fun currentDate(): LocalDate = LocalDate.now(clock)

    /**
     * Records one reminder only after its primary Overlay was actually added to the window.
     * Notification fallback, starting a timer, and handling an action must never call this method.
     */
    suspend fun recordTriggeredReminder(triggeredAt: Instant = clock.instant()) {
        val date = triggeredAt.atZone(clock.zone).toLocalDate()
        val key = dailyCountKey(date)
        dataStore.edit { preferences ->
            val currentCount = preferences[key] ?: 0
            preferences[key] = if (currentCount == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                currentCount + 1
            }
        }
    }
}

private fun dailyCountKey(date: LocalDate): Preferences.Key<Int> =
    intPreferencesKey("$DAILY_COUNT_KEY_PREFIX$date")

private fun Preferences.toDailyReminderCounts(): DailyReminderCounts {
    val counts = asMap().mapNotNull { (key, value) ->
        if (!key.name.startsWith(DAILY_COUNT_KEY_PREFIX) || value !is Int) {
            return@mapNotNull null
        }

        val date = runCatching {
            LocalDate.parse(key.name.removePrefix(DAILY_COUNT_KEY_PREFIX))
        }.getOrNull() ?: return@mapNotNull null

        date to value.coerceAtLeast(0)
    }.toMap()

    return DailyReminderCounts(countsByDate = counts)
}
