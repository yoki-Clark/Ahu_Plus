package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jw.TrainingPlanResponse
import com.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request

/** 培养方案仓库。programId 需从当前登录用户的完成概览页动态解析。 */
class TrainingPlanRepository(private val jwAuthRepository: JwAuthRepository) {
    private val gson = GsonProvider.instance
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar, followRedirects = false, disableGzip = false,
        trustAll = true,  // jw.ahu.edu.cn 自签名证书
        extraInterceptors = listOf(okhttp3.Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", UA).header("x-requested-with", "XMLHttpRequest").build())
        })
    )

    @Volatile private var cachedProgramId: Long? = null

    suspend fun getTrainingPlan(): Result<TrainingPlanResponse> = try {
        val programId = resolveProgramId().getOrElse { return Result.failure(it) }
        val url = "$JW_BASE/student/for-std/credit-certification-apply/other_apply/get-all-course-module?programId=$programId"
        Log.i(TAG, "请求培养方案: programId=$programId")
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json, text/javascript, */*; q=0.01").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (JwSessionResponseClassifier.isExpired(
                    response.code, response.header("Location"), body
                )) throw SessionExpiredException()
            if (!response.isSuccessful) throw Exception("培养方案查询失败: HTTP ${response.code}")
            if (body.isBlank() || body[0] != '{') throw Exception("培养方案接口返回非 JSON: ${body.take(200)}")
            val parsed = gson.fromJson(body, TrainingPlanResponse::class.java)
            Log.i(TAG, "培养方案: ${parsed.children?.size ?: 0} 一级模块, 总要求=${parsed.sumChildrenRequiredCreditsOrZero}")
            Result.success(parsed)
        }
    } catch (e: Exception) { Log.e(TAG, "培养方案失败", e); Result.failure(e) }

    private suspend fun resolveProgramId(): Result<Long> {
        cachedProgramId?.let { return Result.success(it) }
        return try {
            val studentId = resolveStudentId().getOrElse { return Result.failure(it) }
            val url = "$JW_BASE/student/for-std/program-completion-preview/info/$studentId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                if (JwSessionResponseClassifier.isExpired(
                        response.code, response.header("Location"), html
                    )) throw SessionExpiredException()
                if (!response.isSuccessful) throw Exception("培养方案上下文请求失败: HTTP ${response.code}")
                val programId = parseProgramIdFromCompletionHtml(html)
                    ?: throw Exception("无法从培养方案完成概览解析 programId")
                cachedProgramId = programId
                Log.i(TAG, "解析培养方案 programId=$programId")
                Result.success(programId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveProgramId 失败", e)
            Result.failure(e)
        }
    }

    private suspend fun resolveStudentId(): Result<Long> {
        return try {
            val request = Request.Builder()
                .url("$JW_BASE/student/for-std/program-completion-preview")
                .get()
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"].orEmpty()
                val body = response.body?.string().orEmpty()
                if (JwSessionResponseClassifier.isExpired(response.code, location, body)) {
                    throw SessionExpiredException()
                }
                val id = GradeRepository.parseStudentIdFromLocation(location)
                    ?: parseStudentIdFromCompletionHtml(body)
                if (id != null) Result.success(id)
                else Result.failure(Exception("无法从完成概览入口解析 studentId: $location"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveStudentId 失败", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "TrainingPlanRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"

        internal fun parseProgramIdFromCompletionHtml(html: String): Long? {
            parseObjectAfterAssignment(html, "program")?.let { programObject ->
                extractNumericProperty(programObject, "id")?.let { return it }
            }
            parseObjectAfterProperty(html, "program")?.let { programObject ->
                extractNumericProperty(programObject, "id")?.let { return it }
            }
            return null
        }

        internal fun parseStudentIdFromCompletionHtml(html: String): Long? {
            return Regex("""var\s+studentId\s*=\s*(\d+)""")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
        }

        private fun parseObjectAfterAssignment(source: String, name: String): String? {
            val match = Regex("""var\s+${Regex.escape(name)}\s*=""").find(source) ?: return null
            val start = source.indexOf('{', match.range.last + 1)
            if (start < 0) return null
            val end = findMatchingBrace(source, start)
            return if (end >= 0) source.substring(start, end + 1) else null
        }

        private fun parseObjectAfterProperty(source: String, name: String): String? {
            val match = Regex("""['"]${Regex.escape(name)}['"]\s*:""").find(source) ?: return null
            val start = source.indexOf('{', match.range.last + 1)
            if (start < 0) return null
            val end = findMatchingBrace(source, start)
            return if (end >= 0) source.substring(start, end + 1) else null
        }

        private fun extractNumericProperty(source: String, name: String): Long? {
            return Regex("""['"]${Regex.escape(name)}['"]\s*:\s*(\d+)""")
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
        }

        private fun findMatchingBrace(source: String, start: Int): Int {
            var depth = 0
            var quote: Char? = null
            var escaping = false
            for (i in start until source.length) {
                val ch = source[i]
                if (quote != null) {
                    when {
                        escaping -> escaping = false
                        ch == '\\' -> escaping = true
                        ch == quote -> quote = null
                    }
                    continue
                }
                when (ch) {
                    '\'', '"' -> quote = ch
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            return -1
        }
    }
}
