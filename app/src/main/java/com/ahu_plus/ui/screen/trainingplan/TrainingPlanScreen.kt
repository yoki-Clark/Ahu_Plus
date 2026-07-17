package com.ahu_plus.ui.screen.trainingplan

import com.ahu_plus.ui.components.CenteredLoader
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.jw.CompletionCourse
import com.ahu_plus.data.model.jw.CompletionSummary
import com.ahu_plus.data.model.jw.PlanCourse
import com.ahu_plus.data.model.jw.PlanCourseInfo
import com.ahu_plus.data.model.jw.PlanEnumValue
import com.ahu_plus.data.model.jw.PlanModuleNode
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.DataStatusFooter
import com.ahu_plus.ui.theme.AhuShapes

// ── 颜色常量 ──────────────────────────────────────────────────────────
private val PropRequired = Color(0xFF1565C0)
private val PropElective = Color(0xFF6A1B9A)
private val ExamColor = Color(0xFFE65100)
private val TypeTheory = Color(0xFF2E7D32)
private val TypeExperiment = Color(0xFF0277BD)
private val TypePractice = Color(0xFF6A1B9A)
private val TypeOther = Color(0xFF616161)
private val PassedGreen = Color(0xFF27AE60)
private val NotTakenGray = Color(0xFFBDBDBD)
private val InProgressOrange = Color(0xFFEF6C00)
private val FailedRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanScreen(
    viewModel: TrainingPlanViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("培养方案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
        when {
            uiState.isLoading && uiState.topModules.isEmpty() -> {
                CenteredLoader()
            }
            uiState.error != null && uiState.topModules.isEmpty() -> {
                CenteredError(
                    message = uiState.error!!,
                    onRetry = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh,
                    actionLabel = if (uiState.needsLogin) "去登录" else "重试",
                    modifier = Modifier.fillMaxSize()
                )
            }
            uiState.topModules.isEmpty() -> {
                CenteredMessage(text = "暂无培养方案数据")
            }
            else -> {
                val allCourses = uiState.topModules.sumOf { countAllCourses(it) }
                val rows = buildPlanRows(
                    modules = uiState.topModules,
                    expandedIds = uiState.expandedIds,
                    unmatchedCourses = uiState.unmatchedCompletionCourses
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 总览卡片
                    item(key = "overview") {
                        OverviewCard(
                            totalRequired = uiState.totalRequiredCredits,
                            summary = if (uiState.hasOfficialCompletion) uiState.officialSummary else null,
                            moduleCount = uiState.totalModuleCount,
                            totalCourses = allCourses
                        )
                    }

                    // 一级模块 + 子模块 + 课程已全部展平到 rows，无嵌套滚动
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is PlanRow.Module -> ModuleHeader(
                                module = row.module,
                                depth = row.depth,
                                expandable = row.expandable,
                                isExpanded = row.module.idOrHash in uiState.expandedIds,
                                isFlexAbsorber = row.isFlexAbsorber,
                                passedCodes = uiState.passedCourseCodes,
                                inProgressCodes = uiState.inProgressCourseCodes,
                                failedCodes = uiState.failedCourseCodes,
                                unmatchedCourses = uiState.unmatchedCompletionCourses,
                                onToggle = viewModel::toggleExpand,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = (row.depth * 12).dp)
                            )
                            is PlanRow.Course -> CourseCard(
                                course = row.course,
                                index = row.index,
                                passedCodes = uiState.passedCourseCodes,
                                inProgressCodes = uiState.inProgressCourseCodes,
                                failedCodes = uiState.failedCourseCodes,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = (row.depth * 12 + 8).dp)
                            )
                            is PlanRow.SectionLabel -> Text(
                                text = row.text,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = (row.depth * 12 + 8).dp, top = 4.dp)
                            )
                        }
                    }

                    uiState.dataStatus?.let { status ->
                        item(key = "data-status") {
                            DataStatusFooter(status = status)
                        }
                    }
                    item(key = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
        }
    }
}

