package com.heyreminder.app.monitor

import com.heyreminder.app.data.ReminderSettings

data class ReminderTimingConfig(
    val reminderIntervalMillis: Long = 10L * 60L * 1_000L,
    val snoozeIntervalMillis: Long = 5L * 60L * 1_000L,
    val pauseDurationMillis: Long = 30L * 60L * 1_000L,
) {
    init {
        require(reminderIntervalMillis > 0) { "reminderIntervalMillis must be positive" }
        require(snoozeIntervalMillis > 0) { "snoozeIntervalMillis must be positive" }
        require(pauseDurationMillis > 0) { "pauseDurationMillis must be positive" }
    }
}

val DEFAULT_REMINDER_TIMING = ReminderTimingConfig()

fun ReminderSettings.toReminderTimingConfig() = ReminderTimingConfig(
    reminderIntervalMillis = reminderMinutes.toMilliseconds(),
    snoozeIntervalMillis = snoozeMinutes.toMilliseconds(),
    pauseDurationMillis = pauseMinutes.toMilliseconds(),
)

data class ContinuousUsageState(
    val packageName: String? = null,
    val startedAtElapsedMillis: Long? = null,
    val nextReminderAtElapsedMillis: Long? = null,
    val reminderConditionReached: Boolean = false,
) {
    val isActive: Boolean
        get() = packageName != null && startedAtElapsedMillis != null
}

data class ReminderConditionReachedEvent(
    val packageName: String,
    val sessionStartedAtElapsedMillis: Long,
    val reminderDueAtElapsedMillis: Long,
    val continuousDurationMillis: Long,
)

data class ReminderActionTarget(
    val packageName: String,
    val sessionStartedAtElapsedMillis: Long,
    val reminderDueAtElapsedMillis: Long,
)

data class ContinuousUsageUpdate(
    val state: ContinuousUsageState,
    val event: ReminderConditionReachedEvent? = null,
)

class ContinuousUsageTracker(
    private val reminderIntervalMillis: Long = DEFAULT_REMINDER_TIMING.reminderIntervalMillis,
) {
    init {
        require(reminderIntervalMillis > 0) {
            "reminderIntervalMillis must be positive"
        }
    }

    fun update(
        previousState: ContinuousUsageState,
        foregroundPackage: String?,
        monitoredPackages: Set<String>,
        nowElapsedMillis: Long,
    ): ContinuousUsageUpdate {
        val monitoredPackage = foregroundPackage?.takeIf(monitoredPackages::contains)
            ?: return ContinuousUsageUpdate(state = ContinuousUsageState())

        val sessionStart = previousState.startedAtElapsedMillis
        val nextReminderAt = previousState.nextReminderAtElapsedMillis
        if (
            previousState.packageName != monitoredPackage ||
            sessionStart == null ||
            nextReminderAt == null ||
            nowElapsedMillis < sessionStart
        ) {
            return ContinuousUsageUpdate(
                state = ContinuousUsageState(
                    packageName = monitoredPackage,
                    startedAtElapsedMillis = nowElapsedMillis,
                    nextReminderAtElapsedMillis = nowElapsedMillis + reminderIntervalMillis,
                ),
            )
        }

        val continuousDurationMillis = nowElapsedMillis - sessionStart
        if (
            !previousState.reminderConditionReached &&
            nowElapsedMillis >= nextReminderAt
        ) {
            return ContinuousUsageUpdate(
                state = previousState.copy(reminderConditionReached = true),
                event = ReminderConditionReachedEvent(
                    packageName = monitoredPackage,
                    sessionStartedAtElapsedMillis = sessionStart,
                    reminderDueAtElapsedMillis = nextReminderAt,
                    continuousDurationMillis = continuousDurationMillis,
                ),
            )
        }

        return ContinuousUsageUpdate(state = previousState)
    }

    fun scheduleNextReminder(
        previousState: ContinuousUsageState,
        target: ReminderActionTarget,
        delayMillis: Long,
        nowElapsedMillis: Long,
    ): ContinuousUsageState? {
        require(delayMillis > 0) { "delayMillis must be positive" }
        if (!matchesCurrentReminder(previousState, target)) return null

        return previousState.copy(
            nextReminderAtElapsedMillis = nowElapsedMillis + delayMillis,
            reminderConditionReached = false,
        )
    }

    fun matchesCurrentReminder(
        state: ContinuousUsageState,
        target: ReminderActionTarget,
    ): Boolean =
        state.reminderConditionReached &&
            state.packageName == target.packageName &&
            state.startedAtElapsedMillis == target.sessionStartedAtElapsedMillis &&
            state.nextReminderAtElapsedMillis == target.reminderDueAtElapsedMillis
}

private fun Int.toMilliseconds(): Long = this * 60L * 1_000L
