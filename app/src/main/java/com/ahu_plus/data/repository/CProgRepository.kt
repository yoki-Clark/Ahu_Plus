package com.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ahu_plus.data.model.CProgAttemptPage
import com.ahu_plus.data.model.CProgExamPage
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.data.model.CProgOption
import com.ahu_plus.data.model.CProgPaper
import com.ahu_plus.data.model.CProgQuestionItem
import com.ahu_plus.data.model.CProgQuestionType
import com.ahu_plus.data.model.CProgSection
import com.ahu_plus.data.model.CProgSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * 大学计算机平台只读查询仓库。复用 [CProgAuthRepository] 的 client/cookieJar,
 * 所有业务接口把主站 JWT 放在 query 的 tk 参数。
 *
 * 会话过期(errCode 997 或 HTML 踢回登录页)统一映射为 [SESSION_EXPIRED] 异常,
 * 供 ViewModel 判定 needsLogin。
 */
class CProgRepository(
    private val auth: CProgAuthRepository,
) {
    companion object {
        private const val TAG = "CProg"
        const val SESSION_EXPIRED = "CPROG_SESSION_EXPIRED"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
    }

    private val client get() = auth.client
    private val base get() = auth.baseUrl

    private fun tkUrl(path: String, referer: String): Pair<String, String> {
        val tk = auth.tk.orEmpty()
        return "$base$path?tk=$tk" to "$base$referer"
    }

    /** 统一 POST:form-urlencoded,带 Referer/X-Requested-With,返回原始 body 字符串 */
    private fun post(path: String, referer: String, form: Map<String, String>): String {
        val (url, ref) = tkUrl(path, referer)
        val fb = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val resp = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ref)
                .post(fb).build()
        ).execute()
        resp.use {
            // 会话过期:服务端 302 回登录页 或返回 HTML(非 JSON)
            if (it.code == 302 || it.code == 401 || it.code == 403) throw IOException(SESSION_EXPIRED)
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            if (body.contains("/redirect/login") || body.trimStart().startsWith("<"))
                throw IOException(SESSION_EXPIRED)
            return body
        }
    }

    private fun get(path: String, referer: String): String {
        val (url, ref) = tkUrl(path, referer)
        val resp = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ref)
                .build()
        ).execute()
        resp.use {
            if (it.code == 302 || it.code == 401 || it.code == 403) throw IOException(SESSION_EXPIRED)
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            if (body.contains("/redirect/login") || body.trimStart().startsWith("<"))
                throw IOException(SESSION_EXPIRED)
            return body
        }
    }

    private fun buildUrl(
        path: String,
        query: Map<String, String> = emptyMap(),
        includeTk: Boolean = false,
    ): String = "$base$path".toHttpUrl().newBuilder().apply {
        if (includeTk) addQueryParameter("tk", auth.tk.orEmpty())
        query.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build().toString()

    /** POST used by the achievement pages. Referer query parameters matter on detail requests. */
    private fun postAchievement(
        path: String,
        refererPath: String,
        form: Map<String, String>,
        refererQuery: Map<String, String> = emptyMap(),
        includeTkInReferer: Boolean = false,
    ): String {
        val url = buildUrl(
            path = path,
            query = mapOf("rand" to System.currentTimeMillis().toString()),
            includeTk = true,
        )
        val requestBody = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val response = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", buildUrl(refererPath, refererQuery, includeTkInReferer))
                .post(requestBody)
                .build()
        ).execute()
        response.use {
            if (it.code == 302 || it.code == 401 || it.code == 403) throw IOException(SESSION_EXPIRED)
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            if (isLoginPage(body)) throw IOException(SESSION_EXPIRED)
            return body
        }
    }

    /** GET a content page. Valid HTML must not be classified as an expired JSON session. */
    private fun getHtml(
        path: String,
        refererPath: String,
        query: Map<String, String>,
        refererQuery: Map<String, String> = emptyMap(),
        includeTk: Boolean = false,
        includeTkInReferer: Boolean = false,
    ): String {
        val response = client.newCall(
            Request.Builder().url(buildUrl(path, query, includeTk))
                .header("User-Agent", UA)
                .header("Referer", buildUrl(refererPath, refererQuery, includeTkInReferer))
                .build()
        ).execute()
        response.use {
            if (it.code == 302 || it.code == 401 || it.code == 403) throw IOException(SESSION_EXPIRED)
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            if (isLoginPage(body)) throw IOException(SESSION_EXPIRED)
            return body
        }
    }

    private fun isLoginPage(body: String): Boolean =
        body.contains("/login/get") && body.contains("asdfsdf")

    private fun requireData(body: String): JsonObject {
        val json = JsonParser.parseString(body).asJsonObject
        val err = json.get("errCode")?.asString
        if (err == "997") throw IOException(SESSION_EXPIRED)
        if (err != "0") throw IOException(json.get("errMsg")?.asString ?: "接口错误($err)")
        return json
    }

    /** 取对象成员;成员缺失或为 JSON null 都返回 null(getAsJsonObject 遇 JsonNull 会抛 ClassCast) */
    private fun JsonObject.objOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    /** 同上,取数组成员 */
    private fun JsonObject.arrOrNull(key: String): com.google.gson.JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    // JsonNull.getAsX() 会抛异常,这些扩展遇 null/JsonNull 一律返回 null
    private val com.google.gson.JsonElement.asStringOrNull: String?
        get() = if (isJsonPrimitive) asString else null
    private val com.google.gson.JsonElement.asIntOrNull: Int?
        get() = if (isJsonPrimitive) runCatching { asInt }.getOrNull() else null
    private val com.google.gson.JsonElement.asDoubleOrNull: Double?
        get() = if (isJsonPrimitive) runCatching { asDouble }.getOrNull() else null

    // ── 只读成绩与作答历史 ───────────────────────────────────────────────

    /** 已提交成绩列表。该接口只查询历史结果,不会创建或恢复答题场次。 */
    suspend fun listResults(
        subjectId: String = "",
        caption: String = "",
        page: Int = 1,
        rows: Int = 50,
    ): Result<CProgExamPage> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = auth.userId.orEmpty()
            val body = postAchievement(
                path = "/site/achievement/gradeTable/query",
                refererPath = "/site/achievement/search/init",
                refererQuery = mapOf("userId" to userId),
                includeTkInReferer = true,
                form = mapOf(
                    "filter_EQ_eu.user_id" to userId,
                    "filter_EQ_e.subject_id" to subjectId,
                    "filter_LIKE_e.caption" to caption,
                    "filter_NEQ_e.status" to "0",
                    "filter_EQ_e.grouping" to "",
                    "page" to page.toString(),
                    "rows" to rows.toString(),
                    "sidx" to "createTime",
                    "sord" to "desc",
                ),
            )
            CProgResponseParser.parseResultPage(body, subjectId)
        }.onFailure { Log.e(TAG, "listResults", it) }
    }

    /** 某个考试的全部已提交作答,按成绩和更新时间倒序。 */
    suspend fun listAttempts(
        examId: String,
        page: Int = 1,
        rows: Int = 50,
    ): Result<CProgAttemptPage> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = auth.userId.orEmpty()
            val refererQuery = mapOf("userId" to userId, "examId" to examId)
            val body = postAchievement(
                path = "/site/achievement/full/history/query",
                refererPath = "/site/achievement/full/history/init",
                refererQuery = refererQuery,
                form = mapOf(
                    "filter_EQ_euh.EXAM_ID" to examId,
                    "filter_EQ_euh.USER_ID" to userId,
                    "page" to page.toString(),
                    "rows" to rows.toString(),
                    "sidx" to "grade,updateTime",
                    "sord" to "desc,desc",
                ),
            )
            CProgResponseParser.parseAttemptPage(body)
        }.onFailure { Log.e(TAG, "listAttempts", it) }
    }

    /**
     * 读取一次历史作答。先打开 details/init 获取服务端注入的 paperId,再调用只读 result。
     * 整个流程不触达 assign/paper3,因此不会创建答题场次或消耗次数。
     */
    suspend fun getAttemptPaper(examId: String, attemptId: String): Result<CProgPaper> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = auth.userId.orEmpty()
                val historyQuery = mapOf("userId" to userId, "examId" to examId)
                val initQuery = mapOf(
                    "examId" to examId,
                    "userId" to userId,
                    "top" to "0",
                    "id" to attemptId,
                )
                val html = getHtml(
                    path = "/site/achievement/full/history/details/init",
                    refererPath = "/site/achievement/full/history/init",
                    query = initQuery,
                    refererQuery = historyQuery,
                )
                val paperId = CProgResponseParser.extractPaperId(html)
                    ?: throw IOException("未找到作答试卷标识")
                val body = postAchievement(
                    path = "/site/achievement/full/history/details/result",
                    refererPath = "/site/achievement/full/history/details/init",
                    refererQuery = initQuery,
                    form = mapOf(
                        "eid" to examId,
                        "pid" to paperId,
                        "userId" to userId,
                        "id" to attemptId,
                    ),
                )
                CProgResponseParser.parsePaperResponse(body)
            }.onFailure { Log.e(TAG, "getAttemptPaper", it) }
        }

    // ── 分类计数 ─────────────────────────────────────────────
    suspend fun getSections(): Result<List<CProgSection>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = post(
                "/site/test/main/section/query", "/site/test/main",
                mapOf("userId" to auth.userId.orEmpty()),
            )
            val arr = requireData(body).arrOrNull("data") ?: return@runCatching emptyList()
            arr.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map {
                CProgSection(
                    codes = it.get("codes")?.asStringOrNull.orEmpty(),
                    title = it.get("title")?.asStringOrNull.orEmpty(),
                    counts = it.get("counts")?.asStringOrNull ?: "0",
                )
            }
        }.onFailure { Log.e(TAG, "getSections", it) }
    }

    // ── 科目 ─────────────────────────────────────────────────
    suspend fun getSubjects(): Result<List<CProgSubject>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                "/mgr/common/subjects/site/getSubjects",
                "/site/test/main",
            )
            val arr = requireData(body).arrOrNull("data") ?: return@runCatching emptyList()
            arr.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map {
                CProgSubject(
                    subjectId = it.get("subjectId")?.asStringOrNull.orEmpty(),
                    caption = it.get("caption")?.asStringOrNull.orEmpty(),
                    seq = it.get("seq")?.asIntOrNull ?: 0,
                )
            }.sortedByDescending { it.seq }
        }.onFailure { Log.e(TAG, "getSubjects", it) }
    }

    // ── 列表(jqGrid,无 errCode)─────────────────────────────
    /**
     * 分页查询。examStatus 固定 "1"(进行中),对齐抓包行为。
     * 分类(练习/测试/…)由 examStatus + 服务端会话状态决定,这里只按科目/标题过滤。
     */
    suspend fun listExams(
        subjectId: String = "",
        caption: String = "",
        page: Int = 1,
        rows: Int = 50,
    ): Result<CProgExamPage> = withContext(Dispatchers.IO) {
        runCatching {
            val body = post(
                "/site/evaluation/exams/search/query",
                "/site/evaluation/exams/search/init",
                mapOf(
                    "filter_EQ_e.subject_id" to subjectId,
                    "filter_LIKE_e.caption" to caption,
                    "filter_EQ_eu.status" to "",
                    "filter_EQ_e.status" to "1",
                    "filter_EQ_e.grouping" to "0",
                    "filter_EQ_eu.user_id" to auth.userId.orEmpty(),
                    "page" to page.toString(),
                    "rows" to rows.toString(),
                    "sidx" to "createTime",
                    "sord" to "desc",
                ),
            )
            // jqGrid:直接 {total, records, page, rows:[...]},无 errCode 包装
            val json = JsonParser.parseString(body).asJsonObject
            if (json.has("errCode") && json.get("errCode").asString == "997") throw IOException(SESSION_EXPIRED)
            val rowsArr = json.arrOrNull("rows") ?: return@runCatching CProgExamPage()
            val list = rowsArr.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map { o ->
                CProgExamRow(
                    examId = o.get("examId")?.asStringOrNull.orEmpty(),
                    examCaption = o.get("examCaption")?.asStringOrNull.orEmpty(),
                    subjectId = o.get("subjectId")?.asStringOrNull,
                    subjectCaption = o.get("subjectCaption")?.asStringOrNull,
                    status = o.get("status")?.asIntOrNull ?: 0,
                    grade = o.get("grade")?.asDoubleOrNull ?: 0.0,
                    examCreateTime = o.get("examCreateTime")?.asStringOrNull,
                    examClient = o.get("examClient")?.asStringOrNull,
                )
            }
            CProgExamPage(
                total = json.get("total")?.asIntOrNull ?: 0,
                records = json.get("records")?.asIntOrNull ?: 0,
                page = json.get("page")?.asIntOrNull ?: page,
                rows = list,
            )
        }.onFailure { Log.e(TAG, "listExams", it) }
    }

    // ── 整卷(assign/paper3 → paper/message)──────────────────
    /**
     * 抽卷并拉完整试卷(题干 + 参考答案)。
     * ⚠️ 仅应对"练习"调用 —— 对考试/测试会真正开始一场受监考考试并消耗次数。
     * 方法暂时保留供未来答题能力复用,当前 UI 不调用。
     */
    suspend fun getPaper(examId: String): Result<CProgPaper> = withContext(Dispatchers.IO) {
        runCatching {
            val ref = "/site/evaluation/exams/main/init"
            // rrm=0:全新抽卷(rrm=1 是"续做已开始的场次",无场次时 message 返回 data:null)
            val assignBody = post(
                "/site/evaluation/exams/assign/paper3", ref,
                mapOf("eid" to examId, "rrm" to "0", "pt" to "0", "emp" to "0"),
            )
            // data 是字符串 mid;为 JsonNull/空则抽卷失败
            val mid = requireData(assignBody).get("data")
                ?.takeIf { it.isJsonPrimitive }?.asString
                ?: throw IOException("抽卷失败(该练习可能不可用)")
            val msgBody = post(
                "/site/evaluation/exams/assign/paper/message", ref,
                mapOf("mid" to mid),
            )
            val data = requireData(msgBody).objOrNull("data")
                ?: throw IOException("试卷为空,该练习可能不可用")
            parsePaper(data)
        }.onFailure { Log.e(TAG, "getPaper", it) }
    }

    private fun parsePaper(data: JsonObject): CProgPaper {
        val types = data.arrOrNull("studentPaperQuestionTypeVoList")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }?.map { t ->
            val items = t.arrOrNull("studentPaperItemVoList")
                ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }?.map { q ->
                val opts = q.arrOrNull("options")
                    ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }?.map { o ->
                    CProgOption(o.get("code")?.asStringOrNull, o.get("content")?.asStringOrNull)
                } ?: emptyList()
                CProgQuestionItem(
                    id = q.get("id")?.asStringOrNull ?: q.get("questionId")?.asStringOrNull.orEmpty(),
                    text = q.get("text")?.asStringOrNull ?: q.get("content")?.asStringOrNull.orEmpty(),
                    answer = q.get("answer")?.asStringOrNull,
                    knowledgeCaption = q.get("knowledgeCaption")?.asStringOrNull,
                    options = opts,
                )
            } ?: emptyList()
            CProgQuestionType(
                questionTypeCaption = t.get("questionTypeCaption")?.asStringOrNull.orEmpty(),
                baseQuestionType = t.get("baseQuestionType")?.asStringOrNull,
                questionCount = t.get("questionCount")?.asIntOrNull ?: items.size,
                items = items,
            )
        } ?: emptyList()
        return CProgPaper(
            examId = data.get("examId")?.asStringOrNull.orEmpty(),
            examCaption = data.get("examCaption")?.asStringOrNull.orEmpty(),
            subjectCaption = data.get("subjectCaption")?.asStringOrNull,
            paperQuestionCount = data.get("paperQuestionCount")?.asIntOrNull ?: 0,
            paperQuestionTypeCount = data.get("paperQuestionTypeCount")?.asIntOrNull ?: 0,
            paperGrade = data.get("paperGrade")?.asDoubleOrNull ?: 0.0,
            paperRemainingTimesHourMinuteSecond = data.get("paperRemainingTimesHourMinuteSecond")?.asStringOrNull,
            questionTypes = types,
        )
    }
}
