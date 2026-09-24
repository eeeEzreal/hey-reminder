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
import com.heyreminder.app.data.MonitoringPauseRepository
import com.heyreminder.app.data.ReminderStatsRepository
import com.heyreminder.app.data.ReminderSettings
import com.heyreminder.app.data.ReminderSettingsRepository
import com.heyreminder.app.data.UsageAccessRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsageMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appSelectionRepository: AppSelectionRepository
    private lateinit var usageAccessRepository: UsageAccessRepository
    private lateinit var monitoringPauseRepository: MonitoringPauseRepository
    private lateinit var reminderSettingsRepository: ReminderSettingsRepository
    private lateinit var foregroundAppDetector: ForegroundAppDetector
    private lateinit var reminderCoordinator: ReminderNotificationCoordinator
    private lateinit var reminderNotifier: UsageReminderNotifier
    private var currentSettings = ReminderSettings()
    private var timingConfig = currentSettings.toReminderTimingConfig()
    private var usageTracker = ContinuousUsageTracker(timingConfig.reminderIntervalMillis)
    private var reminderActionReducer = ReminderActionReducer(usageTracker, timingConfig)
    private val stateMutex = Mutex()
    private val settingsReady = CompletableDeferred<Unit>()

    private var selectedPackages: Set<String> = emptySet()
    private var launchablePackages: Set<String> = emptySet()
    private var monitoredPackages: Set<String> = emptySet()
    private var monitorJob: Job? = null
    private var usageState = ContinuousUsageState()
    private var pauseUntilEpochMillis = 0L

    override fun onCreate() {
        super.onCreate()
        appSelectionRepository = AppSelectionRepository(this)
        usageAccessRepository = UsageAccessRepository(this)
        monitoringPauseRepository = MonitoringPauseRepository(this)
        reminderSettingsRepository = ReminderSettingsRepository(this)
        foregroundAppDetector = UsageEventsForegroundAppDetector(this)
        val reminderStatsRepository = ReminderStatsRepository(this)
        reminderNotifier = UsageReminderNotifier(this, timingConfig)
        reminderCoordinator = ReminderNotificationCoordinator(
            notificationGateway = reminderNotifier,
            recordTriggeredReminder = reminderStatsRepository::recordTriggeredReminder,
        )
        createNotificationChannel()
        startAsForegroundService()

        serviceScope.launch {
            appSelectionRepository.selectedPackages.collectLatest { packages ->
                stateMutex.withLock {
                    selectedPackages = packages
                    updateMonitoredPackages()
                }
            }
        }
        serviceScope.launch {
            val packages = runCatching { appSelectionRepository.loadInstalledApps() }
                .onFailure { error -> Log.e(TAG, "Unable to load launchable apps", error) }
                .getOrDefault(emptyList())
                .map { app -> app.packageName }
                .toSet()
            stateMutex.withLock {
                launchablePackages = packages
                updateMonitoredPackages()
            }
        }
        serviceScope.launch {
            reminderSettingsRepository.settings.collectLatest { settings ->
                applySettings(settings)
                settingsReady.complete(Unit)
                if (!settings.isReminderEnabled) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ReminderNotificationActionContract.parse(intent)?.let { command ->
            serviceScope.launch {
                runCatching {
                    handleReminderAction(command)
                }.onFailure { error ->
                    Log.e(TAG, "Unable to handle reminder action", error)
                }
            }
        }
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch {
                monitorUsage()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob = null
        if (::reminderNotifier.isInitialized) {
            reminderNotifier.dismiss()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorUsage() {
        settingsReady.await()
        stateMutex.withLock {
            pauseUntilEpochMillis = monitoringPauseRepository.currentPauseUntilEpochMillis()
        }
        while (serviceScope.isActive) {
            if (isMonitoringPaused()) {
                delay(POLL_INTERVAL_MILLIS)
                continue
            }
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
            val (previousState, update) = stateMutex.withLock {
                val previous = usageState
                val currentUpdate = if (pauseUntilEpochMillis > System.currentTimeMillis()) {
                    ContinuousUsageUpdate(state = ContinuousUsageState())
                } else {
                    usageTracker.update(
                        previousState = previous,
                        foregroundPackage = eligibleForegroundPackage,
                        monitoredPackages = monitoredPackages,
                        nowElapsedMillis = SystemClock.elapsedRealtime(),
                    )
                }
                usageState = currentUpdate.state
                previous to currentUpdate
            }
            logStateTransition(previousState, update)
            update.event?.let { event ->
                runCatching {
                    reminderCoordinator.onReminderConditionReached(event)
                }.onSuccess { notificationTriggered ->
                    Log.i(
                        TAG,
                        "reminder_notification_triggered package=${event.packageName} " +
                            "success=$notificationTriggered",
                    )
                    if (!notificationTriggered) {
                        scheduleReminderDeliveryRetry(event)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Unable to deliver or record reminder notification", error)
                    scheduleReminderDeliveryRetry(event)
                }
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun applySettings(settings: ReminderSettings) {
        stateMutex.withLock {
            if (currentSettings == settings) return@withLock

            currentSettings = settings
            timingConfig = settings.toReminderTimingConfig()
            usageTracker = ContinuousUsageTracker(timingConfig.reminderIntervalMillis)
            reminderActionReducer = ReminderActionReducer(usageTracker, timingConfig)
            reminderNotifier.updateTimingConfig(timingConfig)
            usageState = ContinuousUsageState()
            reminderNotifier.dismiss()
            updateMonitoredPackages()
        }
    }

    private fun updateMonitoredPackages() {
        monitoredPackages = MonitoredAppResolver.resolve(
            monitoringMode = currentSettings.monitoringMode,
            selectedPackages = selectedPackages,
            launchablePackages = launchablePackages,
        )
        usageState = ContinuousUsageState()
    }

    private suspend fun isMonitoringPaused(): Boolean = stateMutex.withLock {
        val nowEpochMillis = System.currentTimeMillis()
        if (pauseUntilEpochMillis > nowEpochMillis) {
            usageState = ContinuousUsageState()
            return@withLock true
        }
        if (pauseUntilEpochMillis > 0L) {
            pauseUntilEpochMillis = 0L
            monitoringPauseRepository.clearPause()
            Log.i(TAG, "monitoring_pause_ended")
        }
        false
    }

    private suspend fun handleReminderAction(command: ReminderActionCommand) {
        reminderNotifier.dismiss()
        val accepted = stateMutex.withLock {
            val result = reminderActionReducer.reduce(
                previousState = usageState,
                action = command.action,
                target = command.target,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
                nowEpochMillis = System.currentTimeMillis(),
            ) ?: return@withLock false
            result.pauseUntilEpochMillis?.let { pauseUntil ->
                pauseUntilEpochMillis = pauseUntil
                monitoringPauseRepository.pauseUntil(pauseUntil)
            }
            usageState = result.usageState
            true
        }
        Log.i(
            TAG,
            "reminder_action action=${command.action.name} accepted=$accepted " +
                "package=${command.target.packageName}",
        )
    }

    private suspend fun scheduleReminderDeliveryRetry(event: ReminderConditionReachedEvent) {
        val rescheduled = stateMutex.withLock {
            usageTracker.scheduleNextReminder(
                previousState = usageState,
                target = ReminderActionTarget(
                    packageName = event.packageName,
                    sessionStartedAtElapsedMillis = event.sessionStartedAtElapsedMillis,
                    reminderDueAtElapsedMillis = event.reminderDueAtElapsedMillis,
                ),
                delayMillis = REMINDER_DELIVERY_RETRY_MILLIS,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )?.also { retryState ->
                usageState = retryState
            }
        }
        if (rescheduled != null) {
            Log.w(
                TAG,
                "reminder_delivery_retry_scheduled package=${event.packageName} " +
                    "delay_ms=$REMINDER_DELIVERY_RETRY_MILLIS",
            )
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
        private const val REMINDER_DELIVERY_RETRY_MILLIS = 30_000L
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
