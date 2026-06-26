package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.AiPlatform
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.ui.theme.AhuShapes

/**
 * 学习通设置页(2026-06-20 重做)。
 *
 * [isEmbedded] = true 时只渲染纯内容（供 ChaoxingTabScreen 嵌入 pager）。
 * [isEmbedded] = false 时包一层 Scaffold + TopAppBar（供 ChaoxingMainScreen 独立使用）。
 *
 * 设计改进:
 *   - 登录表单全宽独立 Card，不再挤在一行
 *   - 分组更清晰：账户 → 学习设置 → 答题设置 → 关于
 */

@Composable
internal fun SignConfigSetting(viewModel: ChaoxingViewModel) {
    val signState by viewModel.signState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // 进入设置页时确保配置值已从 SessionManager 载入(signState 默认值为 -1.0/空)
    LaunchedEffect(Unit) { viewModel.loadSignConfig() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("签到参数配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            val locOk = signState.configuredLat >= 0 && signState.configuredLon >= 0
            val gesOk = signState.configuredGesture.isNotBlank()
            val summary = when {
                locOk && gesOk -> "位置 + 手势已配置"
                locOk -> "位置已配置"
                gesOk -> "手势已配置"
                else -> "位置 / 手势签到需先配置"
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            modifier = Modifier.rotate(if (expanded) 180f else 0f),
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column {
            Spacer(Modifier.height(8.dp))
            // 位置:复用即时签到的共享选择器(校区二级 + 自定义 + GPS + 手动)
            Text(
                "位置签到预设",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            val savedLoc = if (signState.configuredLat >= 0 && signState.configuredLon >= 0)
                "已保存:%.5f, %.5f%s".format(
                    signState.configuredLat, signState.configuredLon,
                    if (signState.configuredAddress.isNotBlank()) " · ${signState.configuredAddress}" else "",
                ) else "未配置;选择下方任一来源即保存为自动签到用坐标"
            Text(savedLoc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            com.yourname.ahu_plus.ui.screen.chaoxing.sign.LocationPicker(
                viewModel = viewModel,
                onPicked = { lat, lon, name -> viewModel.updateSignLocation(lat, lon, name) },
            )

            Spacer(Modifier.height(16.dp))
            // 手势:按钮 → 弹窗绘制(避免内联画板清除残留)
            Text(
                "手势签到预设",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (signState.configuredGesture.isBlank()) "未配置"
                else "已保存手势:${signState.configuredGesture}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            var showGestureDialog by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showGestureDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (signState.configuredGesture.isBlank()) "绘制手势" else "重新绘制手势")
            }
            if (showGestureDialog) {
                com.yourname.ahu_plus.ui.screen.chaoxing.sign.GesturePadDialog(
                    initial = signState.configuredGesture,
                    onConfirm = { code -> viewModel.updateSignGesture(code); showGestureDialog = false },
                    onDismiss = { showGestureDialog = false },
                )
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════
//  首次登录免责警告对话框 (2026-06-23)
// ══════════════════════════════════════════════════════════════

/**
 * 首次登录学习通成功后弹出的一次性警告。
 * 警告内容:
 *  - 非必要不要开倍速 / 并发 / 刷访问次数,可能导致账号异常
 *  - 后果由用户自行承担
 *  - 备注:支持后台刷,不影响前台使用,挂在后台即可
 *
 * 用户点 "我已知晓" 后通过 SessionManager 持久化标志,不再重复弹出。
 */
@Composable
internal fun LoginWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "学习通使用提示",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    "非必要情况下,请勿开启：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 视频倍速\n• 多节并发\n• 刷访问次数",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "异常配置可能触发平台风控,导致账号异常。开启上述选项产生的所有后果,由您自行承担。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "提示:现已支持后台自动刷课,不会影响您前台正常使用,挂到后台即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我已知晓")
            }
        },
    )
}

// ══════════════════════════════════════════════════════════════
//  AI 平台选择器
// ══════════════════════════════════════════════════════════════

/**
 * AI 平台下拉选择器。
 *
 * 选中平台后通过 [onPlatformSelected] 回调,外部负责更新 baseUrl 和模型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiPlatformSelector(
    selectedPlatform: AiPlatform,
    onPlatformSelected: (AiPlatform) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedPlatform.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("AI 平台") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = AhuShapes.Card,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AiPlatform.entries.forEach { platform ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(platform.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (platform.defaultBaseUrl.isNotBlank()) {
                                Text(
                                    platform.defaultBaseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onPlatformSelected(platform)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * AI 模型选择器。
 *
 * 固定模型列表(下拉选择)+底部的自定义输入框(可手填任意模型名)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiModelSelector(
    models: List<String>,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onCustomModelEntered: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(models.none { it == selectedModel } && selectedModel.isNotBlank()) }

    Column {
        // 下拉选择已有的模型
        ExposedDropdownMenuBox(
            expanded = expanded && !showCustom,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = if (showCustom) "$selectedModel (自定义)" else selectedModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("模型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = AhuShapes.Card,
                singleLine = true,
            )
            if (models.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                showCustom = false
                                onModelSelected(model)
                                expanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "输入自定义模型...",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            showCustom = true
                            expanded = false
                        },
                    )
                }
            }
        }

        // 自定义模型输入（用户想输入不在列表中的模型时）
        if (showCustom || models.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = if (showCustom) selectedModel else "",
                onValueChange = { onCustomModelEntered(it) },
                label = { Text(if (models.isEmpty()) "自定义模型" else "自定义模型 (覆盖下拉)") },
                placeholder = {
                    Text(
                        if (models.isEmpty()) "输入模型名称" else selectedModel.ifBlank { "输入其他模型名称" },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = AhuShapes.Card,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  隐藏课程管理 BottomSheet 内容
// ══════════════════════════════════════════════════════════════

@Composable
internal fun HiddenCoursesDialog(
    courses: List<CxCourse>,
    hiddenKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    // checked = 显示 (visible), unchecked = 隐藏
    val checkedState = remember { mutableStateMapOf<String, Boolean>() }

    // 只在 courses 变化时同步初始状态（默认全部可见 = 不在 hiddenKeys 中）
    LaunchedEffect(courses) {
        courses.forEach { c ->
            val key = "${c.courseId}_${c.clazzId}"
            if (key !in checkedState) {
                checkedState[key] = key !in hiddenKeys  // 不在隐藏集合中 = 可见 = 勾选
            }
        }
        val currentKeys = courses.map { "${it.courseId}_${it.clazzId}" }.toSet()
        checkedState.keys.removeAll { it !in currentKeys }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理显示课程", fontWeight = FontWeight.Bold) },
        text = {
            if (courses.isEmpty()) {
                Text("暂无课程数据，请先加载课程列表",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "取消勾选的课程将不在课程列表和作业中显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(courses, key = { "${it.courseId}_${it.clazzId}" }) { course ->
                        val key = "${course.courseId}_${course.clazzId}"
                        val isVisible = checkedState[key] ?: true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedState[key] = !isVisible }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isVisible,
                                onCheckedChange = { checkedState[key] = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(course.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (course.teacher.isNotBlank()) {
                                    Text(course.teacher, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // 未勾选的 = 要隐藏的; hiddenKeys = 所有课程 - 勾选的课程
                val visibleKeys = checkedState.filter { it.value }.keys
                val allKeys = courses.map { "${it.courseId}_${it.clazzId}" }.toSet()
                onConfirm(allKeys - visibleKeys)  // 把不显示的传给 onConfirm → updateHiddenCourses
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
