package com.ahu_plus.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ahu_plus.ui.theme.AhuShapes

/**
 * 使用帮助（使用指南 + 常见问题 合并版）。
 *
 * 单一 Composable 内含自己的导航栈，三种页面：
 *  - [GuidePage.Root]   分类 → 条目列表，顶部置一个「未来更新计划」入口
 *  - [GuidePage.Entry]  某条目的详情（按小节渲染）
 *  - [GuidePage.Roadmap] 聚合所有「未来的计划」的路线图
 *
 * 关键交互：从路线图点进某条目，返回时回到路线图（而非根），符合需求
 * 「点击可以跳转到对应位置，返回之后就会返回此页面」。
 *
 * @param introSeen 首开说明弹窗是否已展示过（持久化，退登不清）。
 * @param onIntroSeen 首次展示弹窗后回调，用于落盘标记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    introSeen: Boolean,
    onIntroSeen: () -> Unit,
    onBack: () -> Unit,
) {
    // 导航栈：栈底恒为 Root。Entry / Roadmap 入栈，返回即出栈。
    val backStack = remember { mutableStateListOf<GuidePage>(GuidePage.Root) }
    val current = backStack.last()

    // 首次进入弹说明窗
    var showIntro by remember { mutableStateOf(!introSeen) }
    LaunchedEffect(Unit) {
        if (!introSeen) onIntroSeen()
    }

    fun push(page: GuidePage) { backStack.add(page) }
    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack()
    }

    BackHandler(enabled = true) { pop() }

    val title = when (current) {
        GuidePage.Root -> "使用帮助"
        GuidePage.Roadmap -> "未来更新计划"
        is GuidePage.Entry -> findGuideEntry(current.entryId)?.second?.title ?: "使用帮助"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                // 入栈(深度增加)右进左出，出栈反向
                val forward = targetState.depth >= initialState.depth
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { w -> dir * w } + fadeIn(tween(260)))
                    .togetherWith(
                        slideOutHorizontally(tween(260)) { w -> -dir * w } + fadeOut(tween(260))
                    )
            },
            label = "guide-page",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                GuidePage.Root -> RootPage(
                    onOpenEntry = { push(GuidePage.Entry(it)) },
                    onOpenRoadmap = { push(GuidePage.Roadmap) },
                )
                GuidePage.Roadmap -> RoadmapPage(
                    onOpenEntry = { push(GuidePage.Entry(it)) },
                )
                is GuidePage.Entry -> EntryPage(entryId = page.entryId)
            }
        }
    }

    if (showIntro) {
        IntroDialog(onDismiss = { showIntro = false })
    }
}

// ── 导航页面模型 ──

private sealed interface GuidePage {
    /** 用于动画方向判断：栈越深值越大。 */
    val depth: Int

    data object Root : GuidePage { override val depth = 0 }
    data object Roadmap : GuidePage { override val depth = 1 }
    data class Entry(val entryId: String) : GuidePage { override val depth = 2 }
}

// ── 根页：分类 → 条目 ──

