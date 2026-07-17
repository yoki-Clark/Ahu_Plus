package com.ahu_plus.notification

import com.ahu_plus.data.model.BillRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class CardBalanceAlertMode {
    FIXED,
    CANTEEN_DAILY_AVERAGE;

    companion object {
        fun fromStored(value: String?): CardBalanceAlertMode =
            entries.firstOrNull { it.name == value } ?: FIXED
    }
}

internal fun effectiveCardBalanceThreshold(
    mode: CardBalanceAlertMode,
    fixedThreshold: Double,
    bills: List<BillRecord>,
    lookbackDays: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Double {
    if (mode == CardBalanceAlertMode.FIXED) return fixedThreshold
    return recentCanteenDailyAverage(bills, lookbackDays, nowMillis)
        .takeIf { it > 0.0 }
        ?: fixedThreshold
}

internal fun recentCanteenDailyAverage(
    bills: List<BillRecord>,
    lookbackDays: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Double {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val firstDate = today.minusDays(lookbackDays.coerceIn(7, 90).toLong() - 1L)
    val expensesByDay = bills.mapNotNull { record ->
        val date = parseBillDate(record.effectDateStr, record.jndatetimeStr) ?: return@mapNotNull null
        if (date.isBefore(firstDate) || date.isAfter(today)) return@mapNotNull null
        val searchText = listOf(
            record.resume,
            record.toMerchant,
            record.locationName,
            record.turnoverType,
            record.consumeTypeName.orEmpty(),
        ).joinToString(" ")
        val isCanteen = searchText.contains("食堂") || searchText.contains("餐厅")
        val isRefund = listOf("退款", "退费", "充值", "转入", "入账").any(searchText::contains)
        val isExpense = record.tranAmt < 0 || listOf("消费", "支付", "扣款").any(searchText::contains)
        if (!isCanteen || isRefund || !isExpense) return@mapNotNull null
        date to abs(record.tranAmt) / 100.0
    }.groupBy({ it.first }, { it.second })

    if (expensesByDay.isEmpty()) return 0.0
    return expensesByDay.values.sumOf { it.sum() } / expensesByDay.size
}

private fun parseBillDate(primary: String, fallback: String): LocalDate? {
    val value = primary.ifBlank { fallback }.trim()
    if (value.isBlank()) return null
    return BILL_DATE_FORMATS.firstNotNullOfOrNull { formatter ->
        runCatching { java.time.LocalDateTime.parse(value, formatter).toLocalDate() }.getOrNull()
    }
}

private val BILL_DATE_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
)
