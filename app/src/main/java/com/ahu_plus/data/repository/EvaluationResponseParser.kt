package com.ahu_plus.data.repository

import com.ahu_plus.data.model.evaluation.CheckSubmitResult
import com.ahu_plus.data.model.evaluation.EvaluationOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestion
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.ByteString.Companion.decodeBase64

/** 评教响应的纯解析层，便于用 HAR 结构做 JVM 回归测试。 */
internal object EvaluationResponseParser {

    fun isJwtUsable(jwt: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val payload = jwtPayload(jwt) ?: return false
        val exp = payload.get("exp")?.asLong ?: return false
        return exp > nowEpochSeconds + 60
    }

    fun currentSemesterId(jwt: String): String? =
        jwtPayload(jwt)?.get("currentSemesterId")?.asString?.takeIf { it.isNotBlank() }

    fun parseSemesters(body: String): List<EvaluationSemester> {
        val root = parseSuccessfulRoot(body, "semester")
        val arr = root.getAsJsonArray("data") ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.string("id", "semesterId", "value") ?: return@mapNotNull null
            val name = obj.string("nameZh", "name", "semesterName", "label") ?: id
            EvaluationSemester(id = id, name = name)
        }
    }

    fun parseTasks(body: String): List<TeacherEvaluationTask> {
        val root = parseSuccessfulRoot(body, "task search")
        val data = root.get("data") ?: return emptyList()
        val courses = when {
            data.isJsonArray -> data.asJsonArray
            data.isJsonObject -> data.asJsonObject.array("data", "taskList", "list", "records")
            else -> null
        } ?: return emptyList()

        return courses.flatMap { courseElement ->
            val course = courseElement.asJsonObject
            val taskList = course.getAsJsonArray("taskList")
            if (taskList == null) {
                parseTask(course, null, null, null)
            } else {
                val courseName = course.string("courseName", "lessonName", "lessonNameZh")
                val courseCode = course.string("lessonCode", "courseCode")
                val semesterId = course.string("semesterId")
                taskList.flatMap { taskElement ->
                    parseTask(taskElement.asJsonObject, courseName, courseCode, semesterId)
                }
            }
        }
    }

    fun parseQuestionnaire(body: String): EvaluationQuestionnaire {
        val root = parseSuccessfulRoot(body, "questionnaire")
        val data = root.getAsJsonObject("data")
            ?: throw EvaluationApiException("questionnaire 缺 data: $body")
        val id = data.string("questionnaireId", "id")
            ?: throw EvaluationApiException("questionnaire 缺 id")
        val name = data.string("nameZh", "questionnaireName", "name").orEmpty()
        // HAR 中 status=false 表示模板状态，不是提交 payload 的 enable；Web 端固定提交 true。
        val enable = data.get("enable")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
        val questionsElement = data.get("questions")
        val questionsArray = when {
            questionsElement == null || questionsElement.isJsonNull -> JsonArray()
            questionsElement.isJsonArray -> questionsElement.asJsonArray
            questionsElement.isJsonPrimitive -> runCatching {
                JsonParser.parseString(questionsElement.asString).asJsonArray
            }.getOrNull() ?: JsonArray()
            else -> JsonArray()
        }
        val questions = questionsArray.mapNotNull { element ->
            runCatching { parseQuestion(element.asJsonObject) }.getOrNull()
        }.sortedBy { it.orderNum }
        return EvaluationQuestionnaire(id, name, enable, questions)
    }

    fun parseCheckResult(body: String): CheckSubmitResult {
        val root = parseSuccessfulRoot(body, "check-submit")
        val data = root.get("data")
        val message = data?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive) it.asString else it.toString()
        }?.takeIf { it.isNotBlank() }
        // code=0 只代表请求成功；data 非空是业务拦截文案，不能继续 submit。
        return CheckSubmitResult(code = 0, message = message, pass = message == null)
    }

    fun requireNoBadwords(body: String) {
        val root = parseSuccessfulRoot(body, "badword")
        val hits = when (val data = root.get("data")) {
            null -> emptyList()
            else -> when {
                data.isJsonNull -> emptyList()
                data.isJsonPrimitive -> listOf(data.asString)
                data.isJsonArray -> data.asJsonArray.mapNotNull { it.asStringOrNull() }
                else -> listOf(data.toString())
            }
        }.filter { it.isNotBlank() }
        if (hits.isNotEmpty()) {
            throw EvaluationApiException("检测到敏感词：${hits.joinToString("、")}")
        }
    }

    private fun parseTask(
        task: JsonObject,
        courseNameFallback: String?,
        courseCodeFallback: String?,
        semesterIdFallback: String?,
    ): List<TeacherEvaluationTask> {
        val questionnaireId = task.string("evaluationQuestionnaireId", "questionnaireId")
            ?: return emptyList()
        val questionnaireName = task.string("evaluationQuestionnaireName", "questionnaireName")
            ?: "评教问卷"
        val courseName = task.string("courseName", "course") ?: courseNameFallback ?: "未知课程"
        val courseCode = task.string("courseCode") ?: courseCodeFallback
        val semesterId = task.string("semesterId") ?: semesterIdFallback
        val teachers = task.getAsJsonArray("teachers")

        if (teachers != null && teachers.size() > 0) {
            return teachers.mapNotNull { teacherElement ->
                val teacher = teacherElement.asJsonObject
                val taskId = teacher.string("stdSumTaskId") ?: task.string("stdSumTaskId")
                    ?: return@mapNotNull null
                TeacherEvaluationTask(
                    stdSumTaskId = taskId,
                    courseName = courseName,
                    courseCode = courseCode,
                    teacherName = teacher.string("teacherName", "name") ?: "未知教师",
                    teacherId = teacher.string("teacherId", "id"),
                    semesterId = semesterId,
                    evaluationQuestionnaireId = questionnaireId,
                    evaluationQuestionnaireName = questionnaireName,
                    status = teacher.string("status", "taskStatus")
                        ?: task.string("status", "taskStatus"),
                )
            }
        }

        val taskId = task.string("stdSumTaskId") ?: return emptyList()
        return listOf(
            TeacherEvaluationTask(
                stdSumTaskId = taskId,
                courseName = courseName,
                courseCode = courseCode,
                teacherName = task.string("teacherName", "teacher") ?: "未知教师",
                teacherId = task.string("teacherId"),
                semesterId = semesterId,
                evaluationQuestionnaireId = questionnaireId,
                evaluationQuestionnaireName = questionnaireName,
                status = task.string("status", "taskStatus"),
            )
        )
    }

    private fun parseQuestion(obj: JsonObject): EvaluationQuestion {
        val attr = obj.getAsJsonObject("attribute")
        // Web 提交使用 attribute.id（如 1/9），questionItemId UUID 只是指标库 ID。
        val id = attr?.string("id")
            ?: obj.string("questionId", "id")
            ?: attr?.string("questionItemId")
            ?: throw EvaluationApiException("question 缺 id")
        val content = attr?.string("name") ?: obj.string("content", "title", "text").orEmpty()
        val type = obj.get("type")?.asIntOrNull()
            ?: attr?.get("typeId")?.asIntOrNull()
            ?: when (attr?.get("typeName")?.asString?.lowercase()) {
                "input", "text", "textarea", "fill" -> 4
                else -> 1
            }
        val required = attr?.get("required")?.asBoolean ?: obj.get("required")?.asBoolean ?: false
        val order = obj.get("index")?.asIntOrNull()
            ?: obj.get("orderNum")?.asIntOrNull()
            ?: obj.get("order")?.asIntOrNull()
            ?: 0
        val score = attr?.get("score")?.asDoubleOrNull()
            ?: obj.get("score")?.asDoubleOrNull()
            ?: 0.0
        val options = (obj.getAsJsonArray("options") ?: JsonArray()).mapNotNull { element ->
            runCatching {
                val option = element.asJsonObject
                EvaluationOption(
                    optionId = option.string("optionId", "id")
                        ?: throw EvaluationApiException("option 缺 id"),
                    content = option.string("value", "content", "text").orEmpty(),
                    optionScore = option.get("optionScore")?.asDoubleOrNull() ?: 0.0,
                )
            }.getOrNull()
        }
        return EvaluationQuestion(id, content, type, required, order, score, options)
    }

    private fun parseSuccessfulRoot(body: String, operation: String): JsonObject {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: throw EvaluationApiException("$operation 响应不是 JSON: $body")
        val code = root.get("code")?.asInt ?: -1
        if (code != 0) {
            val message = root.string("msg", "message") ?: body
            if (code == 4001 || message.contains("login", ignoreCase = true)) {
                throw EvaluationAuthException("评教登录已失效: $message")
            }
            throw EvaluationApiException("$operation code=$code: $message")
        }
        return root
    }

    private fun jwtPayload(jwt: String): JsonObject? {
        val payloadPart = jwt.split('.').getOrNull(1) ?: return null
        val decoded = payloadPart.decodeBase64()?.utf8() ?: return null
        return runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
    }

    private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.array(vararg names: String): JsonArray? =
        names.firstNotNullOfOrNull { getAsJsonArray(it) }

    private fun JsonElement.asIntOrNull(): Int? = runCatching { asInt }.getOrNull()
    private fun JsonElement.asDoubleOrNull(): Double? = runCatching { asDouble }.getOrNull()
    private fun JsonElement.asStringOrNull(): String? = runCatching { asString }.getOrNull()
}
