package com.heyreminder.app.monitor

const val DEFAULT_REMINDER_THRESHOLD_MILLIS = 10L * 60L * 1_000L

data class ContinuousUsageState(
    val packageName: String? = null,
    val startedAtElapsedMillis: Long? = null,
    val reminderConditionReached: Boolean = false,
) {
    val isActive: Boolean
        get() = packageName != null && startedAtElapsedMillis != null
}

data class ReminderConditionReachedEvent(
    val packageName: String,
    val continuousDurationMillis: Long,
)

data class ContinuousUsageUpdate(
    val state: ContinuousUsageState,
    val event: ReminderConditionReachedEvent? = null,
)

class ContinuousUsageTracker(
    private val reminderThresholdMillis: Long = DEFAULT_REMINDER_THRESHOLD_MILLIS,
) {
    init {
        require(reminderThresholdMillis > 0) {
            "reminderThresholdMillis must be positive"
        }
    }

    fun update(
        previousState: ContinuousUsageState,
        foregroundPackage: String?,
        selectedPackages: Set<String>,
        nowElapsedMillis: Long,
    ): ContinuousUsageUpdate {
        val monitoredPackage = foregroundPackage?.takeIf(selectedPackages::contains)
            ?: return ContinuousUsageUpdate(state = ContinuousUsageState())

        val sessionStart = previousState.startedAtElapsedMillis
        if (
            previousState.packageName != monitoredPackage ||
            sessionStart == null ||
            nowElapsedMillis < sessionStart
        ) {
            return ContinuousUsageUpdate(
                state = ContinuousUsageState(
                    packageName = monitoredPackage,
                    startedAtElapsedMillis = nowElapsedMillis,
                ),
            )
        }

        val continuousDurationMillis = nowElapsedMillis - sessionStart
        if (
            !previousState.reminderConditionReached &&
            continuousDurationMillis >= reminderThresholdMillis
        ) {
            return ContinuousUsageUpdate(
                state = previousState.copy(reminderConditionReached = true),
                event = ReminderConditionReachedEvent(
                    packageName = monitoredPackage,
                    continuousDurationMillis = continuousDurationMillis,
                ),
            )
        }

        return ContinuousUsageUpdate(state = previousState)
    }
}
