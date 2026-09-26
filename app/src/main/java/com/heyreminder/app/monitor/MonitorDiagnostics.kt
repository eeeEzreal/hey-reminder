package com.heyreminder.app.monitor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitorDiagnosticSnapshot(
    val updatedAtEpochMillis: Long = 0L,
    val usageAccessGranted: Boolean = false,
    val detectionStatus: String = "尚未检测",
    val rawForegroundPackage: String? = null,
    val eligibleForegroundPackage: String? = null,
    val isForegroundPackageMonitored: Boolean = false,
    val monitoredPackageCount: Int = 0,
    val sessionPackage: String? = null,
    val sessionElapsedMillis: Long = 0L,
    val lastMonitoredSessionPackage: String? = null,
    val lastMonitoredSessionElapsedMillis: Long = 0L,
    val reminderIntervalMillis: Long = 0L,
    val enginePhase: String = ReminderEnginePhase.MONITORING.name,
    val lastUsageEvent: String = "无",
    val lastSessionTransition: String = "无",
    val lastThresholdEvent: String = "无",
    val lastEngineEffect: String = "无",
    val lastPresentation: String = "无",
)

/**
 * Debug diagnostics with a small persisted history. Live values still update in memory every poll,
 * while significant events are persisted so opening Hey!, changing a setting, or restarting the
 * service cannot erase the evidence needed to diagnose a failed real reminder.
 */
object MonitorDiagnostics {
    private val mutableSnapshots = MutableStateFlow(MonitorDiagnosticSnapshot())
    val snapshots: StateFlow<MonitorDiagnosticSnapshot> = mutableSnapshots.asStateFlow()

    @Volatile
    private var preferences: SharedPreferences? = null

    @Synchronized
    internal fun initialize(context: Context) {
        if (preferences != null) return
        val storedPreferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        preferences = storedPreferences
        val defaults = mutableSnapshots.value
        mutableSnapshots.value = defaults.copy(
            lastMonitoredSessionPackage = storedPreferences.getString(
                KEY_LAST_MONITORED_PACKAGE,
                defaults.lastMonitoredSessionPackage,
            ),
            lastMonitoredSessionElapsedMillis = storedPreferences.getLong(
                KEY_LAST_MONITORED_ELAPSED,
                defaults.lastMonitoredSessionElapsedMillis,
            ),
            lastUsageEvent = storedPreferences.getString(
                KEY_LAST_USAGE_EVENT,
                defaults.lastUsageEvent,
            ) ?: defaults.lastUsageEvent,
            lastSessionTransition = storedPreferences.getString(
                KEY_LAST_SESSION_TRANSITION,
                defaults.lastSessionTransition,
            ) ?: defaults.lastSessionTransition,
            lastThresholdEvent = storedPreferences.getString(
                KEY_LAST_THRESHOLD_EVENT,
                defaults.lastThresholdEvent,
            ) ?: defaults.lastThresholdEvent,
            lastEngineEffect = storedPreferences.getString(
                KEY_LAST_ENGINE_EFFECT,
                defaults.lastEngineEffect,
            ) ?: defaults.lastEngineEffect,
            lastPresentation = storedPreferences.getString(
                KEY_LAST_PRESENTATION,
                defaults.lastPresentation,
            ) ?: defaults.lastPresentation,
        )
    }

    @Synchronized
    internal fun update(
        persistHistory: Boolean = false,
        block: (MonitorDiagnosticSnapshot) -> MonitorDiagnosticSnapshot,
    ) {
        val updated = block(mutableSnapshots.value)
        mutableSnapshots.value = updated
        if (persistHistory) persistHistory(updated)
    }

    @Synchronized
    internal fun persistCurrent() {
        persistHistory(mutableSnapshots.value)
    }

    @Synchronized
    internal fun resetForServiceStart(reminderIntervalMillis: Long) {
        val previous = mutableSnapshots.value
        mutableSnapshots.value = previous.copy(
            updatedAtEpochMillis = System.currentTimeMillis(),
            usageAccessGranted = false,
            detectionStatus = "服务刚启动",
            rawForegroundPackage = null,
            eligibleForegroundPackage = null,
            isForegroundPackageMonitored = false,
            monitoredPackageCount = 0,
            sessionPackage = null,
            sessionElapsedMillis = 0L,
            reminderIntervalMillis = reminderIntervalMillis,
            enginePhase = ReminderEnginePhase.MONITORING.name,
        )
    }

    private fun persistHistory(snapshot: MonitorDiagnosticSnapshot) {
        preferences?.edit()
            ?.putString(KEY_LAST_MONITORED_PACKAGE, snapshot.lastMonitoredSessionPackage)
            ?.putLong(KEY_LAST_MONITORED_ELAPSED, snapshot.lastMonitoredSessionElapsedMillis)
            ?.putString(KEY_LAST_USAGE_EVENT, snapshot.lastUsageEvent)
            ?.putString(KEY_LAST_SESSION_TRANSITION, snapshot.lastSessionTransition)
            ?.putString(KEY_LAST_THRESHOLD_EVENT, snapshot.lastThresholdEvent)
            ?.putString(KEY_LAST_ENGINE_EFFECT, snapshot.lastEngineEffect)
            ?.putString(KEY_LAST_PRESENTATION, snapshot.lastPresentation)
            ?.apply()
    }

    private const val PREFERENCES_NAME = "monitor_diagnostics"
    private const val KEY_LAST_MONITORED_PACKAGE = "last_monitored_package"
    private const val KEY_LAST_MONITORED_ELAPSED = "last_monitored_elapsed"
    private const val KEY_LAST_USAGE_EVENT = "last_usage_event"
    private const val KEY_LAST_SESSION_TRANSITION = "last_session_transition"
    private const val KEY_LAST_THRESHOLD_EVENT = "last_threshold_event"
    private const val KEY_LAST_ENGINE_EFFECT = "last_engine_effect"
    private const val KEY_LAST_PRESENTATION = "last_presentation"
}
