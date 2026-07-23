package com.ahu_plus.data.model.jw

/**
 * 中文排课文本解析器（纯函数，JVM 单测；无 Android/网络依赖）。
 *
 * 把 [LessonRecord.scheduleText] 的中文展示文本解析成结构化时段 [Slot]，供周网格课表渲染。
 *
 * **格式依据**（2026-07-23 HAR 实测，见 tools/lesson_search/har_schedule_variety.py 结论）：
 * - 单条记录可含多时段，用 `; `（分号+空格）分隔。
 * - 每个时段字段顺序：**周次 → 星期 → 节次**（`1~16周 星期一 1~2节 博北201`），
 *   `(\d+)~(\d+)周 星期X (\d+)~(\d+)节` 在 410 条里命中 335 条。
 * - 周次写法：范围 `1~16` / 离散 `1,3,5` / 连字符 `1-16`，可带 `单`/`双` 限定单双周。
 * - 星期：`星期一`~`星期六`、`星期日`/`星期天`。
 * - 节次：`X~Y节`，也兼容单节 `X节`。
 * - 61/410 时间为空（待定），解析为空结果。
 *
 * **诚实边界**：字段顺序与分隔符已实测；教室的确切位置（时段串尾 vs 独立 roomSeatText）
 * 未逐一验证，故几何（星期/节次/周次）只依赖已确认信息，教室为尽力而为的标签
 * （优先时段串尾捕获，回退 [LessonScheduleText.roomSeatText]）。解析不了的时段计入
 * [ParseResult.unparsedSegments]，调用方据此把整条记录落到网格下方列表。
 */
object LessonScheduleParser {

    /** 一个可渲染时段：ISO 星期(周一=1…周日=7) + 节次范围 + 生效周次 + 教室。 */
    data class Slot(
        val weekday: Int,
        val startUnit: Int,
        val endUnit: Int,
        val weekIndexes: List<Int>,
        val room: String?,
    )

    /** 解析结果：成功时段 + 未能解析的原始时段串（非空但没匹配上）。 */
    data class ParseResult(
        val slots: List<Slot>,
        val unparsedSegments: List<String>,
    ) {
        /** 至少解析出一个时段。 */
        val hasSlots: Boolean get() = slots.isNotEmpty()

        /** 存在非空但没解析出来的时段（→ 该记录应同时进网格下方列表兜底）。 */
        val hasUnparsed: Boolean get() = unparsedSegments.isNotEmpty()
    }

