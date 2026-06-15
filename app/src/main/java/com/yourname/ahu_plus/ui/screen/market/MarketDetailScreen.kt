package com.yourname.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.MarketComment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketDetailScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onLoadMoreReplies: (MarketComment) -> Unit,
    onCommentDraftChanged: (String) -> Unit,
    onCommentSubmit: () -> Unit,
    onCancelReply: () -> Unit,
    onStartReplyingToComment: (MarketComment) -> Unit,
    onStartReplyingToReply: (MarketComment, MarketComment) -> Unit,
    onCommentSuccessShown: () -> Unit
) {
    val topic = uiState.topicDetail ?: uiState.selectedTopic
    val commentsListState = rememberLazyListState()
    val shouldLoadMoreComments by remember(uiState.comments.size, uiState.hasMoreComments) {
        derivedStateOf {
            val total = commentsListState.layoutInfo.totalItemsCount
            val lastVisible = commentsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(shouldLoadMoreComments, uiState.comments.size, uiState.hasMoreComments) {
        if (shouldLoadMoreComments) onLoadMoreComments()
    }

    LaunchedEffect(uiState.postCommentSuccessMessage) {
        val msg = uiState.postCommentSuccessMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onCommentSuccessShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CommentComposerBar(
                draft = uiState.commentDraft,
                replyingTo = uiState.replyingTo,
                isPosting = uiState.isPostingComment,
                error = uiState.postCommentError,
                onDraftChanged = onCommentDraftChanged,
                onSubmit = onCommentSubmit,
                onCancelReply = onCancelReply
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = commentsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (topic != null) {
                item { MarketTopicDetailCard(topic = topic, school = uiState.topicSchoolMap[topic.id]) }
            }

            if (uiState.detailLoading) {
                item { LoadingRow("正在同步详情...") }
            }

            uiState.detailError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            item {
                Text(
                    text = "评论",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (uiState.commentsLoading && uiState.comments.isEmpty()) {
                item { LoadingRow("正在加载评论...") }
            }

            uiState.commentsError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.commentsLoading && uiState.commentsError == null && uiState.comments.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂无评论",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.comments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    isLoadingReplies = uiState.replyLoadingCommentIds.contains(comment.id),
                    replyError = uiState.replyErrors[comment.id],
                    onLoadMoreReplies = { onLoadMoreReplies(comment) },
                    onReplyClick = { onStartReplyingToComment(comment) },
                    onReplyReplyClick = { reply -> onStartReplyingToReply(comment, reply) }
                )
            }

            if (uiState.comments.isNotEmpty()) {
                item {
                    AutoLoadFooter(
                        isLoading = uiState.commentsLoadingMore,
                        hasMore = uiState.hasMoreComments,
                        loadingText = "正在加载更多评论...",
                        emptyText = "没有更多评论了"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CommentCard(
    comment: MarketComment,
    isLoadingReplies: Boolean,
    replyError: String?,
    onLoadMoreReplies: () -> Unit,
    onReplyClick: () -> Unit,
    onReplyReplyClick: (MarketComment) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user = comment.userInfo, size = 32.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = comment.createTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (comment.likeCount > 0) {
                    Text(
                        text = "赞 ${comment.likeCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onReplyClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("回复", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = comment.content.ifBlank { "无内容" },
                style = MaterialTheme.typography.bodyMedium
            )
            val replies = comment.visibleReplies
            if (replies.isNotEmpty() || comment.visibleReplyCount > 0) {
                HorizontalDivider()
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(10.dp)
                ) {
                    replies.forEach { reply ->
                        ReplyRow(
                            reply = reply,
                            onReplyClick = { onReplyReplyClick(reply) }
                        )
                    }
                    val hiddenCount = comment.visibleReplyCount - replies.size
                    if (hiddenCount > 0) {
                        if (isLoadingReplies) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                M3CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "正在加载回复...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onLoadMoreReplies,
                                modifier = Modifier.padding(horizontal = 0.dp)
                            ) {
                                Text("还有 $hiddenCount 条回复，点击加载")
                            }
                        }
                    }
                    replyError?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onLoadMoreReplies) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: MarketComment,
    onReplyClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        UserAvatar(user = reply.userInfo, size = 24.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "同学",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                reply.pickUserInfo?.nickname?.takeIf { it.isNotBlank() }?.let { pickedName ->
                    Text(
                        text = " 回复 $pickedName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = reply.content.ifBlank { "无内容" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onReplyClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("回复", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentComposerBar(
    draft: String,
    replyingTo: ReplyTarget?,
    isPosting: Boolean,
    error: String?,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelReply: () -> Unit
) {
    val nickname = replyingTo?.displayName.orEmpty()
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (replyingTo != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "回复 @$nickname",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancelReply,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("取消", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (replyingTo != null) "回复 @$nickname…" else "说点什么…"
                        )
                    },
                    maxLines = 3,
                    minLines = 1,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = !isPosting && draft.trim().isNotEmpty()
                ) {
                    if (isPosting) {
                        M3CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送"
                        )
                    }
                }
            }
            error?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
