package com.ahu_plus.data.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeveloperTestSummaryTest {
    @Test
    fun `summary separates assertions from skipped and cancelled checks`() {
        val tests = listOf(
            test("local.pass", DeveloperTestCategory.LOCAL, DeveloperTestStatus.PASSED, 20L, 100L),
            test("local.skip", DeveloperTestCategory.LOCAL, DeveloperTestStatus.SKIPPED, 5L, 200L),
            test("jw.fail", DeveloperTestCategory.ACADEMIC, DeveloperTestStatus.FAILED, 80L, 300L),
            test("jw.timeout", DeveloperTestCategory.ACADEMIC, DeveloperTestStatus.TIMED_OUT, 120L, 400L),
            test("public.cancel", DeveloperTestCategory.PUBLIC_SERVICE, DeveloperTestStatus.CANCELLED, null, 500L),
            test("public.pending", DeveloperTestCategory.PUBLIC_SERVICE, DeveloperTestStatus.NOT_RUN),
        )

        val summary = summarizeDeveloperTests(tests)

        assertEquals(6, summary.total)
        assertEquals(5, summary.completed)
        assertEquals(3, summary.attention)
        assertEquals(33, summary.passRatePercent)
        assertEquals(225L, summary.totalDurationMillis)
        assertEquals(500L, summary.lastRunAtMillis)
        assertEquals("jw.timeout", summary.slowestTest?.id)
        assertEquals(2, summary.categories.first { it.category == DeveloperTestCategory.ACADEMIC }.attention)
    }

    @Test
    fun `summary has no pass rate before an assertion completes`() {
        val summary = summarizeDeveloperTests(
            listOf(test("local.pending", DeveloperTestCategory.LOCAL, DeveloperTestStatus.NOT_RUN)),
        )

        assertNull(summary.passRatePercent)
        assertNull(summary.lastRunAtMillis)
        assertNull(summary.slowestTest)
    }

    private fun test(
        id: String,
        category: DeveloperTestCategory,
        status: DeveloperTestStatus,
        durationMillis: Long? = null,
        lastRunAtMillis: Long? = null,
    ) = DeveloperModuleTest(
        id = id,
        category = category,
        title = id,
        description = "test",
        risk = DeveloperTestRisk.LOCAL_ONLY,
        status = status,
        durationMillis = durationMillis,
        lastRunAtMillis = lastRunAtMillis,
    )
}
