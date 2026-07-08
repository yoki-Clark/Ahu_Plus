package com.yourname.ahu_plus.ui.screen.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.local.CacheSizeInfo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanupScreen(
    sizeInfo: CacheSizeInfo?,
    downloadSize: Long,
    downloadCount: Int,
    isCalculating: Boolean,
    onToggleGroup: (String) -> Unit,
    onClear: (selectedGroups: List<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allGroupIds = remember {
        listOf(
            "academic", "student_info", "finance_attendance",
            "campus_services", "chaoxing", "public_data", "app_records", "download"
        )
    }
    var selection by remember { mutableStateOf(allGroupIds.toSet()) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val groupDefs = remember {
        listOf(
            CacheGroupDef("academic", "教务数据", "课表、成绩、GPA、考试、培养方案、空教室"),
            CacheGroupDef("student_info", "学生信息", "基本信息、住宿、学业预警"),
            CacheGroupDef("finance_attendance", "财务与考勤", "财务汇总、考勤记录、一卡通账单"),
            CacheGroupDef("campus_services", "校园服务", "水电配置、浴室电话、评教、作业等"),
            CacheGroupDef("chaoxing", "超星学习通", "课程列表、进度、作业、消息（登录态保留）"),
            CacheGroupDef("public_data", "公开数据", "开发者公告、天气、排考预测"),
            CacheGroupDef("app_records", "应用记录", "最近使用应用、已关闭的公告"),
            CacheGroupDef("download", "下载的安装包", "过往 APK 文件，共 ${downloadCount} 个"),
        )
    }

    val totalSelected = selection.sumOf { id ->
        when (id) {
            "download" -> downloadSize
            else -> sizeInfo?.getSize(id) ?: 0L
        }
    }

    fun doClear() {
        val selected = selection.toList()
        onClear(selected)
        Toast.makeText(context, "已释放 ${formatSize(totalSelected)}", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("清理本地缓存") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            if (!isCalculating) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "已选择释放 ${formatSize(totalSelected)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = selection.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("确认清理")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isCalculating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("计算中...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    Text(
                        text = "请勾选要清理的数据项（登录态和设置均保留）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(groupDefs) { group ->
                    val size = when (group.id) {
                        "download" -> downloadSize
                        else -> sizeInfo?.getSize(group.id) ?: 0L
                    }
                    CacheGroupItem(
                        group = group,
                        size = size,
                        checked = group.id in selection,
                        onToggle = {
                            selection = if (it in selection) {
                                selection - it
                            } else {
                                selection + it
                            }
                            onToggleGroup(it)
                        }
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认清理？") },
            text = {
                Text("将释放 ${formatSize(totalSelected)} 存储空间，此操作不可恢复。您的登录状态和个人设置将保留。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        doClear()
                    }
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private data class CacheGroupDef(
    val id: String,
    val title: String,
    val description: String
)

@Composable
private fun CacheGroupItem(
    group: CacheGroupDef,
    size: Long,
    checked: Boolean,
    onToggle: (String) -> Unit
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.title,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSize(size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle(group.id) }
            )
        },
        modifier = Modifier.clickable { onToggle(group.id) }
    )
    HorizontalDivider()
}

/** 格式化字节大小 */
internal fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** 计算 download 目录中 APK 文件的总大小和数量 */
fun calculateDownloadApkSize(context: Context): Pair<Long, Int> {
    var totalSize = 0L
    var count = 0
    val internalDir = File(context.filesDir, "download")
    internalDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.forEach {
        totalSize += it.length()
        count++
    }
    context.getExternalFilesDir(null)?.let { extDir ->
        val extDownload = File(extDir, "download")
        extDownload.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.forEach {
            totalSize += it.length()
            count++
        }
    }
    return totalSize to count
}

/** 删除 download 目录中的 APK 文件 */
fun deleteDownloadApks(context: Context) {
    val internalDir = File(context.filesDir, "download")
    internalDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.forEach {
        it.delete()
    }
    context.getExternalFilesDir(null)?.let { extDir ->
        val extDownload = File(extDir, "download")
        extDownload.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.forEach {
            it.delete()
        }
    }
}
