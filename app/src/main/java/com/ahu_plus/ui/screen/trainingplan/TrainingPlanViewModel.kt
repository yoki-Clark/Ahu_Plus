package com.ahu_plus.ui.screen.trainingplan

import com.ahu_plus.data.diagnostic.SafeLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.model.jw.CompletionCourse
import com.ahu_plus.data.model.jw.CompletionSummary
import com.ahu_plus.data.model.jw.GradeResponse
import com.ahu_plus.data.model.jw.PlanModuleNode
import com.ahu_plus.data.model.jw.ResultTypeEntry
import com.ahu_plus.data.model.jw.TrainingPlanResponse
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.JwAuthException
import com.ahu_plus.data.repository.ProgramCompletionRepository
import com.ahu_plus.data.repository.SessionExpiredException
import com.ahu_plus.data.repository.TrainingPlanRepository
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class TrainingPlanViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val completionRepository: ProgramCompletionRepository? = null,
    private val sessionManager: com.ahu_plus.data.local.SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingPlanUiState())
    val uiState: StateFlow<TrainingPlanUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance
    // 多级展开：父子模块可同时展开（旧版单一 expandedId 会让展开子模块折叠父模块）
    private var expandedIds: Set<Int> = emptySet()
    private var refreshJob: Job? = null
    private var requestGeneration = 0L

    init {
        viewModelScope.launch { loadFromCache() }
    }

    fun activate() {
        val updatedAt = sessionManager?.getTrainingPlanUpdatedAt() ?: 0L
        if (!DataRefreshPolicy.isStale(updatedAt, 30L * 24 * 60 * 60 * 1000)) return
        startRefresh(isRefresh = _uiState.value.topModules.isNotEmpty())
    }

    fun onRefresh() {
        startRefresh(isRefresh = true)
    }

    private fun startRefresh(isRefresh: Boolean) {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { loadAll(isRefresh, generation) }
    }

    fun toggleExpand(moduleId: Int) {
        // 点击已展开的 → 折叠；点击新的 → 追加展开（父子互不影响）
        expandedIds = if (moduleId in expandedIds) expandedIds - moduleId else expandedIds + moduleId
        _uiState.update { it.copy(expandedIds = expandedIds) }
    }

    private suspend fun loadFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getTrainingPlanJson() ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val resp = gson.fromJson(json, TrainingPlanResponse::class.java)
                applyTrainingPlan(resp)

                // 从缓存加载成绩（用于粗略完成状态兜底）
                val gradesJson = sm.getGradesJson()
                if (gradesJson != null) {
                    runCatching { gson.fromJson(gradesJson, GradeResponse::class.java) }
                        .getOrNull()?.let { applyGrades(it) }
                }
                // 从缓存加载课表（用于在读判断兜底）
                val scheduleJson = sm.getScheduleJson()
                if (scheduleJson != null) {
                    applyScheduleCache(scheduleJson)
                }
                // 从缓存加载完成数据（program-completion-preview 解析结果）。
                // typeId / moduleName 已随 CompletionCourse 序列化，冷启动恢复后
                // 子分类路由与进度条计算无需重新请求网络即可还原正确样式。
                val compCoursesJson = sm.getCompletionCoursesJson()
                val compSummaryJson = sm.getCompletionSummaryJson()
                if (!compCoursesJson.isNullOrEmpty() && !compSummaryJson.isNullOrEmpty()) {
                    runCatching {
                        val courses = gson.fromJson(
                            compCoursesJson,
                            object : TypeToken<List<CompletionCourse>>() {}.type
                        ) as List<CompletionCourse>
                        val summary = gson.fromJson(compSummaryJson, CompletionSummary::class.java)
                        applyCompletionData(courses, summary)
                    }.onFailure { Log.w(TAG, "Failed to restore completion data from cache: ${it.message}") }
                }
            }
            _uiState.update {
                it.copy(dataStatus = DataSnapshotStatus.cache(sm.getTrainingPlanUpdatedAt()))
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadAll(isRefresh: Boolean, generation: Long) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        } else {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        val wasLoaded = _uiState.value.topModules.isNotEmpty()
        try {
            withContext(Dispatchers.IO) {
                // 并行加载培养方案 + 完成数据
                val planResult = jwAuthRepository.executeWithSessionRetry {
                    trainingPlanRepository.getTrainingPlan()
                }
                val compResult = completionRepository?.let { repository ->
                    jwAuthRepository.executeWithSessionRetry {
                        repository.getCompletionData()
                    }
                }

                planResult.fold(
                    onSuccess = { resp ->
                        if (generation != requestGeneration) return@fold
                        sessionManager?.let { sm ->
                            try { sm.saveTrainingPlanJson(gson.toJson(resp)) }
                            catch (_: Exception) { Log.w(TAG, "Failed to cache training plan JSON") }
                        }
                        applyTrainingPlan(resp)
                        _uiState.update {
                            it.copy(dataStatus = DataSnapshotStatus.network())
                        }
                    },
                    onFailure = { e ->
                        if (generation != requestGeneration) return@fold
                        // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "培养方案加载失败") else it.error,
                                needsLogin = !wasLoaded &&
                                    (e is SessionExpiredException || e is JwAuthException),
                                dataStatus = if (wasLoaded) {
                                    it.dataStatus?.withFailedRefresh()
                                } else it.dataStatus,
                            )
                        }
                    }
                )

                compResult?.fold(
                    onSuccess = { (courses, summary) ->
                        if (generation != requestGeneration) return@fold
                        applyCompletionData(courses, summary)
                        // 持久化完成数据到缓存，确保冷启动能还原 typeId / moduleName
                        // 子分类路由与进度条计算无需重新请求网络。
                        sessionManager?.let { sm ->
                            try {
                                val coursesJson = gson.toJson(courses)
                                val summaryJson = gson.toJson(summary)
                                sm.saveCompletionData(coursesJson, summaryJson)
                            } catch (_: Exception) {
                                Log.w(TAG, "Failed to cache completion data")
                            }
                        }
                    },
                    onFailure = { /* 完成数据加载失败使用兜底方案 */ }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != requestGeneration) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    needsLogin = !wasLoaded &&
                        (e is SessionExpiredException || e is JwAuthException),
                    dataStatus = if (wasLoaded) {
                        it.dataStatus?.withFailedRefresh()
                    } else it.dataStatus,
                )
            }
        }
        _uiState.update { it.copy(isRefreshing = false) }
    }

    private fun applyTrainingPlan(resp: TrainingPlanResponse) {
        _uiState.update {
            it.copy(
                isLoading = false,
                planId = resp.id,
                topModules = resp.children.orEmpty(),
                totalRequiredCredits = resp.sumChildrenRequiredCreditsOrZero?.toDoubleOrNull()
                    ?: resp.requireInfo?.requiredCredits,
                creditBySubModule = resp.creditBySubModule.orEmpty(),
                error = null,
                needsLogin = false
            )
        }
    }

    /** 官方 program-completion-preview 数据 */
    private fun applyCompletionData(courses: List<CompletionCourse>, summary: CompletionSummary) {
        val passedCodes = courses.filter { it.isPassed }.mapNotNull { it.code }.toSet()
        val takingCodes = courses.filter { it.isTaking }.mapNotNull { it.code }.toSet()
        val failedCodes = courses.filter { it.isFailed }.mapNotNull { it.code }.toSet()

        // code → credits 映射
        val codeCredits = courses.filter { it.code != null }.associate { it.code!! to (it.credits ?: 0.0) }

        _uiState.update { state ->
            // 收集培养方案中所有课程代码
            val allPlanCodes = mutableSetOf<String>()
            fun collectCodes(node: PlanModuleNode) {
                node.planCourses?.forEach { c -> c.course?.code?.let { allPlanCodes.add(it) } }
                node.children?.forEach { collectCodes(it) }
            }
            state.topModules.forEach { collectCodes(it) }

            // 已通过但不在培养方案中的课程代码（如通识选修的 TX 课程）
            val unmatchedPassed = passedCodes - allPlanCodes

            // 构建 typeId → moduleName 映射（含子模块），用于按 CompletionCourse.typeId 精确归属未匹配学分。
            // typeId 来自 completion preview HTML 中 allCourseList 所属模块的 typeId，与 PlanModuleNode.type.id 对齐。
            val typeIdToName = mutableMapOf<Int, String>()
            // 同时构建 moduleName → typeId 反向映射，用于 typeId 为空时按名称回退匹配
            val moduleNameToTypeId = mutableMapOf<String, Int>()
            fun collectTypeNames(node: PlanModuleNode) {
                node.type?.let { typeInfo ->
                    typeInfo.id?.let { tid ->
                        typeInfo.nameZh?.let { name ->
                            typeIdToName[tid] = name
                            moduleNameToTypeId[name] = tid
                        }
                    }
                }
                node.children?.forEach { collectTypeNames(it) }
            }
            state.topModules.forEach { collectTypeNames(it) }
            Log.i(TAG, "typeId 映射: ${typeIdToName.size} 个, name 映射: ${moduleNameToTypeId.size} 个")

            // 按 typeId 精确归属未匹配已通过学分到对应模块（含子分类）；
            // typeId 为空或未命中时，回退到 moduleName 匹配；仍不命中则回退到「通识选修」
            val flexModule = "通识选修"
            val courseByCode = courses.filter { it.code != null }.associateBy { it.code!! }
            val unmatchedCreditsByModule = mutableMapOf<String, Double>()
            var unmatchedPassedCredits = 0.0
            for (code in unmatchedPassed) {
                val credits = codeCredits[code] ?: 0.0
                unmatchedPassedCredits += credits
                val course = courseByCode[code]
                // 三级匹配：typeId → moduleName → 通识选修回退
                val targetName = course?.typeId?.let { typeIdToName[it] }
                    ?: course?.moduleName?.let { name -> typeIdToName.values.find { it == name } }
                    ?: flexModule
                unmatchedCreditsByModule[targetName] = (unmatchedCreditsByModule[targetName] ?: 0.0) + credits
            }

            // 同样收集未匹配的修读中/挂科/未修课程
            val allUnmatchedCodes = (passedCodes + takingCodes + failedCodes) - allPlanCodes
            val unmatchedCourses = courses.filter { it.code in allUnmatchedCodes }

            // ponytail: 与 applyGrades 兜底的 grades 通识选修历史选课合并去重,避免覆盖丢失
            val mergedUnmatched = uniqueUnmatched(unmatchedCourses, state.unmatchedCompletionCourses)

            // 计算每模块完成学分
            val moduleCompletion = computeModuleCompletion(
                state.topModules, passedCodes, takingCodes, failedCodes, codeCredits
            )
            // 把未匹配学分按 typeId 精确分配到对应模块（含子分类），回退到通识选修
            val adjustedCompletion = moduleCompletion.toMutableMap()
            for ((moduleName, credits) in unmatchedCreditsByModule) {
                if (credits > 0) {
                    adjustedCompletion[moduleName] = (adjustedCompletion[moduleName] ?: 0.0) + credits
                }
            }

            state.copy(
                officialSummary = summary.copy(
                    requiredCredits = state.totalRequiredCredits,
                    passedCount = passedCodes.size,
                    takingCount = takingCodes.size,
                    unrepairedCount = courses.count { c -> c.isUnrepaired },
                    failedCount = failedCodes.size
                ),
                passedCourseCodes = passedCodes,
                inProgressCourseCodes = takingCodes,
                failedCourseCodes = failedCodes,
                moduleCompletion = adjustedCompletion,
                unmatchedCompletionCourses = mergedUnmatched,
                hasOfficialCompletion = true,
                isLoading = false
            )
        }
    }

    /** 递归计算每个模块的已通过学分（深度遍历子树） */
    private fun computeModuleCompletion(
        modules: List<PlanModuleNode>,
        passedCodes: Set<String>,
        takingCodes: Set<String>,
        failedCodes: Set<String>,
        codeCredits: Map<String, Double>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (m in modules) {
            result[m.type?.nameZh ?: continue] = sumPassedRecursive(m, passedCodes, codeCredits)
        }
        return result
    }

    private fun sumPassedRecursive(
        node: PlanModuleNode,
        passedCodes: Set<String>,
        codeCredits: Map<String, Double>
    ): Double {
        var sum = 0.0
        node.planCourses?.forEach { c ->
            val code = c.course?.code ?: return@forEach
            if (code in passedCodes) sum += codeCredits[code] ?: c.displayCredits
        }
        node.children?.forEach { child ->
            sum += sumPassedRecursive(child, passedCodes, codeCredits)
        }
        return sum
    }

    /** 兜底：从成绩数据粗略判断已修 + 补全 completion preview 拿不到的通识选修历史选课 */
    private fun applyGrades(resp: GradeResponse) {
        val allGrades = resp.allGrades()
        val gradeByCode = allGrades
            .filter { it.courseCode != null }
            .groupBy { it.courseCode!! }
            .mapValues { (_, grades) ->
                grades.firstOrNull { it.gaGrade != null && it.gaGrade != "--" && it.published == true }
                    ?: grades.firstOrNull { it.published == true }
                    ?: grades.first()
            }

        val passedCodes = gradeByCode
            .filter { (_, g) -> g.gaGrade != null && g.gaGrade != "--" && g.published == true }
            .keys

        // ponytail: completion preview HTML 只含当学期选课,通识选修历史课(TX 前缀)从 grades 兜底
        val gradeUnmatched = gradeByCode.values
            .filter { it.courseTaxon == "通识选修" && (it.passed == true || (it.gaGrade != null && it.gaGrade != "--" && it.published == true)) }
            .map { g -> CompletionCourse(
                code = g.courseCode,
                nameZh = g.courseName,
                credits = g.credits,
                compulsory = g.compulsory,
                finalResultType = ResultTypeEntry(name = if (g.passed == true) "PASSED" else "UNREPAIRED")
            ) }

        if (!_uiState.value.hasOfficialCompletion) {
            _uiState.update { it.copy(passedCourseCodes = it.passedCourseCodes + passedCodes) }
        }
        // 无论 hasOfficialCompletion 如何都合并,补全 completion preview 漏掉的历史选课
        _uiState.update { state ->
            state.copy(
                unmatchedCompletionCourses = uniqueUnmatched(state.unmatchedCompletionCourses, gradeUnmatched)
            )
        }
    }

    /** 兜底：从缓存的课表 JSON 提取 courseCode */
    private fun applyScheduleCache(json: String) {
        runCatching {
            val data = gson.fromJson(json, com.ahu_plus.data.model.jw.ScheduleData::class.java)
            val codes = data.activities.mapNotNull { it.courseCode }.toSet()
            if (!_uiState.value.hasOfficialCompletion) {
                _uiState.update { it.copy(inProgressCourseCodes = it.inProgressCourseCodes + codes) }
            }
        }
    }

    fun getCourseStatus(courseCode: String): CourseCompletion = when {
        courseCode in _uiState.value.passedCourseCodes -> CourseCompletion.PASSED
        courseCode in _uiState.value.failedCourseCodes -> CourseCompletion.FAILED
        courseCode in _uiState.value.inProgressCourseCodes -> CourseCompletion.IN_PROGRESS
        else -> CourseCompletion.NOT_TAKEN
    }

    private companion object {
        private const val TAG = "TrainingPlanVM"
    }

    /** 2026-06-23: SessionExpiredException 后尝试静默重连+重试一次。 */
}

