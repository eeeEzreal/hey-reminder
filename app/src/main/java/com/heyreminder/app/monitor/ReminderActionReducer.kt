package com.heyreminder.app.monitor

enum class ReminderActionType {
    ACKNOWLEDGE,
    SNOOZE,
    PAUSE,
}

data class ReminderActionResult(
    val usageState: ContinuousUsageState,
    val pauseUntilEpochMillis: Long? = null,
)

class ReminderActionReducer(
    private val usageTracker: ContinuousUsageTracker,
    private val timingConfig: ReminderTimingConfig,
) {
    fun reduce(
        previousState: ContinuousUsageState,
        action: ReminderActionType,
        target: ReminderActionTarget,
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): ReminderActionResult? = when (action) {
        ReminderActionType.ACKNOWLEDGE -> usageTracker.scheduleNextReminder(
            previousState = previousState,
            target = target,
            delayMillis = timingConfig.reminderIntervalMillis,
            nowElapsedMillis = nowElapsedMillis,
        )?.let(::ReminderActionResult)

        ReminderActionType.SNOOZE -> usageTracker.scheduleNextReminder(
            previousState = previousState,
            target = target,
            delayMillis = timingConfig.snoozeIntervalMillis,
            nowElapsedMillis = nowElapsedMillis,
        )?.let(::ReminderActionResult)

        ReminderActionType.PAUSE -> {
            if (!usageTracker.matchesCurrentReminder(previousState, target)) {
                null
            } else {
                ReminderActionResult(
                    usageState = ContinuousUsageState(),
                    pauseUntilEpochMillis = nowEpochMillis + timingConfig.pauseDurationMillis,
                )
            }
        }
    }
}
