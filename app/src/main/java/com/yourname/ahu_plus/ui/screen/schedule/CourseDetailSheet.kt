package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.GetDataLesson

/** 备注最大字符数 (≈1 分钟输入上限) */
private const val NOTE_MAX_LEN = 500

/**
 * 课程详情底部弹窗。
 *
 * 显示课程的完整信息(来自 [CourseDisplayItem] + [GetDataLesson] 增强字段),
 * 并在底部提供一个可编辑的备注区域,用户可记录考核方案、复习要点等。
 *
 * @param detail 当前展示的详情快照,由 ViewModel 提供
 * @param onNoteChange 备注草稿变化回调
 * @param onSave 保存备注
 * @param onDismiss 关闭弹窗 (保留草稿丢弃,实际是关闭后 detail=null)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    detail: CourseDetailUiModel,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 标题 ─────────────────────────────────
            Text(
                text = detail.item.courseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            // ── 详情键值对 ───────────────────────────
            val lessonDetail = detail.lessonDetail
            DetailRow("课程代码", detail.item.courseCode)
            DetailRow(
                "学分",
                (lessonDetail?.course?.credits ?: detail.item.credits)?.toString()
            )
            DetailRow("教师", detail.item.teacherNames)
            DetailRow("教室", detail.item.room)
            DetailRow("校区", lessonDetail?.campus?.nameZh ?: detail.item.campus)
            DetailRow(
                "课程类型",
                lessonDetail?.courseType?.nameZh ?: detail.item.courseType
            )
            DetailRow(
                "考核方式",
                lessonDetail?.examMode?.nameZh
            )
            DetailRow(
                "修读类别",
                lessonDetail?.compulsorysStr
            )
            DetailRow("周次", detail.item.weeksStr ?: detail.item.weekIndexes.toString())
            DetailRow(
                "上课时间",
                buildString {
                    detail.item.startTime?.let { append(it) }
                    detail.item.endTime?.let {
                        if (isNotEmpty()) append(" - ")
                        append(it)
                    }
                }.ifBlank { null }
            )
            DetailRow(
                "排课描述",
                lessonDetail?.scheduleText?.dateTimePlaceText?.textZh
                    ?: lessonDetail?.scheduleText?.dateTimePlacePersonText?.textZh
            )
            if (lessonDetail?.stdCount != null || lessonDetail?.limitCount != null) {
                DetailRow(
                    "已选/限选",
                    "${lessonDetail?.stdCount ?: "-"} / ${lessonDetail?.limitCount ?: "-"}"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // ── 备注编辑 ─────────────────────────────
            Text(
                text = "我的备注",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "记录这门课的考核方案、复习要点、作业 deadline 等",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = detail.noteDraft,
                onValueChange = { newText ->
                    // 限制最大长度
                    if (newText.length <= NOTE_MAX_LEN) onNoteChange(newText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("例如:考核方式为开卷考,占比 60%;期末重点章节 3、5、7…") },
                supportingText = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${detail.noteDraft.length} / $NOTE_MAX_LEN",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = if (detail.noteDraft.length >= NOTE_MAX_LEN)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                maxLines = 8,
            )

            Spacer(Modifier.height(12.dp))

            // ── 按钮 ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !detail.isSaving,
                ) {
                    if (detail.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("保存备注")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单行键值对:左边标签灰色,右边取值右对齐;值为空不渲染整行 */
@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
