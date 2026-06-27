package com.yourname.ahu_plus.data.model

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