@Composable
private fun RootPage(
    onOpenEntry: (String) -> Unit,
    onOpenRoadmap: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val isSearching = trimmed.isNotEmpty()
    val hits = remember(trimmed) { searchGuideEntries(trimmed) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        GuideSearchField(
            query = query,
            onQueryChange = { query = it },
        )

        if (isSearching) {
            if (hits.isEmpty()) {
                Text(
                    text = "未找到与「$trimmed」相关的条目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
                )
            } else {
                Text(
                    text = "${hits.size} 条结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(2.dp))
                hits.forEach { hit ->
                    SearchHitRow(
                        hit = hit,
                        query = trimmed,
                        onClick = { onOpenEntry(hit.entry.id) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
            // 「未来更新计划」聚合入口（置顶）
            RoadmapEntryCard(onClick = onOpenRoadmap)

            guideCategories.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                    Card(
                        shape = AhuShapes.Card,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            category.entries.forEachIndexed { index, entry ->
                                EntryRow(
                                    title = entry.title,
                                    summary = entry.summary,
                                    onClick = { onOpenEntry(entry.id) }
                                )
                                if (index != category.entries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun GuideSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索功能、问题、关键词") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除")
                }
            }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = AhuShapes.Card,
    )
}

@Composable
private fun SearchHitRow(
    hit: GuideSearchHit,
    query: String,
    onClick: () -> Unit,
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = hit.category.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = highlightMatches(hit.entry.title, query),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val snippet = snippetAround(hit.snippet, hit.matchStart, hit.matchLength, query)
            if (snippet.text.isNotEmpty()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 给原文加粗+红色高亮所有命中关键字（不区分大小写）。query 为空直接返回纯文本。 */
private fun highlightMatches(text: String, query: String): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    val needle = query.lowercase()
    val lower = text.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val idx = lower.indexOf(needle, cursor)
            if (idx < 0) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, idx))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = HighlightRed)) {
                append(text.substring(idx, idx + needle.length))
            }
            cursor = idx + needle.length
        }
    }
}

/**
 * 在 [matchStart] 处裁出左右各 [ctx] 字符的上下文，前后视截断补「…」，
 * 然后对裁后片段做关键字高亮。
 */
private fun snippetAround(
    text: String,
    matchStart: Int,
    matchLength: Int,
    query: String,
    ctx: Int = 24,
): AnnotatedString {
    val start = maxOf(0, matchStart - ctx)
    val end = minOf(text.length, matchStart + matchLength + ctx)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    val visible = prefix + text.substring(start, end) + suffix
    return highlightMatches(visible, query)
}

// 与集市搜索一致的命中红色，避免另起 design token；ponytail: 复用现成颜色，等设计 token 扩展再迁移
private val HighlightRed = Color(0xFFD32F2F)

@Composable
private fun RoadmapEntryCard(onClick: () -> Unit) {
    // TODO: 跳转暂时关闭，恢复时把 .clickable(onClick = onClick) 加回去并恢复尾部箭头
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "未来更新计划",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    text = "汇总所有计划中的功能（入口暂时关闭）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── 条目详情页 ──

@Composable
private fun EntryPage(entryId: String) {
    val found = findGuideEntry(entryId)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        if (found == null) {
            Text(
                text = "内容未找到",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val (category, entry) = found
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            val sections = entry.visibleSections
            if (sections.isEmpty()) {
                Text(
                    text = "该功能的详细说明正在补充中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sections.forEach { section -> SectionCard(section) }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionCard(section: GuideSection) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${section.kind.emoji} ${section.displayLabel}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            section.body.forEach { block -> BodyBlock(block) }
        }
    }
}

@Composable
private fun BodyBlock(block: GuideBlock) {
    when (block) {
        is GuideBlock.Para -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        is GuideBlock.Bullet -> Text(
            text = "• ${block.text}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

// ── 路线图（未来更新计划）页 ──

@Composable
private fun RoadmapPage(onOpenEntry: (String) -> Unit) {
    val items = roadmapItems
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "汇总所有计划中的功能。点击任意项跳转到对应说明，返回后回到本页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        if (items.isEmpty()) {
            Text(
                text = "暂无计划中的功能。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp)
            )
        } else {
            items.forEach { item ->
                RoadmapRow(item = item, onClick = { onOpenEntry(item.entryId) })
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RoadmapRow(item: RoadmapItem, onClick: () -> Unit) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = GuideSectionKind.FUTURE.emoji)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.categoryTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 1.dp)
                )
                if (item.summary.isNotBlank()) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── 首次打开说明弹窗 ──

@Composable
private fun IntroDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("关于这份帮助文档") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "这里整合了原来的「使用指南」和「常见问题」。每一项功能尽量从以下几个角度说明（不一定每项都齐全）：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                GuideSectionKind.entries.forEach { kind ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(text = kind.emoji, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(
                                text = kind.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = kind.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = "顶部「未来更新计划」汇总了所有计划中的功能，点进去能直接跳到对应说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        }
    )
}
