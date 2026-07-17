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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.MarketNotice
import com.ahu_plus.ui.components.AhuTopAppBar
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
    onRefresh: () -> Unit,
    onOpenAcademic: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenChaoxing: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("消息中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
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
                    MessageSourceCard(
                        title = "教务通知",
                        subtitle = if (academicNotices.isEmpty()) "打开原通知列表查看全部" else "${academicNotices.size} 条最新通知",
                        icon = Icons.Filled.Campaign,
                        onClick = onOpenAcademic,
                    )
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
                    items(marketNotices.take(3), key = { "market-${it.id}" }) { notice ->
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
                    items(cxMessages.take(3), key = { "cx-${it.id}" }) { message ->
                        MessagePreview(
                            source = "学习通",
                            title = message.title,
                            body = message.content.ifBlank { message.courseName },
                            time = formatEpochTime(message.time),
                            onClick = onOpenChaoxing,
                        )
                    }
                }
                if (academicNotices.isNotEmpty()) {
                    items(academicNotices.take(3), key = { "academic-${it.url}" }) { notice ->
                        MessagePreview(
                            source = "教务通知",
                            title = notice.title,
                            body = notice.date,
                            time = notice.date,
                            onClick = onOpenAcademic,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
