package com.ahu_plus.data.model.jw

import java.time.LocalDate

/**
 * 课表「假期感知」纯逻辑。
 *
 * 目标(2026-07-31):
 *  - 寒暑假期间打开课表,默认展示**下一个学期**的课表;
 *  - 但新学期还没到正式教学周时,不把当天误算成"有课"(假期实际不上课)。
 *
 * 学期日期来自 JW `get-data` 返回的 [SemesterInfo.startDate/endDate]
 * (学期列表 HTML 只有 id + 名称,没有日期,因此优先用已加载/已缓存学期的日期)。
 *
 * 规则:
 *  1. 今天落在某个已知学期日期范围内 → 该学期为当前学期(非假期),用服务器周次;
 *  2. 今天不在任何已知范围内(假期):
 *     - 优先选 startDate 晚于今天且最近的一个学期(下一个学期);
 *     - 只有"已结束学期"的日期时,按学期 id 顺序取它的下一个学期;
 *     - 都无法判断时回退 [fallbackId],并标记为假期;
 *  3. 完全没有日期信息时无法判断假期 → 信任服务器周次(保持旧行为)。
 *
 * 2026-07-31 补充硬规则:学校教学周一般 18 周 + 1 周考试周,正常不可能算出
 * 第 20 周及以上的结果。因此服务器返回的周次 ≥ [MAX_TEACHING_WEEK] 时,
 * 无论日期是否齐全,一律判定为假期(当前周记 0、默认选下一个学期)。
 */
object SemesterScheduleResolver {

    /** 教学周 + 考试周的理论上限。超过即超出学期范围,必然是假期。 */
    const val MAX_TEACHING_WEEK = 20

    /** 学期解析结果。 */
    data class Resolution(
        /** 默认应展示的学期 ID。 */
        val semesterId: Int,
        /** true = 今天不在任何已知学期的教学周内(假期)。 */
        val vacation: Boolean,
    )

    /**
     * 解析默认应展示的学期。
     *
     * @param semesters          远端学期列表(id + 名称,无日期)
     * @param knownDatedSemesters 已知日期的学期(已加载/缓存的学期详情)
     * @param today               今天
     * @param fallbackId          全部无法判断时的兜底学期 ID
     */
    fun resolveDefaultSemester(
        semesters: List<SemesterInfo>,
        knownDatedSemesters: List<SemesterInfo>,
        today: LocalDate,
        fallbackId: Int,
        /**
         * 已知「服务器周次已超教学周上限(≥ [MAX_TEACHING_WEEK])」的学期 id。
         * 这类学期必然已经结束(即使缺少日期也能判断),用于定位下一个学期。
         */
        overrunSemesterIds: Set<Int> = emptySet(),
    ): Resolution {
        val dated = knownDatedSemesters.filter { s ->
            s.id != null && parseDate(s.startDate) != null && parseDate(s.endDate) != null
        }

        // 1) 今天在某学期范围内 → 该学期为当前学期
        dated.firstOrNull { s ->
            val start = parseDate(s.startDate)!!
            val end = parseDate(s.endDate)!!
            !today.isBefore(start) && !today.isAfter(end)
        }?.let { return Resolution(it.id!!, vacation = false) }

        // 2) 假期:找 startDate 晚于今天且最近的一个学期(下一个学期)
        val upcoming = dated
            .mapNotNull { s ->
                val id = s.id
                val start = parseDate(s.startDate)
                if (id != null && start != null) id to start else null
            }
            .filter { (_, start) -> start.isAfter(today) }
            .minByOrNull { (_, start) -> start }
        if (upcoming != null) {
            return Resolution(upcoming.first, vacation = true)
        }

        // 3) 已结束学期:按日期判断,或服务器周次已超上限(≥ 20 周)的学期;
        //    取「最后结束」的那个,按学期 id 递增顺序取它的下一个学期
        val sortedIds = semesters.mapNotNull { it.id }.distinct().sorted()
        val endedByDate = dated
            .mapNotNull { s ->
                val id = s.id
                val end = parseDate(s.endDate)
                if (id != null && end != null) id to end else null
            }
            .filter { (_, end) -> end.isBefore(today) }
        val lastEndedId = endedByDate.maxByOrNull { (_, end) -> end }?.first
            ?: overrunSemesterIds.maxOrNull()
        lastEndedId?.let { endedId ->
            val index = sortedIds.indexOf(endedId)
            if (index >= 0 && index + 1 < sortedIds.size) {
                return Resolution(sortedIds[index + 1], vacation = true)
            }
        }

        // 4) 兜底:有日期信息或已确认超周次的学期 → 假期;完全无信息 → 无法判断,信任服务器
        return Resolution(
            fallbackId,
            vacation = dated.isNotEmpty() || overrunSemesterIds.isNotEmpty(),
        )
    }

    /**
     * 计算展示用的"当前周"。
     *
     * 学期日期缺失时信任服务器周次(保持旧行为);日期齐全且今天不在教学周内
     * (开学前/放假后)返回 0 —— 0 表示"今天没有课",课表不再高亮今天、不画当前时间线。
     */
    fun effectiveCurrentWeek(
        semester: SemesterInfo?,
        serverWeek: Int,
        today: LocalDate,
    ): Int {
        // 硬规则:正常教学周不可能到第 20 周及以上,返回 >= 20 必然是假期;
        // 服务器周次 < 1(如 -6)表示新学期还没开始(开学前),同样是假期
        if (serverWeek < 1 || serverWeek >= MAX_TEACHING_WEEK) return 0
        if (semester == null) return serverWeek
        val start = parseDate(semester.startDate) ?: return serverWeek
        val end = parseDate(semester.endDate) ?: return serverWeek
        return if (today.isBefore(start) || today.isAfter(end)) 0 else serverWeek
    }

    /** 该周次是否已超出教学周范围(≥ [MAX_TEACHING_WEEK]),用于 Widget/提醒等读缓存处。 */
    fun isVacationWeek(week: Int): Boolean = week >= MAX_TEACHING_WEEK

    private fun parseDate(value: String?): LocalDate? {
        val day = value?.trim()?.take(10)?.takeIf {
            it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        } ?: return null
        return runCatching { LocalDate.parse(day) }.getOrNull()
    }
}
