package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.needsLogin) {
        if (uiState.needsLogin) onNeedsLogin()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 缓存极值,避免每帧 4 次 O(n) 遍历
        val minWeek = remember(uiState.weekIndices) { uiState.weekIndices.minOrNull() ?: 1 }
        val maxWeek = remember(uiState.weekIndices) { uiState.weekIndices.maxOrNull() ?: 20 }

        // ── 顶栏：返回 + 周导航 + 刷新（合并为一行）─────
        WeekHeader(
            selectedWeek = uiState.selectedWeek,
            currentWeek = uiState.currentWeek,
            studentName = uiState.studentName,
            minWeek = minWeek,
            maxWeek = maxWeek,
            hasPrevious = uiState.selectedWeek > minWeek,
            hasNext = uiState.selectedWeek < maxWeek,
            onBack = onBack,
            onPrevious = { viewModel.onPreviousWeek() },
            onNext = { viewModel.onNextWeek() },
            onRefresh = { viewModel.onRefresh() },
            onSettings = { viewModel.onToggleSettings() }
        )

        // ── 内容区 ────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "加载课表中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                uiState.error != null && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.onRefresh() }) {
                            Text("重试")
                        }
                    }
                }

                uiState.allActivities.isEmpty() && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "暂无课表数据",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.onRefresh() }) {
                            Text("刷新")
                        }
                    }
                }

                else -> {
                    WeekGrid(
                        displayItems = uiState.displayItems,
                        unitTimes = uiState.unitTimes,
                        selectedWeek = uiState.selectedWeek,
                        currentWeek = uiState.currentWeek,
                        onCourseClick = viewModel::onCourseClicked,
                        modifier = Modifier.fillMaxSize(),
                        colWidth = uiState.colWidthDp.dp,
                        rowHeight = uiState.rowHeightDp.dp,
                        fontScale = uiState.fontScale,
                    )
                }
            }
        }
    }

    // ── 课程详情 BottomSheet ────────────────────────
    uiState.selectedCourseDetail?.let { detail ->
        CourseDetailSheet(
            detail = detail,
            onNoteChange = viewModel::onNoteDraftChanged,
            onSave = viewModel::onNoteSave,
            onDismiss = viewModel::onDismissSheet,
        )
    }

    // ── 课表显示设置 BottomSheet ────────────────────
    if (uiState.showSettings) {
        ScheduleSettingsSheet(
            colWidthDp = uiState.colWidthDp,
            rowHeightDp = uiState.rowHeightDp,
            fontScale = uiState.fontScale,
            onColWidthChanged = viewModel::onColWidthChanged,
            onRowHeightChanged = viewModel::onRowHeightChanged,
            onFontScaleChanged = viewModel::onFontScaleChanged,
            onReset = viewModel::onResetSettings,
            onDismiss = { viewModel.onToggleSettings() },
        )
    }
}

@Composable
private fun WeekHeader(
    selectedWeek: Int,
    currentWeek: Int,
    studentName: String?,
    minWeek: Int,
    maxWeek: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回键
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 周切换
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "上一周",
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = "第 $selectedWeek 周",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        if (selectedWeek == currentWeek) {
            Text(
                text = "（本周）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "下一周",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 课表设置
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "课表设置",
                modifier = Modifier.size(20.dp)
            )
        }
        // 刷新
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSettingsSheet(
    colWidthDp: Float,
    rowHeightDp: Float,
    fontScale: Float,
    onColWidthChanged: (Float) -> Unit,
    onRowHeightChanged: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "课表显示设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            SettingSlider(
                label = "课程卡片宽度",
                value = colWidthDp,
                valueRange = 48f..80f,
                steps = 15,
                valueText = { "${it.roundToInt()} dp" },
                onValueChange = onColWidthChanged,
            )

            SettingSlider(
                label = "课程卡片高度",
                value = rowHeightDp,
                valueRange = 44f..72f,
                steps = 13,
                valueText = { "${it.roundToInt()} dp" },
                onValueChange = onRowHeightChanged,
            )

            SettingSlider(
                label = "字体缩放",
                value = fontScale,
                valueRange = 0.75f..1.5f,
                steps = 14,
                valueText = { "%.2fx".format(it) },
                onValueChange = onFontScaleChanged,
            )

            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("恢复默认")
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueText(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
