package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.evaluation.CheckSubmitResult
import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestion
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.EvaluationSemester
import com.ahu_plus.data.model.evaluation.QuestionAnswer
import com.ahu_plus.data.model.evaluation.QuestionAnswerOption
import com.ahu_plus.data.model.evaluation.QuestionnaireResponse
import com.ahu_plus.data.model.evaluation.SubmissionPayload
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder

/**
 * 评教 (jw.ahu.edu.cn/eams5-evaluation-service) 业务仓库。
 *
 * 鉴权分层:
 *  - SESSION(教务门户):复用 [JwAuthRepository.jwCookieJar],访问 SPA /iframe 时由浏览器带。
 *  - JWT(评教后端):[SessionManager] 缓存,EVALUATION_JWT_KEY。业务接口带
 *    `Authorization: <jwt>`(裸串,不带 Bearer 前缀);Cookie 里的 SESSION/prodid/
 *    Evaluation-Student-Token 全由 [JwAuthRepository.jwCookieJar] 自动携带。
 *
 * JWT 获取分两步(HAR 实测,原实现漏了第 1 步导致 `token/renew` 返回 4001 unlogin):
 *  1. **bootstrap**:GET `/student/for-std/extra-system/student-summation-forstudent/index`,
 *     jw SSO 网关 302 到 SPA,token 藏在 Location 的 fragment(`#/extra-login?token=<JWT>`)。
 *  2. **renew**:POST `/eams5-evaluation-service/token/renew`(注意**不带** /api/v1),body
 *     `{"token":"<bootstrap>"}` 续期换更长有效期的 JWT,并 Set-Cookie 下发 prodid。
 *     renew 是「续期已有 token」而非「凭空发 token」,必须先有 bootstrap token 才能调。
 *
 * 401 触发 lazy 重新 bootstrap+renew 一次:只重试一次,不级联。
 * ponytail: 单 mutex 串行,并发请求都共用同一 JWT。
 */
