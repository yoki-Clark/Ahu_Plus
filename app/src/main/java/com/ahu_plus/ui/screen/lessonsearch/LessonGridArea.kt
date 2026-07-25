package com.ahu_plus.ui.screen.lessonsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.ui.screen.schedule.FixedTimeColumn
import com.ahu_plus.ui.screen.schedule.WeekGrid

/**
 * 定位到单个教学班后的「周网格课表」视图。
 *
 * 组成：周次下拉（1..gridMaxWeek）+ 固定时间列 + [WeekGrid]。
 * 数据来自 VM 已解析的 [LessonSearchUiState.gridDisplayItems]（按 gridWeek 过滤）。
 * 解析失败的记录由外层落到网格下方 [LessonSearchUiState.unparsedRecords] 列表兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonGridArea(
    uiState: LessonSearchUiState,
    viewModel: LessonSearchViewModel,
) {
    var selectedCourse by remember { mutableStateOf<CourseDisplayItem?>(null) }
    val rowHeight = 56.dp
    val unitTimes = remember { viewModel.gridUnitTimes() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 周次下拉
        var weekExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = weekExpanded,
            onExpandedChange = { weekExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "第 ${uiState.gridWeek} 周",
                onValueChange = {},
                readOnly = true,
                label = { Text("周次") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = weekExpanded,
                onDismissRequest = { weekExpanded = false }
            ) {
                (1..uiState.gridMaxWeek.coerceAtLeast(1)).forEach { week ->
                    DropdownMenuItem(
                        text = { Text("第 $week 周") },
                        onClick = {
                            weekExpanded = false
                            viewModel.selectGridWeek(week)
                        }
                    )
                }
            }
        }

        if (uiState.gridDisplayItems.isEmpty()) {
            Text(
                text = "本周无可排入网格的课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            val scroll = rememberScrollState()
            Row(modifier = Modifier.fillMaxWidth().height(rowHeight * unitTimes.size + 40.dp)) {
                FixedTimeColumn(
                    unitTimes = unitTimes,
                    rowHeight = rowHeight,
                    fontScale = 1f,
                    verScroll = scroll,
                )
                WeekGrid(
                    displayItems = uiState.gridDisplayItems,
                    unitTimes = unitTimes,
                    selectedWeek = uiState.gridWeek,
                    currentWeek = -1,
                    onCourseClick = { selectedCourse = it },
                    modifier = Modifier.weight(1f),
                    rowHeight = rowHeight,
                    sharedVerScroll = scroll,
                )
            }
        }
    }

    selectedCourse?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedCourse = null },
            confirmButton = {
                TextButton(onClick = { selectedCourse = null }) { Text("关闭") }
            },
            title = { Text(item.courseName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.courseCode?.let { GridDetailLine("课程代码", it) }
                    if (item.teacherNames.isNotBlank()) GridDetailLine("教师", item.teacherNames)
                    GridDetailLine("时间", "第 ${item.startUnit}-${item.endUnit} 节")
                    item.room?.let { GridDetailLine("地点", it) }
                    item.weeksStr?.let { GridDetailLine("周次", it) }
                    item.courseType?.let { GridDetailLine("类型", it) }
                }
            }
        )
    }
}

@Composable
private fun GridDetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
