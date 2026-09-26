package com.heyreminder.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heyreminder.app.BuildConfig
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.OverlayPermissionRepository
import com.heyreminder.app.data.ReminderSettings
import com.heyreminder.app.monitor.MonitorDiagnosticSnapshot

private val REMINDER_MINUTE_OPTIONS = listOf(5, 10, 15, 20, 30)
private val SNOOZE_MINUTE_OPTIONS = listOf(5, 10, 15, 20, 30)
private val PAUSE_MINUTE_OPTIONS = listOf(15, 30, 60)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onManageApps: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {}
    BackHandler(onBack = onBack)
    SettingsScreen(
        settings = state.settings,
        testReminderResult = state.testReminderResult,
        diagnostics = state.diagnostics,
        onBack = onBack,
        onManageApps = onManageApps,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onMonitoringModeChange = viewModel::setMonitoringMode,
        onReminderMinutesChange = viewModel::setReminderMinutes,
        onSnoozeMinutesChange = viewModel::setSnoozeMinutes,
        onPauseMinutesChange = viewModel::setPauseMinutes,
        onDebug30SecondReminderChange = viewModel::setDebug30SecondReminderEnabled,
        onSendTestReminder = {
            if (
                viewModel.showTestStrongReminder() ==
                TestReminderResult.NEEDS_OVERLAY_PERMISSION
            ) {
                overlayPermissionLauncher.launch(
                    OverlayPermissionRepository(context).createSettingsIntent(),
                )
            }
        },
    )
}

@Composable
fun SettingsScreen(
    settings: ReminderSettings,
    testReminderResult: TestReminderResult?,
    diagnostics: MonitorDiagnosticSnapshot,
    onBack: () -> Unit,
    onManageApps: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onMonitoringModeChange: (MonitoringMode) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onPauseMinutesChange: (Int) -> Unit,
    onDebug30SecondReminderChange: (Boolean) -> Unit,
    onSendTestReminder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        SettingsHeader(onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        ReminderSwitchCard(
            enabled = settings.isReminderEnabled,
            onEnabledChange = onReminderEnabledChange,
        )
        OutlinedButton(
            onClick = onSendTestReminder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("测试强提醒")
        }
        testReminderResult?.let { result ->
            Text(
                text = when (result) {
                    TestReminderResult.SHOWN -> "强提醒已显示，点击任一操作即可关闭。"
                    TestReminderResult.NEEDS_OVERLAY_PERMISSION ->
                        "请先允许 Hey! 显示在其他应用上层，然后再次测试。"
                    TestReminderResult.FAILED -> "强提醒显示失败，请重新授权后再试。"
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result == TestReminderResult.SHOWN) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (BuildConfig.DEBUG) {
            DebugReminderCard(
                enabled = settings.isDebug30SecondReminderEnabled,
                onEnabledChange = onDebug30SecondReminderChange,
            )
            DebugDiagnosticsCard(diagnostics)
        }
        SettingsSectionTitle("监控模式")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = settings.monitoringMode == MonitoringMode.BLACKLIST,
                onClick = { onMonitoringModeChange(MonitoringMode.BLACKLIST) },
                label = { Text("黑名单") },
            )
            FilterChip(
                selected = settings.monitoringMode == MonitoringMode.WHITELIST,
                onClick = { onMonitoringModeChange(MonitoringMode.WHITELIST) },
                label = { Text("白名单") },
            )
        }
        Text(
            text = if (settings.monitoringMode == MonitoringMode.BLACKLIST) {
                "名单中的 App 不监控，其他可启动 App 会被监控。"
            } else {
                "只监控名单中的 App。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onManageApps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("管理名单 App")
        }
        MinuteSetting(
            title = "连续使用提醒",
            description = "连续使用达到该时间后发送提醒。",
            selectedMinutes = settings.reminderMinutes,
            options = REMINDER_MINUTE_OPTIONS,
            onSelected = onReminderMinutesChange,
        )
        MinuteSetting(
            title = "延迟提醒时间",
            description = "点击“再给我几分钟”后使用。",
            selectedMinutes = settings.snoozeMinutes,
            options = SNOOZE_MINUTE_OPTIONS,
            onSelected = onSnoozeMinutesChange,
        )
        MinuteSetting(
            title = "临时暂停时间",
            description = "点击暂停后，Hey! 在这段时间内不监控提醒。",
            selectedMinutes = settings.pauseMinutes,
            options = PAUSE_MINUTE_OPTIONS,
            onSelected = onPauseMinutesChange,
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DebugReminderCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("30 秒端到端测试", fontWeight = FontWeight.SemiBold)
                Text(
                    "仅 Debug APK 生效；开启后真实监控链路会在 30 秒到时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun DebugDiagnosticsCard(diagnostics: MonitorDiagnosticSnapshot) {
    SettingsSectionTitle("监控链路诊断（Debug）")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DiagnosticLine("Usage Access", diagnostics.usageAccessGranted.toString())
            DiagnosticLine(
                "前台识别",
                "${diagnostics.detectionStatus} · ${diagnostics.rawForegroundPackage ?: "无"}",
            )
            DiagnosticLine(
                "监控命中",
                "${diagnostics.isForegroundPackageMonitored} " +
                    "(${diagnostics.monitoredPackageCount} 个目标)",
            )
            DiagnosticLine(
                "连续会话",
                "${diagnostics.sessionPackage ?: "无"} · " +
                    "${diagnostics.sessionElapsedMillis / 1_000} 秒 / " +
                    "${diagnostics.reminderIntervalMillis / 1_000} 秒",
            )
            DiagnosticLine(
                "最近目标会话",
                "${diagnostics.lastMonitoredSessionPackage ?: "无"} · " +
                    "${diagnostics.lastMonitoredSessionElapsedMillis / 1_000} 秒",
            )
            DiagnosticLine("Engine", diagnostics.enginePhase)
            DiagnosticLine("最近 UsageEvent", diagnostics.lastUsageEvent)
            DiagnosticLine("最近会话变化", diagnostics.lastSessionTransition)
            DiagnosticLine("最近到时事件", diagnostics.lastThresholdEvent)
            DiagnosticLine("最近 Engine Effect", diagnostics.lastEngineEffect)
            DiagnosticLine("最近展示结果", diagnostics.lastPresentation)
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("返回")
        }
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReminderSwitchCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hey! 提醒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) "监控和提醒已开启" else "监控和提醒已关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun MinuteSetting(
    title: String,
    description: String,
    selectedMinutes: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit,
) {
    SettingsSectionTitle(title)
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelected(minutes) },
                label = { Text("$minutes 分钟") },
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
