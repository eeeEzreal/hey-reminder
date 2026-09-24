package com.heyreminder.app.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {
    private val event = ReminderConditionReachedEvent(
        packageName = "com.example.target",
        sessionStartedAtElapsedMillis = 1_000L,
        reminderDueAtElapsedMillis = 601_000L,
        continuousDurationMillis = 600_000L,
    )
    private val usageState = ContinuousUsageState(
        packageName = event.packageName,
        startedAtElapsedMillis = event.sessionStartedAtElapsedMillis,
        nextReminderAtElapsedMillis = event.reminderDueAtElapsedMillis,
        reminderConditionReached = true,
    )

    @Test
    fun `first due requests first reminder and successful presentation becomes visible`() {
        val engine = ReminderEngine()

        val effect = engine.onReminderDue(event, 601_000L) as ReminderEngineEffect.ShowReminder
        assertEquals(ReminderVisualLevel.FIRST, effect.level)
        assertTrue(effect.shouldRecordTriggeredReminder)
        assertEquals(ReminderEnginePhase.FIRST_REMINDER_DUE, engine.state.phase)

        engine.onPresentationResult(event.toActionTarget(), effect.level, true, 602_000L)

        assertEquals(ReminderEnginePhase.REMINDER_VISIBLE, engine.state.phase)
        assertEquals(602_000L, engine.state.firstPresentedAtElapsedMillis)
    }

    @Test
    fun `acknowledge snooze and pause explicitly handle a visible reminder`() {
        ReminderActionType.entries.forEach { action ->
            val engine = visibleEngine()
            val effect = engine.onAction(ReminderActionCommand(action, event.toActionTarget()))

            assertEquals(ReminderEngineEffect.DismissReminder, effect)
            assertEquals(
                when (action) {
                    ReminderActionType.ACKNOWLEDGE -> ReminderEnginePhase.USER_ACKNOWLEDGED
                    ReminderActionType.SNOOZE -> ReminderEnginePhase.SNOOZED
                    ReminderActionType.PAUSE -> ReminderEnginePhase.PAUSED
                },
                engine.state.phase,
            )
        }
    }

    @Test
    fun `unhandled first reminder escalates after five minutes`() {
        val engine = visibleEngine(presentedAt = 602_000L)

        assertNull(engine.onTick(usageState, 901_999L))
        val effect = engine.onTick(usageState, 902_000L) as ReminderEngineEffect.ShowReminder

        assertEquals(ReminderVisualLevel.ESCALATED, effect.level)
        assertFalse(effect.shouldRecordTriggeredReminder)
        assertEquals(ReminderEnginePhase.ESCALATION_DUE, engine.state.phase)
        assertEquals(901_000L, effect.event.continuousDurationMillis)
    }

    @Test
    fun `leaving app ends session and dismisses overlay`() {
        val engine = visibleEngine()

        assertEquals(
            ReminderEngineEffect.DismissReminder,
            engine.onTick(ContinuousUsageState(), 603_000L),
        )
        assertEquals(ReminderEnginePhase.SESSION_ENDED, engine.state.phase)
    }

    @Test
    fun `failed presentation retries without treating reminder as handled`() {
        val engine = ReminderEngine(presentationRetryMillis = 30_000L)
        val effect = engine.onReminderDue(event, 601_000L) as ReminderEngineEffect.ShowReminder
        engine.onPresentationResult(event.toActionTarget(), effect.level, false, 602_000L)

        assertNull(engine.onTick(usageState, 631_999L))
        val retry = engine.onTick(usageState, 632_000L) as ReminderEngineEffect.ShowReminder
        assertEquals(ReminderVisualLevel.FIRST, retry.level)
        assertTrue(retry.shouldRecordTriggeredReminder)
    }

    @Test
    fun `recording first presentation prevents duplicate count on retry or escalation`() {
        val engine = ReminderEngine()
        engine.onReminderDue(event, 601_000L)
        engine.markTriggeredReminderRecorded(event.toActionTarget())
        engine.onPresentationResult(event.toActionTarget(), ReminderVisualLevel.FIRST, true, 602_000L)

        val escalation = engine.onTick(usageState, 902_000L) as ReminderEngineEffect.ShowReminder
        assertFalse(escalation.shouldRecordTriggeredReminder)
    }

    @Test
    fun `stale and repeated actions cannot handle current or completed reminder`() {
        val engine = visibleEngine()
        val staleTarget = event.toActionTarget().copy(reminderDueAtElapsedMillis = 999_000L)

        assertNull(
            engine.onAction(ReminderActionCommand(ReminderActionType.ACKNOWLEDGE, staleTarget)),
        )
        assertEquals(ReminderEnginePhase.REMINDER_VISIBLE, engine.state.phase)

        val command = ReminderActionCommand(ReminderActionType.ACKNOWLEDGE, event.toActionTarget())
        assertEquals(ReminderEngineEffect.DismissReminder, engine.onAction(command))
        assertNull(engine.onAction(command))
    }

    @Test
    fun `new session is isolated from an ended session`() {
        val engine = visibleEngine()
        engine.onTick(ContinuousUsageState(), 603_000L)
        val nextEvent = event.copy(
            sessionStartedAtElapsedMillis = 1_000_000L,
            reminderDueAtElapsedMillis = 1_600_000L,
        )

        val effect = engine.onReminderDue(nextEvent, 1_600_000L) as ReminderEngineEffect.ShowReminder
        assertEquals(nextEvent, effect.event)
        assertEquals(ReminderEnginePhase.FIRST_REMINDER_DUE, engine.state.phase)
    }

    private fun visibleEngine(presentedAt: Long = 602_000L): ReminderEngine {
        val engine = ReminderEngine()
        engine.onReminderDue(event, 601_000L)
        engine.markTriggeredReminderRecorded(event.toActionTarget())
        engine.onPresentationResult(event.toActionTarget(), ReminderVisualLevel.FIRST, true, presentedAt)
        return engine
    }
}
