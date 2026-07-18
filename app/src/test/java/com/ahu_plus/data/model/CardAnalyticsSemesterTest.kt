package com.ahu_plus.data.model

import com.ahu_plus.data.model.jw.SemesterInfo
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAnalyticsSemesterTest {
    @Test
    fun `semester analysis uses academic calendar boundaries`() {
        val bills = listOf(
            expense("2026-02-20 12:00:00", "before"),
            expense("2026-02-24 12:00:00", "inside"),
        )
        val semester = SemesterInfo(
            id = 112,
            nameZh = "2025-2026学年第二学期",
            code = "2025-2026-2",
            schoolYear = "2025-2026",
            startDate = "2026-02-23",
            endDate = "2026-07-05",
            season = "春",
        )

        val report = bills.toAnalyticsReport(
            today = date("2026-03-01 12:00:00"),
            academicSemesters = listOf(semester),
        )

        assertEquals(1, report.semesterPeriods.size)
        assertEquals("2026-02-23", report.semesterPeriods.single().startDay)
        assertEquals("2026-07-05", report.semesterPeriods.single().endDay)
        assertEquals(1, report.currentSemester?.expenseCount)
    }

    @Test
    fun `semester analysis is unavailable without registrar dates`() {
        val report = listOf(expense("2026-03-01 12:00:00", "expense"))
            .toAnalyticsReport(today = date("2026-03-01 12:00:00"))

        assertTrue(report.semesterPeriods.isEmpty())
    }

    private fun expense(time: String, id: String) = BillRecord(
        resume = "食堂消费",
        tranAmt = -100,
        effectDateStr = time,
        orderId = id,
    )

    private fun date(value: String) =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(value)!!
}
