package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.model.jw.Exam
import com.ahu_plus.data.network.SecureHttpClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 教务处考试安排仓库。
 *
 * 真实端点（基于 JwSystem 9 实测）：
 *   GET /student/for-std/exam-arrange?semesterId=...   → 302 → Location 含 studentId
 *   GET /student/for-std/exam-arrange/info/{studentId}  → HTML（Thymeleaf SSR）
 *
 * **响应体是 HTML 而非 JSON**，需要用 Jsoup 解析：
 *   `<table id="exams">` 内每个 `<tr class="finished hide">` 或 `<tr class="unfinished hide">` 是
 *   一场考试。结构：
 *   ```
 *   <tr data-finished="true" class="finished hide">
 *     <td>
 *       <div class="time">2026-05-24 14:00~15:40</div>
 *       <div>
 *         <span>磬苑校区</span>
 *         <span>博学楼</span>
 *         <span>博学楼B101</span>
 *         <span id="seat-2306048"></span>    ← 空标签，seat id 是数据库记录 ID
 *       </div>
 *     </td>
 *     <td>
 *       <div><span>程序设计基础</span></div>
 *       <div><span class="tag-span type1">考试</span></div>
 *     </td>
 *     <td>已结束</td>
 *   </tr>
 *   ```
 *
 *   **座位号**不在 HTML 表格中（`<span id="seat-xxx">` 为空），而是嵌在页面底部
 *   `<script>` 块的 `var studentExamList = [...]` 数组里，每个对象含 `id`（对应
 *   `<span id="seat-{id}">`）和 `seatNo`（座位序号）。`extractSeatMap()` 负责提取。
 */
class ExamRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    @Volatile private var cachedStudentId: Long? = null

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        trustAll = true,  // jw.ahu.edu.cn 自签名证书
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    /**
     * 拉取考试安排（HTML 解析）。
     *
     * 注：API 不接受 semesterId 参数（302 redirect 不变），返回的是所有学期（过往 + 未来）的考试。
     * UI 端可按时间过滤。
     */
    suspend fun getExams(
        @Suppress("UNUSED_PARAMETER") semesterId: Int = CourseRepository.DEFAULT_SEMESTER_ID
    ): Result<List<Exam>> {
        return try {
            val studentId = resolveStudentId().getOrElse {
                return Result.failure(it)
            }
            val url = "$JW_BASE/student/for-std/exam-arrange/info/$studentId"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (JwSessionResponseClassifier.isExpired(
                        response.code, response.header("Location"), body
                    )) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("考试安排查询失败: HTTP ${response.code}")
                }
                if (body.isBlank()) {
                    throw Exception("考试安排返回空响应")
                }
                val exams = parseExamHtml(body)
                Log.i(TAG, "考试安排解析完成: ${exams.size} 条 (studentId=$studentId)")
                Result.success(exams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "考试安排查询失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析考试安排 HTML —— 提取 `<table id="exams">` 内的所有 `<tr>` 行。
     *
     * 座位号不在 HTML 表格中（`<span id="seat-xxx">` 为空），而是嵌在页面底部的
     * JavaScript 变量 `studentExamList` 里。先提取该数组建立 id→seatNo 映射，再解析表格。
     */
    internal fun parseExamHtml(html: String): List<Exam> {
        val seatMap = extractSeatMap(html)
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("exams") ?: return emptyList()
        val rows = table.select("tbody > tr")
        return rows.mapNotNull { parseRow(it, seatMap) }
    }

    /**
     * 从 HTML 中的 `<script>` 块提取 `studentExamList` JSON 数组，构建 id → seatNo 映射。
     *
     * 页面底部有如下 JS 代码：
     * ```
     * var studentExamList = [{'studentId':99166,'seatMap':{'columns':10,...},'id':2306048,'seatNo':6}, ...];
     * ```
     * 因为对象内含嵌套的 `seatMap:{...}`，不能用简单的 `\{[^{}]+\}` 匹配，
     * 改用深度追踪逐字符分割顶层对象。
     */
    private fun extractSeatMap(html: String): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val listPattern = Regex("""var\s+studentExamList\s*=\s*\[(.+?)\];""", RegexOption.DOT_MATCHES_ALL)
        val listMatch = listPattern.find(html) ?: return result
        val listBody = listMatch.groupValues[1]

        // 按顶层对象拆分（处理嵌套的 seatMap:{...}）
        val objects = splitTopLevelObjects(listBody)
        val idPattern = Regex("""['"]id['"]\s*:\s*(\d+)""")
        val seatNoPattern = Regex("""['"]seatNo['"]\s*:\s*(\d+)""")
        for (obj in objects) {
            val id = idPattern.find(obj)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val seatNo = seatNoPattern.find(obj)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            result[id] = seatNo
        }
        Log.i(TAG, "从 JS 提取座位号映射: ${result.size} 条")
        return result
    }

    /**
     * 按顶层 `{...}` 边界拆分字符串，正确处理嵌套花括号。
     */
    private fun splitTopLevelObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result.add(text.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun parseRow(row: Element, seatMap: Map<Int, Int>): Exam? {
        val rowClass = row.className()
        if ("finished" !in rowClass && "unfinished" !in rowClass) {
            // 跳过占位行（tr-empty 等）
            return null
        }
        val tds = row.select("> td")
        if (tds.size < 2) return null

        // —— 时间 + 地点 + 座位号 ——
        val firstTd = tds[0]
        val timeText = firstTd.selectFirst(".time")?.text()?.trim().orEmpty()
        val locationSpans = firstTd.select("> div > span")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && !it.startsWith("座位") }
        val campus = locationSpans.getOrNull(0).orEmpty()
        val building = locationSpans.getOrNull(1).orEmpty()
        val room = locationSpans.getOrNull(2).orEmpty()
        // 座位号：从 JS studentExamList 提取的 seatMap 中查找
        val seatElement = firstTd.selectFirst("[id^=seat-]")
        val seatRecordId = seatElement?.id()?.removePrefix("seat-")?.toIntOrNull()
        val seatNo = seatRecordId?.let { seatMap[it] }
        val seatNumber = seatNo?.toString()

        // —— 课程名 + 考试类型 ——
        val secondTd = tds[1]
        // 课程名：第二个 td 第一个 div 里的 span
        val courseName = secondTd.selectFirst("div > span")?.text()?.trim().orEmpty()
        // 考试类型：tag-span 类的 span
        val examType = secondTd.selectFirst(".tag-span")?.text()?.trim().orEmpty()

        // —— 状态：第三个 td ——
        val status = tds.getOrNull(2)?.text()?.trim().orEmpty()
        val isFinished = "finished" in rowClass ||
            status.contains("完成") || status.contains("已考") || status.contains("已结束")

        if (courseName.isBlank() && timeText.isBlank()) return null

        val id = "$courseName|$timeText|$room".hashCode().toLong()
        return Exam(
            id = if (id < 0) "e${-id}" else "e$id",
            courseName = courseName,
            examType = examType,
            examTime = timeText,
            campus = campus,
            building = building,
            room = room,
            seatNumber = seatNumber,
            isFinished = isFinished
        )
    }

    private suspend fun resolveStudentId(): Result<Long> {
        cachedStudentId?.let { return Result.success(it) }
        return try {
            val url = "$JW_BASE/student/for-std/exam-arrange" +
                "?semesterId=${CourseRepository.DEFAULT_SEMESTER_ID}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"] ?: ""
                Log.i(TAG, "exam-arrange HTTP ${response.code}, Location=$location")
                if (JwSessionResponseClassifier.isExpired(response.code, location)) {
                    return Result.failure(SessionExpiredException())
                }
                val id = GradeRepository.parseStudentIdFromLocation(location)
                if (id != null) {
                    cachedStudentId = id
                    Result.success(id)
                } else {
                    Result.failure(Exception("无法从 Location 解析 studentId: $location"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveStudentId 失败", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ExamRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}
