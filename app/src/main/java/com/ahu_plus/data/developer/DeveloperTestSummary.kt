package com.ahu_plus.data.developer

data class DeveloperTestCategorySummary(
    val category: DeveloperTestCategory,
    val total: Int,
    val completed: Int,
    val passed: Int,
    val attention: Int,
    val skipped: Int,
    val running: Int,
)

data class DeveloperTestSuiteSummary(
    val total: Int,
    val completed: Int,
    val passed: Int,
    val failed: Int,
    val timedOut: Int,
    val skipped: Int,
    val cancelled: Int,
    val running: Int,
    val notRun: Int,
    val totalDurationMillis: Long,
    val lastRunAtMillis: Long?,
    val slowestTest: DeveloperModuleTest?,
    val categories: List<DeveloperTestCategorySummary>,
) {
    val attention: Int
        get() = failed + timedOut + cancelled

    /** Pass rate excludes skipped/cancelled checks because they produced no assertion result. */
    val passRatePercent: Int?
        get() {
            val asserted = passed + failed + timedOut
            return if (asserted == 0) null else ((passed * 100f) / asserted).toInt()
        }
}

fun summarizeDeveloperTests(tests: List<DeveloperModuleTest>): DeveloperTestSuiteSummary {
    val terminalStatuses = setOf(
        DeveloperTestStatus.PASSED,
        DeveloperTestStatus.FAILED,
        DeveloperTestStatus.TIMED_OUT,
        DeveloperTestStatus.SKIPPED,
        DeveloperTestStatus.CANCELLED,
    )
    val attentionStatuses = setOf(
        DeveloperTestStatus.FAILED,
        DeveloperTestStatus.TIMED_OUT,
        DeveloperTestStatus.CANCELLED,
    )
    val categorySummaries = DeveloperTestCategory.entries.mapNotNull { category ->
        val categoryTests = tests.filter { it.category == category }
        if (categoryTests.isEmpty()) return@mapNotNull null
        DeveloperTestCategorySummary(
            category = category,
            total = categoryTests.size,
            completed = categoryTests.count { it.status in terminalStatuses },
            passed = categoryTests.count { it.status == DeveloperTestStatus.PASSED },
            attention = categoryTests.count { it.status in attentionStatuses },
            skipped = categoryTests.count { it.status == DeveloperTestStatus.SKIPPED },
            running = categoryTests.count { it.status == DeveloperTestStatus.RUNNING },
        )
    }
    return DeveloperTestSuiteSummary(
        total = tests.size,
        completed = tests.count { it.status in terminalStatuses },
        passed = tests.count { it.status == DeveloperTestStatus.PASSED },
        failed = tests.count { it.status == DeveloperTestStatus.FAILED },
        timedOut = tests.count { it.status == DeveloperTestStatus.TIMED_OUT },
        skipped = tests.count { it.status == DeveloperTestStatus.SKIPPED },
        cancelled = tests.count { it.status == DeveloperTestStatus.CANCELLED },
        running = tests.count { it.status == DeveloperTestStatus.RUNNING },
        notRun = tests.count { it.status == DeveloperTestStatus.NOT_RUN },
        totalDurationMillis = tests.sumOf { it.durationMillis ?: 0L },
        lastRunAtMillis = tests.mapNotNull(DeveloperModuleTest::lastRunAtMillis).maxOrNull(),
        slowestTest = tests.filter { it.durationMillis != null }.maxByOrNull { it.durationMillis ?: 0L },
        categories = categorySummaries,
    )
}
