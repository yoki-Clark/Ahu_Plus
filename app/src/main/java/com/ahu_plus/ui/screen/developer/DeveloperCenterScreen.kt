package com.ahu_plus.ui.screen.developer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.developer.DeveloperCacheReport
import com.ahu_plus.data.developer.DeveloperEventRecorder
import com.ahu_plus.data.developer.DeveloperLogLevel
import com.ahu_plus.data.developer.DeveloperModuleTest
import com.ahu_plus.data.developer.DeveloperNetworkFault
import com.ahu_plus.data.developer.DeveloperOverview
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.data.developer.DeveloperStatusItem
import com.ahu_plus.data.developer.DeveloperStatusKind
import com.ahu_plus.data.developer.DeveloperTestCategory
import com.ahu_plus.data.developer.DeveloperTestStatus
import com.ahu_plus.data.developer.NetworkDiagnosticCategory
import com.ahu_plus.data.developer.NetworkDiagnosticHosts
import com.ahu_plus.data.developer.NetworkDiagnosticResult
import com.ahu_plus.data.developer.NetworkDiagnosticStatus
import com.ahu_plus.data.developer.NetworkHostSpec
import com.ahu_plus.ui.theme.AhuShapes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DeveloperTab(val title: String) {
    OVERVIEW("概览"),
    MODULES("模块"),
    NETWORK("网络"),
    DATA("数据"),
    TOOLS("模拟"),
    LOGS("日志"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AhuPlusApplication
    val factory = remember(app) { DeveloperCenterViewModel.Factory(app) }
    val viewModel: DeveloperCenterViewModel = viewModel(factory = factory)
    val scope = rememberCoroutineScope()

    val overview by viewModel.overview.collectAsState()
    val tests by viewModel.moduleTests.collectAsState()
    val cacheReport by viewModel.cacheReport.collectAsState()
    val networkResults by viewModel.networkResults.collectAsState()
    val runningNetworkIds by viewModel.runningNetworkIds.collectAsState()
    val runningModules by viewModel.runningAllModules.collectAsState()
    val runningNetworkBatch by viewModel.runningNetworkBatch.collectAsState()
    val runtime by DeveloperRuntime.state.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var exportRunning by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelAllOperations() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!exportRunning) {
                                scope.launch {
                                    exportRunning = true
                                    try {
                                        val report = viewModel.buildDiagnosticReport()
                                        shareDiagnosticReport(context, report)
                                    } catch (error: Throwable) {
                                        DeveloperEventRecorder.record(
                                            category = "诊断报告",
                                            message = "导出失败",
                                            detail = error.javaClass.simpleName,
                                            level = DeveloperLogLevel.ERROR,
                                        )
                                        Toast.makeText(
                                            context,
                                            "导出诊断报告失败",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        exportRunning = false
                                    }
                                }
                            }
                        },
                    ) {
                        if (exportRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Share, contentDescription = "导出诊断报告")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (runtime.hasActiveOverrides) {
                ActiveOverrideBanner(
                    runtime.networkFault,
                    runtime.targetHost,
                    onReset = DeveloperRuntime::resetOverrides,
                )
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                DeveloperTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                    )
                }
            }

            when (DeveloperTab.entries[selectedTabIndex]) {
                DeveloperTab.OVERVIEW -> DeveloperOverviewTab(
                    overview = overview,
                    tests = tests,
                    cacheReport = cacheReport,
                    networkResults = networkResults,
                    isRunningTests = runningModules,
                    onRefresh = viewModel::refreshOverview,
                    onRunTests = viewModel::runAllModuleTests,
                    onCancelTests = viewModel::cancelAllModuleTests,
                )
                DeveloperTab.MODULES -> DeveloperModulesTab(
                    tests = tests,
                    isRunningAll = runningModules,
                    onRun = viewModel::runModuleTest,
                    onCancel = viewModel::cancelModuleTest,
                    onRunAll = viewModel::runAllModuleTests,
                    onCancelAll = viewModel::cancelAllModuleTests,
                )
                DeveloperTab.NETWORK -> DeveloperNetworkTab(
                    results = networkResults,
                    runningIds = runningNetworkIds,
                    runningBatch = runningNetworkBatch,
                    onRun = viewModel::runNetwork,
                    onCancel = viewModel::cancelNetwork,
                    onRunCore = viewModel::runCoreNetworkBatch,
                    onRunCategory = viewModel::runNetworkCategory,
                    onCancelBatch = viewModel::cancelNetworkBatch,
                )
                DeveloperTab.DATA -> DeveloperDataTab(
                    report = cacheReport,
                    onClearKey = viewModel::clearCacheKey,
                    onClearCategory = viewModel::clearCacheCategory,
                )
                DeveloperTab.TOOLS -> DeveloperToolsTab(
                    app = app,
                    viewModel = viewModel,
                )
                DeveloperTab.LOGS -> DeveloperLogsTab()
            }
        }
    }
}

