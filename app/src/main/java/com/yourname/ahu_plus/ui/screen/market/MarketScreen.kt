package com.yourname.ahu_plus.ui.screen.market

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yourname.ahu_plus.data.model.MarketComment
import com.yourname.ahu_plus.data.model.MarketNode
import com.yourname.ahu_plus.data.model.MarketNotice
import com.yourname.ahu_plus.data.model.MarketTopic
import com.yourname.ahu_plus.data.model.MarketUser
import kotlinx.coroutines.launch

@Composable
fun MarketScreen(viewModel: MarketViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hotListState = rememberLazyListState()

    if (uiState.showCompose) {
        BackHandler(onBack = viewModel::closeCompose)
        ComposePostScreen(
            uiState = uiState,
            onBack = viewModel::closeCompose,
            onNodeMenuToggle = viewModel::onComposeNodeMenuToggle,
            onNodeSelected = viewModel::onComposeNodeSelected,
            onTitleChanged = viewModel::onComposeTitleChanged,
            onContentChanged = viewModel::onComposeContentChanged,
            onAnonChanged = viewModel::onComposeAnonChanged,
            onSubmit = viewModel::submitPost
        )
    } else if (uiState.selectedTopic != null) {
        BackHandler(onBack = viewModel::closeTopic)
        MarketDetailScreen(
            uiState = uiState,
            onBack = viewModel::closeTopic,
            onRefresh = viewModel::retryDetail,
            onLoadMoreComments = viewModel::loadMoreComments,
            onLoadMoreReplies = viewModel::loadMoreReplies,
            onCommentDraftChanged = viewModel::onCommentDraftChanged,
            onCommentSubmit = viewModel::submitComment,
            onCancelReply = viewModel::cancelReply,
            onStartReplyingToComment = viewModel::startReplyingToComment,
            onStartReplyingToReply = viewModel::startReplyingToReply,
            onCommentSuccessShown = viewModel::dismissPostCommentSuccessMessage
        )
    } else if (uiState.showHotTopics) {
        BackHandler(onBack = viewModel::closeHotTopics)
        MarketHotScreen(
            uiState = uiState,
            listState = hotListState,
            onBack = viewModel::closeHotTopics,
            onRefresh = viewModel::refreshHotTopics,
            onOpenTopic = viewModel::openTopic
        )
    } else if (uiState.showNotices) {
        BackHandler(onBack = viewModel::closeNotices)
        MarketNoticesScreen(
            uiState = uiState,
            onBack = viewModel::closeNotices,
            onRefresh = viewModel::refreshNotices,
            onLoadMore = viewModel::loadMoreNotices,
            onOpenTopic = viewModel::openTopic
        )
    } else {
        MarketListScreen(
            uiState = uiState,
            listState = listState,
            onIdentityChanged = viewModel::onIdentityInputChanged,
            onSaveIdentity = viewModel::saveIdentity,
            onClearIdentity = viewModel::clearIdentity,
            onRefresh = viewModel::refreshTopics,
            onLoadMore = viewModel::loadNextPage,
            onOpenHot = viewModel::openHotTopics,
            onOpenTopic = viewModel::openTopic,
            onOpenCompose = viewModel::openCompose,
            onOpenNotices = viewModel::openNotices
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketListScreen(
    uiState: MarketUiState,
    listState: LazyListState,
    onIdentityChanged: (String) -> Unit,
    onSaveIdentity: () -> Unit,
    onClearIdentity: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenHot: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit,
    onOpenCompose: () -> Unit,
    onOpenNotices: () -> Unit
) {
    val shouldLoadMore by remember(uiState.topics.size, uiState.hasMoreTopics) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.topics.size, uiState.hasMoreTopics) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园集市") },
                actions = {
                    if (uiState.hasSavedIdentity) {
                        IconButton(onClick = onOpenNotices) {
                            Icon(Icons.Filled.Notifications, contentDescription = "消息")
                        }
                        IconButton(onClick = onOpenCompose) {
                            Icon(Icons.Filled.Add, contentDescription = "发帖")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.topics.isEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!uiState.hasSavedIdentity) {
                item {
                    IdentityCard(
                        uiState = uiState,
                        onIdentityChanged = onIdentityChanged,
                        onSave = onSaveIdentity,
                        onClear = onClearIdentity
                    )
                }
            } else {
                item {
                    MarketHeaderCard(school = uiState.school)
                }
                item {
                    HotEntryCard(onClick = onOpenHot)
                }
            }

            if (uiState.isLoading) {
                item { LoadingRow("正在加载集市...") }
            }

            uiState.error?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            uiState.saveMessage?.let { message ->
                item { StatusCard(text = message, color = Color(0xFF2A9D8F)) }
            }

            if (uiState.hasSavedIdentity && !uiState.isLoading && uiState.error == null &&
                uiState.topics.isEmpty()
            ) {
                item {
                    StatusCard(
                        text = "暂时没有加载到集市内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.topics, key = { it.id }) { topic ->
                MarketTopicCard(
                    topic = topic,
                    onClick = { onOpenTopic(topic) }
                )
            }

            if (uiState.topics.isNotEmpty()) {
                item {
                    AutoLoadFooter(
                        isLoading = uiState.isLoadingMore,
                        hasMore = uiState.hasMoreTopics,
                        loadingText = "正在加载更多...",
                        emptyText = "没有更多帖子了"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketHotScreen(
    uiState: MarketUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("集市热榜") },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFE65100)
                        )
                        Column(
                            modifier = Modifier.padding(start = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "十大热帖",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5D2A00)
                            )
                            Text(
                                text = "按热度展示近期讨论最多的帖子",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8A4B12)
                            )
                        }
                    }
                }
            }

            if (uiState.hotLoading && uiState.hotTopics.isEmpty()) {
                item { LoadingRow("正在加载热榜...") }
            }

            uiState.hotError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.hotLoading && uiState.hotError == null && uiState.hotTopics.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂时没有热榜内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.hotTopics, key = { it.id }) { topic ->
                HotTopicCard(
                    topic = topic,
                    rank = uiState.hotTopics.indexOf(topic) + 1,
                    onClick = { onOpenTopic(topic) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketNoticesScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(uiState.notices.size, uiState.hasMoreNotices) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.notices.size, uiState.hasMoreNotices) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val countSuffix = if (uiState.noticesCount > 0) "（${uiState.noticesCount}）" else ""
                    Text("消息$countSuffix")
                },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.noticesLoading && uiState.notices.isEmpty()) {
                item { LoadingRow("正在加载消息...") }
            }

            uiState.noticesError?.let { error ->
                item {
                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
            }

            if (!uiState.noticesLoading && uiState.noticesError == null && uiState.notices.isEmpty()) {
                item {
                    StatusCard(
                        text = "暂时没有消息",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.notices, key = { it.id }) { notice ->
                MarketNoticeCard(
                    notice = notice,
                    onClick = {
                        notice.topic?.let { topic ->
                            onOpenTopic(topic.toMarketTopic())
                        }
                    }
                )
            }

            if (uiState.notices.isNotEmpty()) {
                item {
                    AutoLoadFooter(
                        isLoading = uiState.noticesLoadingMore,
                        hasMore = uiState.hasMoreNotices,
                        loadingText = "正在加载更多...",
                        emptyText = "没有更多消息了"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun MarketNoticeCard(
    notice: MarketNotice,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user = notice.senderUserInfo, size = 36.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = notice.senderUserInfo?.nickname?.takeIf { it.isNotBlank() }
                            ?: "匿名同学",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = notice.actionTypeText.ifBlank { defaultActionText(notice) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                NoticeActionIcon(notice = notice)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = notice.createTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            val body = notice.bodyText
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            notice.topic?.let { topic ->
                val topicTitle = topic.title.takeIf { it.isNotBlank() && it != "none" }
                    ?: topic.content.lineSequence().firstOrNull().orEmpty()
                if (topicTitle.isNotBlank()) {
                    Text(
                        text = "原帖：$topicTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeActionIcon(notice: MarketNotice) {
    val tint = if (notice.isLike) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
    if (notice.isLike) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    } else {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun defaultActionText(notice: MarketNotice): String = when (notice.actionType) {
    2 -> "评论了你的帖子"
    3 -> "回复了你"
    4 -> "点赞了你的主题"
    6 -> "点赞了你的回复"
    else -> if (notice.isLike) "赞了你" else "提到了你"
}

/**
 * 把通知里嵌入的精简主题展开成 `MarketTopic`，交给 `viewModel::openTopic`。
 * 缺省字段（node / isAnon / likeCount 等）由 `loadTopicDetail` 异步补全，
 * 进入详情页时会先看到一次 loading，然后切换到完整数据。
 */
private fun com.yourname.ahu_plus.data.model.MarketNoticeTopic.toMarketTopic(): MarketTopic =
    MarketTopic(
        id = id,
        title = title,
        content = content,
        imgs = imgs
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketDetailScreen(
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
                item { MarketTopicDetailCard(topic = topic) }
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
private fun MarketHeaderCard(school: String?) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "集市内容流",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = school?.let { "当前学校：$it" } ?: "身份字段已保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun HotEntryCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = Color(0xFFE65100)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "集市热榜",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "查看近期讨论最热的帖子",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "进入",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MarketIdentityEditor(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    IdentityCard(
        uiState = uiState,
        onIdentityChanged = onIdentityChanged,
        onSave = onSave,
        onClear = onClear,
        modifier = modifier
    )
}

@Composable
private fun IdentityCard(
    uiState: MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showIdentity by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2C94C).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = Color(0xFFB7791F)
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "集市身份字段",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.school?.let { "当前学校：$it" } ?: "粘贴 Bearer 字段后加载内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = uiState.identityInput,
                onValueChange = onIdentityChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 身份字段") },
                placeholder = { Text("Bearer eyJ...") },
                minLines = 2,
                maxLines = 4,
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showIdentity = !showIdentity }) {
                        Icon(
                            imageVector = if (showIdentity) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (showIdentity) "隐藏" else "显示"
                        )
                    }
                },
                visualTransformation = if (showIdentity) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            uiState.identityError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2A9D8F)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = uiState.hasSavedIdentity
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除")
                }
            }
        }
    }
}

@Composable
private fun MarketTopicCard(topic: MarketTopic, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopicMetaHeader(topic = topic)
            TopicTitle(topic = topic)
            Text(
                text = topic.content.ifBlank { "无正文" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            TopicFirstImage(topic = topic)
            TopicFooter(topic = topic)
        }
    }
}

@Composable
private fun HotTopicCard(
    topic: MarketTopic,
    rank: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFE53935)
                            2 -> Color(0xFFFF9800)
                            3 -> Color(0xFFFFC107)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicMetaHeader(topic = topic)
                TopicTitle(topic = topic)
                Text(
                    text = topic.content.ifBlank { "无正文" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                TopicFirstImage(topic = topic)
                TopicFooter(topic = topic)
            }
        }
    }
}

@Composable
private fun MarketTopicDetailCard(topic: MarketTopic) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopicMetaHeader(topic = topic)
            TopicTitle(topic = topic)
            Text(
                text = topic.content.ifBlank { "无正文" },
                style = MaterialTheme.typography.bodyLarge
            )
            topic.imgs.forEach { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
            TopicFooter(topic = topic)
        }
    }
}

