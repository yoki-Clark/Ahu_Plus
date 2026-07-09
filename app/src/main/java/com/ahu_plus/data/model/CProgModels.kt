package com.ahu_plus.data.model

/**
 * 大学计算机平台(C 语言在线评测系统, issuer w2eesweb)数据模型。
 *
 * 独立认证:学号 + 身份证后 6 位 + 验证码 → JWT + JSESSIONID 双因素。
 * 只读接入:分类计数 → 科目 → 考试/练习列表(jqGrid) → 整卷(题干 + 参考答案)。
 *
 * ⚠️ 合规:进卷(assign/paper3)对"考试/测试"是真正开始一场受监考的考试,
 * 仅"练习(lianxi)"分类无害且带标准答案 —— UI 层只放行练习进卷。
 */

/** 登录第一步 /login/get 返回:userId(uuid) + 第一段 JWT */
data class CProgLoginStep1(
    val userId: String,
    val jwt1: String,
)

/** 5 大分类:练习/测试/实验/作业/考试,含各自计数 */
data class CProgSection(
    val codes: String,   // lianxi / ceshi / shiyan / zuoye / kaoshi
    val title: String,   // 练习 / 测试 / ...
    val counts: String,  // "22"
) {
    /** 只有练习可安全进卷(带标准答案、可重复、不触发监考) */
    val isPracticeSafe: Boolean get() = codes == "lianxi"
}

/** 科目:C语言 / Python程序设计 / ... */
data class CProgSubject(
    val subjectId: String,
    val caption: String,
    val seq: Int = 0,
)

/** 考试/练习列表行(jqGrid rows)。字段名对齐服务端返回,只保留 UI 需要的。 */
data class CProgExamRow(
    val examId: String,
    val examCaption: String,
    val subjectId: String?,
    val subjectCaption: String?,
    val status: Int,               // 用户-考试状态 0=未做 1=进行中 2=已交
    val grade: Double = 0.0,
    val examCreateTime: String?,
    val examClient: String?,       // "1"=客户端 "2"=服务端
)

/** jqGrid 分页响应包装(无 errCode,靠 HTTP 状态判成败) */
data class CProgExamPage(
    val total: Int = 0,      // 总页数
    val records: Int = 0,    // 总记录数
    val page: Int = 1,
    val rows: List<CProgExamRow> = emptyList(),
)

/** 整卷:题型分组 + 元信息 */
data class CProgPaper(
    val examId: String,
    val examCaption: String,
    val subjectCaption: String?,
    val paperQuestionCount: Int = 0,
    val paperQuestionTypeCount: Int = 0,
    val paperGrade: Double = 0.0,
    val paperRemainingTimesHourMinuteSecond: String? = null,
    val questionTypes: List<CProgQuestionType> = emptyList(),
)

/** 一个题型分组(程序设计 CL_P / 程序改错 CL_E / 单选 CL_R …) */
data class CProgQuestionType(
    val questionTypeCaption: String,
    val baseQuestionType: String?,  // CL_P / CL_E / CL_R
    val questionCount: Int,
    val items: List<CProgQuestionItem>,
)

/** 单题:题干 + 参考答案 + (单选)选项 */
data class CProgQuestionItem(
    val id: String,
    val text: String,               // 题干(content 常为空,真正题干在 text)
    val answer: String?,            // 参考答案(改错题以 ^~^ 分隔片段)
    val knowledgeCaption: String?,  // 知识点,如 "/C语言/数据类型"
    val options: List<CProgOption> = emptyList(),
)

/** 单选题选项 */
data class CProgOption(
    val code: String?,     // A/B/C/D
    val content: String?,
)
