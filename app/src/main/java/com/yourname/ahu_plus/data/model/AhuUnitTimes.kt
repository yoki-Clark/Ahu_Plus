package com.yourname.ahu_plus.data.model

import java.time.LocalTime

/**
 * 安大标准节次时间表 + 工具方法。
 *
 * 用于空教室查询中：确定当前节次 → 计算剩余节次 → 格式化时间范围。
 *
 * 2026-06-23 修复：与教务处 `CourseRepository.defaultUnitTimes()` 时间表对齐。
 * 旧版本第 1 节为 08:20-09:05（与教务实际课表错位 20 分钟），现已改为 08:00-08:45，
 * 5 节上午 + 5 节下午 + 3 节晚上，与教务处默认布局保持一致，避免空教室与课表页面
 * 显示同一节次的时间范围不一致。
 */
object AhuUnitTimes {
    /** 节次编号 → (开始时间, 结束时间)，格式 "HH:mm"。
     *  来源:教务处 CourseRepository.defaultUnitTimes() (HHMM 整数 → "HH:mm" 字符串)。 */
    val UNIT_TO_TIME: Map<Int, Pair<String, String>> = mapOf(
        1 to ("08:00" to "08:45"),
        2 to ("08:50" to "09:35"),
        3 to ("09:50" to "10:35"),
        4 to ("10:40" to "11:25"),
        5 to ("11:30" to "12:15"),
        6 to ("14:00" to "14:45"),
        7 to ("14:50" to "15:35"),
        8 to ("15:50" to "16:35"),
        9 to ("16:40" to "17:25"),
        10 to ("17:30" to "18:15"),
        11 to ("19:00" to "19:45"),
        12 to ("19:50" to "20:35"),
        13 to ("20:40" to "21:25")
    )

    private val MAX_UNIT: Int = UNIT_TO_TIME.keys.maxOrNull() ?: 13

    /**
     * 返回当前时刻所在的节次编号，或即将到来的下一节。
     * 若在当日所有节次之后，返回 null。
     */
    fun getCurrentUnit(now: LocalTime = LocalTime.now()): Int? {
        val nowMinutes = now.hour * 60 + now.minute
        var nextUpcoming: Int? = null
        for ((unit, time) in UNIT_TO_TIME) {
            val startMinutes = parseMinutes(time.first)
            val endMinutes = parseMinutes(time.second)
            if (nowMinutes in startMinutes..endMinutes) return unit
            if (nowMinutes < startMinutes && nextUpcoming == null) nextUpcoming = unit
        }
        return nextUpcoming
    }

    /** 返回从 [fromUnit] 开始到当日最后一节的节次编号列表。 */
    fun getRemainingUnits(fromUnit: Int): List<Int> =
        UNIT_TO_TIME.keys.filter { it >= fromUnit }.sorted()

    /**
     * 格式化节次范围，如 "第 5-8 节 (14:00-17:25)"。
     */
    fun formatUnitRange(units: List<Int>): String {
        if (units.isEmpty()) return ""
        val first = UNIT_TO_TIME[units.first()] ?: return ""
        val last = UNIT_TO_TIME[units.last()] ?: return ""
        return "第 ${units.first()}-${units.last()} 节 (${first.first}-${last.second})"
    }

    /** 格式化单节时间，如 "14:00-14:45"。 */
    fun formatUnitTime(unit: Int): String {
        val (start, end) = UNIT_TO_TIME[unit] ?: return ""
        return "$start-$end"
    }

    /** 当日总节次（用于可视化全宽）。 */
    fun totalUnits(): Int = MAX_UNIT

    private fun parseMinutes(s: String): Int {
        val parts = s.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
