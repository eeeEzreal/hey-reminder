package com.heyreminder.app.monitor

enum class ReminderEnginePhase {
    MONITORING,
    FIRST_REMINDER_DUE,
    REMINDER_VISIBLE,
    USER_ACKNOWLEDGED,
    SNOOZED,
    PAUSED,
    ESCALATION_DUE,
    SESSION_ENDED,
}

data class ReminderEngineState(
    val phase: ReminderEnginePhase = ReminderEnginePhase.MONITORING,
    val event: ReminderConditionReachedEvent? = null,
    val visualLevel: ReminderVisualLevel? = null,
    val firstPresentedAtElapsedMillis: Long? = null,
    val nextPresentationAtElapsedMillis: Long? = null,
    val triggeredReminderRecorded: Boolean = false,
)

sealed interface ReminderEngineEffect {
    data class ShowReminder(
        val event: ReminderConditionReachedEvent,
        val level: ReminderVisualLevel,
        val shouldRecordTriggeredReminder: Boolean,
    ) : ReminderEngineEffect

    data object DismissReminder : ReminderEngineEffect
}

internal class ReminderEngine(
    private val escalationDelayMillis: Long = DEFAULT_ESCALATION_DELAY_MILLIS,
    private val presentationRetryMillis: Long = DEFAULT_PRESENTATION_RETRY_MILLIS,
) {
    init {
        require(escalationDelayMillis > 0L)
        require(presentationRetryMillis > 0L)
    }

    var state: ReminderEngineState = ReminderEngineState()
        private set

    fun onReminderDue(
        event: ReminderConditionReachedEvent,
        nowElapsedMillis: Long,
    ): ReminderEngineEffect? {
        val target = event.toActionTarget()
        if (state.event?.toActionTarget() == target && state.phase.isActiveReminder()) return null

        state = ReminderEngineState(
            phase = ReminderEnginePhase.FIRST_REMINDER_DUE,
            event = event,
            visualLevel = ReminderVisualLevel.FIRST,
            nextPresentationAtElapsedMillis = nowElapsedMillis,
        )
        return showEffect()
    }

    fun onPresentationResult(
        target: ReminderActionTarget,
        level: ReminderVisualLevel,
        wasPresented: Boolean,
        nowElapsedMillis: Long,
    ) {
        if (state.event?.toActionTarget() != target || state.visualLevel != level) return
        if (state.phase !in setOf(
                ReminderEnginePhase.FIRST_REMINDER_DUE,
                ReminderEnginePhase.ESCALATION_DUE,
            )
        ) {
            return
        }

        state = if (wasPresented) {
            state.copy(
                phase = ReminderEnginePhase.REMINDER_VISIBLE,
                firstPresentedAtElapsedMillis =
                    state.firstPresentedAtElapsedMillis ?: nowElapsedMillis,
                nextPresentationAtElapsedMillis = null,
            )
        } else {
            state.copy(
                nextPresentationAtElapsedMillis = nowElapsedMillis + presentationRetryMillis,
            )
        }
    }

    fun markTriggeredReminderRecorded(target: ReminderActionTarget) {
        if (state.event?.toActionTarget() == target) {
            state = state.copy(triggeredReminderRecorded = true)
        }
    }

    fun onTick(
        usageState: ContinuousUsageState,
        nowElapsedMillis: Long,
    ): ReminderEngineEffect? {
        val event = state.event ?: return null
        if (!usageState.matches(event.toActionTarget())) {
            state = ReminderEngineState(phase = ReminderEnginePhase.SESSION_ENDED)
            return ReminderEngineEffect.DismissReminder
        }

        val retryAt = state.nextPresentationAtElapsedMillis
        if (
            state.phase in setOf(
                ReminderEnginePhase.FIRST_REMINDER_DUE,
                ReminderEnginePhase.ESCALATION_DUE,
            ) &&
            retryAt != null &&
            nowElapsedMillis >= retryAt
        ) {
            return showEffect()
        }

        val firstPresentedAt = state.firstPresentedAtElapsedMillis
        if (
            state.phase == ReminderEnginePhase.REMINDER_VISIBLE &&
            state.visualLevel == ReminderVisualLevel.FIRST &&
            firstPresentedAt != null &&
            nowElapsedMillis >= firstPresentedAt + escalationDelayMillis
        ) {
            state = state.copy(
                phase = ReminderEnginePhase.ESCALATION_DUE,
                event = event.copy(
                    continuousDurationMillis = nowElapsedMillis -
                        event.sessionStartedAtElapsedMillis,
                ),
                visualLevel = ReminderVisualLevel.ESCALATED,
                nextPresentationAtElapsedMillis = nowElapsedMillis,
            )
            return showEffect()
        }

        return null
    }

    fun onAction(command: ReminderActionCommand): ReminderEngineEffect? {
        if (!state.phase.isActiveReminder()) return null
        if (state.event?.toActionTarget() != command.target) return null

        state = ReminderEngineState(
            phase = when (command.action) {
                ReminderActionType.ACKNOWLEDGE -> ReminderEnginePhase.USER_ACKNOWLEDGED
                ReminderActionType.SNOOZE -> ReminderEnginePhase.SNOOZED
                ReminderActionType.PAUSE -> ReminderEnginePhase.PAUSED
            },
        )
        return ReminderEngineEffect.DismissReminder
    }

    fun reset(): ReminderEngineEffect? {
        val shouldDismiss = state.phase.isActiveReminder()
        state = ReminderEngineState()
        return if (shouldDismiss) ReminderEngineEffect.DismissReminder else null
    }

    private fun showEffect(): ReminderEngineEffect.ShowReminder {
        val event = requireNotNull(state.event)
        return ReminderEngineEffect.ShowReminder(
            event = event,
            level = requireNotNull(state.visualLevel),
            shouldRecordTriggeredReminder = !state.triggeredReminderRecorded,
        )
    }

    private fun ContinuousUsageState.matches(target: ReminderActionTarget): Boolean =
        reminderConditionReached &&
            packageName == target.packageName &&
            startedAtElapsedMillis == target.sessionStartedAtElapsedMillis &&
            nextReminderAtElapsedMillis == target.reminderDueAtElapsedMillis

    private fun ReminderEnginePhase.isActiveReminder(): Boolean = when (this) {
        ReminderEnginePhase.FIRST_REMINDER_DUE,
        ReminderEnginePhase.REMINDER_VISIBLE,
        ReminderEnginePhase.ESCALATION_DUE,
        -> true
        else -> false
    }

    companion object {
        const val DEFAULT_ESCALATION_DELAY_MILLIS = 5L * 60L * 1_000L
        const val DEFAULT_PRESENTATION_RETRY_MILLIS = 30L * 1_000L
    }
}

internal fun ReminderConditionReachedEvent.toActionTarget() = ReminderActionTarget(
    packageName = packageName,
    sessionStartedAtElapsedMillis = sessionStartedAtElapsedMillis,
    reminderDueAtElapsedMillis = reminderDueAtElapsedMillis,
)
