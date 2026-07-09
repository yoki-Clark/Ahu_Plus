package com.ahu_plus.data.model

/**
 * WeLearn (welearn.sflep.com) 数据模型。
 *
 * 三层结构:课程 → 单元 → 章节(SCO),通过 courseCode/uid/classid 串联。
 * SCORM 提交时的 cmi JSON 模板见 WeLearnStudyRepository。
 */

data class WeLearnCourse(
    val cid: String,
    val name: String,
    val per: Int,                // 完成度 0-100
    val uid: String? = null,     // 拉课程详情后才会有
    val classid: String? = null,
    val studiedSeconds: Int = 0, // 已学习总时长(秒),从 StudyStat scogeneral.Hour*3600+Minute*60
)

data class WeLearnUnit(
    val unitIdx: Int,
    val unitName: String,
    val name: String,
    val visible: Boolean,
)

/** SCO 三态:待刷 / 已完成 / 未开放。三者独立区分(API 的 iscomplete 含 "未" 都算未完成) */
enum class WeLearnScoStatus { TODO, COMPLETED, LOCKED }

data class WeLearnSco(
    val id: String,
    val name: String,         // 短名,如 "A. Communicate"(location 的最后一段)
    val location: String,     // 全路径,如 "Unit 1 ... > Listening > ... > A. Communicate"
    val status: WeLearnScoStatus,
    val learntimeSeconds: Int = 0,   // 本节已学时长(秒),从 scoLeaves info[].learntime 解析("HH:MM:SS")
    val completetime: String? = null, // 最后完成时间(原始字符串 "YYYY-MM-DD HH:MM:SS"),空=未完成
) {
    /** 已完成 或 未开放 都跳过提交(供刷课引擎用) */
    val isSkippable: Boolean get() = status == WeLearnScoStatus.COMPLETED || status == WeLearnScoStatus.LOCKED
}

/**
 * 单元 + 其章节列表,供课程详情页(WeLearnCourseDetailScreen)树形显示。
 * 与 [WeLearnRepository.CourseTree] 平行,后者只用于刷课引擎,不带章节。
 */
data class WeLearnUnitScos(
    val unit: WeLearnUnit,
    val scos: List<WeLearnSco>,
)

/**
 * 把 "HH:MM:SS" 格式的时长字符串解析成秒数(用于 scoLeaves.learntime)。
 * ponytail: 字段缺/格式异常 → 0,不抛。
 */
fun parseHmsToSeconds(hms: String?): Int {
    if (hms.isNullOrBlank()) return 0
    val parts = hms.split(":")
    if (parts.size != 3) return 0
    return runCatching {
        parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
    }.getOrDefault(0)
}

/**
 * 人类友好的秒数格式化:1 小时以上 "X 小时 Y 分",否则 "Y 分 Z 秒",0 显示 "—"。
 */
fun formatStudiedDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 && m > 0 -> "${h} 小时 ${m} 分钟"
        h > 0 -> "${h} 小时"
        m > 0 && s > 0 -> "${m} 分 ${s} 秒"
        m > 0 -> "${m} 分钟"
        else -> "${s} 秒"
    }
}

/** sco 级小字时长(空间窄):1 小时以上 "Xh Ym",否则 "Ym Zs" 或 "Zs" */
fun formatScoLearntime(seconds: Int): String? {
    if (seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "已学 ${h}h${m}m"
        m > 0 -> "已学 ${m}分${s}秒"
        else -> "已学 ${s}秒"
    }
}
