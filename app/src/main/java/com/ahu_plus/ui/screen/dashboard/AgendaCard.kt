package com.ahu_plus.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.agenda.AgendaEvent
import com.ahu_plus.data.model.agenda.AgendaSource
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.CourseColors
import java.time.LocalDate

/**
 * 首页"日程"卡片(取代旧的"近期任务"卡片)。
 *
 * 显示今天起最近的若干条事件(跨日,跳过空日),整卡点击进日程页;右上"+"直接添加日程。
 *
 * @param eventsByDate 来自 AgendaViewModel 的分组事件
 * @param onOpenAgenda 打开日程页
 * @param onAdd 添加日程(打开添加 sheet)
 */
@Composable
fun AgendaCard(
    eventsByDate: Map<LocalDate, List<AgendaEvent>>,
    onOpenAgenda: () -> Unit,
    onAdd: () -> Unit,
) {
    val today = DebugClock.todayDate()
    // 今天 → 本周日(周一为周首);若今天就是周日则至少展示今天
    val weekEnd = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val inWeek = eventsByDate
        .filterKeys { !it.isBefore(today) && !it.isAfter(weekEnd) }
        .toSortedMap()
        .flatMap { (date, events) -> events.map { date to it } }

    Card(
        modifier = Modifier.fillMaxWidth().clip(AhuShapes.LargeCard).clickable(onClick = onOpenAgenda),
        shape = AhuShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = " 本周日程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "添加日程", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (inWeek.isEmpty()) {
                Text(
                    text = "本周暂无安排。点右上 + 添加日程,课程和考试也会自动出现在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                // 卡片内可下滑显示本周其余日程;点行/滚动由 verticalScroll 消费
                Column(
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    inWeek.forEach { (date, event) ->
                        AgendaCardRow(date = date, today = today, event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaCardRow(date: LocalDate, today: LocalDate, event: AgendaEvent) {
    val accent = when (event.source) {
        AgendaSource.COURSE -> CourseColors[event.colorIndex % CourseColors.size]
        AgendaSource.EXAM -> MaterialTheme.colorScheme.error
        AgendaSource.HOMEWORK -> MaterialTheme.colorScheme.tertiary
        AgendaSource.CUSTOM -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenLabel(date, today) + (event.startClock()?.let { " · $it" } ?: " · 全天"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun whenLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    today.plusDays(2) -> "后天"
    else -> "${date.monthValue}-${date.dayOfMonth}"
}
