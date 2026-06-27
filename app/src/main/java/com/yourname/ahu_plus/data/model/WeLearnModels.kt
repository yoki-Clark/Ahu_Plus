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

data class WeLearnSco(
    val id: String,
    val location: String,
    val isComplete: Boolean,     // true = 已完成,跳过
    val isVisible: Boolean,      // false = 未开放,跳过
)

/** SCORM 提交返回值(给 UI 显示双路成功/失败) */
data class WeLearnSubmitResult(
    val scoId: String,
    val location: String,
    val way1Ok: Boolean,         // setscoinfo 成功
    val way2Ok: Boolean,         // savescoinfo160928 成功
)