    /** 星期中文 → ISO 数字。 */
    private val WEEKDAY_MAP = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6,
        '日' to 7, '天' to 7,
    )

    /**
     * 单个时段主正则：周次 → 星期 → 节次 →（可选）教室尾串。
     * - g1 周次串（数字/逗号/~/-/单双）
     * - g2 星期字（一二三四五六日天）
     * - g3 起始节，g4 结束节（可空 → 单节）
     * - g5 节次后的剩余（可能是教室，也可能含教师，调用方再清洗）
     *
     * `周` 与 `星期` 之间用 `[^星]*?` 兜住 `(单)`/`(双)` 等括注（单双也可写在 g1 里，
     * 由 [parseWeeks] 处理，两处任一命中都能过滤单双周）。按时段切分后一段只含一个星期，
     * 故 `[^星]*?` 不会跨时段误吞。
     */
    private val SLOT_REGEX = Regex(
        """([\d,，、~\-－单双]+?)周[^星]*?星期([一二三四五六日天])[^\d]*?(\d+)\s*(?:[~\-－]\s*(\d+))?\s*节\s*(.*)"""
    )

    /** 时段分隔符：分号（半/全角）为主，兼容顿号分隔的多时段串。 */
    private val SEGMENT_SPLIT = Regex("""\s*[;；]\s*""")

    /**
     * 从 [record] 解析。优先含地点的文本（尾串可捞教室），回退纯时间文本；
     * 教室兜底用 [LessonScheduleText.roomSeatText]。
     */
    fun parse(record: LessonRecord): ParseResult {
        val st = record.scheduleText
        val text = st?.dateTimePlaceText?.textZh
            ?: st?.dateTimePlacePersonText?.textZh
            ?: st?.dateTimeText?.textZh
        val fallbackRoom = st?.roomSeatText?.textZh?.trim()?.takeIf { it.isNotBlank() }
        return parseText(text, fallbackRoom)
    }

    /**
     * 解析原始文本（可单测）。[fallbackRoom] 在时段尾串没捞到教室时兜底。
     */
    fun parseText(text: String?, fallbackRoom: String? = null): ParseResult {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return ParseResult(emptyList(), emptyList())

        val slots = ArrayList<Slot>()
        val unparsed = ArrayList<String>()
        for (rawSeg in trimmed.split(SEGMENT_SPLIT)) {
            val seg = rawSeg.trim()
            if (seg.isEmpty()) continue
            val m = SLOT_REGEX.find(seg)
            if (m == null) {
                unparsed.add(seg)
                continue
            }
            val weekday = WEEKDAY_MAP[m.groupValues[2].firstOrNull()]
            val weeks = parseWeeks(m.groupValues[1])
            val startUnit = m.groupValues[3].toIntOrNull()
            val endUnit = m.groupValues[4].toIntOrNull() ?: startUnit
            val room = cleanRoom(m.groupValues[5]) ?: fallbackRoom
            if (weekday == null || startUnit == null || endUnit == null ||
                weeks.isEmpty() || endUnit < startUnit
            ) {
                unparsed.add(seg)
                continue
            }
            slots.add(Slot(weekday, startUnit, endUnit, weeks, room))
        }
        return ParseResult(slots, unparsed)
    }

    /**
     * 解析周次串：`1~16` / `1-16` / `1,3,5` / 混合 `1~4,7,9~11`，可带 `单`/`双` 过滤单双周。
     * 无法解析出任何周次返回空列表（调用方判为未解析）。
     */
    internal fun parseWeeks(raw: String): List<Int> {
        val spec = raw.trim()
        if (spec.isEmpty()) return emptyList()
        val odd = spec.contains('单')
        val even = spec.contains('双')
        // 去掉单双/括号等非数字范围符号，留数字、逗号、范围连字符
        val cleaned = spec
            .replace('，', ',').replace('、', ',')
            .replace('－', '-').replace('～', '~')
            .filter { it.isDigit() || it == ',' || it == '~' || it == '-' }
        val out = LinkedHashSet<Int>()
        for (token in cleaned.split(',')) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val range = t.split('~', '-').mapNotNull { it.trim().toIntOrNull() }
            when {
                range.size >= 2 -> {
                    val a = range.first()
                    val b = range.last()
                    if (a in 1..60 && b in 1..60 && b >= a) for (w in a..b) out.add(w)
                }
                range.size == 1 -> if (range[0] in 1..60) out.add(range[0])
            }
        }
        return when {
            odd && !even -> out.filter { it % 2 == 1 }
            even && !odd -> out.filter { it % 2 == 0 }
            else -> out.toList()
        }
    }

    /** 清洗时段尾串作教室：去首尾分隔符/空白，空则 null（交给 fallbackRoom）。 */
    private fun cleanRoom(raw: String): String? {
        val r = raw.trim().trim('(', ')', '（', '）', ',', '，', '、', '/').trim()
        return r.takeIf { it.isNotBlank() }
    }

    /**
     * 把一条记录的解析时段映射为某一 [week] 周的网格条目 [CourseDisplayItem]。
     * 只产出 weekIndexes 含 [week] 的时段；[colorIndex] 由课程名哈希稳定着色。
     */
    fun displayItemsFor(record: LessonRecord, result: ParseResult, week: Int): List<CourseDisplayItem> {
        val name = record.courseName()
        val colorIndex = Math.abs(name.hashCode()) % 10
        val teachers = record.teacherNames()
        val lessonId = record.id ?: record.code?.hashCode()?.toLong() ?: name.hashCode().toLong()
        return result.slots
            .filter { week in it.weekIndexes }
            .map { slot ->
                CourseDisplayItem(
                    lessonId = lessonId,
                    courseName = name,
                    courseCode = record.course?.code,
                    teacherNames = teachers,
                    room = slot.room,
                    weekday = slot.weekday,
                    startUnit = slot.startUnit,
                    endUnit = slot.endUnit,
                    weekIndexes = slot.weekIndexes,
                    weeksStr = slot.weekIndexes.joinToString(",") + "周",
                    startTime = null,
                    endTime = null,
                    courseType = record.courseType?.nameZh,
                    credits = record.course?.credits,
                    campus = null,
                    colorIndex = colorIndex,
                    lessonDetail = null,
                )
            }
    }

    /** 一条记录跨所有时段覆盖的最大周次（用于周次下拉上界）。0 表示无可解析周次。 */
    fun maxWeek(result: ParseResult): Int =
        result.slots.flatMap { it.weekIndexes }.maxOrNull() ?: 0
}