class EvaluationRepository(
    private val jwAuthRepository: JwAuthRepository,
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG = "EvaluationRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val JW_HOST = "jw.ahu.edu.cn"
        private const val EVAL_BASE = "$JW_BASE/eams5-evaluation-service"
        private const val API_BASE = "$EVAL_BASE/api/v1"
        // SSO 网关入口:302 到 SPA,bootstrap token 藏在 Location fragment。
        private const val EXTRA_SYSTEM_ENTRY =
            "$JW_BASE/student/for-std/extra-system/student-summation-forstudent/index"
        private val JSON_MEDIA = "application/json; charset=UTF-8".toMediaType()

        private const val UA = "Mozilla/5.0 (Linux; Android 14) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }

    private val gson = GsonProvider.instance

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = true,
        trustAll = true,  // jw.ahu.edu.cn 自签名证书
    )

    /** 串行化 JWT renew,避免并发请求都触发 renew。 */
    private val jwtMutex = Mutex()

    /** 最近一次评教 JWT 声明的当前学期，不改变学期列表本身的时间排序。 */
    fun getCurrentSemesterId(): String? = sessionManager.getEvaluationJwt()
        ?.let(EvaluationResponseParser::currentSemesterId)

    // ════════════════════════════════════════════════════════
    // JWT 管理
    // ════════════════════════════════════════════════════════

    /**
     * 拿到当前 JWT,过期或缺失则 renew。
     * 401 后 lazy 重试一次,会用本方法获取新 JWT。
     */
    private suspend fun obtainJwt(forceRenew: Boolean = false): String {
        if (!forceRenew) {
            sessionManager.getEvaluationJwt()
                ?.takeIf(EvaluationResponseParser::isJwtUsable)
                ?.let { return it }
        }
        return jwtMutex.withLock {
            // 持锁再查一次:其他协程可能刚 renew 好
            if (!forceRenew) {
                sessionManager.getEvaluationJwt()
                    ?.takeIf(EvaluationResponseParser::isJwtUsable)
                    ?.let { return@withLock it }
            }
            // forceRenew(401 重试)时缓存的 token 已过期,必须重新 bootstrap 拿新的。
            val newJwt = renewJwt(forceBootstrap = forceRenew)
            sessionManager.saveEvaluationJwt(newJwt)
            Log.i(TAG, "JWT renewed, len=${newJwt.length}")
            newJwt
        }
    }

    /**
     * 两步拿 JWT:bootstrap → renew。
     *
     * renew 是「续期已有 token」,不能凭空发 token(否则后端返回 4001 unlogin)。
     * 所以先 [bootstrapToken] 拿 SSO 网关下发的初始 token,再 POST `/token/renew` 续期。
     *
     * @param forceBootstrap true 时忽略缓存,强制重新走 SSO 入口(401 重试用)。
     */
    private suspend fun renewJwt(forceBootstrap: Boolean): String = withContext(Dispatchers.IO) {
        val current = if (forceBootstrap) {
            bootstrapToken()
        } else {
            sessionManager.getEvaluationJwt()
                ?.takeIf(EvaluationResponseParser::isJwtUsable)
                ?: bootstrapToken()
        }
        // token 放进共享 jar → renew 及后续业务请求靠 jar 自动带 Cookie(SESSION/prodid/token)。
        putTokenCookie(current)
        val body = JsonObject().apply {
            addProperty("token", current)
        }
        val request = Request.Builder()
            .url("$EVAL_BASE/token/renew")  // 注意:renew 不带 /api/v1(HAR 实测)
            .header("User-Agent", UA)
            .header("Authorization", current)  // 裸 JWT,无 Bearer 前缀
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw EvaluationAuthException("token/renew HTTP ${response.code}: $responseBody")
            }
            val renewed = parseJwtFromResponse(responseBody)
            putTokenCookie(renewed)  // 续期后的新 token 覆盖 jar
            renewed
        }
    }

    /**
     * SSO 网关 bootstrap:GET 评教「外部系统」入口,jw 网关 302 到 SPA,
     * 初始 JWT 藏在 Location 的 fragment(`#/extra-login?token=<JWT>&bizTypeId=2`)。
     *
     * 入口靠 jw SESSION 鉴权 → 先确保 SESSION 有效(authenticate 内部探测+复用)。
     */
    private suspend fun bootstrapToken(): String {
        jwAuthRepository.authenticate().getOrElse {
            throw EvaluationAuthException("教务处登录失败: ${it.message}")
        }
        val request = Request.Builder()
            .url(EXTRA_SYSTEM_ENTRY)
            .header("User-Agent", UA)
            .header("Referer", "$JW_BASE/student/home")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val location = response.header("Location").orEmpty()
            extractTokenFromLocation(location)
                ?: throw EvaluationAuthException(
                    "SSO 入口未返回 bootstrap token (HTTP ${response.code}),SESSION 可能已失效"
                )
        }
    }

    /** 从 302 Location 的 fragment 里抠出 `token=` 后、`&` 前的 JWT。 */
    private fun extractTokenFromLocation(location: String): String? {
        if (location.isBlank()) return null
        val idx = location.indexOf("token=")
        if (idx < 0) return null
        val raw = location.substring(idx + "token=".length).substringBefore("&")
        if (raw.isBlank()) return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    /** 把 JWT 写进共享 jwCookieStore 作为 Evaluation-Student-Token(与 SESSION 同域并存)。 */
    private fun putTokenCookie(jwt: String) {
        val list = jwAuthRepository.jwCookieStore.getOrPut(JW_HOST) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it.name == "Evaluation-Student-Token" }
            list.add(
                Cookie.Builder()
                    .name("Evaluation-Student-Token")
                    .value(jwt)
                    .domain(JW_HOST)
                    .path("/")
                    .build()
            )
        }
    }

    /**
     * 后端 token/renew 响应体形如 `{"code":0,"data":"<jwt>"}` 或
     * `{"code":0,"data":{"token":"<jwt>"}}`(版本差异),两种都兼容。
     */
    private fun parseJwtFromResponse(body: String): String {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() as? JsonObject
            ?: throw EvaluationAuthException("token/renew 响应不是 JSON: $body")
        val code = root.get("code")?.asInt ?: -1
        if (code != 0) {
            throw EvaluationAuthException("token/renew code=$code: $body")
        }
        val data = root.get("data")
            ?: throw EvaluationAuthException("token/renew 缺 data 字段: $body")
        return when {
            data.isJsonPrimitive && data.asJsonPrimitive.isString -> data.asString
            data.isJsonObject -> data.asJsonObject.get("token")?.asString
                ?: throw EvaluationAuthException("token/renew data.token 缺失")
            else -> throw EvaluationAuthException("token/renew data 形态未知: $data")
        }
    }

    // ════════════════════════════════════════════════════════
    // 业务端点
    // ════════════════════════════════════════════════════════

    /** 学期下拉。 */
    suspend fun getSemesters(): Result<List<EvaluationSemester>> = withContext(Dispatchers.IO) {
        runApi("getSemesters") { jwt ->
            val req = buildGet("$API_BASE/common/drop-down/stu_semester?enabled=true&idc_=self", jwt)
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401) throw Jwt401Exception()
                if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                EvaluationResponseParser.parseSemesters(body)
            }
        }
    }

    /** 教师任务列表。evaluated=false 拉未评,空值拉全量。 */
    suspend fun getTasks(
        semesterId: String,
        evaluated: Boolean? = false,
    ): Result<List<TeacherEvaluationTask>> = withContext(Dispatchers.IO) {
        val evaluatedParam = when (evaluated) {
            null -> ""
            true -> "true"
            false -> "false"
        }
        val url = buildString {
            append("$API_BASE/for-student/student-summation-forstudent/search")
            append("?queryPage__=1%2C100")
            append("&orderBy=")
            append("&semesterId=$semesterId")
            if (evaluatedParam.isNotEmpty()) append("&evaluated=$evaluatedParam")
        }
        runApi("getTasks") { jwt ->
            val req = buildGet(url, jwt)
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401) throw Jwt401Exception()
                if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                EvaluationResponseParser.parseTasks(body)
            }
        }
    }

    /** 拉问卷模板(按 questionnaireId)。 */
    suspend fun getQuestionnaire(qid: String): Result<EvaluationQuestionnaire> =
        withContext(Dispatchers.IO) {
            runApi("getQuestionnaire") { jwt ->
                val req = buildGet(
                    "$API_BASE/for-student/student-summation-forstudent/search-questionnaire/$qid",
                    jwt,
                )
                client.newCall(req).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 401) throw Jwt401Exception()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                    EvaluationResponseParser.parseQuestionnaire(body)
                }
            }
        }

    /** 批量敏感词测试 — 仅对 type=4 文本题调用。空列表直接 Ok。 */
    suspend fun testBadwordBatch(words: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (words.isEmpty()) return@withContext Result.success(Unit)
        val filteredWords = words.filter { it.isNotBlank() }
        val arr = JsonArray()
        filteredWords.forEach { w ->
            arr.add(JsonObject().apply { addProperty("testWord", w) })
        }
        if (arr.isEmpty) return@withContext Result.success(Unit)
        val ts = System.currentTimeMillis()
        runApi("testBadwordBatch", retryOn401 = false) { jwt ->
            filteredWords.forEachIndexed { index, word ->
                val singleBody = JsonObject().apply { addProperty("testWord", word) }
                val singleRequest = Request.Builder()
                    .url("$API_BASE/student-summation-questionnaire/test-badword?idc_=${ts + index}")
                    .header("User-Agent", UA)
                    .header("Authorization", jwt)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .post(singleBody.toString().toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(singleRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                    EvaluationResponseParser.requireNoBadwords(body)
                }
            }
            val req = Request.Builder()
                .url("$API_BASE/student-summation-questionnaire/test-badword-batch?idc_=$ts")
                .header("User-Agent", UA)
                .header("Authorization", jwt)  // HAR 实测:裸 JWT。Cookie 由 jwCookieJar 自动带
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(arr.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $body")
                EvaluationResponseParser.requireNoBadwords(body)
            }
        }
    }

    /** 业务预校验 — 不落库,仅返回文案(如"问卷选项连续相同")。 */
    suspend fun checkSubmit(payload: SubmissionPayload): Result<CheckSubmitResult> =
        withContext(Dispatchers.IO) {
            runApi("checkSubmit", retryOn401 = false) { jwt ->
                val body = gson.toJson(payload)
                val req = Request.Builder()
                    .url("$API_BASE/for-student/student-summation-forstudent/check-submit")
                    .header("User-Agent", UA)
                    .header("Authorization", jwt)  // HAR 实测:裸 JWT。Cookie 由 jwCookieJar 自动带
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()
                client.newCall(req).execute().use { response ->
                    val respBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $respBody")
                    EvaluationResponseParser.parseCheckResult(respBody)
                }
            }
        }

    /** 真正提交 — 落库。 */
    suspend fun submit(payload: SubmissionPayload): Result<Unit> = withContext(Dispatchers.IO) {
        runApi("submit", retryOn401 = false) { jwt ->
            val body = gson.toJson(payload)
            val req = Request.Builder()
                .url("$API_BASE/for-student/student-summation-forstudent/submit")
                .header("User-Agent", UA)
                .header("Authorization", jwt)  // HAR 实测:裸 JWT。Cookie 由 jwCookieJar 自动带
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $respBody")
                val root = runCatching { JsonParser.parseString(respBody).asJsonObject }.getOrNull()
                val code = root?.get("code")?.asInt ?: -1
                if (code != 0) error("submit code=$code: $respBody")
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // 解析
    // ════════════════════════════════════════════════════════

    // 响应解析集中在 EvaluationResponseParser，保持 Repository 只负责网络与重试。

    // ════════════════════════════════════════════════════════
    // 通用执行 + 重试
    // ════════════════════════════════════════════════════════

    /**
     * block 内 throw 表示需要 JWT 重试。
     */
    private class Jwt401Exception : Exception()

    /**
     * 业务请求统一外壳:
     *  - 自动 JWT 注入;
     *  - 401 → 强制 renew 一次并重试(只一次,失败抛 EvaluationAuthException);
     *  - 其他异常包装为 Result.failure(EvaluationApiException),不向上抛。
     *
     * 用法:block 直接返回业务值 T;若需触发 JWT 重试,抛 [Jwt401Exception]。
     *
     * @param retryOn401 false 时 401 直接抛错(用于 test-badword/check-submit/submit,
     *                  它们本身就是写操作,JWT 过期应让用户感知而不是静默重试)。
     */
    private suspend fun <T> runApi(
        op: String,
        retryOn401: Boolean = true,
        block: suspend (jwt: String) -> T,
    ): Result<T> {
        return runCatching {
            var jwt = obtainJwt()
            var attempt = 0
            while (true) {
                try {
                    return@runCatching block(jwt)
                } catch (e: Jwt401Exception) {
                    if (!retryOn401 || attempt >= 1) throw EvaluationAuthException("JWT 过期 (HTTP 401)")
                    attempt++
                    Log.w(TAG, "$op 收到 401,强制 renew 重试")
                    jwt = obtainJwt(forceRenew = true)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }.recoverCatching { e ->
            throw when (e) {
                is EvaluationApiException, is EvaluationAuthException -> e
                else -> EvaluationApiException("$op 失败: ${e.message}", e)
            }
        }
    }

    private fun buildGet(url: String, jwt: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Authorization", jwt)  // HAR 实测: 裸 JWT,无 Bearer 前缀。Cookie 由 jwCookieJar 自动带
            .get()
            .build()

    /**
     * 把 UI 草稿(questionId → [EvaluationAnswer])组装成提交 payload。
     *
     * - 单选(type=1):questionAnsExpSaveList[{optionId, questionnaireId}]
     * - 文本(type=4):answer = 文本,questionAnsExpSaveList = []
     */
    fun buildPayload(
        task: TeacherEvaluationTask,
        questionnaire: EvaluationQuestionnaire,
        anonymous: Boolean,
        draft: Map<String, EvaluationAnswer>,
    ): SubmissionPayload {
        val list = questionnaire.questions.map { q ->
            val answer = draft[q.questionId]
            val qAns = when (q.type) {
                1 -> {
                    val opt = answer as? EvaluationAnswer.Option
                    QuestionAnswer(
                        questionId = q.questionId,
                        questionnaireId = questionnaire.questionnaireId,
                        type = q.type.toString(),
                        score = opt?.optionScore ?: 0.0,
                        answer = null,
                        questionAnsExpSaveList = if (opt != null) listOf(
                            QuestionAnswerOption(
                                optionId = opt.optionId,
                                questionnaireId = questionnaire.questionnaireId,
                            )
                        ) else emptyList(),
                    )
                }
                4 -> {
                    val text = (answer as? EvaluationAnswer.Text)?.text.orEmpty()
                    QuestionAnswer(
                        questionId = q.questionId,
                        questionnaireId = questionnaire.questionnaireId,
                        type = q.type.toString(),
                        score = 0.0,
                        answer = text,
                        questionAnsExpSaveList = emptyList(),
                    )
                }
                else -> QuestionAnswer(
                    questionId = q.questionId,
                    questionnaireId = questionnaire.questionnaireId,
                    type = q.type.toString(),
                    score = 0.0,
                    answer = null,
                    questionAnsExpSaveList = emptyList(),
                )
            }
            qAns
        }
        return SubmissionPayload(
            stdSumTaskId = task.stdSumTaskId,
            anonymous = anonymous,
            evaluationQuestionnaireRes = QuestionnaireResponse(
                questionnaireId = questionnaire.questionnaireId,
                questionnaireName = questionnaire.questionnaireName,
                enable = questionnaire.enable,
                answer = "[]",
                score = 0.0,
                questionAnsSaveList = list,
            ),
        )
    }
}

/** 评教 API 业务异常(包含 4xx/5xx、解析错误等)。 */
class EvaluationApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 评教鉴权异常(JWT 失效 / renew 失败 / 401 持久)。 */
class EvaluationAuthException(message: String) : Exception(message)
