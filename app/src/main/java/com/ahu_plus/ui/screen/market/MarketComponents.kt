package com.ahu_plus.ui.screen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ahu_plus.data.model.MarketComment
import com.ahu_plus.data.model.MarketIdentity
import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.model.MarketUser
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.components.AhuTag
import com.ahu_plus.ui.theme.MarketColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════
//  头像 / Loading / Status / Footer
// ═══════════════════════════════════════════════════════════

@Composable
internal fun UserAvatar(user: MarketUser?, size: Dp) {
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
internal fun AutoLoadFooter(
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
internal fun LoadingRow(text: String) {
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
internal fun StatusCard(
    text: String,
    color: Color,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        shape = AhuShapes.Card,
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

// ═══════════════════════════════════════════════════════════
//  帖子主题渲染：标题 / 头 / 首图 / 底部
// ═══════════════════════════════════════════════════════════

@Composable
internal fun TopicMetaHeader(topic: MarketTopic, school: String? = null) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AhuTag(
                    text = topic.node.ifBlank { "集市" },
                    color = MaterialTheme.colorScheme.primary
                )
                if (school != null) {
                    AhuTag(
                        text = school,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        Text(
            text = relativeMarketTime(topic.createTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun TopicTitle(topic: MarketTopic, highlightQuery: String? = null) {
    val title = topic.title.takeIf { it.isNotBlank() && it != "none" }
    if (title != null) {
        Text(
            text = buildSearchAnnotated(title, highlightQuery, MarketColors.SearchHighlight),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TopicFirstImage(topic: MarketTopic) {
    TopicImageGrid(imgs = topic.imgs, detail = false)
}

@Composable
internal fun TopicFooter(topic: MarketTopic) {
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

/**
 * 多校模式下的「选学校发布」行 —— 横向 [FilterChip] 列表,让用户明确选择把新帖发到哪所学校。
 * 单校模式不调用此组件(由调用方根据 [com.ahu_plus.data.model.MarketIdentity] 数量判断)。
 */
@Composable
internal fun SchoolPickerRow(
    identities: List<MarketIdentity>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择发布到哪个学校",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            identities.forEach { identity ->
                FilterChip(
                    selected = identity.id == selectedId,
                    onClick = { onSelect(identity.id) },
                    label = { Text(identity.school ?: "未识别校区") }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  帖子卡片（列表 / 热榜 / 详情 通用）
// ═══════════════════════════════════════════════════════════

@Composable
internal fun MarketTopicCard(
    topic: MarketTopic,
    onClick: () -> Unit,
    school: String? = null,
    showTopComments: Boolean = true,
    highlightQuery: String? = null
) {
    Card(
        shape = AhuShapes.Card,
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
            TopicMetaHeader(topic = topic, school = school)
            TopicTitle(topic = topic, highlightQuery = highlightQuery)
            Text(
                text = buildSearchAnnotated(
                    topic.content.ifBlank { "无正文" },
                    highlightQuery,
                    MarketColors.SearchHighlight
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            TopicFirstImage(topic = topic)
            // 列表接口可能返回前两条热门评论(若接口字段未提供则空列表,不显示)
            if (showTopComments && topic.topComments.isNotEmpty()) {
                TopCommentsPreview(
                    comments = topic.topComments.take(2),
                    totalCount = topic.commentCount
                )
            }
            TopicFooter(topic = topic)
        }
    }
}

/**
 * 列表卡片底部的「前两条评论」预览(无头像,只显示用户名 + 内容)。
 * 设计:半透明背景 + 左侧色条,跟帖子主区域视觉分层。
 */
@Composable
private fun TopCommentsPreview(
    comments: List<MarketComment>,
    totalCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AhuShapes.Card)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        comments.forEach { comment ->
            val nickname = comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名"
            Text(
                text = "${nickname}: ${comment.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (totalCount > comments.size) {
            Text(
                text = "查看全部 $totalCount 条评论",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  小红书双列瀑布流卡片
// ═══════════════════════════════════════════════════════════

/**
 * 小红书风格双列卡片:
 * - 有图:大图(3:4 portrait aspectRatio)+ 标题 2 行 + 用户 + 学校 + 评论数
 * - 纯文字:大字体居中显示部分内容,占据图位
 *
 * 注意:瀑布流模式按用户要求不显示前两条评论。
 */
@Composable
internal fun StaggerMarketTopicCard(
    topic: MarketTopic,
    onClick: () -> Unit,
    school: String? = null
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // 图片/文字区(占图位)
            if (topic.imgs.isNotEmpty()) {
                AsyncImage(
                    model = topic.imgs.first(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 纯文字:把字调大,占图位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = topic.content.take(60).ifBlank { topic.title },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 标题 + meta
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = topic.title.takeIf { it.isNotBlank() && it != "none" }
                        ?: topic.content.take(20),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = topic.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (school != null) {
                        Text(
                            text = "· $school",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "💬 ${topic.commentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun HotTopicCard(
    topic: MarketTopic,
    rank: Int,
    onClick: () -> Unit,
    school: String? = null
) {
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
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> MarketColors.HotFlame
                            2 -> MarketColors.HotBadge
                            3 -> MarketColors.HotBadgeGold
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
                TopicMetaHeader(topic = topic, school = school)
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
internal fun MarketTopicDetailCard(
    topic: MarketTopic,
    school: String? = null,
    highlightQuery: String? = null,
    onImageClick: (String, Int) -> Unit = { _, _ -> }
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopicMetaHeader(topic = topic, school = school)
            TopicTitle(topic = topic, highlightQuery = highlightQuery)
            Text(
                text = buildSearchAnnotated(
                    topic.content.ifBlank { "无正文" },
                    highlightQuery,
                    MarketColors.SearchHighlight
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            TopicImageGrid(imgs = topic.imgs, detail = true, onImageClick = onImageClick)
            TopicFooter(topic = topic)
        }
    }
}

@Composable
private fun TopicImageGrid(
    imgs: List<String>,
    detail: Boolean,
    onImageClick: (String, Int) -> Unit = { _, _ -> }
) {
    val visible = imgs.filter { it.isNotBlank() }
    if (visible.isEmpty()) return

    val corner = AhuShapes.Card
    val singleHeight = if (detail) 260.dp else 190.dp

    // 使用 HorizontalPager 实现左右滑动
    if (visible.size > 1) {
        val pagerState = rememberPagerState(pageCount = { visible.size })
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(singleHeight)
            ) { page ->
                TopicImage(
                    url = visible[page],
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(singleHeight)
                        .clip(corner)
                        .clickable { onImageClick(visible[page], page) }
                )
            }
            if (visible.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(visible.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                        if (index < visible.size - 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    } else if (visible.size == 1) {
        TopicImage(
            url = visible.first(),
            modifier = Modifier
                .fillMaxWidth()
                .height(singleHeight)
                .clip(corner)
                .clickable { onImageClick(visible.first(), 0) }
        )
    }
}

@Composable
private fun TopicImage(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

// ═══════════════════════════════════════════════════════════
//  集市身份字段输入卡（列表页 + 设置页 + 我的页 共享）
// ═══════════════════════════════════════════════════════════

private fun relativeMarketTime(raw: String): String {
    if (raw.isBlank()) return ""
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)
    }.getOrNull() ?: return raw
    val diff = Date().time - parsed.time
    if (diff < 0) return raw
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} 分钟前"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} 小时前"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} 天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(parsed)
    }
}

/**
 * 搜索结果高亮:服务端返回的 `<em class="highlight">…</em>` 先剥离,再用客户端 query 做
 * 大小写不敏感匹配,命中段加粗+红色 ([MarketColors.SearchHighlight])。query 空白时只剥标签。
 */
internal fun buildSearchAnnotated(
    raw: String,
    query: String?,
    highlightColor: Color
): AnnotatedString {
    val stripped = raw.replace(Regex("""</?em[^>]*>""", RegexOption.IGNORE_CASE), "")
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty()) return AnnotatedString(stripped)
    val lower = stripped.lowercase()
    val n = needle.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (cursor <= stripped.length) {
            val idx = lower.indexOf(n, cursor)
            if (idx < 0) {
                append(stripped.substring(cursor))
                break
            }
            append(stripped.substring(cursor, idx))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                append(stripped.substring(idx, idx + n.length))
            }
            cursor = idx + n.length
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  可拖动悬浮按钮(DraggableFab) + 可拖动返回顶部按钮
// ═══════════════════════════════════════════════════════════

/**
 * 可拖动悬浮按钮(FAB)。
 * - 默认位置:右下角(bottom 16dp,end 16dp)
 * - 拖动:用 [pointerInput] 累积 [IntOffset]
 * - 位置:用 [rememberSaveable] 持久化(横竖屏切换后保留)
 * - 边界:约束在父容器内(0..maxX, 0..maxY)
 * - 可见性:由 `visible` 控制;false 时不渲染
 */
@Composable
fun DraggableFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val density = LocalDensity.current
    val fabSize = 56.dp
    val initialMargin = 16.dp

    // 用 rememberSaveable 持久化位置(x, y 单位 px,相对屏幕右下角偏移)
    var offsetX by rememberSaveable { mutableIntStateOf(0) }
    var offsetY by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 整体定位在右下角,然后用 offset 调整用户拖动
            .padding(end = initialMargin, bottom = initialMargin),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .offset {
                    // 负值表示「往左上方向移动」,所以保存正值表示「往右下方向」
                    IntOffset(
                        x = -offsetX,
                        y = -offsetY
                    )
                }
                .size(fabSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // 累积拖动量;限制在容器内
                        val maxX = (size.width - with(density) { fabSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        val maxY = (size.height - with(density) { fabSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        offsetX = (offsetX - drag.x.toInt()).coerceIn(-maxX, maxX)
                        offsetY = (offsetY - drag.y.toInt()).coerceIn(-maxX, maxY)
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = "发帖",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * 可拖动的"返回顶部"按钮。
 * - 默认位置:右侧,在发布按钮上方(bottom 84dp,end 16dp)
 * - 拖动:用 [pointerInput] 累积 [IntOffset]
 * - 位置:用 [rememberSaveable] 持久化
 * - 点击:滚动到顶部 + 触发刷新
 */
@Composable
internal fun DraggableScrollToTopButton(
    visible: Boolean,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val density = LocalDensity.current
    val buttonSize = 48.dp
    val initialMarginEnd = 16.dp
    val initialMarginBottom = 84.dp  // 在发布按钮上方

    // 用 rememberSaveable 持久化位置
    var offsetX by rememberSaveable { mutableIntStateOf(0) }
    var offsetY by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = initialMarginEnd, bottom = initialMarginBottom),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            onClick = onScrollToTop,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = -offsetX,
                        y = -offsetY
                    )
                }
                .size(buttonSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val maxX = (size.width - with(density) { buttonSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        val maxY = (size.height - with(density) { buttonSize.toPx() }).toInt()
                            .coerceAtLeast(0)
                        offsetX = (offsetX - drag.x.toInt()).coerceIn(-maxX, maxX)
                        offsetY = (offsetY - drag.y.toInt()).coerceIn(-maxY, maxY)
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.VerticalAlignTop,
                    contentDescription = "回到顶部",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  多图浏览:HorizontalPager + 点击黑边关闭
// ═══════════════════════════════════════════════════════════

/** 多图预览的状态。`urls` 为当前帖子所有图片,`initialIndex` 为点击时的索引。 */
