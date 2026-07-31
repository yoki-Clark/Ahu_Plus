package com.ahu_plus.ui.screen.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.mail.MailMessageSummary
import com.ahu_plus.ui.theme.AhuSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 教育邮箱列表页(收件箱)。
 *
 * 状态覆盖:加载中 / 空列表 / 错误重试 / 刷新中 / 正常列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    viewModel: MailViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInbox()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教育邮箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshInbox() },
                        enabled = !uiState.isRefreshing,
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null && uiState.messages == null -> ErrorState(
                    message = uiState.error!!,
                    onRetry = { viewModel.refreshInbox() },
                )
                uiState.isEmpty -> EmptyState()
                uiState.messages != null -> MessageList(
                    messages = uiState.messages!!,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(AhuSpacing.md))
            Text("正在加载邮箱...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AhuSpacing.md))
            Text("收件箱是空的", style = MaterialTheme.typography.titleMedium)
            Text(
                "下拉刷新或检查邮箱是否配置正确",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(AhuSpacing.lg),
        ) {
            Text(
                "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(AhuSpacing.sm))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AhuSpacing.md))
            Text(
                "重试",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onRetry() },
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MailMessageSummary>,
    onOpenDetail: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = AhuSpacing.sm,
        ),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(message = message, onClick = { onOpenDetail(message.id) })
        }
    }
}

@Composable
private fun MessageItem(message: MailMessageSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = AhuSpacing.ScreenHorizontal,
                vertical = AhuSpacing.md,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // 发件人首字母头像(未读用主色,已读用浅色)
        val avatarText = (message.from.name ?: message.from.address)
            .trim()
            .take(1)
            .uppercase()
            .ifEmpty { "?" }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (message.isRead) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (message.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }
        Spacer(modifier = Modifier.size(AhuSpacing.md))
        // 主体内容
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.from.name ?: message.from.address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(AhuSpacing.sm))
                Text(
                    text = formatMailDate(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (message.hasAttachment) {
                Spacer(modifier = Modifier.height(AhuSpacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(AhuSpacing.xs))
                    Text(
                        text = "附件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 格式化邮件日期(毫秒时间戳 → "MM-dd HH:mm")。 */
private fun formatMailDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Date()
    val mailDate = Date(timestamp)
    val isToday = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now) ==
        SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(mailDate)
    val pattern = if (isToday) "HH:mm" else "MM-dd"
    return SimpleDateFormat(pattern, Locale.ROOT).format(mailDate)
}