@Composable
private fun TopicMetaHeader(topic: MarketTopic) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(user = topic.userInfo, size = 36.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = topic.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = topic.node.ifBlank { "集市" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = topic.createTime,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TopicTitle(topic: MarketTopic) {
    val title = topic.title.takeIf { it.isNotBlank() && it != "none" }
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopicFirstImage(topic: MarketTopic) {
    topic.imgs.firstOrNull()?.let { imageUrl ->
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun TopicFooter(topic: MarketTopic) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "赞 ${topic.likeCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = topic.commentCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (topic.imgs.isNotEmpty()) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = topic.imgs.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 0.dp
                    )
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
                                CircularProgressIndicator(
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 0.dp
            )
        ) {
            Text("回复", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UserAvatar(user: MarketUser?, size: Dp) {
    val avatar = user?.avatar?.takeIf { it.isNotBlank() }
    val fallback = user?.nickname?.takeIf { it.isNotBlank() }?.firstOrNull()?.toString() ?: "匿"

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun AutoLoadFooter(
    isLoading: Boolean,
    hasMore: Boolean,
    loadingText: String,
    emptyText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> LoadingRow(loadingText)
            hasMore -> Text(
                text = "继续下滑加载更多",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun StatusCard(
    text: String,
    color: Color,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposePostScreen(
    uiState: MarketUiState,
    onBack: () -> Unit,
    onNodeMenuToggle: (Boolean) -> Unit,
    onNodeSelected: (Long) -> Unit,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onAnonChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.postSuccessMessage) {
        val message = uiState.postSuccessMessage
        if (!message.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发布帖子") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ComposeNodeCard(
                nodes = uiState.composeNodes,
                selectedId = uiState.composeNodeId,
                menuOpen = uiState.composeNodeMenuOpen,
                onMenuToggle = onNodeMenuToggle,
                onNodeSelected = onNodeSelected
            )

            OutlinedTextField(
                value = uiState.composeTitle,
                onValueChange = onTitleChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题（可留空）") },
                placeholder = { Text("一句话描述你的帖子") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            OutlinedTextField(
                value = uiState.composeContent,
                onValueChange = onContentChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = { Text("正文") },
                placeholder = { Text("说点什么吧…") },
                minLines = 6,
                maxLines = 18,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Default,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "匿名发布",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "开启后其他同学看不到你的昵称",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.composeIsAnon,
                        onCheckedChange = onAnonChanged
                    )
                }
            }

            uiState.postError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !uiState.isPosting && uiState.composeContent.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (uiState.isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("发布中…")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("发布")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ComposeNodeCard(
    nodes: List<MarketNode>,
    selectedId: Long,
    menuOpen: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onNodeSelected: (Long) -> Unit
) {
    val selected = nodes.firstOrNull { it.id == selectedId } ?: nodes.firstOrNull()
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "选择板块",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = selected?.name ?: "请选择板块",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMenuToggle(true) },
                    enabled = false,
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuToggle(false) }
                ) {
                    if (nodes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无可用板块") },
                            onClick = { onMenuToggle(false) }
                        )
                    } else {
                        nodes.forEach { node ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = node.name,
                                        fontWeight = if (node.id == selectedId) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                },
                                onClick = { onNodeSelected(node.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        )
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
                        CircularProgressIndicator(
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
