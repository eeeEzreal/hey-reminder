package com.heyreminder.app.monitor

import com.heyreminder.app.data.ReminderSettings

data class ReminderPresentationResult(
    val overlayShown: Boolean,
    val notificationShown: Boolean,
    val overlayFailureReason: String? = null,
) {
    /** Notification is fallback only; it must never make the engine believe the Overlay succeeded. */
    val wasPresented: Boolean
        get() = overlayShown
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
        val notificationShown = if (shouldShowNotificationFallback(overlayShown)) {
            notificationPresenter.show(event, level)
        } else {
            notificationPresenter.dismiss()
            false
        }
        return ReminderPresentationResult(
            overlayShown = overlayShown,
            notificationShown = notificationShown,
            overlayFailureReason = overlayPresenter.lastFailureReason,
        )
    }

    fun dismiss() {
        overlayPresenter.dismiss()
        notificationPresenter.dismiss()
    }
}

internal fun shouldShowNotificationFallback(overlayShown: Boolean): Boolean = !overlayShown
