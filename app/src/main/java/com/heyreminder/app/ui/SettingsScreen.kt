package com.heyreminder.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heyreminder.app.data.MonitoringMode
import com.heyreminder.app.data.ReminderSettings

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
    BackHandler(onBack = onBack)
    SettingsScreen(
        settings = state.settings,
        testReminderResult = state.testReminderResult,
        onBack = onBack,
        onManageApps = onManageApps,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onMonitoringModeChange = viewModel::setMonitoringMode,
        onReminderMinutesChange = viewModel::setReminderMinutes,
        onSnoozeMinutesChange = viewModel::setSnoozeMinutes,
        onPauseMinutesChange = viewModel::setPauseMinutes,
        onSendTestReminder = viewModel::sendTestReminder,
    )
}

@Composable
fun SettingsScreen(
    settings: ReminderSettings,
    testReminderResult: TestReminderResult?,
    onBack: () -> Unit,
    onManageApps: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onMonitoringModeChange: (MonitoringMode) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onPauseMinutesChange: (Int) -> Unit,
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
            Text("立即测试提醒")
        }
        testReminderResult?.let { result ->
            Text(
                text = when (result) {
                    TestReminderResult.SENT -> "测试提醒已发送，请检查屏幕顶部和通知栏。"
                    TestReminderResult.UNAVAILABLE ->
                        "测试提醒发送失败，请返回首页检查通知权限和提醒渠道。"
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result == TestReminderResult.SENT) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
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
