package com.ahu_plus.ui.screen.evaluation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredLoader
import com.ahu_plus.ui.components.CenteredMessage
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.theme.AhuIndigo
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import com.ahu_plus.ui.theme.AhuTeal

/** 评教列表：学期筛选 → 课程卡 → 展开教师 → 进入对应问卷。 */
@Composable
fun EvaluationListScreen(
    viewModel: EvaluationViewModel,
    onBack: () -> Unit,
    onOpenTask: (TeacherEvaluationTask) -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val courseGroups = remember(state.tasks) { groupEvaluationTasks(state.tasks) }
    var expandedCourseKey by rememberSaveable(state.selectedSemesterId) {
        mutableStateOf<String?>(null)
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("评教") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshList) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 学期栏属于页面固定筛选器；即使当前学期为空，也必须保留用于切换。
            if (state.semesters.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(
                        horizontal = AhuSpacing.ScreenHorizontal,
                        vertical = AhuSpacing.sm,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
                ) {
                    items(state.semesters, key = { it.id }) { semester ->
                        FilterChip(
                            selected = semester.id == state.selectedSemesterId,
                            onClick = { viewModel.selectSemester(semester.id) },
                            label = { Text(semester.name) },
                            shape = AhuShapes.Pill,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.tasks.isEmpty() -> CenteredLoader()
                    state.error != null && state.tasks.isEmpty() -> CenteredError(
                        message = state.error!!,
                        onRetry = viewModel::refreshList,
                    )
                    state.tasks.isEmpty() && state.semesters.isNotEmpty() ->
                        CenteredMessage("本学期暂无评教任务")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = AhuSpacing.ScreenHorizontal,
                            vertical = AhuSpacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AhuSpacing.CardGap),
                    ) {
                        items(courseGroups, key = { it.key }) { group ->
                            CourseEvaluationCard(
                                group = group,
                                expanded = expandedCourseKey == group.key,
                                onToggle = {
                                    expandedCourseKey = if (expandedCourseKey == group.key) null else group.key
                                },
                                onOpenTask = onOpenTask,
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AhuSpacing.xl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseEvaluationCard(
    group: EvaluationCourseGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenTask: (TeacherEvaluationTask) -> Unit,
) {
    val pendingCount = group.tasks.count { !isEvaluationFinished(it.status) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(AhuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AhuShapes.IconBox)
                    .background(AhuGradient.Blue.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PendingActions,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(AhuSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("${group.tasks.size} 位任课教师")
                        if (pendingCount > 0) append(" · 待评 $pendingCount")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起教师" else "展开教师",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                group.tasks.forEachIndexed { index, task ->
                    TeacherEvaluationRow(task = task, onClick = { onOpenTask(task) })
                    if (index != group.tasks.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = AhuSpacing.md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherEvaluationRow(
    task: TeacherEvaluationTask,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AhuSpacing.md, vertical = AhuSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.teacherName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (task.evaluationQuestionnaireName.isNotBlank()) {
                Text(
                    text = task.evaluationQuestionnaireName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusChip(status = task.status)
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "进入${task.teacherName}的评教",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = AhuSpacing.xs),
        )
    }
}

private fun isEvaluationFinished(status: String?): Boolean =
    status.equals("FINISHED", ignoreCase = true) ||
        status.equals("SUBMITTED", ignoreCase = true) ||
        status.equals("DONE", ignoreCase = true)

@Composable
private fun StatusChip(status: String?) {
    val finished = isEvaluationFinished(status)
    val color = if (finished) AhuTeal else AhuIndigo
    val label = if (finished) "已评" else "未评"
    val icon = if (finished) Icons.Filled.CheckCircle else Icons.Filled.PendingActions

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
