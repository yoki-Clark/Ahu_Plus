package com.ahu_plus.ui.screen.chaoxing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CxAttachment
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.CxMessageSource
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.ChaoxingColors
import kotlinx.coroutines.launch

/** 消息子 Tab */
private enum class MsgTab(val label: String) {
    INBOX("收件箱"),
    ACTIVITY("课程活动"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesTabContent(
    viewModel: ChaoxingViewModel,
    loginState: CxLoginState,
) {
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val mergeInbox = settingsState.messagesMergeInbox

    // 子 Tab 状态
    var selectedMsgTab by rememberSaveable { mutableStateOf(MsgTab.INBOX.ordinal) }

    // 详情弹窗
    var detailMessage by remember { mutableStateOf<CxMessage?>(null) }

    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    // 首次进入时加载消息
    LaunchedEffect(loginState.isLoggedIn) {
        if (loginState.isLoggedIn && messagesState.messages.isEmpty() && !messagesState.isLoading) {
            viewModel.loadMessages()
        }
    }

    // 过滤消息
    val filteredMessages = remember(messagesState.messages, selectedMsgTab, mergeInbox) {
        if (mergeInbox) {
            messagesState.messages
        } else {
            when (MsgTab.entries[selectedMsgTab]) {
                MsgTab.INBOX -> messagesState.messages.filter { it.source == CxMessageSource.NOTICE }
                MsgTab.ACTIVITY -> messagesState.messages.filter { it.source == CxMessageSource.ACTIVITY }
            }
        }
    }

    // 监听加载完成 → 关闭刷新指示器
    LaunchedEffect(messagesState.isLoading) {
        if (!messagesState.isLoading) isRefreshing = false
    }

    // 详情弹窗（放在 Box 外面，避免嵌套冲突）
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDetail = detailMessage
    val downloadScope = rememberCoroutineScope()
    if (currentDetail != null) {
        MessageDetailSheet(
            message = currentDetail,
            onDismiss = { detailMessage = null },
            onDownload = { att ->
                downloadScope.launch {
                    val result = viewModel.cxRepo.getAttachmentDownloadUrl(att)
                    result.onSuccess { (url, referer) ->
                        val dm = getSystemService(context, android.app.DownloadManager::class.java)
                        if (dm != null) {
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                .setTitle(att.name)
                                .setDescription("正在下载 ${att.name}")
                                .addRequestHeader("Referer", referer)
                                .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, att.name)
                            dm.enqueue(request)
                        } else {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }
                    result.onFailure { e ->
                        android.util.Log.e("CxMsg", "下载失败: ${e.message}")
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !loginState.isLoggedIn -> {
                NotLoggedInHint(onNavigateToSettings = {})
            }
            messagesState.isLoading && messagesState.messages.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("加载消息中…", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            messagesState.error != null && messagesState.messages.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(messagesState.error!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadMessages() }, shape = AhuShapes.Card) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.loadMessages()
                    },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── 子 Tab 栏（非聚合模式才显示）─────────
                        if (!mergeInbox) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MsgTab.entries.forEachIndexed { index, tab ->
                                    val selected = selectedMsgTab == index
                                    val count = when (tab) {
                                        MsgTab.INBOX -> messagesState.messages.count { it.source == CxMessageSource.NOTICE }
                                        MsgTab.ACTIVITY -> messagesState.messages.count { it.source == CxMessageSource.ACTIVITY }
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedMsgTab = index },
                                        label = {
                                            Text(
                                                "${tab.label} ($count)",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // ── 消息列表 ────────────────────────────
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 统计头部（聚合模式）
                            if (mergeInbox) {
                                item {
                                    val total = messagesState.messages.size
                                    val readIds = messagesState.readMessageIds
                                    val unread = messagesState.messages.count { !it.isRead && it.id !in readIds }
                                    MessageStatsHeader(total = total, unread = unread)
                                }
                            }

                            items(
                                items = filteredMessages,
                                key = { it.id },
                            ) { message ->
                                MessageCard(
                                    message = message,
                                    isEffectivelyRead = message.isRead || message.id in messagesState.readMessageIds,
                                    onClick = {
                                        viewModel.markMessageAsRead(message.id)
                                        detailMessage = message
                                    },
                                )
                            }

                            // 加载更多（仅收件箱 tab 或聚合模式）
                            val showLoadMore = (mergeInbox || MsgTab.entries[selectedMsgTab] == MsgTab.INBOX) && messagesState.hasMore
                            if (showLoadMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.loadMoreMessages() }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (messagesState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                "加载更多通知",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }

                            // 空状态
                            if (filteredMessages.isEmpty() && !messagesState.isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "暂无消息",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ── 消息统计头部 ──────────────────────────────────────────────

@Composable
private fun MessageStatsHeader(total: Int, unread: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = AhuShapes.Card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.MarkEmailUnread,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$total 条消息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (unread > 0) {
                Text(
                    text = " · $unread 未读",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ── 消息卡片 ──────────────────────────────────────────────────

@Composable
private fun MessageCard(message: CxMessage, isEffectivelyRead: Boolean, onClick: () -> Unit = {}) {
    val cardShape = AhuShapes.Card
    val isActivity = message.source == CxMessageSource.ACTIVITY
    val unread = !isEffectivelyRead

    // 图标和颜色
    val (icon, iconColor) = when {
        isActivity && message.type == 2 -> Icons.Filled.LocationOn to ChaoxingColors.Signin
        isActivity && message.type == 42 -> Icons.Filled.Edit to ChaoxingColors.Exercise
        isActivity && message.type == 45 -> Icons.Filled.Notifications to ChaoxingColors.Notice
        isActivity && message.type == 11 -> Icons.AutoMirrored.Filled.Chat to ChaoxingColors.Selection
        isActivity -> Icons.Filled.AccessTime to ChaoxingColors.OtherActivity
        else -> Icons.Filled.Email to MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconColor,
                )
            }

            Spacer(Modifier.width(12.dp))

            // 中间内容
            Column(modifier = Modifier.weight(1f)) {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.title.ifBlank { message.content.take(30) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActivity && message.typeName.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = iconColor.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = message.typeName,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // 摘要
                val summary = message.content.trim().replace("\n", " ")
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 底部：发送者 + 时间 + 状态
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isActivity && message.activityStatus == 1) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ChaoxingColors.Active.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChaoxingColors.Active,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (isActivity && message.userStatus == 1) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已参与",
                            modifier = Modifier.size(12.dp),
                            tint = ChaoxingColors.Active,
                        )
                    }
                }
            }

            // 未读指示点
            if (unread) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// ── 消息详情弹窗 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageDetailSheet(
    message: CxMessage,
    onDismiss: () -> Unit,
    onDownload: (CxAttachment) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AhuShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // 防御 null（Gson 反序列化缓存不走 Kotlin 默认值）
            val safeTitle = (message.title ?: "").ifBlank { "消息详情" }
            val safeSender = message.senderName ?: ""
            val safeTypeName = message.typeName ?: ""
            val safeCourseName = message.courseName ?: ""
            val safeContent = message.content ?: ""
            val safeRtf = message.rtfContent ?: ""

            // 标题
            Text(
                text = safeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            // 元信息行
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (safeSender.isNotBlank()) {
                    Text(
                        text = safeSender,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (message.source == CxMessageSource.ACTIVITY && safeTypeName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = safeTypeName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (message.time > 0) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = message.time }
                    Text(
                        text = "%04d-%02d-%02d %02d:%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH),
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 课程信息（活动通知）
            if (message.source == CxMessageSource.ACTIVITY && safeCourseName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.School,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = safeCourseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (message.activityStatus == 1) {
                            Text("进行中", style = MaterialTheme.typography.labelSmall, color = ChaoxingColors.Active)
                        } else if (message.activityStatus == 2) {
                            Text("已结束", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (message.userStatus == 1) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.CheckCircle, contentDescription = "已参与", modifier = Modifier.size(14.dp), tint = ChaoxingColors.Active)
                        }
                    }
                }
                if (message.releaseNum > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${message.attendNum}/${message.releaseNum} 人已参与",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // 内容
            val content = safeContent.trim()
            val rtfContent = safeRtf.trim()

            if (rtfContent.isNotBlank() && rtfContent != content) {
                Text("详细内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = rtfContent.replace(Regex("<[^>]+>"), "").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = "无详细内容",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 附件列表（Gson 反序列化可能丢失，从原始 JSON 降级解析）
            val safeRawAttachment = message.attachment ?: ""
            val attachments = (message.attachments ?: emptyList()).ifEmpty {
                if (safeRawAttachment.isNotBlank()) {
                    try {
                        val arr = com.google.gson.JsonParser.parseString(safeRawAttachment).asJsonArray
                        arr.mapNotNull { el ->
                            try {
                                val cloud = el.asJsonObject.getAsJsonObject("att_clouddisk") ?: return@mapNotNull null
                                CxAttachment(
                                    name = cloud.get("name")?.asString ?: "",
                                    fileSize = cloud.get("fileSize")?.asString ?: "",
                                    suffix = cloud.get("suffix")?.asString ?: "",
                                    objectId = cloud.get("objectId")?.asString ?: "",
                                    preview = cloud.get("preview")?.asString ?: "",
                                    puid = cloud.get("puid")?.asString ?: "",
                                    forbidDownload = cloud.get("forbidDownload")?.asInt ?: 0,
                                )
                            } catch (_: Exception) { null }
                        }
                    } catch (_: Exception) { emptyList() }
                } else emptyList()
            }
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "附件 (${attachments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                attachments.forEach { att ->
                    AttachmentCard(
                        attachment = att,
                        onDownload = { onDownload(att) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── 附件卡片 ──────────────────────────────────────────────────

@Composable
private fun AttachmentCard(attachment: CxAttachment, onDownload: () -> Unit) {
    // 文件类型图标颜色
    val suffixColor = when (attachment.suffix.lowercase()) {
        "pdf" -> ChaoxingColors.FilePdf
        "doc", "docx" -> ChaoxingColors.FileDoc
        "xls", "xlsx" -> ChaoxingColors.FileSheet
        "ppt", "pptx" -> ChaoxingColors.FileSlide
        "zip", "rar", "7z" -> ChaoxingColors.FileArchive
        "jpg", "jpeg", "png", "gif" -> ChaoxingColors.FileImage
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = attachment.forbidDownload == 0, onClick = onDownload)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 文件类型标签
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = suffixColor.copy(alpha = 0.12f),
            ) {
                Text(
                    text = attachment.suffix.uppercase().ifBlank { "FILE" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = suffixColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            Spacer(Modifier.width(10.dp))

            // 文件名 + 大小
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attachment.fileSize.isNotBlank()) {
                    Text(
                        text = attachment.fileSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 下载按钮
            if (attachment.forbidDownload == 0) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "下载",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "禁止下载",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  课程 Tab 内容
// ══════════════════════════════════════════════════════════════

