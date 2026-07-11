package com.ahu_plus.data.model.evaluation

import com.google.gson.JsonElement

/**
 * 评教 (jw.ahu.edu.cn/eams5-evaluation-service) 全部 DTO 集合。
 *
 * 数据流:
 *   EvaluationRepository
 *     ├─ GET /common/drop-down/stu_semester           → List<EvaluationSemester>
 *     ├─ GET /for-student/.../search                  → List<TeacherEvaluationTask>
 *     ├─ GET /for-student/.../search-questionnaire/{qid} → EvaluationQuestionnaire
 *     └─ POST /check-submit / /submit                 → SubmissionPayload / Result
 *
 * 字段命名严格按后端返回(Gson 默认映射,不做 @SerializedName 改装):
 *   - questionnaireId / evaluationQuestionnaireId  → 后端 ID 字段
 *   - type = 1 (单选) / type = 4 (文本)
 *
 * 后续文档参考:`E:\cc项目\0.非项目使用\26.7\7.10-23\智慧安大评教链路分析.md`
 */

// ── 学期 ─────────────────────────────────────────────

/**
 * 学生可选学期。`/common/drop-down/stu_semester` 返回。
 * `id` 数字(`112` 等)用作 `/search?semesterId=...` 的查询参数。
 */
data class EvaluationSemester(
    val id: String,
    val name: String,
)

// ── 教师任务(列表项)──────────────────────────────────

/**
 * 一位老师对应一门课程的一个评教任务。
 *
 * 后端返回的 `taskList[].teachers[]` 可能包含多位老师，Repository 会按教师展开，
 * UI 再按课程聚合为可折叠课程卡。
 *
 * 用户要求:一个课程可能有多个老师,每个老师单独评教。
 * 后端天然就是「每位老师一条 task」,UI 直接按 `stdSumTaskId` 1:1 渲染。
 */
data class TeacherEvaluationTask(
    val stdSumTaskId: String,
    val courseName: String,
    val courseCode: String?,
    val teacherName: String,
    val teacherId: String?,
    val semesterId: String?,
    val evaluationQuestionnaireId: String,
    val evaluationQuestionnaireName: String,
    /**
     * 后端 status 字段: TO_REVIEW / FINISHED 等。UI 用其判断"已评/未评"。
     * 不强约束枚举,字符串透传,由 UI 决定 chip 颜色。
     */
    val status: String?,
)

// ── 问卷模板 ─────────────────────────────────────────

/** 问卷:一个模板对应一种问卷类型(实验课 / 理论课)。 */
data class EvaluationQuestionnaire(
    val questionnaireId: String,
    val questionnaireName: String,
    val enable: Boolean,
    val questions: List<EvaluationQuestion>,
)

/**
 * 单题。
 *
 * @param type 1 = 单选(`questionAnsExpSaveList` 填 optionId)
 *             4 = 文本(`answer` 填字符串,提交前过 test-badword)
 */
data class EvaluationQuestion(
    val questionId: String,
    val content: String,
    /** 1 = 单选,4 = 文本 */
    val type: Int,
    val required: Boolean,
    val orderNum: Int,
    /** 单题总分(冗余字段,后端校验时也会下发) */
    val score: Double,
    val options: List<EvaluationOption>,
)

/** 单题选项(单选时用)。 */
data class EvaluationOption(
    val optionId: String,
    val content: String,
    val optionScore: Double,
)

// ── 提交载荷 ─────────────────────────────────────────

/**
 * 提交给 /check-submit 与 /submit 的 payload。
 *
 * 服务端真正落库的是 `evaluationQuestionnaireRes.questionAnsSaveList[]`,
 * `stdSumTaskId` 与 `anonymous` 是顶层冗余。
 */
data class SubmissionPayload(
    val stdSumTaskId: String,
    val anonymous: Boolean,
    val evaluationQuestionnaireRes: QuestionnaireResponse,
)

/**
 * 问卷响应(对应后端 `evaluationQuestionnaireRes`)。
 *
 * 顶层冗余字段(`answer`/`score`)前端不计算,照抄 web 默认值。
 */
data class QuestionnaireResponse(
    val questionnaireId: String,
    val questionnaireName: String,
    val enable: Boolean,
    /** 恒 `"[]"`,后端冗余字段 */
    val answer: String,
    /** 恒 `0`,后端冗余字段 */
    val score: Double,
    val questionAnsSaveList: List<QuestionAnswer>,
)

/** 单题作答 — UI 草稿 → 提交 payload。 */
data class QuestionAnswer(
    val questionId: String,
    val questionnaireId: String,
    val type: String,
    /** 该题选中项的 optionScore,冗余但建议带上 */
    val score: Double,
    /**
     * type=1 单选 → null(选项在 questionAnsExpSaveList)
     * type=4 文本 → 文本字符串
     */
    val answer: String?,
    val questionAnsExpSaveList: List<QuestionAnswerOption>,
)

/** 单题选项作答。type=1 单选时填一个;type=4 文本时为空列表。 */
data class QuestionAnswerOption(
    val optionId: String,
    val questionnaireId: String,
)

// ── 响应包装 ─────────────────────────────────────────

/** /check-submit 响应:code=0,data 是业务文案("问卷选项连续相同"等) */
data class CheckSubmitResult(
    val code: Int,
    val message: String?,
    val pass: Boolean,
)

/**
 * 通用响应包装 — 多数接口返回 `{ code:0, data: <T>, message?: string }`。
 * 用 JsonElement 暴露 `data`,Repository 层按需解析。
 */
data class EvaluationEnvelope<T>(
    val code: Int,
    val data: T?,
    val message: String?,
)

// ── UI 草稿状态 ──────────────────────────────────────

/**
 * UI 层答案草稿。ViewModel 内部维护 `Map<questionId, Answer>`,
 * 提交时再转 [QuestionAnswer]。
 */
sealed interface EvaluationAnswer {
    /** 单选 */
    data class Option(val questionId: String, val optionId: String, val optionScore: Double) :
        EvaluationAnswer

    /** 文本 */
    data class Text(val questionId: String, val text: String) : EvaluationAnswer
}

// ── 一键填写评语选项 ─────────────────────────────────────

/**
 * 评教「一键填写」的评语备选项。
 *
 * 弹窗里以单选列表呈现,选中后把 [text] 填到所有文本题(type=4)。
 * 单选题策略固定为「6 优 + 1 良,良在前 7 题中随机」,不暴露给用户配置。
 *
 * @param id 唯一标识,内置选项为 `"default"`,用户新增为 UUID
 * @param builtIn 内置选项不能删;用户新增通过 SessionManager 持久化
 */
data class EvaluationCommentOption(
    val id: String,
    val text: String,
    val builtIn: Boolean = false,
)
