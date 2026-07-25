package com.ahu_plus.data.repository

import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.jw.CompletionCourse
import com.ahu_plus.data.model.jw.CompletionSummary
import com.ahu_plus.data.network.SecureHttpClientFactory
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 培养方案完成概览仓库。
 *
 * 端点: GET /student/for-std/program-completion-preview → 302 → /info/{studentId}
 * 返回 HTML SSR 页面，内嵌 JS 变量 allCourseList（培养方案内课程+完成状态）
 * 和 outerCourseList（外部/转学分课程）。
 */
class ProgramCompletionRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        tlsPolicy = com.ahu_plus.data.network.TlsPolicy.LegacyCampusHosts(setOf("jw.ahu.edu.cn")),
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .build()
                chain.proceed(req)
            }
        )
    )

    @Volatile private var cachedStudentId: Long? = null

    fun clearAccountState() {
        cachedStudentId = null
    }

    /**
     * 获取培养方案完成数据（含每门课修读状态 + 学分汇总）。
     */
    suspend fun getCompletionData(): Result<Pair<List<CompletionCourse>, CompletionSummary>> {
        return try {
            val studentId = resolveStudentId().getOrElse { return Result.failure(it) }
            val url = "$JW_BASE/student/for-std/program-completion-preview/info/$studentId"
            Log.i(TAG, "请求培养方案完成数据")

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                if (JwSessionResponseClassifier.isExpired(
                        response.code, response.header("Location"), html
                    )) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("完成概览请求失败: HTTP ${response.code}")
                }
                if (html.isBlank()) {
                    throw Exception("完成概览返回空页")
                }

                val courses = parseAllCourseList(html)
                val summary = computeSummary(courses)
                Log.i(TAG, "完成数据解析: ${courses.size} 门课, passed=${summary.passedCredits}, taking=${summary.takingCredits}")
                Result.success(courses to summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "完成数据获取失败", e)
            Result.failure(e)
        }
    }

    /** 从程序概览页 302 提取 studentId */
    private suspend fun resolveStudentId(): Result<Long> {
        cachedStudentId?.let { return Result.success(it) }
        return try {
            val url = "$JW_BASE/student/for-std/program-completion-preview"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val location = response.headers["Location"] ?: ""
                val body = response.body?.string().orEmpty()
                if (JwSessionResponseClassifier.isExpired(response.code, location, body)) {
                    throw SessionExpiredException()
                }
                val id = GradeRepository.parseStudentIdFromLocation(location)
                if (id != null) {
                    cachedStudentId = id
                    Log.i(TAG, "已解析培养方案学生标识")
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

    // ── HTML 解析（委托到 companion object，便于纯 JVM 测试） ─────

    private fun parseAllCourseList(html: String): List<CompletionCourse> =
        Companion.parseAllCourseList(html)

    private fun extractTypeId(html: String, allCourseListIdx: Int): Int? =
        Companion.extractTypeId(html, allCourseListIdx)

    /** 从已解析的 allCourseList 计算学分汇总 */
    private fun computeSummary(courses: List<CompletionCourse>): CompletionSummary {
        var passed = 0.0
        var taking = 0.0
        var failed = 0.0
        var passedCount = 0
        var takingCount = 0
        var unrepairedCount = 0
        var failedCount = 0

        for (c in courses) {
            val credits = c.credits ?: 0.0
            when {
                c.isPassed -> { passed += credits; passedCount++ }
                c.isTaking -> { taking += credits; takingCount++ }
                c.isFailed -> { failed += credits; failedCount++ }
                c.isUnrepaired -> { unrepairedCount++ }
            }
        }
        return CompletionSummary(
            passedCredits = passed,
            takingCredits = taking,
            failedCredits = failed,
            passedCount = passedCount,
            takingCount = takingCount,
            unrepairedCount = unrepairedCount,
            failedCount = failedCount
        )
    }

    // ── 辅助 ────────────────────────────────────────────────────

    /** JS 对象转 JSON：单引号→双引号，null 去引号 */
    private fun jsToJson(js: String): String = Companion.jsToJson(js)

    private fun findMatchingBracket(s: String, start: Int): Int = Companion.findMatchingBracket(s, start)

    companion object {
        private const val TAG = "ProgramCompRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"

        private val gson = GsonProvider.instance

        /** 模块信息：从 allModuleList 树中提取，用于精确归属课程到子分类 */
        internal data class ModuleInfo(
            val typeId: Int? = null,
            val moduleName: String? = null,
            val moduleId: Int? = null,
            val depth: Int = 0
        )

        /**
         * 从 HTML 内嵌 JS 中提取所有 allCourseList 数组并去重合并。
         *
         * **双策略模块归属**：
         * 1. **树结构解析（首选）**：直接解析 `allModuleList` 树，从每个模块 JSON 对象中
         *    精确提取 typeId/name/id，建立 courseCode → ModuleInfo 映射。这种方式不依赖
         *    正则匹配或文本回看，最可靠。
         * 2. **文本回看（回退）**：若树结构解析失败，回退到从 allCourseList 关键字附近
         *    用灵活正则提取 typeId 和 name（支持单引号、双引号、无引号三种键格式）。
         *
         * 去重策略不变：同一门课在多个 allCourseList 中出现时，优先保留来自更小列表
         * （更具体的子分类）的出现。
         */
        internal fun parseAllCourseList(html: String): List<CompletionCourse> {
            // 策略 1：解析 allModuleList 树结构，建立 courseCode → ModuleInfo 映射
            val treeMapping = parseModuleTreeMapping(html)
            if (treeMapping.isNotEmpty()) {
                Log.i(TAG, "allModuleList 树解析成功: ${treeMapping.size} 个课程有模块映射")
            } else {
                Log.i(TAG, "allModuleList 树解析无结果，使用文本回看策略")
            }

            // 策略 2：解析所有 allCourseList 数组获取课程数据
            val occurrences = mutableMapOf<String, MutableList<Pair<CompletionCourse, Int>>>()
            // 灵活匹配 allCourseList 键：支持 'allCourseList':[ / "allCourseList":[ / allCourseList:[
            val searchPattern = Regex("""allCourseList['"]?\s*:\s*\[""")

            var lastMatchEnd = 0
            while (true) {
                val match = searchPattern.find(html, lastMatchEnd) ?: break
                val keyEnd = match.range.last()
                // 找到 [ 的位置
                val bracketStart = html.indexOf('[', match.range.first)
                if (bracketStart < 0) break
                val end = findMatchingBracket(html, bracketStart)
                if (end < 0) {
                    lastMatchEnd = match.range.last() + 1
                    continue
                }

                val raw = html.substring(bracketStart, end + 1)
                val json = jsToJson(raw)
                try {
                    val type = object : TypeToken<List<CompletionCourse>>() {}.getType()
                    val list: List<CompletionCourse> = gson.fromJson(json, type)
                    if (list.isEmpty()) {
                        lastMatchEnd = end + 1
                        continue
                    }

                    // 优先用树映射设置 typeId/moduleName；回退到文本提取
                    val textTypeId = extractTypeId(html, bracketStart)
                    val textModuleName = extractModuleName(html, bracketStart)

                    for (c in list) {
                        val code = c.code ?: continue
                        val treeInfo = treeMapping[code]
                        if (treeInfo != null) {
                            c.typeId = treeInfo.typeId
                            c.moduleName = treeInfo.moduleName
                        } else {
                            c.typeId = textTypeId
                            c.moduleName = textModuleName
                        }
                        occurrences.getOrPut(code) { mutableListOf() }.add(c to list.size)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "allCourseList 解析失败: ${e.message}")
                }
                lastMatchEnd = end + 1
            }

            // 去重：每个 code 保留来自最小 allCourseList（最具体的子分类）的出现
            val allCourses = mutableListOf<CompletionCourse>()
            for ((code, pairs) in occurrences) {
                val best = pairs.minByOrNull { it.second } ?: continue
                allCourses.add(best.first)
            }

            if (allCourses.isEmpty()) {
                Log.w(TAG, "未找到任何 allCourseList")
            } else {
                val withTypeId = allCourses.count { it.typeId != null }
                val withName = allCourses.count { it.moduleName != null }
                Log.i(TAG, "课程解析完成: ${allCourses.size} 门, typeId 已设置=$withTypeId, moduleName 已设置=$withName")
            }
            return allCourses
        }

        /**
         * 解析 `allModuleList` 树结构，建立 courseCode → ModuleInfo 映射。
         *
         * completion preview 页面内嵌的 `allModuleList` 是树结构 JSON，每个模块对象包含：
         * - `typeId`：与 [PlanModuleNode.type.id] 对齐
         * - `name`：模块名称（可能与 type.nameZh 对齐）
         * - `id`：模块 ID
         * - `allCourseList`：该模块下的课程列表
         * - `children`：子模块（更具体的子分类）
         *
         * 同一门课可能在父模块和子模块的 allCourseList 中都出现。
         * 优先保留来自更深层（更具体子分类）的模块信息。
         *
         * 支持单引号、双引号、无引号三种 JS 键格式。
         */
        internal fun parseModuleTreeMapping(html: String): Map<String, ModuleInfo> {
            // 尝试多种变量名和键格式
            val varNames = listOf("allModuleList", "moduleList")
            for (varName in varNames) {
                val mapping = tryParseModuleArray(html, varName)
                if (mapping.isNotEmpty()) return mapping
            }
            return emptyMap()
        }

        private fun tryParseModuleArray(html: String, varName: String): Map<String, ModuleInfo> {
            // 匹配 varName 后跟 : 或 =（属性赋值或变量赋值），支持单引号、双引号、无引号键
            val patterns = listOf(
                Regex("""${varName}'\s*:\s*\["""),
                Regex("""${varName}"\s*:\s*\["""),
                Regex("""\b${varName}\s*:\s*\["""),
                Regex("""\b${varName}\s*=\s*\[""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html) ?: continue
                val bracketStart = html.indexOf('[', match.range.first)
                if (bracketStart < 0) continue
                val end = findMatchingBracket(html, bracketStart)
                if (end < 0) continue

                val raw = html.substring(bracketStart, end + 1)
                val json = jsToJson(raw)

                try {
                    val tree = JsonParser.parseString(json)
                    if (!tree.isJsonArray) continue
                    val mapping = mutableMapOf<String, ModuleInfo>()
                    traverseModuleTree(tree.asJsonArray, mapping, 0)
                    if (mapping.isNotEmpty()) return mapping
                } catch (e: Exception) {
                    Log.w(TAG, "$varName 树解析失败: ${e.message}")
                }
            }
            return emptyMap()
        }

        /** 递归遍历模块树，为每个模块的 allCourseList 课程建立 code → ModuleInfo 映射 */
        private fun traverseModuleTree(
            array: com.google.gson.JsonArray,
            mapping: MutableMap<String, ModuleInfo>,
            depth: Int
        ) {
            for (element in array) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject

                val typeId = obj.get("typeId")?.takeIf { !it.isJsonNull }?.asInt
                val moduleName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                val moduleId = obj.get("id")?.takeIf { !it.isJsonNull }?.asInt
                val info = ModuleInfo(typeId, moduleName, moduleId, depth)

                // 处理本模块的 allCourseList
                obj.get("allCourseList")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { courseElem ->
                    if (!courseElem.isJsonObject) return@forEach
                    val code = courseElem.asJsonObject.get("code")?.takeIf { !it.isJsonNull }?.asString
                    if (code.isNullOrEmpty()) return@forEach
                    // 优先保留更深层（更具体子分类）的模块信息
                    val existing = mapping[code]
                    if (existing == null || depth > existing.depth) {
                        mapping[code] = info
                    }
                }

                // 递归处理子模块
                obj.get("children")?.takeIf { it.isJsonArray }?.asJsonArray?.let { children ->
                    traverseModuleTree(children, mapping, depth + 1)
                }
            }
        }

        /**
         * 从 allCourseList 关键字附近的 HTML 模块对象中提取 `typeId` 字段（回退策略）。
         *
         * 支持三种键格式：`'typeId':42`、`"typeId":42`、`typeId:42`。
         * 向前回看 2000 字符，取窗口内最后一个匹配（离 allCourseList 最近）。
         */
        internal fun extractTypeId(html: String, allCourseListIdx: Int): Int? {
            val lookBack = 2000
            val from = (allCourseListIdx - lookBack).coerceAtLeast(0)
            val window = html.substring(from, allCourseListIdx)
            // 灵活匹配：typeId 后跟可选引号、冒号、可选空白、数字
            val pattern = Regex("""typeId['"]?\s*:\s*(\d+)""")
            val matches = pattern.findAll(window).toList()
            return matches.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        }

        /**
         * 从 allCourseList 关键字附近的 HTML 模块对象中提取 `name` 字段（回退策略）。
         *
         * 支持三种键格式：`'name':'xxx'`、`"name":"xxx"`、`name:'xxx'`。
         * 向前回看 2000 字符，取窗口内最后一个匹配。
         */
        internal fun extractModuleName(html: String, allCourseListIdx: Int): String? {
            val lookBack = 2000
            val from = (allCourseListIdx - lookBack).coerceAtLeast(0)
            val window = html.substring(from, allCourseListIdx)
            // 匹配 name:'xxx' 或 name:"xxx" 或 'name':'xxx' 等
            val pattern = Regex("""name['"]?\s*:\s*['"]([^'"]+)['"]""")
            val matches = pattern.findAll(window).toList()
            return matches.lastOrNull()?.groupValues?.get(1)
        }

        /** JS 对象转 JSON：单引号→双引号，null 去引号 */
        internal fun jsToJson(js: String): String {
            return js
                .replace("'", "\"")
                .let { Regex("\"null\"").replace(it, "null") }
        }

        internal fun findMatchingBracket(s: String, start: Int): Int {
            var depth = 0
            for (i in start until s.length) {
                when (s[i]) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            return -1
        }
    }
}
