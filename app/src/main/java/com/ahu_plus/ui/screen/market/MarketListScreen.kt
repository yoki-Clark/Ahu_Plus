package com.ahu_plus.ui.screen.market

import com.ahu_plus.data.diagnostic.SafeLog as Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggerItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ahu_plus.ui.components.AhuEmptyState
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import com.ahu_plus.ui.components.AhuSkeletonList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.MarketIdentity
import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import com.ahu_plus.ui.theme.MarketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketListScreen(
    uiState: MarketUiState,
    listState: LazyListState,
    staggerListState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState? = null,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenHot: () -> Unit,
    onOpenTopic: (MarketTopic) -> Unit,
    onOpenCompose: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onToggleSchool: (String, Boolean) -> Unit = { _, _ -> },
    onSelectAllSchools: () -> Unit = {},
    onSelectReadOnlyNode: (String?) -> Unit = {},
) {
    val isSingleSchool = uiState.selectedIdentityIds.size <= 1
    // 只读模式:无身份,展示安大热榜累积流(替代旧的身份输入卡空态)
    val readOnlyMode = !uiState.hasSavedIdentity
    // 只读板块筛选(纯本地):从累积帖动态汇总,按帖子数排序
    val readOnlyNodes = if (readOnlyMode) {
        uiState.readOnlyTopics
            .map { it.node }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    } else emptyList()
    val displayTopics = when {
        readOnlyMode && uiState.readOnlySelectedNode != null ->
            uiState.readOnlyTopics.filter { it.node == uiState.readOnlySelectedNode }
        readOnlyMode -> uiState.readOnlyTopics
        else -> uiState.topics
    }
    val displayLoading = if (readOnlyMode) uiState.readOnlyLoading else uiState.isLoading
    val displayError = if (readOnlyMode) uiState.readOnlyError else uiState.error
    // 2026-06-17 Bug5: 优先用外部传入的 state (MarketScreen 已将 state 提升, 返回时可恢复位置)
    val staggerState = staggerListState ?: rememberLazyStaggeredGridState()
    val shouldLoadMore by remember(uiState.topics.size, uiState.hasMoreTopics) {
        derivedStateOf {
            // 瀑布流与单列模式共用一个判断：取两个 state 中实际有数据的那个来计算
            val staggerInfo = staggerState.layoutInfo
            val useStagger = staggerInfo.totalItemsCount > 0
            val total = if (useStagger) staggerInfo.totalItemsCount else listState.layoutInfo.totalItemsCount
            val lastVisible = if (useStagger) {
                staggerInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.topics.size, uiState.hasMoreTopics) {
        if (shouldLoadMore && !uiState.isSearching && !readOnlyMode) onLoadMore()
    }

    // 搜索防抖:query 稳定 500ms 后自动提交。键值变化会取消上一次 delay,避免每次按键都打接口
    LaunchedEffect(uiState.searchQuery, uiState.isSearching) {
        if (!uiState.isSearching) return@LaunchedEffect
        val q = uiState.searchQuery.trim()
        if (q.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        if (q == uiState.searchQuery.trim()) onSearchSubmit()
    }

    // FAB 仅在列表页（一级页）显示：未在搜索/详情/设置/发帖/热榜/消息任一状态时
    val showFab = uiState.hasSavedIdentity && !uiState.isSearching

    // "回到顶部"按钮：列表下滑后显示
    val scope = rememberCoroutineScope()
    val isScrolledFromTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 160 ||
                staggerState.firstVisibleItemIndex > 0 ||
                staggerState.firstVisibleItemScrollOffset > 160
        }
    }
    val showScrollToTop = uiState.scrollToTopEnabled && isScrolledFromTop && uiState.hasSavedIdentity && !uiState.isSearching

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            if (uiState.isSearching) {
                AhuTopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索帖子内容") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清除")
                            }
                        }
                        TextButton(
                            onClick = onSearchSubmit,
                            enabled = uiState.searchQuery.isNotBlank() && !uiState.searchLoading
                        ) {
                            Text(if (uiState.searchLoading) "搜索中" else "搜索")
                        }
                    }
                )
            } else {
                AhuTopAppBar(
                    title = { Text("校园集市") },
                    actions = {
                        if (uiState.hasSavedIdentity) {
                            IconButton(onClick = onOpenSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                            // 多校模式不显示消息(避免不同学校消息混淆)
                            if (isSingleSchool) {
                                IconButton(onClick = onOpenNotices) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "消息")
                                }
                            }
                        } else {
                            // 只读模式:仅保留设置入口(导入身份/清除数据等)
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        if (uiState.isSearching) {
            SearchResultList(
                uiState = uiState,
                onOpenTopic = onOpenTopic,
                onLoadMore = onLoadMoreSearch,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            AhuPullToRefreshBox(
                isRefreshing = displayLoading,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.listLayoutMode == "stagger") {
                    // 小红书双列瀑布流模式
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = staggerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalItemSpacing = 10.dp,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        if (readOnlyMode) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                ReadOnlyBanner(onImport = onOpenSettings)
                            }
                            if (readOnlyNodes.size > 1) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    ReadOnlyNodeFilterRow(
                                        nodes = readOnlyNodes,
                                        selected = uiState.readOnlySelectedNode,
                                        onSelect = onSelectReadOnlyNode
                                    )
                                }
                            }
                        } else if (uiState.hasSavedIdentity) {
                            if (uiState.identities.size > 1) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    SchoolSwitcherRow(
                                        identities = uiState.identities,
                                        selectedIds = uiState.selectedIdentityIds,
                                        onToggle = onToggleSchool,
                                        onSelectAll = onSelectAllSchools,
                                    )
                                }
                            }
                            if (isSingleSchool) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    HotEntryCard(onClick = onOpenHot)
                                }
                            }
                        }

                        if (displayLoading && displayTopics.isEmpty()) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                AhuSkeletonList(itemCount = 4)
                            }
                        }

                        // 有缓存(topics 非空)刷新失败不插全宽错误卡,避免打断已加载内容(项55);
                        // 无缓存时才全屏错误+重试。
                        if (displayError != null && displayTopics.isEmpty()) {
                            displayError?.let { error ->
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                                        TextButton(onClick = onRefresh) { Text("重试") }
                                    }
                                }
                            }
                        }

                        uiState.saveMessage?.let { message ->
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                StatusCard(text = message, color = MarketColors.Success)
                            }
                        }

                        if (!displayLoading && displayError == null && displayTopics.isEmpty()) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                AhuEmptyState(
                                    icon = Icons.Filled.Storefront,
                                    title = if (readOnlyMode) "暂时没有内容" else "暂时没有内容",
                                    subtitle = if (readOnlyMode) "下拉刷新可拉取最新帖子，多刷几次内容会越来越多"
                                    else "换个学校或稍后再来看看",
                                )
                            }
                        }

                        staggerItems(displayTopics, key = { it.id }) { topic ->
                            StaggerMarketTopicCard(
                                topic = topic,
                                onClick = { onOpenTopic(topic) },
                                // 只读模式始终显示来源 chip(圈子/校友圈);单身份登录模式不显示
                                school = if (readOnlyMode) uiState.topicSchoolMap[topic.id]
                                else if (isSingleSchool) null
                                else uiState.topicSchoolMap[topic.id]
                            )
                        }
                        if (displayTopics.isNotEmpty() && !readOnlyMode) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                AutoLoadFooter(
                                    isLoading = uiState.isLoadingMore,
                                    hasMore = uiState.hasMoreTopics,
                                    loadingText = "正在加载更多...",
                                    emptyText = "没有更多帖子了"
                                )
                            }
                        }
                        if (readOnlyMode && displayTopics.isNotEmpty()) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                ReadOnlyFooterHint()
                            }
                        }
                        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                } else {
                    // 单列列表模式(默认)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(AhuSpacing.CardGap)
                    ) {
                        if (readOnlyMode) {
                            item { ReadOnlyBanner(onImport = onOpenSettings) }
                            if (readOnlyNodes.size > 1) {
                                item {
                                    ReadOnlyNodeFilterRow(
                                        nodes = readOnlyNodes,
                                        selected = uiState.readOnlySelectedNode,
                                        onSelect = onSelectReadOnlyNode
                                    )
                                }
                            }
                        } else if (uiState.hasSavedIdentity) {
                            if (uiState.identities.size > 1) {
                                item {
                                    SchoolSwitcherRow(
                                        identities = uiState.identities,
                                        selectedIds = uiState.selectedIdentityIds,
                                        onToggle = onToggleSchool,
                                        onSelectAll = onSelectAllSchools
                                    )
                                }
                            }
                            // 单校模式才显示热榜;多校模式热榜跨校内容会混乱
                            if (isSingleSchool) {
                                item { HotEntryCard(onClick = onOpenHot) }
                            }
                        }

                        if (displayLoading && displayTopics.isEmpty()) {
                            item { AhuSkeletonList(itemCount = 4) }
                        }

                        // 有缓存(topics 非空)刷新失败不插全宽错误卡(项55)。
                        if (displayError != null && displayTopics.isEmpty()) {
                            displayError?.let { error ->
                                item {
                                    StatusCard(text = error, color = MaterialTheme.colorScheme.error) {
                                        TextButton(onClick = onRefresh) { Text("重试") }
                                    }
                                }
                            }
                        }

                        uiState.saveMessage?.let { message ->
                            item { StatusCard(text = message, color = MarketColors.Success) }
                        }

                        if (!displayLoading && displayError == null && displayTopics.isEmpty()) {
                            item {
                                AhuEmptyState(
                                    icon = Icons.Filled.Storefront,
                                    title = "暂时没有内容",
                                    subtitle = if (readOnlyMode) "下拉刷新可拉取最新帖子，多刷几次内容会越来越多"
                                    else "换个学校或稍后再来看看",
                                )
                            }
                        }

                        items(displayTopics, key = { it.id }) { topic ->
                            MarketTopicCard(
                                topic = topic,
                                onClick = { onOpenTopic(topic) },
                                // 只读模式始终显示来源 chip(圈子/校友圈);单身份登录模式不显示
                                school = if (readOnlyMode) uiState.topicSchoolMap[topic.id]
                                else if (isSingleSchool) null
                                else uiState.topicSchoolMap[topic.id],
                                modifier = Modifier.animateItem(),
                            )
                        }

                        if (displayTopics.isNotEmpty() && !readOnlyMode) {
                            item {
                                AutoLoadFooter(
                                    isLoading = uiState.isLoadingMore,
                                    hasMore = uiState.hasMoreTopics,
                                    loadingText = "正在加载更多...",
                                    emptyText = "没有更多帖子了"
                                )
                            }
                        }
                        if (readOnlyMode && displayTopics.isNotEmpty()) {
                            item { ReadOnlyFooterHint() }
                        }

                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

        // 悬浮发帖按钮,叠加在 Scaffold 之上
        DraggableFab(
            visible = showFab,
            onClick = onOpenCompose
        )

        // "回到顶部"按钮 — 右侧,发布按钮上方,可拖动
        DraggableScrollToTopButton(
            visible = showScrollToTop,
            onScrollToTop = {
                Log.i("MarketList", "scroll-to-top clicked, firing onRefresh immediately")
                onRefresh()  // 立即触发,viewModelScope 独立,不受 UI scope / 滚动挂起影响
                scope.launch {
                    listState.animateScrollToItem(0)
                    staggerState.animateScrollToItem(0)
                }
            }
        )
    }
}

