package com.ahu_plus.ui.screen.developer

import android.app.Activity
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.developer.DeveloperCacheRepository
import com.ahu_plus.data.developer.DeveloperEventRecorder
import com.ahu_plus.data.developer.DeveloperLogLevel
import com.ahu_plus.data.developer.DeveloperModuleTest
import com.ahu_plus.data.developer.DeveloperOverview
import com.ahu_plus.data.developer.DeveloperStatusItem
import com.ahu_plus.data.developer.DeveloperStatusKind
import com.ahu_plus.data.developer.DeveloperTestCategory
import com.ahu_plus.data.developer.DeveloperTestRisk
import com.ahu_plus.data.developer.DeveloperTestSuiteSummary
import com.ahu_plus.data.developer.DeveloperTestStatus
import com.ahu_plus.data.developer.NetworkDiagnosticCategory
import com.ahu_plus.data.developer.NetworkDiagnosticHosts
import com.ahu_plus.data.developer.NetworkDiagnosticResult
import com.ahu_plus.data.developer.NetworkDiagnosticStatus
import com.ahu_plus.data.developer.NetworkHostSpec
import com.ahu_plus.data.developer.summarizeDeveloperTests
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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
    val networkResults by viewModel.networkResults.collectAsState()
    val runningNetworkIds by viewModel.runningNetworkIds.collectAsState()
    val runningModules by viewModel.runningAllModules.collectAsState()
    val moduleBatchProgress by viewModel.moduleBatchProgress.collectAsState()
    val runningNetworkBatch by viewModel.runningNetworkBatch.collectAsState()
    val networkBatchProgress by viewModel.networkBatchProgress.collectAsState()
    val runningAnyModule = runningModules || tests.any { it.status == DeveloperTestStatus.RUNNING }

    DisposableEffect(context, viewModel) {
        onDispose {
            if ((context as? Activity)?.isChangingConfigurations != true) {
                viewModel.cancelAllOperations()
            }
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    var exportRunning by remember { mutableStateOf(false) }
    var cacheReloadKey by remember { mutableIntStateOf(0) }
    val cacheRepository = remember(app) { DeveloperCacheRepository(app.appDataStore) }
    val cacheState by produceState<DeveloperDataUiState>(
        initialValue = DeveloperDataUiState.Loading,
        key1 = cacheRepository,
        key2 = cacheReloadKey,
    ) {
        value = DeveloperDataUiState.Loading
        cacheRepository.observeReport()
            .catch { error ->
                value = DeveloperDataUiState.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName,
                )
            }
            .collect { report -> value = DeveloperDataUiState.Ready(report) }
    }

    BackHandler(onBack = onBack)
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
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
                scrollBehavior = topBarScrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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

            val selectedTab = DeveloperTab.entries[selectedTabIndex]
            tabStateHolder.SaveableStateProvider(selectedTab.name) {
            when (selectedTab) {
                DeveloperTab.OVERVIEW -> DeveloperOverviewTab(
                    overview = overview,
                    tests = tests,
                    cacheEntryCount = (cacheState as? DeveloperDataUiState.Ready)?.report?.totalEntryCount,
                    networkResults = networkResults,
                    isRunningTests = runningAnyModule,
                    batchProgress = moduleBatchProgress,
                    onRefresh = viewModel::refreshOverview,
                    onRunQuickTests = viewModel::runSmokeModuleTests,
                    onRunAllTests = viewModel::runAllModuleTests,
                    onCancelTests = viewModel::cancelAllModuleTests,
                )
                DeveloperTab.MODULES -> DeveloperModulesTab(
                    tests = tests,
                    isRunningAll = runningModules,
                    batchProgress = moduleBatchProgress,
                    onRun = viewModel::runModuleTest,
                    onCancel = viewModel::cancelModuleTest,
                    onRunAll = viewModel::runAllModuleTests,
                    onRunLocal = viewModel::runLocalModuleTests,
                    onRunPublic = viewModel::runPublicModuleTests,
                    onRunAuthenticated = viewModel::runAuthenticatedModuleTests,
                    onRerunFailed = viewModel::rerunFailedModuleTests,
                    onCancelAll = viewModel::cancelAllModuleTests,
                )
                DeveloperTab.NETWORK -> DeveloperNetworkTab(
                    results = networkResults,
                    runningIds = runningNetworkIds,
                    runningBatch = runningNetworkBatch,
                    batchProgress = networkBatchProgress,
                    onRun = viewModel::runNetwork,
                    onCancel = viewModel::cancelNetwork,
                    onRunCore = viewModel::runCoreNetworkBatch,
                    onRunCategory = viewModel::runNetworkCategory,
                    onCancelBatch = viewModel::cancelNetworkBatch,
                )
                DeveloperTab.DATA -> DeveloperDataTab(
                    state = cacheState,
                    onClearKey = viewModel::clearCacheKey,
                    onClearCategory = viewModel::clearCacheCategory,
                    onRetry = { cacheReloadKey++ },
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperOverviewTab(
    overview: DeveloperOverview,
    tests: List<DeveloperModuleTest>,
    cacheEntryCount: Int?,
    networkResults: Map<String, NetworkDiagnosticResult>,
    isRunningTests: Boolean,
    batchProgress: DeveloperModuleBatchProgress,
    onRefresh: () -> Unit,
    onRunQuickTests: () -> Unit,
    onRunAllTests: () -> Unit,
    onCancelTests: () -> Unit,
) {
    val summary = summarizeDeveloperTests(tests)
    val networkAttention = networkResults.values.count {
        it.status in setOf(NetworkDiagnosticStatus.FAILED, NetworkDiagnosticStatus.WARNING)
    }
    val completedNetworkCount = networkResults.values.count { it.status.isTerminal() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DeveloperHealthPanel(summary = summary, batchProgress = batchProgress) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric("模块通过率", summary.passRatePercent?.let { "$it%" } ?: "-", developerSuccessColor(), Modifier.weight(1f))
                    SummaryMetric("模块异常", summary.attention.toString(), if (summary.attention > 0) MaterialTheme.colorScheme.error else developerSuccessColor(), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric("网络提醒", networkAttention.toString(), if (networkAttention > 0) developerWarningColor() else developerSuccessColor(), Modifier.weight(1f))
                    SummaryMetric("未运行", summary.notRun.toString(), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric("本地缓存", cacheEntryCount?.toString() ?: "-", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    SummaryMetric("网络覆盖", "$completedNetworkCount/${NetworkDiagnosticHosts.all.size}", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }
        }
        item { DeveloperCategoryCoverage(summary) }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            ) {
                Button(onClick = if (isRunningTests) onCancelTests else onRunQuickTests) {
                    Icon(if (isRunningTests) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunningTests) "停止体检" else "快速体检")
                }
                OutlinedButton(onClick = onRunAllTests, enabled = !isRunningTests) {
                    Text("完整体检")
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("刷新状态")
                }
            }
        }
        if (batchProgress.isRunning) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            batchProgress.currentTitle ?: batchProgress.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${batchProgress.completed}/${batchProgress.total}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { batchProgress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item { StatusSection("构建信息", overview.build) }
        item { StatusSection("设备环境", overview.device) }
        item { StatusSection("权限与系统能力", overview.permissions) }
        item { StatusSection("认证与会话", overview.sessions) }
        item { StatusSection("运行状态", overview.runtime) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun DeveloperHealthPanel(
    summary: DeveloperTestSuiteSummary,
    batchProgress: DeveloperModuleBatchProgress,
) {
    val color = when {
        summary.running > 0 || batchProgress.isRunning -> MaterialTheme.colorScheme.primary
        summary.attention > 0 -> MaterialTheme.colorScheme.error
        summary.completed > 0 && summary.notRun == 0 -> developerSuccessColor()
        else -> MaterialTheme.colorScheme.tertiary
    }
    val title = when {
        summary.running > 0 || batchProgress.isRunning -> "体检进行中"
        summary.attention > 0 -> "发现 ${summary.attention} 项需要处理"
        summary.completed == 0 -> "尚未运行体检"
        summary.notRun > 0 -> "已完成 ${summary.completed}/${summary.total} 项检查"
        else -> "本轮检查未发现异常"
    }
    Surface(
        shape = AhuShapes.Card,
        color = color.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.padding(AhuSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AhuSpacing.md)) {
                ModuleStatusIcon(
                    when {
                        summary.running > 0 || batchProgress.isRunning -> DeveloperTestStatus.RUNNING
                        summary.attention > 0 -> DeveloperTestStatus.FAILED
                        summary.completed > 0 && summary.notRun == 0 -> DeveloperTestStatus.PASSED
                        else -> DeveloperTestStatus.NOT_RUN
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                    Text(
                        "${summary.passed} 通过 · ${summary.skipped} 跳过 · ${formatDuration(summary.totalDurationMillis)} 累计耗时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                summary.passRatePercent?.let {
                    Text("$it%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                }
            }
            DeveloperOutcomeBar(summary)
            val details = buildList {
                summary.lastRunAtMillis?.let { add("最近运行 ${formatModuleRunTime(it)}") }
                summary.slowestTest?.durationMillis?.let { duration ->
                    add("最慢 ${summary.slowestTest.title} ${formatDuration(duration)}")
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeveloperOutcomeBar(summary: DeveloperTestSuiteSummary) {
    if (summary.total == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, AhuShapes.Pill),
    ) {
        listOf(
            summary.passed to developerSuccessColor(),
            summary.attention to MaterialTheme.colorScheme.error,
            summary.skipped to developerWarningColor(),
            (summary.notRun + summary.running) to MaterialTheme.colorScheme.outlineVariant,
        ).forEach { (count, segmentColor) ->
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxSize()
                        .background(segmentColor),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperCategoryCoverage(summary: DeveloperTestSuiteSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm)) {
        DeveloperSectionTitle("分类覆盖")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
        ) {
            summary.categories.forEach { category ->
                val color = when {
                    category.attention > 0 -> MaterialTheme.colorScheme.error
                    category.completed == category.total -> developerSuccessColor()
                    else -> MaterialTheme.colorScheme.primary
                }
                SmallStatusLabel(
                    "${category.category.displayText()} ${category.completed}/${category.total}" +
                        if (category.attention > 0) " · ${category.attention} 异常" else "",
                    color,
                )
            }
        }
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
                    val displayValue = developerOverviewDisplayValue(item.id, item.value)
                    val isTechnicalValue = item.id in setOf("application_id", "signature", "abi")
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    displayValue,
                                    color = statusColor(item.kind),
                                    style = if (isTechnicalValue) {
                                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.detail.takeIf(String::isNotBlank)?.let { detail ->
                                    Text(
                                        detail,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        leadingContent = { StatusIcon(item.kind) },
                    )
                    if (index != items.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperModulesTab(
    tests: List<DeveloperModuleTest>,
    isRunningAll: Boolean,
    batchProgress: DeveloperModuleBatchProgress,
    onRun: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRunAll: () -> Unit,
    onRunLocal: () -> Unit,
    onRunPublic: () -> Unit,
    onRunAuthenticated: () -> Unit,
    onRerunFailed: () -> Unit,
    onCancelAll: () -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<DeveloperTestCategory?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf(DeveloperTestStatusFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTest by remember { mutableStateOf<DeveloperModuleTest?>(null) }
    val filtered = filterDeveloperModuleTests(tests, selectedCategory, statusFilter, query)
    val hasRunningSingle = !isRunningAll && tests.any { it.status == DeveloperTestStatus.RUNNING }
    val failedCount = tests.count { it.status in DeveloperTestStatusFilter.ATTENTION.statuses }

    selectedTest?.let { test ->
        ModuleTestDetailDialog(test = test, onDismiss = { selectedTest = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AhuSpacing.ScreenHorizontal, vertical = AhuSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = if (isRunningAll) onCancelAll else onRunAll,
                enabled = isRunningAll || !hasRunningSingle,
            ) {
                Icon(if (isRunningAll) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isRunningAll) "停止" else "全部体检")
            }
            OutlinedButton(onClick = onRunLocal, enabled = !isRunningAll && !hasRunningSingle) { Text("本地") }
            OutlinedButton(onClick = onRunPublic, enabled = !isRunningAll && !hasRunningSingle) { Text("公开服务") }
            OutlinedButton(onClick = onRunAuthenticated, enabled = !isRunningAll && !hasRunningSingle) { Text("账号服务") }
            if (failedCount > 0) {
                OutlinedButton(onClick = onRerunFailed, enabled = !isRunningAll && !hasRunningSingle) {
                    Icon(Icons.Filled.Replay, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("复测异常 $failedCount")
                }
            }
        }
        if (isRunningAll) {
            Column(
                modifier = Modifier.padding(horizontal = AhuSpacing.ScreenHorizontal, vertical = AhuSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AhuSpacing.xs),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${batchProgress.label} · ${batchProgress.currentTitle.orEmpty()}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${batchProgress.completed}/${batchProgress.total}", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { batchProgress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            placeholder = { Text("搜索测试名称、说明或结果") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("全部 ${tests.size}") })
            DeveloperTestCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text("${category.displayText()} ${tests.count { it.category == category }}") },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeveloperTestStatusFilter.entries.forEach { filter ->
                val filterCount = tests.count { it.status in filter.statuses }
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text("${filter.label} $filterCount") },
                )
            }
        }
        Text(
            "显示 ${filtered.size}/${tests.size} · ${tests.count { it.status == DeveloperTestStatus.PASSED }} 通过 · " +
                "$failedCount 异常 · ${tests.count { it.status == DeveloperTestStatus.SKIPPED }} 跳过",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { test ->
                ModuleTestRow(
                    test = test,
                    runEnabled = !isRunningAll,
                    onRun = { onRun(test.id) },
                    onCancel = { if (isRunningAll) onCancelAll() else onCancel(test.id) },
                    onOpen = { selectedTest = test },
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("没有匹配的测试", fontWeight = FontWeight.SemiBold)
                        Text("调整搜索词、分类或状态筛选", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ModuleTestRow(
    test: DeveloperModuleTest,
    runEnabled: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(
            onClickLabel = "查看 ${test.title} 测试详情",
            role = Role.Button,
            onClick = onOpen,
        ),
    ) {
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
                    test.result?.let {
                        Text(
                            it,
                            color = test.status.color(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${test.category.displayText()} · ${test.risk.displayText()}" +
                            (test.durationMillis?.let { " · ${it}ms" } ?: "") +
                            (test.lastRunAtMillis?.let { " · ${formatModuleRunTime(it)}" } ?: ""),
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
                    IconButton(onClick = onRun, enabled = runEnabled) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "运行 ${test.title}")
                    }
                }
            },
        )
    }
}

internal enum class DeveloperTestStatusFilter(
    val label: String,
    val statuses: Set<DeveloperTestStatus>,
) {
    ALL("全部状态", DeveloperTestStatus.entries.toSet()),
    ATTENTION("异常", setOf(DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT, DeveloperTestStatus.CANCELLED)),
    NOT_RUN("未运行", setOf(DeveloperTestStatus.NOT_RUN)),
    FINISHED("已完成", setOf(DeveloperTestStatus.PASSED, DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT, DeveloperTestStatus.SKIPPED, DeveloperTestStatus.CANCELLED)),
}

internal fun filterDeveloperModuleTests(
    tests: List<DeveloperModuleTest>,
    category: DeveloperTestCategory?,
    statusFilter: DeveloperTestStatusFilter,
    query: String,
): List<DeveloperModuleTest> {
    val normalizedQuery = query.trim()
    return tests.filter { test ->
        (category == null || test.category == category) &&
            test.status in statusFilter.statuses &&
            (normalizedQuery.isBlank() || listOf(test.title, test.description, test.result.orEmpty(), test.id)
                .any { it.contains(normalizedQuery, ignoreCase = true) })
    }
}

@Composable
private fun ModuleTestDetailDialog(test: DeveloperModuleTest, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(test.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStatusLabel(test.status.displayText(), test.status.color())
                    SmallStatusLabel(test.risk.displayText(), MaterialTheme.colorScheme.primary)
                }
                Text(test.description)
                test.result?.let {
                    Surface(shape = AhuShapes.Card, color = test.status.color().copy(alpha = 0.08f)) {
                        Text(it, modifier = Modifier.padding(10.dp), color = test.status.color())
                    }
                }
                Text("测试 ID\n${test.id}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                Text(
                    buildString {
                        append("分类：${test.category.displayText()}")
                        test.durationMillis?.let { append("\n耗时：${it} ms") }
                        test.lastRunAtMillis?.let { append("\n最近运行：${formatModuleRunTime(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun formatModuleRunTime(timestampMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperNetworkTab(
    results: Map<String, NetworkDiagnosticResult>,
    runningIds: Set<String>,
    runningBatch: Boolean,
    batchProgress: DeveloperNetworkBatchProgress,
    onRun: (NetworkHostSpec) -> Unit,
    onCancel: (String) -> Unit,
    onRunCore: () -> Unit,
    onRunCategory: (NetworkDiagnosticCategory) -> Unit,
    onCancelBatch: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<NetworkDiagnosticCategory?>(NetworkDiagnosticCategory.AHU) }
    var selectedResult by remember { mutableStateOf<NetworkDiagnosticResult?>(null) }
    val hosts = NetworkDiagnosticHosts.all.filter { category == null || it.category == category }
    val visibleResults = hosts.mapNotNull { results[it.id] }
    val completedResults = visibleResults.filter { it.status.isTerminal() }
    val succeeded = completedResults.count { it.status == NetworkDiagnosticStatus.SUCCEEDED }
    val warnings = completedResults.count { it.status == NetworkDiagnosticStatus.WARNING }
    val failed = completedResults.count { it.status == NetworkDiagnosticStatus.FAILED }
    val cancelledOrSkipped = completedResults.count {
        it.status == NetworkDiagnosticStatus.CANCELLED || it.status == NetworkDiagnosticStatus.SKIPPED
    }
    val averageDuration = completedResults.mapNotNull { it.totalDurationMillis }.takeIf { it.isNotEmpty() }?.average()?.toLong()

    selectedResult?.let { result ->
        NetworkResultDialog(result = result, onDismiss = { selectedResult = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AhuSpacing.ScreenHorizontal, vertical = AhuSpacing.sm),
            shape = AhuShapes.Card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(AhuSpacing.md), verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm)) {
                    Text("诊断概况", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${completedResults.size}/${hosts.size} 已检查",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm), verticalArrangement = Arrangement.spacedBy(AhuSpacing.xs)) {
                    SmallStatusLabel("成功 $succeeded", developerSuccessColor())
                    SmallStatusLabel("提醒 $warnings", developerWarningColor())
                    SmallStatusLabel("失败 $failed", MaterialTheme.colorScheme.error)
                    if (cancelledOrSkipped > 0) {
                        SmallStatusLabel("取消/跳过 $cancelledOrSkipped", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    averageDuration?.let { SmallStatusLabel("平均 ${formatDuration(it)}", MaterialTheme.colorScheme.tertiary) }
                }
                if (batchProgress.isRunning) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            batchProgress.currentTitle ?: batchProgress.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${batchProgress.completed}/${batchProgress.total}", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { batchProgress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = category == null,
                onClick = { category = null },
                label = { Text("全部 ${results.values.count { it.status.isTerminal() }}/${NetworkDiagnosticHosts.all.size}") },
            )
            NetworkDiagnosticCategory.entries.forEach { item ->
                val itemHosts = NetworkDiagnosticHosts.forCategory(item)
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = {
                        Text(
                            "${item.displayText()} ${itemHosts.count { results[it.id]?.status?.isTerminal() == true }}/${itemHosts.size}",
                        )
                    },
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AhuSpacing.ScreenHorizontal, vertical = AhuSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.xs),
        ) {
            Button(
                onClick = if (runningBatch) onCancelBatch else onRunCore,
                enabled = runningBatch || runningIds.isEmpty(),
            ) {
                Icon(if (runningBatch) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (runningBatch) "停止批量" else "核心主机体检")
            }
            category?.let { selected ->
                OutlinedButton(
                    onClick = { onRunCategory(selected) },
                    enabled = !runningBatch && runningIds.isEmpty(),
                ) {
                    Text("运行本分类")
                }
            }
        }
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
                    runningBatch = runningBatch,
                    onRun = { onRun(host) },
                    onCancel = { onCancel(host.id) },
                    onCancelBatch = onCancelBatch,
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
    runningBatch: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onCancelBatch: () -> Unit,
    onOpen: () -> Unit,
) {
    val color = result?.status?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = if (result != null) {
            Modifier.clickable(
                onClickLabel = "查看 ${host.displayName} 诊断结果",
                role = Role.Button,
                onClick = onOpen,
            )
        } else {
            Modifier
        },
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
                                if (result.dns.durationMillis != null || result.http.durationMillis != null) {
                                    append(" · DNS ${result.dns.durationMillis ?: "-"}ms / HTTPS ${result.http.durationMillis ?: "-"}ms")
                                }
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
                val batchHostRunning = runningBatch && running
                IconButton(
                    onClick = when {
                        batchHostRunning -> onCancelBatch
                        running -> onCancel
                        else -> onRun
                    },
                    enabled = !runningBatch || batchHostRunning,
                ) {
                    Icon(
                        imageVector = when {
                            batchHostRunning -> Icons.Filled.Stop
                            running -> Icons.Filled.Cancel
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = when {
                            batchHostRunning -> "停止批量诊断，当前主机 ${host.displayName}"
                            running -> "取消诊断 ${host.displayName}"
                            runningBatch -> "批量诊断进行中，暂不可运行 ${host.displayName}"
                            else -> "运行诊断 ${host.displayName}"
                        },
                    )
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
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { heading() },
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
    DeveloperStatusKind.PASS -> developerSuccessColor()
    DeveloperStatusKind.WARNING -> developerWarningColor()
    DeveloperStatusKind.FAIL -> MaterialTheme.colorScheme.error
    DeveloperStatusKind.INFO -> MaterialTheme.colorScheme.primary
}

@Composable
internal fun DeveloperTestStatus.color(): Color = when (this) {
    DeveloperTestStatus.PASSED -> developerSuccessColor()
    DeveloperTestStatus.FAILED, DeveloperTestStatus.TIMED_OUT -> MaterialTheme.colorScheme.error
    DeveloperTestStatus.SKIPPED, DeveloperTestStatus.CANCELLED -> developerWarningColor()
    DeveloperTestStatus.RUNNING -> MaterialTheme.colorScheme.primary
    DeveloperTestStatus.NOT_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun NetworkDiagnosticStatus.color(): Color = when (this) {
    NetworkDiagnosticStatus.SUCCEEDED -> developerSuccessColor()
    NetworkDiagnosticStatus.WARNING -> developerWarningColor()
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

private fun DeveloperTestRisk.displayText(): String = when (this) {
    DeveloperTestRisk.LOCAL_ONLY -> "仅本地"
    DeveloperTestRisk.PUBLIC_READ -> "公开只读"
    DeveloperTestRisk.AUTHENTICATED_READ -> "认证只读"
}

internal fun NetworkDiagnosticStatus.displayText(): String = when (this) {
    NetworkDiagnosticStatus.PENDING -> "等待"
    NetworkDiagnosticStatus.RUNNING -> "运行中"
    NetworkDiagnosticStatus.SUCCEEDED -> "通过"
    NetworkDiagnosticStatus.WARNING -> "警告"
    NetworkDiagnosticStatus.FAILED -> "失败"
    NetworkDiagnosticStatus.SKIPPED -> "跳过"
    NetworkDiagnosticStatus.CANCELLED -> "取消"
}

private fun NetworkDiagnosticStatus.isTerminal(): Boolean = when (this) {
    NetworkDiagnosticStatus.SUCCEEDED,
    NetworkDiagnosticStatus.WARNING,
    NetworkDiagnosticStatus.FAILED,
    NetworkDiagnosticStatus.SKIPPED,
    NetworkDiagnosticStatus.CANCELLED -> true
    NetworkDiagnosticStatus.PENDING,
    NetworkDiagnosticStatus.RUNNING -> false
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

internal fun developerOverviewDisplayValue(
    itemId: String,
    rawValue: String,
    timestampFormatter: (Long) -> String = ::formatOverviewTimestamp,
): String {
    if (itemId != "first_install" && itemId != "last_update") return rawValue
    val timestampMillis = rawValue.toLongOrNull()?.takeIf { it > 0L } ?: return rawValue
    return timestampFormatter(timestampMillis)
}

private fun formatOverviewTimestamp(timestampMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

private fun formatDuration(durationMillis: Long): String = when {
    durationMillis < 1_000L -> "${durationMillis}ms"
    durationMillis < 60_000L -> String.format(Locale.getDefault(), "%.1fs", durationMillis / 1_000.0)
    else -> "${durationMillis / 60_000L}m ${(durationMillis % 60_000L) / 1_000L}s"
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
