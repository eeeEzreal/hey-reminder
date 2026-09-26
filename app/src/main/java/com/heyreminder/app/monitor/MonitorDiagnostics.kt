package com.heyreminder.app.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

/** Process-local diagnostics for the debug build. Historical fields survive opening Hey! itself. */
object MonitorDiagnostics {
    private val mutableSnapshots = MutableStateFlow(MonitorDiagnosticSnapshot())
    val snapshots: StateFlow<MonitorDiagnosticSnapshot> = mutableSnapshots.asStateFlow()

    internal fun update(block: (MonitorDiagnosticSnapshot) -> MonitorDiagnosticSnapshot) {
        mutableSnapshots.update(block)
    }

    internal fun resetForServiceStart(reminderIntervalMillis: Long) {
        mutableSnapshots.value = MonitorDiagnosticSnapshot(
            updatedAtEpochMillis = System.currentTimeMillis(),
            reminderIntervalMillis = reminderIntervalMillis,
        )
    }
}
