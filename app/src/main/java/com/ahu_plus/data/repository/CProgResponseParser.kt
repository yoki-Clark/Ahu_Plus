package com.ahu_plus.data.repository

import com.ahu_plus.data.model.CProgAttempt
import com.ahu_plus.data.model.CProgAttemptPage
import com.ahu_plus.data.model.CProgExamPage
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.data.model.CProgOption
import com.ahu_plus.data.model.CProgPaper
import com.ahu_plus.data.model.CProgQuestionItem
import com.ahu_plus.data.model.CProgQuestionType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.parser.Parser
import java.io.IOException

/** Pure response parsing kept separate so captured payload shapes can be regression-tested on the JVM. */
internal object CProgResponseParser {
    private val paperIdRegex = Regex("""\bpid\s*:\s*["']([^"']+)["']""")
    private val breakTagRegex = Regex("""<br\s*/?>|</(?:p|div|li|pre)>""", RegexOption.IGNORE_CASE)
    private val formattingTagRegex = Regex(
        """</?(?:p|div|span|strong|b|i|em|u|font|pre|code|ul|ol|li)(?:\s[^>]*)?>""",
        RegexOption.IGNORE_CASE,
    )
    private val placeholderAnalysisRegex = Regex("""^试题解析[\s.。…]*$""")

    fun parseResultPage(body: String, subjectId: String): CProgExamPage {
        val json = JsonParser.parseString(body).asJsonObject
        checkSession(json)
        val rows = json.array("rows").mapObjects { item ->
            CProgExamRow(
                examId = item.string("examId").orEmpty(),
                examCaption = item.string("examCaption").orEmpty(),
                subjectId = subjectId.ifBlank { null },
                subjectCaption = item.string("subjectCaption"),
                status = item.int("examUserStatus") ?: 0,
                grade = item.double("examUserGrade") ?: 0.0,
                examCreateTime = item.string("examUserTime"),
                examClient = null,
                recordCounts = item.int("recordCounts") ?: 0,
                examStatus = item.int("examStatus") ?: 0,
                examHistoryLook = item.string("examHistoryLook"),
            )
        }
        return CProgExamPage(
            total = json.int("total") ?: 0,
            records = json.int("records") ?: rows.size,
            page = json.int("page") ?: 1,
            rows = rows,
        )
    }

    fun parseAttemptPage(body: String): CProgAttemptPage {
        val json = JsonParser.parseString(body).asJsonObject
        checkSession(json)
        val rows = json.array("rows").mapObjects { item ->
            CProgAttempt(
                id = item.string("id").orEmpty(),
                examId = item.string("examId").orEmpty(),
                examCaption = item.string("examCaption").orEmpty(),
                subjectCaption = item.string("subjectCaption"),
                status = item.int("status") ?: 0,
                grade = item.double("grade") ?: 0.0,
                durationSeconds = item.int("time") ?: 0,
                createTime = item.string("createTime"),
                submitTime = item.string("submitTime"),
            )
        }
        return CProgAttemptPage(
            total = json.int("total") ?: 0,
            records = json.int("records") ?: rows.size,
            page = json.int("page") ?: 1,
            rows = rows,
        )
    }

    fun extractPaperId(html: String): String? =
        paperIdRegex.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    fun parsePaperResponse(body: String): CProgPaper {
        val root = JsonParser.parseString(body).asJsonObject
        checkSession(root)
        val err = root.string("errCode")
        if (err != null && err != "0") {
            throw IOException(root.string("errMsg") ?: "接口错误($err)")
        }
        val data = root.obj("data") ?: throw IOException("作答详情为空")
        val types = data.array("studentPaperQuestionTypeVoList").mapObjects { type ->
            val items = type.array("studentPaperItemVoList").mapObjects(::parseQuestion)
            CProgQuestionType(
                questionTypeCaption = type.string("questionTypeCaption").orEmpty(),
                baseQuestionType = type.string("baseQuestionType"),
                questionCount = type.int("questionCount") ?: items.size,
                items = items,
            )
        }
        return CProgPaper(
            examId = data.string("examId").orEmpty(),
            examCaption = data.string("examCaption").orEmpty(),
            subjectCaption = data.string("subjectCaption"),
            paperQuestionCount = data.int("paperQuestionCount") ?: 0,
            paperQuestionTypeCount = data.int("paperQuestionTypeCount") ?: types.size,
            paperGrade = data.double("paperGrade") ?: 0.0,
            studentGrade = data.double("studentTotalGrade"),
            paperRemainingTimesHourMinuteSecond = data.string("paperRemainingTimesHourMinuteSecond"),
            questionTypes = types,
        )
    }

    private fun parseQuestion(question: JsonObject): CProgQuestionItem = CProgQuestionItem(
        id = question.string("id") ?: question.string("questionId").orEmpty(),
        text = plainText(question.string("text") ?: question.string("content")).orEmpty(),
        answer = plainText(question.string("answer")),
        knowledgeCaption = plainText(question.string("knowledgeCaption")),
        options = parseOptions(question),
        studentAnswer = plainText(question.string("studentQuestionAnswer")),
        studentGrade = question.double("stutdenQuestionGrade"),
        analysis = plainText(question.string("analysis"))
            ?.takeUnless { placeholderAnalysisRegex.matches(it) },
    )

    private fun parseOptions(question: JsonObject): List<CProgOption> {
        val direct = question.get("options")
        if (direct?.isJsonArray == true) return parseOptionArray(direct.asJsonArray)

        val answerJson = question.string("answerJson")?.takeIf { it.isNotBlank() } ?: return emptyList()
        val parsed = runCatching { JsonParser.parseString(answerJson) }.getOrNull()
        val answerList = parsed?.takeIf { it.isJsonObject }?.asJsonObject?.array("answerList")
            ?: return emptyList()
        return answerList.mapObjects { option ->
            CProgOption(
                code = option.string("answer") ?: option.string("code"),
                content = plainText(option.string("desc") ?: option.string("content")),
            )
        }
    }

    private fun parseOptionArray(array: JsonArray): List<CProgOption> = array.mapObjects { option ->
        CProgOption(
            code = option.string("code") ?: option.string("answer"),
            content = plainText(option.string("content") ?: option.string("desc")),
        )
    }

    private fun plainText(value: String?): String? {
        val source = value?.takeIf { it.isNotBlank() } ?: return null
        val withBreaks = source.replace(breakTagRegex, "\n")
        val withoutFormatting = withBreaks.replace(formattingTagRegex, "")
        return Parser.unescapeEntities(withoutFormatting, false)
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun checkSession(json: JsonObject) {
        if (json.string("errCode") == "997") throw IOException(CProgRepository.SESSION_EXPIRED)
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonObject.string(key: String): String? = get(key).primitiveOrNull()?.asString
    private fun JsonObject.int(key: String): Int? = get(key).primitiveOrNull()?.let { runCatching { it.asInt }.getOrNull() }
    private fun JsonObject.double(key: String): Double? = get(key).primitiveOrNull()?.let { runCatching { it.asDouble }.getOrNull() }
    private fun JsonElement?.primitiveOrNull(): JsonElement? = this?.takeIf { it.isJsonPrimitive }
    private inline fun <T> JsonArray.mapObjects(transform: (JsonObject) -> T): List<T> =
        mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }.map(transform)
}
