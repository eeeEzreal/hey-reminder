package com.heyreminder.app.monitor

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import java.util.concurrent.TimeUnit

interface ForegroundAppDetector {
    fun currentForegroundPackage(): String?
}

internal data class ForegroundActivityEvent(
    val eventType: Int,
    val packageName: String,
    val className: String?,
    val timestampMillis: Long,
)

internal class ForegroundActivityReducer {
    private data class ActivityKey(
        val packageName: String,
        val className: String,
    )

    private val resumedActivities = mutableMapOf<ActivityKey, Long>()

    fun accept(event: ForegroundActivityEvent) {
        val activityKey = ActivityKey(
            packageName = event.packageName,
            className = event.className.orEmpty(),
        )
        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                resumedActivities[activityKey] = event.timestampMillis
            }

            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            -> {
                if (event.className == null) {
                    resumedActivities.keys.removeAll { key ->
                        key.packageName == event.packageName
                    }
                } else {
                    resumedActivities.remove(activityKey)
                }
            }
        }
    }

    fun currentPackage(): String? = resumedActivities
        .maxByOrNull { (_, resumedAtMillis) -> resumedAtMillis }
        ?.key
        ?.packageName
}

class UsageEventsForegroundAppDetector(
    context: Context,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) : ForegroundAppDetector {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val eventReducer = ForegroundActivityReducer()
    private var lastQueryEndMillis: Long? = null

    override fun currentForegroundPackage(): String? {
        if (!powerManager.isInteractive || keyguardManager.isDeviceLocked) {
            return null
        }

        val nowMillis = wallClockMillis()
        val queryStartMillis = lastQueryEndMillis
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
        return eventReducer.currentPackage()
    }

    private companion object {
        val INITIAL_EVENT_LOOKBACK_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
        const val EVENT_QUERY_OVERLAP_MILLIS = 2_000L
    }
}
