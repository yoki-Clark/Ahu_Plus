package com.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.ui.theme.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingStudySheet(
    courses: List<CxCourse>,
    settingsState: CxSettingsState,
    onConfirm: () -> Unit,
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
                .padding(bottom = 32.dp),
        ) {
            Text("确认学习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // 课程列表
            Text("课程 (${courses.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            courses.forEach { course ->
                Text(
                    "· ${course.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row {
                Text("倍速 %.1fx".format(settingsState.speed), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(16.dp))
                Text("并发 ${settingsState.concurrency}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(16.dp))
                Text(
                    when (settingsState.submitMode) {
                        "auto" -> "自动提交"
                        "save" -> "仅保存"
                        else -> "不答题"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始学习")
            }
        }
    }
}
