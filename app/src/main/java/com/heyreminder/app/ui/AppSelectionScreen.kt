package com.heyreminder.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heyreminder.app.data.InstalledApp
import com.heyreminder.app.data.MonitoringMode

@Composable
fun AppSelectionRoute(
    onBack: () -> Unit,
    viewModel: AppSelectionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    AppSelectionScreen(
        state = state,
        onBack = onBack,
        onToggleApp = viewModel::toggleApp,
        onRetry = viewModel::retryLoading,
    )
}

@Composable
fun AppSelectionScreen(
    state: AppSelectionUiState,
    onBack: () -> Unit,
    onToggleApp: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        AppSelectionHeader(
            selectedCount = state.selectedCount,
            monitoringMode = state.monitoringMode,
            onBack = onBack,
        )
        Text(
            text = when (state.monitoringMode) {
                MonitoringMode.BLACKLIST ->
                    "选择需要监控的 App，选择结果会自动保存在本机。"
                MonitoringMode.WHITELIST ->
                    "选择不需要监控的 App，其他可启动 App 会被提醒。"
            },
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> LoadingContent()
            state.loadFailed -> LoadFailedContent(onRetry = onRetry)
            state.apps.isEmpty() -> EmptyContent()
            else -> AppList(
                apps = state.apps,
                selectedPackages = state.selectedPackages,
                onToggleApp = onToggleApp,
            )
        }
    }
}

@Composable
private fun AppSelectionHeader(
    selectedCount: Int,
    monitoringMode: MonitoringMode,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("返回")
        }
        Text(
            text = when (monitoringMode) {
                MonitoringMode.BLACKLIST -> "黑名单 App"
                MonitoringMode.WHITELIST -> "白名单 App"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "已选 $selectedCount 个",
            modifier = Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AppList(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    onToggleApp: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = apps,
            key = InstalledApp::packageName,
        ) { app ->
            AppSelectionRow(
                app = app,
                isSelected = app.packageName in selectedPackages,
                onClick = { onToggleApp(app.packageName) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 88.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = "${app.displayName} 图标",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.displayName.firstOrNull()?.toString().orEmpty().ifBlank { "?" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadFailedContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法读取 App 列表")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "没有找到可选择的 App",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