enum class CourseCompletion { NOT_TAKEN, IN_PROGRESS, PASSED, FAILED }

/** 合并两批 unmatched 课程,按 courseCode 去重(同 code 时保留 a 中的版本,applyCompletionData 优先) */
internal fun uniqueUnmatched(a: List<CompletionCourse>, b: List<CompletionCourse>): List<CompletionCourse> =
    (a + b).distinctBy { it.code }

data class TrainingPlanUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val planId: Int? = null,
    val topModules: List<PlanModuleNode> = emptyList(),
    val totalRequiredCredits: Double? = null,
    val creditBySubModule: Map<String, Double> = emptyMap(),
    val expandedIds: Set<Int> = emptySet(),
    val error: String? = null,
    val needsLogin: Boolean = false,
    // ── 完成状态 ──
    val hasOfficialCompletion: Boolean = false,
    val officialSummary: CompletionSummary = CompletionSummary(),
    val passedCourseCodes: Set<String> = emptySet(),
    val inProgressCourseCodes: Set<String> = emptySet(),
    val failedCourseCodes: Set<String> = emptySet(),
    /** 模块名 → 已通过学分 */
    val moduleCompletion: Map<String, Double> = emptyMap(),
    /** 未匹配到培养方案课程的完成数据（如通识选修的 TX 课程） */
    val unmatchedCompletionCourses: List<CompletionCourse> = emptyList(),
    val dataStatus: DataSnapshotStatus? = null,
) {
    val totalModuleCount: Int get() = topModules.size
    val completionProgress: Float
        get() = if (hasOfficialCompletion) officialSummary.completionProgress
        else if (totalRequiredCredits != null && totalRequiredCredits > 0) {
            (totalEarnedCredits / totalRequiredCredits).toFloat().coerceIn(0f, 1f)
        } else 0f

    /** 兜底：从成绩计算已获学分 */
    private val totalEarnedCredits: Double
        get() = officialSummary.passedCredits
}
