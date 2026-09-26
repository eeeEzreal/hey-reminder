package com.heyreminder.app.monitor

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import java.util.concurrent.TimeUnit

internal enum class ForegroundDetectionStatus {
    CONFIRMED,
    ACTIVITY_TRANSITION,
    NO_FOREGROUND_APP,
    SCREEN_OFF_OR_LOCKED,
    USAGE_ACCESS_DENIED,
    DETECTION_FAILED,
}

internal data class ForegroundDetection(
    val packageName: String?,
    val status: ForegroundDetectionStatus,
    val lastEvent: ForegroundActivityEvent? = null,
)

internal interface ForegroundAppDetector {
    fun detectForegroundApp(): ForegroundDetection
}

internal data class ForegroundActivityEvent(
    val eventType: Int,
    val packageName: String,
    val className: String?,
    val timestampMillis: Long,
) {
    fun diagnosticLabel(): String =
        "${eventType.diagnosticName()}:$packageName/${className ?: "?"}@$timestampMillis"
}

/**
 * Reduces UsageEvents without assuming that an Activity pause and the next resume are delivered
 * in the same query. Some devices split those events across polls during an in-app Activity
 * transition; treating that short gap as an app exit resets a valid continuous-use session.
 */
internal class ForegroundActivityReducer(
    private val transitionGraceMillis: Long = DEFAULT_ACTIVITY_TRANSITION_GRACE_MILLIS,
) {
    private data class ActivityKey(
        val packageName: String,
        val className: String,
    )

    private data class EventSignature(
        val eventType: Int,
        val packageName: String,
        val className: String?,
    )

    private var currentActivity: ActivityKey? = null
    private var confirmedPackage: String? = null
    private var transitionPackage: String? = null
    private var transitionStartedAtMillis: Long? = null
    private var latestTimestampMillis = Long.MIN_VALUE
    private val signaturesAtLatestTimestamp = mutableSetOf<EventSignature>()

    var lastEvent: ForegroundActivityEvent? = null
        private set

    init {
        require(transitionGraceMillis >= 0L)
    }

    fun accept(event: ForegroundActivityEvent) {
        if (!markAsNewEvent(event)) return

        val activityKey = ActivityKey(
            packageName = event.packageName,
            className = event.className.orEmpty(),
        )
        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                currentActivity = activityKey
                confirmedPackage = event.packageName
                transitionPackage = null
                transitionStartedAtMillis = null
                lastEvent = event
            }

            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            -> {
                val current = currentActivity
                val pausesCurrentActivity = current?.packageName == event.packageName &&
                    (event.className == null || current == activityKey)
                if (pausesCurrentActivity) {
                    transitionPackage = confirmedPackage
                    transitionStartedAtMillis = event.timestampMillis
                    currentActivity = null
                    confirmedPackage = null
                    lastEvent = event
                }
            }
        }
    }

    fun currentDetection(nowMillis: Long): ForegroundDetection {
        confirmedPackage?.let { packageName ->
            return ForegroundDetection(
                packageName = packageName,
                status = ForegroundDetectionStatus.CONFIRMED,
                lastEvent = lastEvent,
            )
        }

        val transitionStartedAt = transitionStartedAtMillis
        val transition = transitionPackage
        if (
            transition != null &&
            transitionStartedAt != null &&
            nowMillis >= transitionStartedAt &&
            nowMillis - transitionStartedAt <= transitionGraceMillis
        ) {
            return ForegroundDetection(
                packageName = transition,
                status = ForegroundDetectionStatus.ACTIVITY_TRANSITION,
                lastEvent = lastEvent,
            )
        }

        return ForegroundDetection(
            packageName = null,
            status = ForegroundDetectionStatus.NO_FOREGROUND_APP,
            lastEvent = lastEvent,
        )
    }

    fun clear() {
        currentActivity = null
        confirmedPackage = null
        transitionPackage = null
        transitionStartedAtMillis = null
    }

    private fun markAsNewEvent(event: ForegroundActivityEvent): Boolean {
        if (event.timestampMillis < latestTimestampMillis) return false
        if (event.timestampMillis > latestTimestampMillis) {
            latestTimestampMillis = event.timestampMillis
            signaturesAtLatestTimestamp.clear()
        }
        return signaturesAtLatestTimestamp.add(
            EventSignature(event.eventType, event.packageName, event.className),
        )
    }

    companion object {
        const val DEFAULT_ACTIVITY_TRANSITION_GRACE_MILLIS = 3_000L
    }
}

internal class UsageEventsForegroundAppDetector(
    context: Context,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) : ForegroundAppDetector {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val eventReducer = ForegroundActivityReducer()
    private var lastQueryEndMillis: Long? = null

    override fun detectForegroundApp(): ForegroundDetection {
        val nowMillis = wallClockMillis()
        if (!powerManager.isInteractive || keyguardManager.isDeviceLocked) {
            eventReducer.clear()
            lastQueryEndMillis = nowMillis
            return ForegroundDetection(
                packageName = null,
                status = ForegroundDetectionStatus.SCREEN_OFF_OR_LOCKED,
                lastEvent = eventReducer.lastEvent,
            )
        }

        val previousQueryEnd = lastQueryEndMillis?.takeIf { it <= nowMillis }
        val queryStartMillis = previousQueryEnd
            ?.minus(EVENT_QUERY_OVERLAP_MILLIS)
            ?.coerceAtLeast(nowMillis - INITIAL_EVENT_LOOKBACK_MILLIS)
            ?: (nowMillis - INITIAL_EVENT_LOOKBACK_MILLIS)

        val usageEvents = usageStatsManager.queryEvents(queryStartMillis, nowMillis)
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val packageName = event.packageName ?: continue
            eventReducer.accept(
                ForegroundActivityEvent(
                    eventType = event.eventType,
                    packageName = packageName,
                    className = event.className,
                    timestampMillis = event.timeStamp,
                ),
            )
        }
        lastQueryEndMillis = nowMillis
        return eventReducer.currentDetection(nowMillis)
    }

    private companion object {
        val INITIAL_EVENT_LOOKBACK_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
        const val EVENT_QUERY_OVERLAP_MILLIS = 2_000L
    }
}

private fun Int.diagnosticName(): String = when (this) {
    UsageEvents.Event.ACTIVITY_RESUMED -> "RESUMED"
    UsageEvents.Event.ACTIVITY_PAUSED -> "PAUSED"
    UsageEvents.Event.ACTIVITY_STOPPED -> "STOPPED"
    else -> toString()
}
