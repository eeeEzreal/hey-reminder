package com.heyreminder.app.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.heyreminder.app.MainActivity
import com.heyreminder.app.R
import com.heyreminder.app.data.AppSelectionRepository
import com.heyreminder.app.data.UsageAccessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appSelectionRepository: AppSelectionRepository
    private lateinit var usageAccessRepository: UsageAccessRepository
    private lateinit var foregroundAppDetector: ForegroundAppDetector
    private val usageTracker = ContinuousUsageTracker()

    @Volatile
    private var selectedPackages: Set<String> = emptySet()
    private var monitorJob: Job? = null
    private var usageState = ContinuousUsageState()

    override fun onCreate() {
        super.onCreate()
        appSelectionRepository = AppSelectionRepository(this)
        usageAccessRepository = UsageAccessRepository(this)
        foregroundAppDetector = UsageEventsForegroundAppDetector(this)
        createNotificationChannel()
        startAsForegroundService()

        serviceScope.launch {
            appSelectionRepository.selectedPackages.collectLatest { packages ->
                selectedPackages = packages
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch {
                monitorUsage()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorUsage() {
        while (serviceScope.isActive) {
            val foregroundPackage = if (usageAccessRepository.hasUsageAccess()) {
                runCatching { foregroundAppDetector.currentForegroundPackage() }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to determine the foreground app", error)
                    }
                    .getOrNull()
            } else {
                null
            }
            val eligibleForegroundPackage = foregroundPackage?.takeUnless { packageName ->
                packageName == this.packageName || packageName == SYSTEM_UI_PACKAGE
            }
            val previousState = usageState
            val update = usageTracker.update(
                previousState = previousState,
                foregroundPackage = eligibleForegroundPackage,
                selectedPackages = selectedPackages,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )
            usageState = update.state
            logStateTransition(previousState, update)
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun logStateTransition(
        previousState: ContinuousUsageState,
        update: ContinuousUsageUpdate,
    ) {
        val currentState = update.state
        if (previousState.packageName != currentState.packageName) {
            if (previousState.packageName != null) {
                Log.i(TAG, "session_reset package=${previousState.packageName}")
            }
            if (currentState.packageName != null) {
                Log.i(TAG, "session_started package=${currentState.packageName}")
            }
        }
        update.event?.let { event ->
            Log.i(
                TAG,
                "reminder_condition_reached package=${event.packageName} " +
                    "duration_ms=${event.continuousDurationMillis}",
            )
        }
    }

    private fun startAsForegroundService() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            MONITOR_NOTIFICATION_ID,
            notification,
            serviceType,
        )
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.monitor_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "HeyUsageMonitor"
        private const val MONITOR_CHANNEL_ID = "usage_monitor"
        private const val MONITOR_NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UsageMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