@Composable
private fun ActiveOverrideBanner(
    fault: DeveloperNetworkFault,
    targetHost: String,
    onReset: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text("测试覆盖正在生效", fontWeight = FontWeight.SemiBold)
                Text(
                    "${fault.title} · ${targetHost.ifBlank { "全部主机" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onReset) { Text("恢复") }
        }
    }
}

@Composable
private fun DeveloperOverviewTab(
    overview: DeveloperOverview,
    tests: List<DeveloperModuleTest>,
    cacheReport: DeveloperCacheReport,
    networkResults: Map<String, NetworkDiagnosticResult>,
    isRunningTests: Boolean,
    onRefresh: () -> Unit,
    onRunTests: () -> Unit,
    onCancelTests: () -> Unit,
) {
    val passed = tests.count { it.status == DeveloperTestStatus.PASSED }
    val failed = tests.count { it.status in setOf(DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT) }
    val networkFailed = networkResults.values.count { it.status == NetworkDiagnosticStatus.FAILED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryMetric("模块通过", "$passed/${tests.size}", if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryMetric("网络结果", "${networkResults.size}", if (networkFailed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                SummaryMetric("缓存项", cacheReport.totalEntryCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (isRunningTests) onCancelTests else onRunTests) {
                    Icon(if (isRunningTests) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunningTests) "停止体检" else "运行全部模块体检")
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("刷新状态")
                }
            }
        }
        if (isRunningTests) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        item { StatusSection("构建信息", overview.build) }
        item { StatusSection("设备环境", overview.device) }
        item { StatusSection("权限与系统能力", overview.permissions) }
        item { StatusSection("认证与会话", overview.sessions) }
        item { StatusSection("运行状态", overview.runtime) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AhuShapes.Card,
        color = color.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusSection(title: String, items: List<DeveloperStatusItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DeveloperSectionTitle(title)
        Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
            Column {
                items.forEachIndexed { index, item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = item.detail.takeIf(String::isNotBlank)?.let { detail ->
                            { Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        },
                        trailingContent = {
                            Text(
                                item.value,
                                color = statusColor(item.kind),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = { StatusIcon(item.kind) },
                    )
                    if (index != items.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeveloperModulesTab(
    tests: List<DeveloperModuleTest>,
    isRunningAll: Boolean,
    onRun: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRunAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<DeveloperTestCategory?>(null) }
    val filtered = tests.filter { selectedCategory == null || it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("全部") })
            DeveloperTestCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.displayText()) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = if (isRunningAll) onCancelAll else onRunAll) {
                Icon(if (isRunningAll) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunningAll) "停止" else "全部运行")
            }
            Text(
                "${tests.count { it.status == DeveloperTestStatus.PASSED }} 通过 · " +
                    "${tests.count { it.status == DeveloperTestStatus.FAILED }} 失败 · " +
                    "${tests.count { it.status == DeveloperTestStatus.SKIPPED }} 跳过",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRunningAll) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { test ->
                ModuleTestRow(
                    test = test,
                    onRun = { onRun(test.id) },
                    onCancel = { if (isRunningAll) onCancelAll() else onCancel(test.id) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ModuleTestRow(
    test: DeveloperModuleTest,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(shape = AhuShapes.Card, color = MaterialTheme.colorScheme.surface) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(test.title, fontWeight = FontWeight.Medium)
                    SmallStatusLabel(test.status.displayText(), test.status.color())
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(test.description)
                    test.result?.let { Text(it, color = test.status.color(), style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "${test.category.displayText()} · ${test.risk.name}" +
                            (test.durationMillis?.let { " · ${it}ms" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = { ModuleStatusIcon(test.status) },
            trailingContent = {
                if (test.status == DeveloperTestStatus.RUNNING) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "取消 ${test.title}")
                    }
                } else {
                    IconButton(onClick = onRun) { Icon(Icons.Filled.PlayArrow, contentDescription = "运行 ${test.title}") }
                }
            },
        )
    }
}

@Composable
private fun DeveloperNetworkTab(
    results: Map<String, NetworkDiagnosticResult>,
    runningIds: Set<String>,
    runningBatch: Boolean,
    onRun: (NetworkHostSpec) -> Unit,
    onCancel: (String) -> Unit,
    onRunCore: () -> Unit,
    onRunCategory: (NetworkDiagnosticCategory) -> Unit,
    onCancelBatch: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<NetworkDiagnosticCategory?>(NetworkDiagnosticCategory.AHU) }
    var selectedResult by remember { mutableStateOf<NetworkDiagnosticResult?>(null) }
    val hosts = NetworkDiagnosticHosts.all.filter { category == null || it.category == category }

    selectedResult?.let { result ->
        NetworkResultDialog(result = result, onDismiss = { selectedResult = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") })
            NetworkDiagnosticCategory.entries.forEach { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item.displayText()) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = if (runningBatch) onCancelBatch else onRunCore) {
                Icon(if (runningBatch) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (runningBatch) "停止批量" else "核心主机体检")
            }
            category?.let { selected ->
                OutlinedButton(onClick = { onRunCategory(selected) }, enabled = !runningBatch) {
                    Text("运行本分类")
                }
            }
        }
        if (runningBatch) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hosts, key = { it.id }) { host ->
                val result = results[host.id]
                NetworkHostRow(
                    host = host,
                    result = result,
                    running = host.id in runningIds,
                    onRun = { onRun(host) },
                    onCancel = { onCancel(host.id) },
                    onOpen = { if (result != null) selectedResult = result },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun NetworkHostRow(
    host: NetworkHostSpec,
    result: NetworkDiagnosticResult?,
    running: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    val color = result?.status?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(enabled = result != null, onClick = onOpen),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(host.displayName, fontWeight = FontWeight.Medium)
                    if (host.requiresTls12) SmallStatusLabel("TLS 1.2", MaterialTheme.colorScheme.tertiary)
                }
            },
            supportingContent = {
                Column {
                    Text(host.redactedUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        running -> Text("正在执行 DNS / HTTPS 检查", color = MaterialTheme.colorScheme.primary)
                        result != null -> Text(
                            buildString {
                                append(result.status.displayText())
                                result.http.httpStatusCode?.let { append(" · HTTP $it") }
                                result.http.tlsVersion?.let { append(" · $it") }
                                result.totalDurationMillis?.let { append(" · ${it}ms") }
                                result.error?.message?.let { append(" · $it") }
                            },
                            color = color,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> Text(host.category.displayText(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            leadingContent = {
                if (running) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else NetworkStatusIcon(result?.status)
            },
            trailingContent = {
                IconButton(onClick = if (running) onCancel else onRun) {
                    Icon(if (running) Icons.Filled.Cancel else Icons.Filled.PlayArrow, contentDescription = if (running) "取消" else "运行")
                }
            },
        )
    }
}

@Composable
internal fun DeveloperSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
internal fun SmallStatusLabel(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), AhuShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun StatusIcon(kind: DeveloperStatusKind) {
    val color = statusColor(kind)
    Icon(
        imageVector = when (kind) {
            DeveloperStatusKind.PASS -> Icons.Filled.CheckCircle
            DeveloperStatusKind.WARNING -> Icons.Filled.Warning
            DeveloperStatusKind.FAIL -> Icons.Filled.Error
            DeveloperStatusKind.INFO -> Icons.Filled.Info
        },
        contentDescription = null,
        tint = color,
    )
}

@Composable
private fun ModuleStatusIcon(status: DeveloperTestStatus) {
    val color = status.color()
    Icon(
        imageVector = when (status) {
            DeveloperTestStatus.PASSED -> Icons.Filled.CheckCircle
            DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT -> Icons.Filled.Error
            DeveloperTestStatus.SKIPPED, DeveloperTestStatus.CANCELLED -> Icons.Filled.PauseCircle
            DeveloperTestStatus.RUNNING -> Icons.Filled.Refresh
            DeveloperTestStatus.NOT_RUN -> Icons.Filled.Info
        },
        contentDescription = null,
        tint = color,
    )
}

@Composable
private fun NetworkStatusIcon(status: NetworkDiagnosticStatus?) {
    val color = status?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        imageVector = when (status) {
            NetworkDiagnosticStatus.SUCCEEDED -> Icons.Filled.CheckCircle
            NetworkDiagnosticStatus.WARNING -> Icons.Filled.Warning
            NetworkDiagnosticStatus.FAILED -> Icons.Filled.Error
            NetworkDiagnosticStatus.RUNNING -> Icons.Filled.Refresh
            NetworkDiagnosticStatus.CANCELLED, NetworkDiagnosticStatus.SKIPPED -> Icons.Filled.PauseCircle
            else -> Icons.Filled.Info
        },
        contentDescription = null,
        tint = color,
    )
}

@Composable
private fun statusColor(kind: DeveloperStatusKind): Color = when (kind) {
    DeveloperStatusKind.PASS -> Color(0xFF2E7D32)
    DeveloperStatusKind.WARNING -> Color(0xFFEF6C00)
    DeveloperStatusKind.FAIL -> MaterialTheme.colorScheme.error
    DeveloperStatusKind.INFO -> MaterialTheme.colorScheme.primary
}

@Composable
internal fun DeveloperTestStatus.color(): Color = when (this) {
    DeveloperTestStatus.PASSED -> Color(0xFF2E7D32)
    DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT -> MaterialTheme.colorScheme.error
    DeveloperTestStatus.SKIPPED, DeveloperTestStatus.CANCELLED -> Color(0xFFEF6C00)
    DeveloperTestStatus.RUNNING -> MaterialTheme.colorScheme.primary
    DeveloperTestStatus.NOT_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun NetworkDiagnosticStatus.color(): Color = when (this) {
    NetworkDiagnosticStatus.SUCCEEDED -> Color(0xFF2E7D32)
    NetworkDiagnosticStatus.WARNING -> Color(0xFFEF6C00)
    NetworkDiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
    NetworkDiagnosticStatus.RUNNING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun DeveloperTestStatus.displayText(): String = when (this) {
    DeveloperTestStatus.NOT_RUN -> "未运行"
    DeveloperTestStatus.RUNNING -> "运行中"
    DeveloperTestStatus.PASSED -> "通过"
    DeveloperTestStatus.FAILED -> "失败"
    DeveloperTestStatus.TIMED_OUT -> "超时"
    DeveloperTestStatus.SKIPPED -> "跳过"
    DeveloperTestStatus.CANCELLED -> "已取消"
}

private fun NetworkDiagnosticStatus.displayText(): String = when (this) {
    NetworkDiagnosticStatus.PENDING -> "等待"
    NetworkDiagnosticStatus.RUNNING -> "运行中"
    NetworkDiagnosticStatus.SUCCEEDED -> "通过"
    NetworkDiagnosticStatus.WARNING -> "警告"
    NetworkDiagnosticStatus.FAILED -> "失败"
    NetworkDiagnosticStatus.SKIPPED -> "跳过"
    NetworkDiagnosticStatus.CANCELLED -> "取消"
}

private fun DeveloperTestCategory.displayText(): String = when (this) {
    DeveloperTestCategory.LOCAL -> "本地"
    DeveloperTestCategory.AUTHENTICATION -> "认证"
    DeveloperTestCategory.ACADEMIC -> "教务"
    DeveloperTestCategory.STUDENT_SERVICES -> "学生服务"
    DeveloperTestCategory.CAMPUS_CARD -> "一卡通"
    DeveloperTestCategory.THIRD_PARTY -> "第三方"
    DeveloperTestCategory.PUBLIC_SERVICE -> "公开服务"
}

internal fun NetworkDiagnosticCategory.displayText(): String = when (this) {
    NetworkDiagnosticCategory.AHU -> "安大系统"
    NetworkDiagnosticCategory.MARKET -> "集市"
    NetworkDiagnosticCategory.CHAOXING -> "超星"
    NetworkDiagnosticCategory.WELEARN -> "WeLearn"
    NetworkDiagnosticCategory.PUBLIC_DATA -> "公开数据"
    NetworkDiagnosticCategory.AI_PROVIDER -> "AI 服务"
}

private suspend fun shareDiagnosticReport(context: Context, report: String) {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "developer_reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(directory, "ahu_plus_diagnostics_$stamp.txt").apply { writeText(report, Charsets.UTF_8) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享诊断报告"))
}
