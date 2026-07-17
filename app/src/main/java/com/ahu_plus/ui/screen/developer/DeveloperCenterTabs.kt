package com.ahu_plus.ui.screen.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.developer.DeveloperCacheCategory
import com.ahu_plus.data.developer.DeveloperCacheCategorySummary
import com.ahu_plus.data.developer.DeveloperCacheReport
import com.ahu_plus.data.developer.DeveloperCacheRepository
import com.ahu_plus.data.developer.DeveloperEventRecorder
import com.ahu_plus.data.developer.DeveloperJsonState
import com.ahu_plus.data.developer.DeveloperLogEntry
import com.ahu_plus.data.developer.DeveloperLogLevel
import com.ahu_plus.data.developer.DeveloperMaintenanceAction
import com.ahu_plus.data.developer.DeveloperMaintenanceCategory
import com.ahu_plus.data.developer.DeveloperMaintenanceRepository
import com.ahu_plus.data.developer.DeveloperMaintenanceResult
import com.ahu_plus.data.developer.DeveloperMaintenanceRisk
import com.ahu_plus.data.developer.DeveloperMaintenanceStatus
import com.ahu_plus.data.developer.DeveloperNetworkFault
import com.ahu_plus.data.developer.DeveloperPayloadAnalysis
import com.ahu_plus.data.developer.DeveloperPayloadType
import com.ahu_plus.data.developer.DeveloperPreferenceEntry
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.data.developer.NetworkDiagnosticResult
import com.ahu_plus.ui.theme.AhuShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

internal sealed interface DeveloperDataUiState {
    data object Loading : DeveloperDataUiState

    data class Ready(val report: DeveloperCacheReport) : DeveloperDataUiState

    data class Failed(val message: String) : DeveloperDataUiState
}

internal enum class DeveloperDataResultState {
    HAS_RESULTS,
    EMPTY,
    NO_MATCH,
}

internal fun developerDataResultState(
    totalEntryCount: Int,
    visibleEntryCount: Int,
): DeveloperDataResultState = when {
        visibleEntryCount > 0 -> DeveloperDataResultState.HAS_RESULTS
        totalEntryCount > 0 -> DeveloperDataResultState.NO_MATCH
        else -> DeveloperDataResultState.EMPTY
    }

@Composable
internal fun DeveloperDataTab(
    state: DeveloperDataUiState,
    onClearKey: (String) -> Unit,
    onClearCategory: (DeveloperCacheCategorySummary) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        DeveloperDataUiState.Loading -> DeveloperDataMessage(
            title = "正在读取本地数据",
            message = "请稍候",
            loading = true,
        )
        is DeveloperDataUiState.Failed -> DeveloperDataMessage(
            title = "本地数据读取失败",
            message = state.message,
            onRetry = onRetry,
        )
        is DeveloperDataUiState.Ready -> DeveloperDataContent(
            report = state.report,
            onClearKey = onClearKey,
            onClearCategory = onClearCategory,
        )
    }
}

