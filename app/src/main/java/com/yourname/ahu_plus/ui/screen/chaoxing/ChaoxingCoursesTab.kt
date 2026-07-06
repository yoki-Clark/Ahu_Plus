package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.data.model.CxCourseProgress
import com.yourname.ahu_plus.ui.theme.AhuShapes
import kotlinx.coroutines.launch

@Composable
internal fun CoursesTabContent(
    viewModel: ChaoxingViewModel,
    loginState: CxLoginState,
    coursesState: CxCoursesState,
    onShowStudySheet: () -> Unit,
    onCourseClick: (CxCourse) -> Unit,
    onNavigateToSettings: () -> Unit,
    listState: LazyListState,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenKeys = viewModel.settingsState.collectAsStateWithLifecycle().value.hiddenCourseKeys
    val signFlow = viewModel.signFlowState.collectAsStateWithLifecycle().value

    // 即时签到流程的一次性提示(无活动/完成)→ Snackbar
    LaunchedEffect(signFlow.message) {
        signFlow.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSignFlowMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !loginState.isLoggedIn -> {
                NotLoggedInHint(onNavigateToSettings)
            }
            coursesState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            coursesState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            coursesState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCourses() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                var isCoursesRefreshing by remember { mutableStateOf(false) }
                LaunchedEffect(coursesState.isLoading) {
                    if (!coursesState.isLoading) isCoursesRefreshing = false
                }
                PullToRefreshBox(
                    isRefreshing = isCoursesRefreshing,
                    onRefresh = {
                        isCoursesRefreshing = true
                        viewModel.loadCourses()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 账户头部
                    val totalJobs = coursesState.courseProgress.values.sumOf { it.totalJobs }
                    val completedJobs = coursesState.courseProgress.values.sumOf { it.completedJobs }
                    AccountHeader(
                        courseCount = coursesState.courses.size,
                        selectedCount = coursesState.selectedCourseIds.size,
                        onSelectAll = { viewModel.selectAllCourses() },
                        onDeselectAll = { viewModel.deselectAllCourses() },
                        totalJobs = totalJobs,
                        completedJobs = completedJobs,
                    )

                    // 即时签到入口:点击检索进行中的签到活动并按类型弹窗
                    SignNowButton(
                        searching = signFlow.isSearching,
                        onClick = { viewModel.startSignFlow() },
                    )

                    // 课程列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(coursesState.courses, key = { it.courseId + "_" + it.clazzId }) { course ->
                            val key = course.courseId + "_" + course.clazzId
                            val isHidden = key in hiddenKeys
                            CourseCard(
                                course = course,
                                isSelected = coursesState.selectedCourseIds.contains(key),
                                onToggle = { viewModel.toggleCourseSelection(key) },
                                onClick = { onCourseClick(course) },
                                onLongClick = {
                                    viewModel.toggleHiddenCourse(key)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (isHidden) "已取消隐藏「${course.title.take(12)}」"
                                            else "已隐藏「${course.title.take(12)}」"
                                        )
                                    }
                                },
                                progress = coursesState.courseProgress[key],
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }

                    // 底部操作栏
                    val selected = coursesState.selectedCourseIds.size
                    if (selected > 0) {
                        StudySelectionBar(
                            selectedCount = selected,
                            onStudy = onShowStudySheet,
                        )
                    }
                }
                } // PullToRefreshBox
            }
        }

        // Snackbar (放在 Box 最上层)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )

        // 即时签到分发对话框(有进行中任务时自动展示)
        if (signFlow.hasActiveTask) {
            com.yourname.ahu_plus.ui.screen.chaoxing.sign.SignFlowDialog(viewModel = viewModel)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  立即签到按钮
// ══════════════════════════════════════════════════════════════

@Composable
private fun SignNowButton(searching: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !searching,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = AhuShapes.Card,
    ) {
        if (searching) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text("正在检索签到…")
        } else {
            Icon(Icons.Outlined.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("立即签到")
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  未登录提示
// ══════════════════════════════════════════════════════════════

@Composable
internal fun NotLoggedInHint(onNavigateToSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.School,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "未登录超星账号",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请先在设置页登录后查看课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNavigateToSettings,
                shape = AhuShapes.Card,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("去设置")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  账户头部
// ══════════════════════════════════════════════════════════════

@Composable
private fun AccountHeader(
    courseCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    totalJobs: Int = 0,
    completedJobs: Int = 0,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$courseCount 门课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totalJobs > 0) {
                Text(
                    text = " · 进度: $completedJobs/$totalJobs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedCount > 0) {
                Text(
                    text = " · 已选 $selectedCount",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = if (selectedCount > 0) onDeselectAll else onSelectAll,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = if (selectedCount > 0) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  底部学习操作栏
// ══════════════════════════════════════════════════════════════

@Composable
private fun StudySelectionBar(
    selectedCount: Int,
    onStudy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已选 $selectedCount 门课程",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Button(
                onClick = onStudy,
                shape = AhuShapes.Card,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始学习")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  课程卡片（带选中高亮边框）
// ══════════════════════════════════════════════════════════════

@Composable
private fun CourseCard(
    course: CxCourse,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    progress: CxCourseProgress? = null,
) {
    val cardShape = AhuShapes.Card
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = borderColor,
                        shape = cardShape,
                    )
                } else {
                    Modifier
                }
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 自定义选中指示器（替代 Checkbox，更紧凑）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            )
                        }
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (course.teacher.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = course.teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── 进度条（有数据时显示） ──────────────────────
                if (progress != null && progress.totalJobs > 0) {
                    Spacer(Modifier.height(6.dp))
                    CourseProgressBar(progress = progress)
                }
            }
        }
    }
}

/** 迷你进度条 + 文字 "3/10" */
@Composable
private fun CourseProgressBar(progress: CxCourseProgress) {
    val progressFraction = progress.progress
    val isComplete = progress.completedJobs >= progress.totalJobs
    val fillColor = if (isComplete) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 进度条：轨道（背景）- 圆角裁剪只做一次
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // 填充部分，用 fillMaxWidth(fraction) 控制宽度
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressFraction)
                    .background(fillColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        // 文字
        Text(
            text = progress.text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isComplete) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