// ── 总览卡片 ──────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(
    totalRequired: Double?,
    summary: CompletionSummary?,
    moduleCount: Int,
    totalCourses: Int
) {
    val hasCompletion = summary != null
    val progress = if (hasCompletion) summary!!.completionProgress else -1f

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("培养方案总学分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("$moduleCount 个模块 · $totalCourses 门课程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
                Text(
                    text = totalRequired?.let { "%.0f".format(it) } ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 完成进度条
            if (progress >= 0f) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = if (progress >= 1f) PassedGreen else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (progress >= 1f) PassedGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "已通过 ${summary!!.passedCredits.toInt()} / 要求 ${totalRequired?.let { "%.0f".format(it) } ?: "—"} 学分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                // 状态明细
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusBadge("已修 ${summary.passedCount}", PassedGreen)
                    StatusBadge("在读 ${summary.takingCount}", InProgressOrange)
                    if (summary.failedCount > 0) StatusBadge("挂科 ${summary.failedCount}", FailedRed)
                    StatusBadge("未修 ${summary.unrepairedCount}", NotTakenGray)
                }
            }
        }
    }
}

// ── 模块表头（展平后，不再递归渲染内容） ──────────────────────────────
// 旧实现把子模块/课程嵌套在 heightIn(400).verticalScroll 里，与外层 LazyColumn
// 同轴滚动冲突且裁剪长列表；且单一 expandedId 让展开子模块会折叠父模块。
// 现在整棵树由 buildPlanRows 展平进 LazyColumn，本组件只画一个表头。

