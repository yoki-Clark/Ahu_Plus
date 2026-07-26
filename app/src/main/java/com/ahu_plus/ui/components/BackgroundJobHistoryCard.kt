package com.ahu_plus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.job.BackgroundJobPhase
import com.ahu_plus.data.job.BackgroundJobPlatform
import com.ahu_plus.data.job.userStatusLabel
import com.ahu_plus.service.ChaoxingStudyService
import com.ahu_plus.service.WeLearnStudyService
import com.ahu_plus.ui.theme.AhuShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BackgroundJobHistoryCard(platform: BackgroundJobPlatform, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as AhuPlusApplication
    val allRecords by app.backgroundJobController.records.collectAsStateWithLifecycle()
    val records = allRecords.filter { it.platform == platform }.take(3)
    val hasActive = records.any { it.phase.isActive }
    if (records.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最近作业", style = MaterialTheme.typography.titleSmall)
                if (records.any { !it.phase.isActive }) {
                    TextButton(onClick = {
                        app.applicationScope.launch(Dispatchers.IO) {
                            app.backgroundJobController.clearHistory(platform)
                        }
                    }) { Text("清除") }
                }
            }
            records.forEach { record ->
                Column {
                    Text(record.userStatusLabel(), style = MaterialTheme.typography.bodyMedium)
                    if (record.progress.total > 0) {
                        Text(
                            "${record.progress.completed}/${record.progress.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (record.phase == BackgroundJobPhase.INTERRUPTED || record.phase == BackgroundJobPhase.FAILED) {
                        TextButton(
                            enabled = !hasActive,
                            onClick = {
                            when (platform) {
                                BackgroundJobPlatform.CHAOXING -> ChaoxingStudyService.resume(context, record.id)
                                BackgroundJobPlatform.WELEARN -> WeLearnStudyService.resume(context, record.id)
                            }
                        }) { Text("重新开始") }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
