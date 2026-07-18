package com.ahu_plus.ui.screen.developer

import com.ahu_plus.data.developer.DeveloperModuleTest
import com.ahu_plus.data.developer.DeveloperTestCategory
import com.ahu_plus.data.developer.DeveloperTestRisk
import com.ahu_plus.data.developer.DeveloperTestStatus
import com.ahu_plus.data.developer.NetworkDiagnosticError
import com.ahu_plus.data.developer.NetworkDiagnosticErrorKind
import com.ahu_plus.data.developer.NetworkDiagnosticCategory
import com.ahu_plus.data.developer.NetworkDiagnosticResult
import com.ahu_plus.data.developer.NetworkDiagnosticStatus
import com.ahu_plus.data.developer.NetworkHostSpec
import com.ahu_plus.data.developer.NetworkProbeMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperCenterUiLogicTest {

    @Test
    fun `overview formats install timestamps`() {
        assertEquals(
            "formatted:1234",
            developerOverviewDisplayValue("first_install", "1234") { "formatted:$it" },
        )
        assertEquals(
            "formatted:5678",
            developerOverviewDisplayValue("last_update", "5678") { "formatted:$it" },
        )
    }

    @Test
    fun `overview preserves non timestamp and invalid values`() {
        assertEquals(
            "arm64-v8a",
            developerOverviewDisplayValue("abi", "arm64-v8a") { error("must not format") },
        )
        assertEquals(
            "unknown",
            developerOverviewDisplayValue("first_install", "unknown") { error("must not format") },
        )
        assertEquals(
            "0",
            developerOverviewDisplayValue("last_update", "0") { error("must not format") },
        )
    }

    @Test
    fun `data page distinguishes empty report from empty filter result`() {
        assertEquals(
            DeveloperDataResultState.EMPTY,
            developerDataResultState(totalEntryCount = 0, visibleEntryCount = 0),
        )
        assertEquals(
            DeveloperDataResultState.NO_MATCH,
            developerDataResultState(totalEntryCount = 8, visibleEntryCount = 0),
        )
        assertEquals(
            DeveloperDataResultState.HAS_RESULTS,
            developerDataResultState(totalEntryCount = 8, visibleEntryCount = 3),
        )
    }

    @Test
    fun `module filter combines category status and search query`() {
        val tests = listOf(
            moduleTest("local.storage", "应用存储", DeveloperTestCategory.LOCAL, DeveloperTestStatus.PASSED),
            moduleTest("jw.schedule", "课表接口", DeveloperTestCategory.ACADEMIC, DeveloperTestStatus.FAILED),
            moduleTest("jw.grade", "成绩接口", DeveloperTestCategory.ACADEMIC, DeveloperTestStatus.NOT_RUN),
        )

        val filtered = filterDeveloperModuleTests(
            tests = tests,
            category = DeveloperTestCategory.ACADEMIC,
            statusFilter = DeveloperTestStatusFilter.ATTENTION,
            query = "schedule",
        )

        assertEquals(listOf("jw.schedule"), filtered.map { it.id })
    }

    @Test
    fun `batch progress handles empty partial and complete states`() {
        assertEquals(0f, DeveloperModuleBatchProgress(total = 0, completed = 0).fraction)
        assertEquals(0.5f, DeveloperModuleBatchProgress(total = 4, completed = 2).fraction)
        assertEquals(1f, DeveloperModuleBatchProgress(total = 2, completed = 3).fraction)
        assertEquals(0.5f, DeveloperNetworkBatchProgress(total = 6, completed = 3).fraction)
    }

    @Test
    fun `network fault target accepts hosts and rejects URLs`() {
        assertNull(developerTargetHostError(""))
        assertNull(developerTargetHostError("*.ahu.edu.cn"))
        assertNull(developerTargetHostError("jw.ahu.edu.cn"))
        assertEquals(
            "仅输入主机名，不要包含协议、端口或路径",
            developerTargetHostError("https://jw.ahu.edu.cn/api"),
        )
    }

    @Test
    fun `single network export is sanitized defensively`() {
        val host = NetworkHostSpec(
            id = "test",
            displayName = "Test",
            url = "https://example.com/student/2023123456?token=secret",
            category = NetworkDiagnosticCategory.PUBLIC_DATA,
        )
        val result = NetworkDiagnosticResult(
            hostSpec = host,
            status = NetworkDiagnosticStatus.FAILED,
            startedAtEpochMillis = 1L,
            http = com.ahu_plus.data.developer.NetworkHttpResult(
                method = NetworkProbeMethod.HEAD,
                requestedUrl = host.redactedUrl,
                finalUrl = "https://example.com/student/2023123456?token=secret",
                status = NetworkDiagnosticStatus.FAILED,
                error = NetworkDiagnosticError(
                    kind = NetworkDiagnosticErrorKind.NETWORK_IO,
                    type = "IOException",
                    message = "password=hunter2",
                ),
            ),
        )

        val exported = networkResultExportText(result)

        assertFalse(exported.contains("2023123456"))
        assertFalse(exported.contains("secret"))
        assertFalse(exported.contains("hunter2"))
    }

    private fun moduleTest(
        id: String,
        title: String,
        category: DeveloperTestCategory,
        status: DeveloperTestStatus,
    ) = DeveloperModuleTest(
        id = id,
        category = category,
        title = title,
        description = "test description",
        risk = DeveloperTestRisk.LOCAL_ONLY,
        status = status,
    )
}
