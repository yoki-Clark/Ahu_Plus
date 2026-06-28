package com.yourname.ahu_plus.ui.screen.welearn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourname.ahu_plus.data.model.WeLearnCourse
import com.yourname.ahu_plus.data.model.WeLearnSco
import com.yourname.ahu_plus.data.model.WeLearnScoStatus
import com.yourname.ahu_plus.data.model.WeLearnUnitScos

/**
 * WeLearn 课程详情屏 (2026-06-28)。
 *
 * 仿 ChaoxingCourseDetailScreen 的两段式:
 *  - 顶部:课程元信息(完成度)
 *  - 中段:单元 → 章节(SCO) 的树形列表
 *  - 顶部 action: "开始刷课" → 进入 WeLearnStudyScreen
 *
 * 默认全部单元展开(用户场景就是"看章节");不实现勾选/反选,留给"后续针对性刷"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeLearnCourseDetailScreen(
    course: WeLearnCourse,
    viewModel: WeLearnViewModel,
    onBack: () -> Unit,
    // 2026-06-28:选择性刷 + 顶栏 PlayArrow — unitFilter=null 刷全部,IntArray 刷选中单元(立即启动 Service)
    onStartStudy: (IntArray?) -> Unit,
    // 2026-06-28:刷全部章节 — 仅跳转 Study 屏,不启动 Service(由用户在 Study 屏手动点开始)
    onOpenStudy: () -> Unit,
) {
    val treeState by viewModel.treeState.collectAsState()
    // 2026-06-28:单选 Dialog 开关(tap-to-confirm,无需持有选中状态)
    var showUnitSelector by remember { mutableStateOf(false) }

    LaunchedEffect(course.cid) { viewModel.loadCourseTree(course.cid) }
    // session 过期重登失败 → 自动回主页(Main 屏会弹 LoginSheet)
    LaunchedEffect(treeState.needsLogin) { if (treeState.needsLogin) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onStartStudy(null) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "开始刷课(全部)")
                    }
                },
            )
        },
        bottomBar = {
            // 2026-06-28:详情屏底部加显眼"开始刷课"按钮,用户反馈找不到入口
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showUnitSelector = true },  // 2026-06-28 接通
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("选择性刷")
                    }
                    Button(
                        onClick = { onOpenStudy() },  // 仅跳转 Study 屏,启动由用户在 Study 屏手动触发
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("刷全部章节", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            // 课程元信息卡
            CompletionCard(course)

            when {
                treeState.loading && treeState.units.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                treeState.error != null && treeState.units.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "加载失败: ${treeState.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                treeState.units.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无章节", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val totalScos = treeState.units.sumOf { it.scos.size }
                    val doneScos = treeState.units.sumOf { u -> u.scos.count { it.status == WeLearnScoStatus.COMPLETED } }
                    val lockedScos = treeState.units.sumOf { u -> u.scos.count { it.status == WeLearnScoStatus.LOCKED } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                buildString {
                                    append("${treeState.units.size} 单元 · ")
                                    append("$doneScos/$totalScos 已完成")
                                    if (lockedScos > 0) append(" · $lockedScos 未开放")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(treeState.units, key = { it.unit.unitIdx }) { us ->
                            UnitBlock(
                                us = us,
                                isExpanded = us.unit.unitIdx !in treeState.collapsedUnits,
                                onToggle = { viewModel.toggleUnitExpanded(us.unit.unitIdx) },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }

        // 2026-06-28:选择性刷 — 单选 Dialog,tap 行即触发 + 自动关闭
        if (showUnitSelector) {
            Dialog(onDismissRequest = { showUnitSelector = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "选择单元",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        if (treeState.units.isEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("章节加载中…", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            treeState.units.forEach { us ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showUnitSelector = false
                                            onStartStudy(intArrayOf(us.unit.unitIdx))
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            us.unit.unitName.ifBlank { "Unit ${us.unit.unitIdx + 1}" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        if (us.unit.name.isNotBlank()) {
                                            Text(
                                                us.unit.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    val done = us.scos.count { it.status == WeLearnScoStatus.COMPLETED }
                                    Text(
                                        "$done/${us.scos.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { showUnitSelector = false },
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp),
                        ) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(course: WeLearnCourse) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("当前完成度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${course.per}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(
                    progress = { course.per / 100f },
                    modifier = Modifier.weight(1f).height(8.dp),
                )
            }
        }
    }
}

@Composable
private fun UnitBlock(
    us: WeLearnUnitScos,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val done = us.scos.count { it.status == WeLearnScoStatus.COMPLETED }
    val locked = us.scos.count { it.status == WeLearnScoStatus.LOCKED }
    val total = us.scos.size
    // 折叠态由 ViewModel 持有(避免 UnitBlock 重组/LazyColumn 离屏重建/Detail 屏重挂时丢失)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        us.unit.unitName.ifBlank { "Unit ${us.unit.unitIdx + 1}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (us.unit.name.isNotBlank()) {
                        Text(
                            us.unit.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "$done/$total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (total > 0 && done == total) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isExpanded) {
                if (us.scos.isEmpty()) {
                    Text(
                        if (us.unit.visible) "暂无章节" else "未开放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                } else {
                    us.scos.forEach { sco ->
                        ScoRow(sco)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoRow(sco: WeLearnSco) {
    // 父路径(去掉最后一段,即 name);全 location 太长,父级是更稳定的上下文
    val parentPath = remember(sco.location, sco.name) {
        if (sco.location.isBlank() || sco.name.isBlank()) ""
        else if (sco.location == sco.name) ""
        else sco.location.substringBeforeLast(" > ", missingDelimiterValue = "")
    }
    val (iconTint, iconVec) = when (sco.status) {
        WeLearnScoStatus.COMPLETED -> Color(0xFF4CAF50) to Icons.Filled.CheckCircle
        WeLearnScoStatus.LOCKED -> MaterialTheme.colorScheme.outline to Icons.Filled.Lock
        WeLearnScoStatus.TODO -> MaterialTheme.colorScheme.primary to Icons.Filled.RadioButtonUnchecked
    }
    val nameColor = when (sco.status) {
        WeLearnScoStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                sco.name.ifBlank { sco.location },
                style = MaterialTheme.typography.bodyMedium,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (parentPath.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                parentPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp),
            )
        }
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        modifier = Modifier.padding(start = 48.dp),
    )
}
