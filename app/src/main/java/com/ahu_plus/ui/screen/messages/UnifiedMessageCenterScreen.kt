package com.ahu_plus.ui.screen.messages

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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ListItem
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.MarketNotice
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.data.local.MESSAGE_PREVIEW_COUNT_OPTIONS
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UnifiedMessageCenterScreen(
    academicNotices: List<JwcNotice>,
    marketNotices: List<MarketNotice>,
    marketAvailable: Boolean,
    cxMessages: List<CxMessage>,
    cxAvailable: Boolean,
    isRefreshing: Boolean,
    previewCount: Int,
    onPreviewCountChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenAcademic: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenChaoxing: () -> Unit,
    onBack: () -> Unit,
) {
    var showPreviewSettings by remember { mutableStateOf(false) }

    if (showPreviewSettings) {
        AlertDialog(
            onDismissRequest = { showPreviewSettings = false },
            title = { Text("消息预览") },
            text = {
                Column {
                    MESSAGE_PREVIEW_COUNT_OPTIONS.forEach { count ->
                        ListItem(
                            headlineContent = {
                                Text(if (count == 0) "不显示预览" else "每个来源显示 $count 条")
                            },
                            leadingContent = {
                                RadioButton(selected = previewCount == count, onClick = null)
                            },
                            modifier = Modifier.clickable {
                                onPreviewCountChange(count)
                                showPreviewSettings = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewSettings = false }) { Text("关闭") }
            },
        )
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("消息中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showPreviewSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "消息预览设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        AhuPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    MessageSourceSummary(
                        marketAvailable = marketAvailable,
                        cxAvailable = cxAvailable,
                        previewCount = previewCount,
                    )
                }
                item {
                    MessageSourceCard(
                        title = "教务通知",
                        subtitle = if (academicNotices.isEmpty()) "打开原通知列表查看全部" else "${academicNotices.size} 条最新通知",
                        icon = Icons.Filled.Campaign,
                        onClick = onOpenAcademic,
                    )
                }
                if (academicNotices.isNotEmpty()) {
                    items(academicNotices.take(previewCount), key = { "academic-${it.url}" }) { notice ->
                        MessagePreview(
                            source = "教务通知",
                            title = notice.title,
                            body = notice.date,
                            time = notice.date,
                            onClick = onOpenAcademic,
                        )
                    }
                }
                if (marketAvailable) {
                    item {
                        MessageSourceCard(
                            title = "集市互动",
                            subtitle = if (marketNotices.isEmpty()) "暂无互动消息" else "${marketNotices.size} 条互动消息",
                            icon = Icons.Filled.Storefront,
                            onClick = onOpenMarket,
                        )
                    }
                    items(marketNotices.take(previewCount), key = { "market-${it.id}" }) { notice ->
                        MessagePreview(
                            source = "集市",
                            title = notice.actionTypeText.ifBlank { if (notice.isLike) "收到点赞" else "收到互动" },
                            body = notice.bodyText.ifBlank { notice.topic?.title.orEmpty() },
                            time = notice.createTime,
                            onClick = onOpenMarket,
                        )
                    }
                }
                if (cxAvailable) {
                    item {
                        MessageSourceCard(
                            title = "学习通消息",
                            subtitle = if (cxMessages.isEmpty()) "暂无课程消息" else "${cxMessages.size} 条课程消息",
                            icon = Icons.Filled.ChatBubbleOutline,
                            onClick = onOpenChaoxing,
                        )
                    }
                    items(cxMessages.take(previewCount), key = { "cx-${it.id}" }) { message ->
                        MessagePreview(
                            source = "学习通",
                            title = message.title,
                            body = message.content.ifBlank { message.courseName },
                            time = formatEpochTime(message.time),
                            onClick = onOpenChaoxing,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MessageSourceSummary(
    marketAvailable: Boolean,
    cxAvailable: Boolean,
    previewCount: Int,
) {
    val sources = buildList {
        add("教务通知")
        if (marketAvailable) add("集市互动")
        if (cxAvailable) add("学习通消息")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "当前汇总：${sources.joinToString("、")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (previewCount == 0) {
                        "消息预览已关闭，点击来源卡片查看完整列表"
                    } else {
                        "每个来源显示最近 $previewCount 条；未登录的第三方服务不会显示"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MessageSourceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessagePreview(
    source: String,
    title: String,
    body: String,
    time: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "$source · $title",
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (body.isNotBlank()) {
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (time.isNotBlank()) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun formatEpochTime(value: Long): String {
    if (value <= 0L) return ""
    return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