@Composable
private fun DeveloperDataMessage(
    title: String,
    message: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        } else {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        onRetry?.let {
            Spacer(Modifier.height(12.dp))
            Button(onClick = it) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

@Composable
private fun DeveloperDataContent(
    report: DeveloperCacheReport,
    onClearKey: (String) -> Unit,
    onClearCategory: (DeveloperCacheCategorySummary) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<DeveloperCacheCategory?>(null) }
    var selectedEntry by remember { mutableStateOf<DeveloperPreferenceEntry?>(null) }
    var categoryToClear by remember { mutableStateOf<DeveloperCacheCategorySummary?>(null) }

    selectedEntry?.let { entry ->
        CacheEntryDialog(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onDelete = {
                onClearKey(entry.keyName)
                selectedEntry = null
            },
        )
    }
    categoryToClear?.let { summary ->
        ConfirmDialog(
            title = "清理 ${summary.category.displayText()}？",
            text = "将删除该分类下 ${summary.entryCount} 个 DataStore 项。登录凭据等敏感项也可能包含在内。",
            confirmText = "清理",
            destructive = true,
            onDismiss = { categoryToClear = null },
            onConfirm = {
                onClearCategory(summary)
                categoryToClear = null
            },
        )
    }

    val filtered = report.entries.filter { entry ->
        (category == null || entry.category == category) &&
            (query.isBlank() || entry.keyName.contains(query.trim(), ignoreCase = true) ||
                entry.summary.contains(query.trim(), ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataSummaryPill("总项", report.totalEntryCount.toString(), Modifier.weight(1f))
                DataSummaryPill("估算", formatBytes(report.totalEstimatedBytes), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataSummaryPill("敏感", report.sensitiveEntryCount.toString(), Modifier.weight(1f))
                DataSummaryPill("异常 JSON", report.invalidJsonCount.toString(), Modifier.weight(1f))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索 key 或摘要") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") })
            report.categories.forEach { summary ->
                FilterChip(
                    selected = category == summary.category,
                    onClick = { category = summary.category },
                    label = { Text("${summary.category.displayText()} ${summary.entryCount}") },
                )
            }
        }
        category?.let { selected ->
            report.categories.firstOrNull { it.category == selected }?.let { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${summary.entryCount} 项 · ${formatBytes(summary.estimatedBytes)} · ${summary.sensitiveEntryCount} 个敏感项",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { categoryToClear = summary }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("清理分类")
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (developerDataResultState(report.totalEntryCount, filtered.size)) {
                DeveloperDataResultState.EMPTY,
                DeveloperDataResultState.NO_MATCH -> {
                    item(key = "cache-empty-state") {
                        val hasAnyEntries = report.totalEntryCount > 0
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (hasAnyEntries) "没有匹配的数据" else "暂无缓存数据",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (hasAnyEntries) "请调整搜索词或分类筛选" else "应用产生 DataStore 数据后会显示在这里",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                DeveloperDataResultState.HAS_RESULTS -> {
                    items(filtered, key = { it.keyName }) { entry ->
                        CacheEntryRow(entry = entry, onClick = { selectedEntry = entry })
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun DataSummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(68.dp),
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CacheEntryRow(entry: DeveloperPreferenceEntry, onClick: () -> Unit) {
    val jsonColor = when (entry.jsonState) {
        DeveloperJsonState.VALID -> developerSuccessColor()
        DeveloperJsonState.INVALID -> MaterialTheme.colorScheme.error
        DeveloperJsonState.NOT_APPLICABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        ListItem(
            headlineContent = {
                Text(entry.keyName, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column {
                    Text(entry.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.category.displayText()} · ${entry.type} · ${formatBytes(entry.estimatedBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(
                    if (entry.jsonState == DeveloperJsonState.INVALID) Icons.Filled.Error else Icons.Filled.Info,
                    contentDescription = null,
                    tint = jsonColor,
                )
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    if (entry.sensitive) SmallStatusLabel("敏感", MaterialTheme.colorScheme.error)
                    if (entry.jsonState != DeveloperJsonState.NOT_APPLICABLE) {
                        Spacer(Modifier.height(4.dp))
                        SmallStatusLabel(if (entry.jsonState == DeveloperJsonState.VALID) "JSON" else "JSON 异常", jsonColor)
                    }
                }
            },
        )
    }
}

@Composable
private fun CacheEntryDialog(
    entry: DeveloperPreferenceEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDialog(
            title = "删除 ${entry.keyName}？",
            text = "该操作删除持久化 DataStore 值；当前进程的内存镜像可能继续保留，重启应用后才能完整验证冷启动行为。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.keyName, fontFamily = FontFamily.Monospace) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailLine("分类", entry.category.displayText())
                DetailLine("类型", entry.type.name)
                DetailLine("估算大小", formatBytes(entry.estimatedBytes))
                DetailLine("敏感数据", if (entry.sensitive) "是（值已隐藏）" else "否")
                DetailLine("摘要", entry.summary)
                DetailLine("JSON 状态", entry.jsonState.name)
                entry.jsonRecordCount?.let { DetailLine("JSON 记录数", it.toString()) }
            }
        },
        confirmButton = {
            if (!DeveloperCacheRepository.isProtectedKeyName(entry.keyName)) {
                TextButton(onClick = { confirmDelete = true }) { Text("删除此项") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun DeveloperToolsTab(
    app: AhuPlusApplication,
    viewModel: DeveloperCenterViewModel,
) {
    val runtime by DeveloperRuntime.state.collectAsState()
    val analysis by viewModel.payloadAnalysis.collectAsState()
    var targetHost by rememberSaveable { mutableStateOf(runtime.targetHost) }
    var latencyMillis by rememberSaveable { mutableFloatStateOf(runtime.latencyMillis.toFloat()) }
    var payload by remember { mutableStateOf("") }
    var uiScenario by rememberSaveable { mutableStateOf(DeveloperUiScenario.NORMAL) }

    LaunchedEffect(runtime.targetHost, runtime.latencyMillis) {
        targetHost = runtime.targetHost
        latencyMillis = runtime.latencyMillis.toFloat()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeveloperSectionTitle("全局网络故障模拟")
                Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("场景会作用于统一网络工厂创建的真实业务请求。", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DeveloperNetworkFault.entries.forEach { fault ->
                                FilterChip(
                                    selected = runtime.networkFault == fault,
                                    onClick = {
                                        DeveloperRuntime.configureNetworkFault(
                                            fault = fault,
                                            latencyMillis = latencyMillis.roundToLong(),
                                            targetHost = targetHost,
                                        )
                                    },
                                    label = { Text(fault.title) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = targetHost,
                            onValueChange = {
                                targetHost = it
                                if (runtime.networkFault != DeveloperNetworkFault.NONE) {
                                    DeveloperRuntime.configureNetworkFault(runtime.networkFault, latencyMillis.roundToLong(), it)
                                }
                            },
                            label = { Text("目标主机（留空为全部）") },
                            placeholder = { Text("例如 jw.ahu.edu.cn") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("延迟：${latencyMillis.roundToLong()} ms")
                        Slider(
                            value = latencyMillis,
                            onValueChange = { latencyMillis = it },
                            onValueChangeFinished = {
                                if (runtime.networkFault != DeveloperNetworkFault.NONE) {
                                    DeveloperRuntime.configureNetworkFault(runtime.networkFault, latencyMillis.roundToLong(), targetHost)
                                }
                            },
                            valueRange = 0f..10_000f,
                            steps = 19,
                        )
                        OutlinedButton(onClick = DeveloperRuntime::resetOverrides) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("恢复正常网络")
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeveloperSectionTitle("JSON / HTML 解析实验室")
                Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text("粘贴响应内容") },
                            minLines = 4,
                            maxLines = 10,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.analyzePayload(payload) }) {
                                Icon(Icons.Filled.Science, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("分析")
                            }
                            TextButton(onClick = {
                                payload = ""
                                viewModel.clearPayloadAnalysis()
                            }) { Text("清空") }
                        }
                        analysis?.let { PayloadAnalysisResult(it) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeveloperSectionTitle("UI 状态展台")
                Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DeveloperUiScenario.entries.forEach { scenario ->
                                FilterChip(
                                    selected = uiScenario == scenario,
                                    onClick = { uiScenario = scenario },
                                    label = { Text(scenario.title) },
                                )
                            }
                        }
                        UiScenarioPreview(uiScenario)
                    }
                }
            }
        }

        item { DeveloperMaintenanceSection(app = app, onCompleted = viewModel::refreshOverview) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun PayloadAnalysisResult(analysis: DeveloperPayloadAnalysis) {
    val color = when (analysis.type) {
        DeveloperPayloadType.JSON, DeveloperPayloadType.HTML -> developerSuccessColor()
        DeveloperPayloadType.INVALID_JSON -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = AhuShapes.Card, color = color.copy(alpha = 0.08f)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(analysis.summary, color = color, fontWeight = FontWeight.SemiBold)
            analysis.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (analysis.formatted.isNotBlank()) {
                Text(
                    analysis.formatted.take(4_000),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 20,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class DeveloperUiScenario(val title: String) {
    NORMAL("正常"), LOADING("加载"), EMPTY("空数据"), ERROR("错误"), STALE("旧缓存"), LONG_TEXT("长文本")
}

@Composable
private fun UiScenarioPreview(scenario: DeveloperUiScenario) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, AhuShapes.Card),
        contentAlignment = Alignment.Center,
    ) {
        when (scenario) {
            DeveloperUiScenario.NORMAL -> ListItem(
                headlineContent = { Text("高等数学") },
                supportingContent = { Text("博学南楼 B205 · 第 1-16 周") },
                leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = developerSuccessColor()) },
            )
            DeveloperUiScenario.LOADING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("正在加载…")
            }
            DeveloperUiScenario.EMPTY -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Text("暂无数据")
            }
            DeveloperUiScenario.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("加载失败：模拟服务器异常", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {}) { Text("重试") }
            }
            DeveloperUiScenario.STALE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = developerWarningColor())
                Text("正在显示 14 天前的本地缓存")
                Text("后台刷新失败", style = MaterialTheme.typography.bodySmall)
            }
            DeveloperUiScenario.LONG_TEXT -> Text(
                "这是用于检查超长课程名称、超长教室名称、异常连续字符和多行文本是否会挤压按钮或遮挡相邻内容的测试内容".repeat(2),
                modifier = Modifier.padding(12.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeveloperMaintenanceSection(
    app: AhuPlusApplication,
    onCompleted: () -> Unit,
) {
    val repository = remember(app) { DeveloperMaintenanceRepository(app) }
    val scope = rememberCoroutineScope()
    var selectedCategory by rememberSaveable { mutableStateOf<DeveloperMaintenanceCategory?>(null) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember { mutableStateOf<DeveloperMaintenanceAction?>(null) }
    var selectedResult by remember { mutableStateOf<DeveloperMaintenanceResult?>(null) }
    var results by remember { mutableStateOf<Map<String, DeveloperMaintenanceResult>>(emptyMap()) }

    fun execute(action: DeveloperMaintenanceAction) {
        if (runningId != null) return
        scope.launch {
            runningId = action.id
            DeveloperEventRecorder.record("维护工具", "开始：${action.title}")
            val result = repository.execute(action.id)
            results = results + (action.id to result)
            runningId = null
            DeveloperEventRecorder.record(
                category = "维护工具",
                message = "${result.status.displayText()}：${action.title}",
                detail = result.message,
                level = if (result.status == DeveloperMaintenanceStatus.FAILED) DeveloperLogLevel.ERROR else DeveloperLogLevel.INFO,
            )
            onCompleted()
        }
    }

    pendingConfirmation?.let { action ->
        ConfirmDialog(
            title = "执行“${action.title}”？",
            text = action.description,
            confirmText = "执行",
            destructive = action.risk == DeveloperMaintenanceRisk.HIGH,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                pendingConfirmation = null
                execute(action)
            },
        )
    }
    selectedResult?.let { result ->
        MaintenanceResultDialog(result = result, onDismiss = { selectedResult = null })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DeveloperSectionTitle("维护与危险操作")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("全部") },
            )
            DeveloperMaintenanceCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.displayText()) },
                )
            }
        }
        Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
            Column {
                val actions = repository.actions.filter {
                    selectedCategory == null || it.category == selectedCategory
                }
                actions.forEachIndexed { index, action ->
                    val result = results[action.id]
                    ListItem(
                        modifier = if (result != null) {
                            Modifier.clickable(
                                onClickLabel = "查看 ${action.title} 结果",
                                role = Role.Button,
                                onClick = { selectedResult = result },
                            )
                        } else {
                            Modifier
                        },
                        headlineContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(action.title)
                                SmallStatusLabel(action.risk.displayText(), action.risk.color())
                            }
                        },
                        supportingContent = {
                            Column {
                                Text(action.description)
                                result?.let {
                                    Text(
                                        it.message,
                                        color = it.status.color(),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            if (runningId == action.id) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = action.risk.color())
                            }
                        },
                        trailingContent = {
                            IconButton(
                                enabled = runningId == null,
                                onClick = {
                                    if (action.requiresConfirmation) pendingConfirmation = action else execute(action)
                                },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "执行 ${action.title}")
                            }
                        },
                    )
                    if (index != actions.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MaintenanceResultDialog(
    result: DeveloperMaintenanceResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.status.displayText()) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailLine("结果", result.message)
                DetailLine("耗时", "${result.durationMillis}ms")
                result.errorType?.let { DetailLine("异常", it) }
                result.exportedFilePath?.let { DetailLine("导出路径", it) }
                result.payload?.let { payload ->
                    Text(
                        payload,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun DeveloperLogsTab() {
    val entries by DeveloperEventRecorder.entries.collectAsState()
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }
    var level by rememberSaveable { mutableStateOf<DeveloperLogLevel?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = entries.asReversed().filter {
        (level == null || it.level == level) &&
            (query.isBlank() || it.category.contains(query, true) || it.message.contains(query, true) || it.detail.contains(query, true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("筛选日志") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val text = filtered.asReversed().joinToString("\n") { it.asExportLine() }
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AHU+ 开发者日志", text))
                },
            ) { Icon(Icons.Filled.ContentCopy, contentDescription = "复制当前日志") }
            IconButton(onClick = DeveloperEventRecorder::clear) {
                Icon(Icons.Filled.Delete, contentDescription = "清空日志")
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = level == null, onClick = { level = null }, label = { Text("全部 ${entries.size}") })
            DeveloperLogLevel.entries.forEach { item ->
                FilterChip(
                    selected = level == item,
                    onClick = { level = item },
                    label = { Text(item.name) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { entry -> LogEntryRow(entry) }
            if (filtered.isEmpty()) item {
                Text(
                    "暂无匹配日志",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun LogEntryRow(entry: DeveloperLogEntry) {
    val color = when (entry.level) {
        DeveloperLogLevel.INFO -> MaterialTheme.colorScheme.primary
        DeveloperLogLevel.WARNING -> developerWarningColor()
        DeveloperLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallStatusLabel(entry.level.name, color)
                Spacer(Modifier.width(8.dp))
                Text(entry.category, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(formatTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall)
            }
            Text(entry.message)
            if (entry.detail.isNotBlank()) {
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun NetworkResultDialog(result: NetworkDiagnosticResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.hostSpec.displayName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailLine("地址", result.hostSpec.redactedUrl)
                DetailLine("总状态", result.status.displayText())
                DetailLine("总耗时", result.totalDurationMillis?.let { "${it}ms" } ?: "-")
                DetailLine("DNS", "${result.dns.status.displayText()} · ${result.dns.durationMillis ?: "-"}ms")
                if (result.dns.addresses.isNotEmpty()) DetailLine("解析地址", result.dns.addresses.joinToString())
                DetailLine("HTTPS", "${result.http.status.displayText()} · ${result.http.durationMillis ?: "-"}ms")
                DetailLine("HTTP", "${result.http.httpStatusCode ?: "-"} ${result.http.httpStatusMessage.orEmpty()}")
                result.http.protocol?.let { DetailLine("协议", it) }
                result.http.tlsVersion?.let { DetailLine("TLS", it) }
                result.http.cipherSuite?.let { DetailLine("密码套件", it) }
                result.http.redirectLocation?.let { DetailLine("重定向", it) }
                result.error?.let {
                    DetailLine("错误类型", "${it.kind} / ${it.type}")
                    DetailLine("错误信息", it.message)
                }
                result.http.peerCertificates.forEachIndexed { index, cert ->
                    HorizontalDivider()
                    Text("证书 ${index + 1}", fontWeight = FontWeight.SemiBold)
                    DetailLine("Subject", cert.subject)
                    DetailLine("Issuer", cert.issuer)
                    DetailLine("SHA-256", cert.sha256Fingerprint)
                    DetailLine("有效期", "${formatDate(cert.validFromEpochMillis)} - ${formatDate(cert.validUntilEpochMillis)}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun DeveloperCacheCategory.displayText(): String = when (this) {
    DeveloperCacheCategory.AUTHENTICATION -> "认证"
    DeveloperCacheCategory.ACADEMIC -> "教务"
    DeveloperCacheCategory.CAMPUS_SERVICES -> "校园服务"
    DeveloperCacheCategory.MARKET -> "集市"
    DeveloperCacheCategory.CHAOXING -> "超星"
    DeveloperCacheCategory.WELEARN -> "WeLearn"
    DeveloperCacheCategory.C_PROGRAMMING -> "C 平台"
    DeveloperCacheCategory.PUBLIC_DATA -> "公开数据"
    DeveloperCacheCategory.APP_SETTINGS -> "应用设置"
    DeveloperCacheCategory.DEVELOPER -> "开发者"
    DeveloperCacheCategory.OTHER -> "其他"
}

private fun DeveloperMaintenanceCategory.displayText(): String = when (this) {
    DeveloperMaintenanceCategory.NOTIFICATION -> "通知"
    DeveloperMaintenanceCategory.WIDGET -> "Widget"
    DeveloperMaintenanceCategory.REMINDER -> "提醒"
    DeveloperMaintenanceCategory.STUDY_TASK -> "学习任务"
    DeveloperMaintenanceCategory.SESSION -> "会话"
    DeveloperMaintenanceCategory.RUNTIME -> "运行时"
}

private fun DeveloperMaintenanceRisk.displayText(): String = when (this) {
    DeveloperMaintenanceRisk.LOW -> "低风险"
    DeveloperMaintenanceRisk.MEDIUM -> "中风险"
    DeveloperMaintenanceRisk.HIGH -> "高风险"
}

private fun DeveloperMaintenanceStatus.displayText(): String = when (this) {
    DeveloperMaintenanceStatus.SUCCESS -> "成功"
    DeveloperMaintenanceStatus.SKIPPED -> "已跳过"
    DeveloperMaintenanceStatus.FAILED -> "失败"
}

@Composable
private fun DeveloperMaintenanceRisk.color(): Color = when (this) {
    DeveloperMaintenanceRisk.LOW -> developerSuccessColor()
    DeveloperMaintenanceRisk.MEDIUM -> developerWarningColor()
    DeveloperMaintenanceRisk.HIGH -> MaterialTheme.colorScheme.error
}

@Composable
private fun DeveloperMaintenanceStatus.color(): Color = when (this) {
    DeveloperMaintenanceStatus.SUCCESS -> developerSuccessColor()
    DeveloperMaintenanceStatus.SKIPPED -> developerWarningColor()
    DeveloperMaintenanceStatus.FAILED -> MaterialTheme.colorScheme.error
}

private fun DeveloperLogEntry.asExportLine(): String =
    "${formatTime(timestampMillis)}\t$level\t$category\t$message\t$detail"

private fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMillis))

private fun formatDate(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMillis))

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> String.format(Locale.US, "%.1f KiB", bytes / 1_024.0)
    else -> String.format(Locale.US, "%.1f MiB", bytes / 1_048_576.0)
}

@Composable
internal fun developerSuccessColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF81C784)
    } else {
        Color(0xFF2E7D32)
    }

@Composable
internal fun developerWarningColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFFFB74D)
    } else {
        Color(0xFFEF6C00)
    }
