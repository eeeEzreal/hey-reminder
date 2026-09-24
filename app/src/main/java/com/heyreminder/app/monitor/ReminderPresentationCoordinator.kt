package com.heyreminder.app.monitor

import com.heyreminder.app.data.ReminderSettings

data class ReminderPresentationResult(
    val overlayShown: Boolean,
    val notificationShown: Boolean,
) {
    val wasPresented: Boolean
        get() = overlayShown || notificationShown
}

internal class ReminderPresentationCoordinator(
    private val overlayPresenter: OverlayReminderPresenter,
    private val notificationPresenter: UsageReminderNotifier,
) {
    fun show(
        event: ReminderConditionReachedEvent,
        level: ReminderVisualLevel,
        settings: ReminderSettings,
        onAction: (ReminderActionCommand) -> Unit,
    ): ReminderPresentationResult {
        val overlayShown = overlayPresenter.show(event, level, settings, onAction)
        val notificationShown = notificationPresenter.show(event, level)
        return ReminderPresentationResult(
            overlayShown = overlayShown,
            notificationShown = notificationShown,
        )
    }

    fun dismiss() {
        overlayPresenter.dismiss()
        notificationPresenter.dismiss()
    }
}