@Composable
private fun SearchResultList(
    uiState: MarketUiState,
    onOpenTopic: (MarketTopic) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val shouldLoadMore by remember(uiState.searchResults.size, uiState.hasMoreSearch) {
        derivedStateOf {
            val info = searchListState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.searchResults.size, uiState.hasMoreSearch) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = searchListState,
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.searchLoading && uiState.searchResults.isEmpty()) {
            item { LoadingRow("正在搜索 \"${uiState.searchQuery}\" ...") }
        }

        uiState.searchError?.let { error ->
            item { StatusCard(text = error, color = MaterialTheme.colorScheme.error) }
        }

        if (!uiState.searchLoading && uiState.searchError == null &&
            uiState.searchResults.isEmpty() && uiState.searchQuery.isNotBlank()
        ) {
            item {
                StatusCard(
                    text = "没有找到包含 \"${uiState.searchQuery}\" 的帖子",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(uiState.searchResults, key = { it.id }) { topic ->
            MarketTopicCard(
                topic = topic,
                onClick = { onOpenTopic(topic) },
                school = uiState.topicSchoolMap[topic.id],
                highlightQuery = uiState.searchQuery,
                modifier = Modifier.animateItem(),
            )
        }

        if (uiState.searchResults.isNotEmpty()) {
            item {
                AutoLoadFooter(
                    isLoading = uiState.searchLoadingMore,
                    hasMore = uiState.hasMoreSearch,
                    loadingText = "正在加载更多结果...",
                    emptyText = "没有更多结果了"
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun SchoolSwitcherRow(
    identities: List<MarketIdentity>,
    selectedIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit = {}
) {
    val allSelected = identities.all { it.id in selectedIds }
    val scrollState = rememberScrollState()

    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "校区切换",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "全部" chip
                FilterChip(
                    selected = allSelected,
                    onClick = { if (!allSelected) onSelectAll() },
                    label = { Text("全部校区") },
                    leadingIcon = {
                        if (allSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
                // Individual school chips
                identities.forEach { identity ->
                    val isSelected = identity.id in selectedIds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(identity.id, !isSelected) },
                        label = {
                            Text(
                                text = identity.school ?: "未识别校区",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 只读模式顶部横幅:提示当前未登录,只看安大热榜累积流;「导入身份」进设置页。
 * 可折叠(本会话生效,离开页面再进会重新出现--不永久关闭)。
 */
@Composable
internal fun ReadOnlyBanner(onImport: () -> Unit) {
    var collapsed by remember { mutableStateOf(false) }
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MarketColors.HotEntryIconBg.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "只读模式 · 未登录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onImport) { Text("导入身份") }
                IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (collapsed) Icons.Filled.KeyboardArrowDown
                        else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (collapsed) "展开" else "收起",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!collapsed) {
                Text(
                    text = "当前展示安大圈子与安大校友圈的热门帖子，按发布时间倒序。" +
                        "下拉刷新可拉取最新帖子，多次刷新内容会累积增多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 只读流板块筛选行:板块从累积帖动态汇总,纯本地过滤不产生请求。 */
@Composable
private fun ReadOnlyNodeFilterRow(
    nodes: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") }
        )
        nodes.forEach { node ->
            FilterChip(
                selected = selected == node,
                onClick = { onSelect(if (selected == node) null else node) },
                label = { Text(node, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** 只读流列表底部提示:说明无分页,刷新拉新帖。 */
@Composable
private fun ReadOnlyFooterHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "下拉刷新拉取更多最新帖子",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun HotEntryCard(onClick: () -> Unit) {
    Card(
        shape = AhuShapes.Card,
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
                    .clip(AhuShapes.Card)
                    .background(MarketColors.HotEntryIconBg.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = MarketColors.HotFlame
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