@Composable
private fun ModuleHeader(
    module: PlanModuleNode,
    depth: Int,
    expandable: Boolean,
    isExpanded: Boolean,
    isFlexAbsorber: Boolean,
    passedCodes: Set<String>,
    inProgressCodes: Set<String>,
    failedCodes: Set<String>,
    unmatchedCourses: List<CompletionCourse>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val moduleId = module.idOrHash
    val reqCredits = module.requiredCredits
    val reqCourseNum = module.requireInfo?.requiredCourseNum?.takeIf { it > 0 }
    val creditsUpper = module.limitInfo?.creditsUpperLimit
    val totalCourseCount = countAllCourses(module)
    val sumCredits = module.sumChildrenRequiredCreditsOrZero?.toDoubleOrNull()

    // 计算本模块的完成进度
    val compStats = computeModuleCompletion(module, passedCodes, inProgressCodes, failedCodes)
    // 仅「自由选修吸收桶」一个模块吸收未匹配课程的学分（避免被多个带"选修"模块重复累加）
    val extraPassed = if (isFlexAbsorber) {
        unmatchedCourses.filter { it.isPassed }.sumOf { it.credits ?: 0.0 }
    } else 0.0
    val totalPassed = compStats.passedCredits + extraPassed
    val effectiveRequired = reqCredits ?: sumCredits ?: 0.0
    val moduleProgress = if (effectiveRequired > 0) (totalPassed / effectiveRequired).toFloat().coerceIn(0f, 1f) else -1f

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (depth == 0) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (depth == 0) 1.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable { onToggle(moduleId) } else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Number badge for top-level modules
                if (depth == 0 && module.index != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${(module.index ?: 0) + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.displayName,
                        style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Medium
                    )
                    // Credit & course info with completion
                    val infoParts = mutableListOf<String>()
                    if (effectiveRequired > 0) {
                        infoParts.add("${totalPassed.toInt()}/${effectiveRequired.toInt()} 学分")
                    } else {
                        reqCredits?.let { infoParts.add("要求 ${it.toInt()} 学分") }
                    }
                    reqCourseNum?.let { infoParts.add("至少 ${it} 门") }
                    creditsUpper?.let { infoParts.add("上限 ${it.toInt()} 学分") }
                    if (totalCourseCount > 0) infoParts.add("共 $totalCourseCount 门课")
                    if (sumCredits != null && reqCredits == null) infoParts.add("合计 ${sumCredits.toInt()} 学分")
                    if (infoParts.isNotEmpty()) {
                        Text(
                            text = infoParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 模块迷你进度条
                    if (moduleProgress >= 0f && effectiveRequired > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { moduleProgress },
                                modifier = Modifier.weight(1f).height(4.dp),
                                color = if (moduleProgress >= 1f) PassedGreen else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(moduleProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (expandable) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, "展开/收起",
                        modifier = Modifier.size(24.dp).rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── 树展平：把递归模块树拍平成 LazyColumn 的行，避免嵌套同轴滚动 ──────────

sealed interface PlanRow {
    val key: String
    val depth: Int

    data class Module(
        val module: PlanModuleNode,
        override val depth: Int,
        val expandable: Boolean,
        val isFlexAbsorber: Boolean
    ) : PlanRow {
        override val key get() = "mod_${module.idOrHash}"
    }

    data class Course(
        val course: PlanCourse,
        val index: Int,
        override val depth: Int,
        val parentKey: String
    ) : PlanRow {
        override val key get() = "crs_${parentKey}_$index"
    }

    data class SectionLabel(
        val text: String,
        override val depth: Int,
        val parentKey: String
    ) : PlanRow {
        override val key get() = "sec_${parentKey}"
    }
}

/**
 * 在整棵树里挑「唯一」一个吸收桶，用来归置未匹配课程（学生自选、但不在方案固定课表里的课）。
 * 优先级：名含「通识选修」的模块 > 名含「选修」且本身无固定课表的自由桶。
 * 找不到则返回 null（未匹配课程不挂到任何模块，但总览仍统计）。
 * ponytail: 名字启发式；后端 moduleCompletionId 若能精确对到 plan 模块再换精确归属。
 */
private fun findFlexAbsorberId(modules: List<PlanModuleNode>): Int? {
    var general: Int? = null    // 含「通识选修」
    var freeBucket: Int? = null // 含「选修」且无固定课表

    fun walk(m: PlanModuleNode) {
        val name = m.displayName
        if (general == null && name.contains("通识选修")) general = m.idOrHash
        if (freeBucket == null && name.contains("选修") &&
            m.planCourses.isNullOrEmpty() && m.children.isNullOrEmpty()
        ) freeBucket = m.idOrHash
        m.children?.forEach { walk(it) }
    }
    modules.forEach { walk(it) }
    return general ?: freeBucket
}

/**
 * 把模块树展平成行列表。只展开的模块才会吐出其子行；父子可同时展开。
 */
private fun buildPlanRows(
    modules: List<PlanModuleNode>,
    expandedIds: Set<Int>,
    unmatchedCourses: List<CompletionCourse>
): List<PlanRow> {
    val rows = mutableListOf<PlanRow>()
    val flexAbsorberId = if (unmatchedCourses.isEmpty()) null else findFlexAbsorberId(modules)

    fun walk(module: PlanModuleNode, depth: Int) {
        val isAbsorber = module.idOrHash == flexAbsorberId
        val showUnmatched = isAbsorber && unmatchedCourses.isNotEmpty()
        val expandable = !module.planCourses.isNullOrEmpty() ||
            !module.children.isNullOrEmpty() || showUnmatched

        val parentKey = "mod_${module.idOrHash}"
        rows += PlanRow.Module(module, depth, expandable, isAbsorber)

        if (module.idOrHash !in expandedIds) return

        // 本模块直属课程
        module.planCourses?.forEachIndexed { index, course ->
            rows += PlanRow.Course(course, index, depth + 1, parentKey)
        }
        // 子模块递归
        module.children?.forEach { child -> walk(child, depth + 1) }
        // 未匹配课程（仅唯一吸收桶模块显示，避免多个带"选修"模块重复）
        if (showUnmatched) {
            rows += PlanRow.SectionLabel("已选课程", depth + 1, parentKey)
            unmatchedCourses.forEachIndexed { index, cc ->
                val fake = PlanCourse(
                    course = PlanCourseInfo(nameZh = cc.nameZh, code = cc.code, credits = cc.credits),
                    courseProperty = PlanEnumValue(nameZh = if (cc.compulsory == true) "必修" else "选修"),
                    examMode = null
                )
                rows += PlanRow.Course(fake, index, depth + 1, "${parentKey}_u")
            }
        }
    }

    modules.forEach { walk(it, 0) }
    return rows
}

// ── 课程卡片 ──────────────────────────────────────────────────────────

@Composable
private fun CourseCard(
    course: PlanCourse,
    index: Int,
    passedCodes: Set<String>,
    inProgressCodes: Set<String>,
    failedCodes: Set<String>,
    modifier: Modifier = Modifier
) {
    val periodInfo = course.periodInfo
    val name = course.displayName
    val code = course.displayCode
    val credits = course.displayCredits
    val property = course.displayProperty
    val examMode = course.displayExamMode
    val courseType = course.courseType?.nameZh ?: ""
    val dept = course.openDepartment?.nameZh ?: ""
    val readableTerms = course.readableTerms
    val suggestTerms = course.suggestTerms?.takeIf { it.isNotEmpty() }
    val langZh = course.teachLang?.nameZh

    // 三数据源判断修读状态: 成绩(已修) > 课表(修读中) > 无(未修)
    val status = when {
        code in passedCodes -> CourseCompletion.PASSED
        code in failedCodes -> CourseCompletion.FAILED
        code in inProgressCodes -> CourseCompletion.IN_PROGRESS
        else -> CourseCompletion.NOT_TAKEN
    }

    // 修读状态颜色和文字
    val (statusColor, statusText, statusIcon) = when (status) {
        CourseCompletion.PASSED -> Triple(PassedGreen, "已修", Icons.Filled.CheckCircle)
        CourseCompletion.IN_PROGRESS -> Triple(InProgressOrange, "修读中", Icons.Filled.Schedule)
        CourseCompletion.FAILED -> Triple(FailedRed, "挂科", Icons.Filled.Cancel)
        CourseCompletion.NOT_TAKEN -> Triple(NotTakenGray, "未修", Icons.Filled.RemoveCircleOutline)
    }

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                CourseCompletion.PASSED -> PassedGreen.copy(alpha = 0.04f)
                CourseCompletion.IN_PROGRESS -> InProgressOrange.copy(alpha = 0.04f)
                CourseCompletion.FAILED -> FailedRed.copy(alpha = 0.06f)
                CourseCompletion.NOT_TAKEN -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: status icon + index + course name + credits
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 修读状态图标
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // 状态标签
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (code.isNotBlank()) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Credits badge
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Text(
                        text = "${credits.toInt()} 学分",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Property + Exam mode + Course type chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 必修/选修
                if (property.isNotBlank()) {
                    val pc = if (property.contains("必修")) PropRequired else if (property.contains("选修")) PropElective else TypeOther
                    Chip(property, pc)
                }
                // 考试/考查
                if (examMode.isNotBlank()) {
                    Chip(examMode, if (examMode.contains("考试")) ExamColor else TypeOther)
                }
                // 课程类别
                if (courseType.isNotBlank()) {
                    val tc = when {
                        courseType.contains("理论") -> TypeTheory
                        courseType.contains("实验") -> TypeExperiment
                        courseType.contains("实践") || courseType.contains("实习") -> TypePractice
                        else -> TypeOther
                    }
                    Chip(courseType, tc)
                }
                // 授课语言
                if (langZh != null && langZh != "中文") {
                    Chip(langZh, TypeOther)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Department & semesters
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dept.isNotBlank()) {
                    Icon(Icons.Filled.AccountBalance, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dept,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Row 4: Available semesters
            val termText = buildTermText(readableTerms, suggestTerms)
            if (termText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = termText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Row 5: Period hours breakdown
            val hoursText = buildHoursText(periodInfo)
            if (hoursText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Book, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = hoursText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ── 辅助组件 ──────────────────────────────────────────────────────────

@Composable
private fun Chip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 构建学期文本 */
private fun buildTermText(readableTerms: List<String>?, suggestTerms: List<String>?): String {
    val sb = StringBuilder()
    // Readable terms
    val rt = readableTerms?.takeIf { it.isNotEmpty() }
    if (rt != null) {
        val nums = rt.mapNotNull { it.toIntOrNull() }.sorted()
        if (nums.size == 8 && nums.first() == 1 && nums.last() == 8) {
            sb.append("开设学期：第 1-8 学期")
        } else if (nums.isNotEmpty()) {
            sb.append("开设学期：第${nums.joinToString("、")}学期")
        }
    }
    // Suggested terms
    val st = suggestTerms?.takeIf { it.isNotEmpty() }
    if (st != null) {
        if (sb.isNotEmpty()) sb.append(" ｜ ")
        val names = st.map { termName(it) }
        sb.append("建议修读：${names.joinToString("、")}")
    }
    return sb.toString()
}

/** 构建学时明细文本 */
private fun buildHoursText(pi: com.ahu_plus.data.model.jw.PlanPeriodInfo?): String {
    if (pi == null) return ""
    val parts = mutableListOf<String>()
    pi.theory?.takeIf { it > 0 }?.let { parts.add("理论 ${it}学时") }
    pi.experiment?.takeIf { it > 0 }?.let { parts.add("实验 ${it}学时") }
    pi.practice?.takeIf { it > 0 }?.let { parts.add("实践 ${it}学时") }
    pi.machine?.takeIf { it > 0 }?.let { parts.add("上机 ${it}学时") }
    pi.design?.takeIf { it > 0 }?.let { parts.add("设计 ${it}学时") }
    pi.test?.takeIf { it > 0 }?.let { parts.add("考试 ${it}学时") }
    pi.extra?.takeIf { it > 0 }?.let { parts.add("课外 ${it}学时") }

    val extras = mutableListOf<String>()
    pi.weeks?.let { if (it > 0) extras.add("${it.toInt()}周") }
    pi.periodsPerWeek?.let { if (it > 0) extras.add("周${it.toInt()}学时") }

    val main = if (parts.isEmpty() && pi.total != null && pi.total > 0) {
        "合计 ${pi.total}学时"
    } else {
        parts.joinToString(" · ")
    }

    val suffix = if (extras.isNotEmpty()) "（${extras.joinToString("，")}）" else ""
    return main + suffix
}

private fun termName(term: String): String = when (term) {
    "TERM_1" -> "第1学期"
    "TERM_2" -> "第2学期"
    "TERM_3" -> "第3学期"
    "TERM_4" -> "第4学期"
    "TERM_5" -> "第5学期"
    "TERM_6" -> "第6学期"
    "TERM_7" -> "第7学期"
    "TERM_8" -> "第8学期"
    else -> term
}

/** 递归统计模块下所有课程数 */
private fun countAllCourses(module: PlanModuleNode): Int {
    var count = module.planCourses?.size ?: 0
    module.children?.forEach { count += countAllCourses(it) }
    return count
}

data class ModuleCompStats(
    val passedCredits: Double = 0.0,
    val takingCredits: Double = 0.0,
    val failedCredits: Double = 0.0,
    val passedCount: Int = 0,
    val takingCount: Int = 0,
    val failedCount: Int = 0,
    val unrepairedCount: Int = 0
)

/** 递归计算模块下的完成统计（含子模块） */
private fun computeModuleCompletion(
    module: PlanModuleNode,
    passedCodes: Set<String>,
    inProgressCodes: Set<String>,
    failedCodes: Set<String>
): ModuleCompStats {
    var passedCredits = 0.0
    var takingCredits = 0.0
    var failedCredits = 0.0
    var passedCount = 0
    var takingCount = 0
    var failedCount = 0
    var unrepairedCount = 0

    module.planCourses?.forEach { course ->
        val code = course.displayCode
        val credits = course.displayCredits
        when {
            code in passedCodes -> { passedCredits += credits; passedCount++ }
            code in failedCodes -> { failedCredits += credits; failedCount++ }
            code in inProgressCodes -> { takingCredits += credits; takingCount++ }
            else -> unrepairedCount++
        }
    }
    module.children?.forEach { child ->
        val childStats = computeModuleCompletion(child, passedCodes, inProgressCodes, failedCodes)
        passedCredits += childStats.passedCredits
        takingCredits += childStats.takingCredits
        failedCredits += childStats.failedCredits
        passedCount += childStats.passedCount
        takingCount += childStats.takingCount
        failedCount += childStats.failedCount
        unrepairedCount += childStats.unrepairedCount
    }

    return ModuleCompStats(passedCredits, takingCredits, failedCredits, passedCount, takingCount, failedCount, unrepairedCount)
}

// ── 通用状态组件 ──────────────────────────────────────────────────────

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── 通用状态组件已移至 ui/components/CenteredComponents.kt ──
