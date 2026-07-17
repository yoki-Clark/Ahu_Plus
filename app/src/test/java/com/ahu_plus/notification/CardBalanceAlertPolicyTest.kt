package com.ahu_plus.notification

import com.ahu_plus.data.model.BillRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CardBalanceAlertPolicyTest {
    @Test
    fun canteenAverageUsesRecentActiveCanteenDaysOnly() {
        val now = LocalDateTime.of(2026, 7, 17, 12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val bills = listOf(
            bill("北二区食堂一楼-扫码支付", -1200, "2026-07-16 12:00:00"),
            bill("北二区食堂一楼-扫码支付", -800, "2026-07-16 18:00:00"),
            bill("南区食堂-扫码支付", -1000, "2026-07-15 12:00:00"),
            bill("校园超市-扫码支付", -5000, "2026-07-16 14:00:00"),
            bill("北二区食堂退款", 600, "2026-07-16 13:00:00"),
            bill("北二区食堂一楼-扫码支付", -9999, "2026-06-01 12:00:00"),
        )

        assertEquals(15.0, recentCanteenDailyAverage(bills, 30, now), 0.001)
    }

    @Test
    fun effectiveThresholdFallsBackToFixedWhenThereIsNoCanteenHistory() {
        assertEquals(
            25.0,
            effectiveCardBalanceThreshold(
                mode = CardBalanceAlertMode.CANTEEN_DAILY_AVERAGE,
                fixedThreshold = 25.0,
                bills = emptyList(),
                lookbackDays = 30,
            ),
            0.001,
        )
    }

    private fun bill(resume: String, cents: Int, time: String) = BillRecord(
        resume = resume,
        tranAmt = cents,
        effectDateStr = time,
        turnoverType = if (cents < 0) "消费" else "退款",
    )
}
