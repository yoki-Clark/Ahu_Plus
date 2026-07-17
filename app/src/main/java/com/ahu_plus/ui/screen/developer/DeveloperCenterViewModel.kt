package com.ahu_plus.ui.screen.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.developer.DeveloperCacheCategorySummary
import com.ahu_plus.data.developer.DeveloperCacheReport
import com.ahu_plus.data.developer.DeveloperCacheRepository
import com.ahu_plus.data.developer.DeveloperEventRecorder
import com.ahu_plus.data.developer.DeveloperLogLevel
import com.ahu_plus.data.developer.DeveloperModuleTest
import com.ahu_plus.data.developer.DeveloperModuleTestRepository
import com.ahu_plus.data.developer.DeveloperOverview
import com.ahu_plus.data.developer.DeveloperOverviewRepository
import com.ahu_plus.data.developer.DeveloperPayloadAnalysis
import com.ahu_plus.data.developer.DeveloperPayloadAnalyzer
import com.ahu_plus.data.developer.DeveloperPayloadType
import com.ahu_plus.data.developer.DeveloperTestStatus
import com.ahu_plus.data.developer.NetworkDiagnosticCategory
import com.ahu_plus.data.developer.NetworkDiagnosticEngine
import com.ahu_plus.data.developer.NetworkDiagnosticHosts
import com.ahu_plus.data.developer.NetworkDiagnosticResult
import com.ahu_plus.data.developer.NetworkHostSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeveloperCenterViewModel(
    private val app: AhuPlusApplication,
) : ViewModel() {
    private val overviewRepository = DeveloperOverviewRepository(app)
    private val cacheRepository = DeveloperCacheRepository(app.appDataStore)
    private val moduleRepository = DeveloperModuleTestRepository(app)
    private val networkEngine = NetworkDiagnosticEngine()

    private val _overview = MutableStateFlow(overviewRepository.snapshot())
    val overview: StateFlow<DeveloperOverview> = _overview.asStateFlow()

    val cacheReport: StateFlow<DeveloperCacheReport> = cacheRepository.observeReport()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EMPTY_CACHE_REPORT)

    val moduleTests: StateFlow<List<DeveloperModuleTest>> = moduleRepository.tests

    private val _networkResults = MutableStateFlow<Map<String, NetworkDiagnosticResult>>(emptyMap())
    val networkResults: StateFlow<Map<String, NetworkDiagnosticResult>> = _networkResults.asStateFlow()

    private val _runningNetworkIds = MutableStateFlow<Set<String>>(emptySet())
    val runningNetworkIds: StateFlow<Set<String>> = _runningNetworkIds.asStateFlow()

    private val _runningAllModules = MutableStateFlow(false)
    val runningAllModules: StateFlow<Boolean> = _runningAllModules.asStateFlow()

    private val _runningNetworkBatch = MutableStateFlow(false)
    val runningNetworkBatch: StateFlow<Boolean> = _runningNetworkBatch.asStateFlow()

    private val _payloadAnalysis = MutableStateFlow<DeveloperPayloadAnalysis?>(null)
    val payloadAnalysis: StateFlow<DeveloperPayloadAnalysis?> = _payloadAnalysis.asStateFlow()

    private val networkJobs = mutableMapOf<String, Job>()
    private val moduleJobs = mutableMapOf<String, Job>()
    private var moduleBatchJob: Job? = null
    private var networkBatchJob: Job? = null
    private var payloadJob: Job? = null

    fun refreshOverview() {
        _overview.value = overviewRepository.snapshot()
    }

    fun runModuleTest(id: String) {
        if (moduleJobs[id]?.isActive == true) return
        moduleJobs[id] = viewModelScope.launch {
            val title = moduleRepository.getTests().firstOrNull { it.id == id }?.title ?: id
            DeveloperEventRecorder.record("模块测试", "开始：$title")
            try {
                val result = moduleRepository.run(id)
                DeveloperEventRecorder.record(
                    category = "模块测试",
                    message = "${result.status.asLogLabel()}：$title",
                    detail = result.result.orEmpty(),
                    level = if (result.status == DeveloperTestStatus.PASSED) DeveloperLogLevel.INFO else DeveloperLogLevel.WARNING,
                )
            } catch (_: CancellationException) {
                DeveloperEventRecorder.record("模块测试", "已取消：$title", level = DeveloperLogLevel.WARNING)
            } finally {
                refreshOverview()
                moduleJobs.remove(id)
            }
        }
    }

    fun cancelModuleTest(id: String) {
        moduleJobs[id]?.cancel()
    }

    fun runAllModuleTests() {
        if (moduleBatchJob?.isActive == true) return
        moduleBatchJob = viewModelScope.launch {
            _runningAllModules.value = true
            DeveloperEventRecorder.record("模块测试", "开始全部只读模块测试")
            try {
                for (test in moduleRepository.getTests()) {
                    moduleRepository.run(test.id)
                }
                DeveloperEventRecorder.record("模块测试", "全部只读模块测试已完成")
            } catch (_: CancellationException) {
                DeveloperEventRecorder.record("模块测试", "批量测试已取消", level = DeveloperLogLevel.WARNING)
            } finally {
                _runningAllModules.value = false
                refreshOverview()
            }
        }
    }

    fun cancelAllModuleTests() {
        moduleBatchJob?.cancel()
        moduleJobs.values.forEach(Job::cancel)
    }

    fun runNetwork(hostSpec: NetworkHostSpec) {
        if (networkJobs[hostSpec.id]?.isActive == true) return
        networkJobs[hostSpec.id] = viewModelScope.launch {
            _runningNetworkIds.update { it + hostSpec.id }
            DeveloperEventRecorder.record("网络诊断", "开始：${hostSpec.displayName}", hostSpec.redactedUrl)
            try {
                val result = networkEngine.run(hostSpec) { progress ->
                    _networkResults.update { it + (hostSpec.id to progress) }
                }
                DeveloperEventRecorder.record(
                    category = "网络诊断",
                    message = "${result.status.name}：${hostSpec.displayName}",
                    detail = result.error?.message ?: "${result.totalDurationMillis ?: 0L}ms",
                    level = if (result.error == null) DeveloperLogLevel.INFO else DeveloperLogLevel.WARNING,
                )
            } catch (_: CancellationException) {
                DeveloperEventRecorder.record("网络诊断", "已取消：${hostSpec.displayName}", level = DeveloperLogLevel.WARNING)
            } finally {
                _runningNetworkIds.update { it - hostSpec.id }
                networkJobs.remove(hostSpec.id)
            }
        }
    }

    fun cancelNetwork(id: String) {
        networkJobs[id]?.cancel()
    }

    fun runNetworkCategory(category: NetworkDiagnosticCategory) {
        runNetworkBatch(NetworkDiagnosticHosts.forCategory(category))
    }

    fun runCoreNetworkBatch() {
        val specs = NetworkDiagnosticHosts.all.filter {
            it.category == NetworkDiagnosticCategory.AHU ||
                it.id in setOf("market", "chaoxing_passport", "welearn", "gitee", "weather")
        }
        runNetworkBatch(specs)
    }

    private fun runNetworkBatch(hosts: List<NetworkHostSpec>) {
        if (networkBatchJob?.isActive == true || hosts.isEmpty()) return
        networkBatchJob = viewModelScope.launch {
            _runningNetworkBatch.value = true
            try {
                for (host in hosts) {
                    _runningNetworkIds.update { it + host.id }
                    val result = networkEngine.run(host) { progress ->
                        _networkResults.update { it + (host.id to progress) }
                    }
                    _networkResults.update { it + (host.id to result) }
                    _runningNetworkIds.update { it - host.id }
                }
            } catch (_: CancellationException) {
                DeveloperEventRecorder.record("网络诊断", "批量网络诊断已取消", level = DeveloperLogLevel.WARNING)
            } finally {
                _runningNetworkIds.value = emptySet()
                _runningNetworkBatch.value = false
            }
        }
    }

    fun cancelNetworkBatch() {
        networkBatchJob?.cancel()
    }

    fun cancelAllOperations() {
        moduleBatchJob?.cancel()
        networkBatchJob?.cancel()
        moduleJobs.values.forEach(Job::cancel)
        networkJobs.values.forEach(Job::cancel)
        payloadJob?.cancel()
    }

    fun clearCacheKey(keyName: String) {
        viewModelScope.launch {
            val removed = cacheRepository.clearKey(keyName)
            DeveloperEventRecorder.record(
                category = "缓存",
                message = if (removed) "已从持久化层删除 $keyName；内存镜像需重启验证" else "未找到或不允许删除 $keyName",
                level = if (removed) DeveloperLogLevel.WARNING else DeveloperLogLevel.INFO,
            )
            refreshOverview()
        }
    }

    fun clearCacheCategory(summary: DeveloperCacheCategorySummary) {
        viewModelScope.launch {
            val names = cacheReport.value.entries
                .filter { it.category == summary.category }
                .map { it.keyName }
            val removed = cacheRepository.clearKeys(names)
            DeveloperEventRecorder.record(
                category = "缓存",
                message = "已清理 ${summary.category.name}：$removed 项",
                level = DeveloperLogLevel.WARNING,
            )
            refreshOverview()
        }
    }

    fun analyzePayload(input: String) {
        payloadJob?.cancel()
        if (input.length > MAX_PAYLOAD_CHARS) {
            _payloadAnalysis.value = DeveloperPayloadAnalysis(
                type = DeveloperPayloadType.TEXT,
                summary = "输入内容过大",
                details = listOf("最大允许 $MAX_PAYLOAD_CHARS 个字符，当前 ${input.length} 个字符"),
                formatted = "",
            )
            return
        }
        payloadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                DeveloperPayloadAnalyzer.analyze(input)
            }
            _payloadAnalysis.value = result
            DeveloperEventRecorder.record("解析实验室", result.summary)
        }
    }

    fun clearPayloadAnalysis() {
        payloadJob?.cancel()
        _payloadAnalysis.value = null
    }

    suspend fun buildDiagnosticReport(): String {
        val overviewSnapshot = overviewRepository.snapshot()
        val cacheText = cacheRepository.exportRedactedText()
        val runtime = com.ahu_plus.data.developer.DeveloperRuntime.state.value
        val logs = DeveloperEventRecorder.entries.value
        return buildString {
            appendLine("Ahu Plus Developer Diagnostic Report")
            appendLine("Generated: ${REPORT_TIME_FORMATTER.format(Instant.now())}")
            appendLine("Network override: ${runtime.networkFault.title}, target=${runtime.targetHost.ifBlank { "all" }}")
            appendLine()
            appendOverview("Build", overviewSnapshot.build)
            appendOverview("Device", overviewSnapshot.device)
            appendOverview("Permissions", overviewSnapshot.permissions)
            appendOverview("Sessions", overviewSnapshot.sessions)
            appendOverview("Runtime", overviewSnapshot.runtime)
            appendLine("[Module tests]")
            moduleRepository.getTests().forEach {
                appendLine("${it.id}\t${it.status}\t${it.durationMillis ?: "-"}ms\t${it.result.orEmpty()}")
            }
            appendLine()
            appendLine("[Network diagnostics]")
            _networkResults.value.values.sortedBy { it.hostSpec.id }.forEach {
                appendLine(
                    "${it.hostSpec.id}\t${it.status}\tHTTP ${it.http.httpStatusCode ?: "-"}\t" +
                        "DNS ${it.dns.durationMillis ?: "-"}ms\tTOTAL ${it.totalDurationMillis ?: "-"}ms\t" +
                        (it.error?.message ?: ""),
                )
            }
            appendLine()
            appendLine(cacheText)
            appendLine()
            appendLine("[Developer logs]")
            logs.forEach {
                appendLine("${it.timestampMillis}\t${it.level}\t${it.category}\t${it.message}\t${it.detail}")
            }
        }
    }

    private fun StringBuilder.appendOverview(
        title: String,
        items: List<com.ahu_plus.data.developer.DeveloperStatusItem>,
    ) {
        appendLine("[$title]")
        items.forEach { appendLine("${it.title}\t${it.value}\t${it.detail}") }
        appendLine()
    }

    override fun onCleared() {
        cancelAllOperations()
        super.onCleared()
    }

    class Factory(private val app: AhuPlusApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeveloperCenterViewModel::class.java))
            return DeveloperCenterViewModel(app) as T
        }
    }

    private companion object {
        val EMPTY_CACHE_REPORT = DeveloperCacheReport(
            entries = emptyList(),
            totalEntryCount = 0,
            totalEstimatedBytes = 0L,
            sensitiveEntryCount = 0,
            validJsonCount = 0,
            invalidJsonCount = 0,
            categories = emptyList(),
        )
        val REPORT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss XXX")
            .withZone(ZoneId.systemDefault())
        const val MAX_PAYLOAD_CHARS = 2_000_000
    }
}

private fun DeveloperTestStatus.asLogLabel(): String = when (this) {
    DeveloperTestStatus.PASSED -> "通过"
    DeveloperTestStatus.FAILED -> "失败"
    DeveloperTestStatus.TIMED_OUT -> "超时"
    DeveloperTestStatus.SKIPPED -> "跳过"
    DeveloperTestStatus.CANCELLED -> "取消"
    DeveloperTestStatus.RUNNING -> "运行中"
    DeveloperTestStatus.NOT_RUN -> "未运行"
}
