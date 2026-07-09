package com.ahu_plus.ui.screen.agenda

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

/** 单列滚轮可见行数(奇数,中间为选中行)。 */
private const val VISIBLE = 5
private val ROW_HEIGHT = 40.dp

/**
 * 通用单列滚轮。用 LazyColumn + snap fling 实现:上下各垫 (VISIBLE/2) 个空行,
 * 使任意真实项都能滚到正中;选中项由 firstVisibleItemIndex 推出。
 *
 * @param items 展示项文本
 * @param selectedIndex 当前选中下标
 * @param onSelected 停到某项时回调(滚动稳定后)
 */
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = VISIBLE / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // 外部选中项变化(如月份变导致日数改变后夹取)时同步滚动位置
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(selectedIndex)
        }
    }

    // 滚动稳定后,firstVisibleItem 即为居中的真实项
    val centered by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling && centered != selectedIndex) onSelected(centered) }
    }

    Box(modifier = modifier.height(ROW_HEIGHT * VISIBLE), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(pad) { SpacerRow() }
            itemsIndexed(items) { index: Int, label: String ->
                // 距中心越远:字号越小、颜色越淡
                val dist = kotlin.math.abs(index - centered)
                val fontSize = (20 - dist * 3).coerceAtLeast(11).sp
                val alpha = (1f - dist * 0.28f).coerceAtLeast(0.25f)
                Box(
                    modifier = Modifier.height(ROW_HEIGHT).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = fontSize,
                        fontWeight = if (dist == 0) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(pad) { SpacerRow() }
        }
    }
}

@Composable
private fun SpacerRow() {
    Box(modifier = Modifier.height(ROW_HEIGHT).fillMaxWidth())
}

/**
 * 年月日三列滚轮弹窗。年份范围 [minYear]..[maxYear];日数随年月自动夹取(闰年/大小月)。
 */
@Composable
fun DateWheelDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minYear: Int = initial.year - 1,
    maxYear: Int = initial.year + 3,
) {
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.monthValue) }
    var day by remember { mutableStateOf(initial.dayOfMonth) }

    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    if (day > daysInMonth) day = daysInMonth

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择日期") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WheelColumn(
                    items = years.map { "${it}年" },
                    selectedIndex = years.indexOf(year).coerceAtLeast(0),
                    onSelected = { year = years[it] },
                    modifier = Modifier.weight(1.2f),
                )
                WheelColumn(
                    items = (1..12).map { "${it}月" },
                    selectedIndex = month - 1,
                    onSelected = { month = it + 1 },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = (1..daysInMonth).map { "${it}日" },
                    selectedIndex = day - 1,
                    onSelected = { day = it + 1 },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth())))
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 时分两列滚轮弹窗(24 小时制,分钟步进 [minuteStep])。
 */
@Composable
fun TimeWheelDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    minuteStep: Int = 1,
) {
    val minutes = remember(minuteStep) { (0 until 60 step minuteStep).toList() }
    var hour by remember { mutableStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableStateOf(nearestMinuteIndex(initialMinute, minutes)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelColumn(
                    items = (0..23).map { "%02d".format(it) },
                    selectedIndex = hour,
                    onSelected = { hour = it },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = minutes.map { "%02d".format(it) },
                    selectedIndex = minute,
                    onSelected = { minute = it },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minutes[minute]) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun nearestMinuteIndex(minute: Int, options: List<Int>): Int {
    val idx = options.indexOfFirst { it >= minute }
    return if (idx < 0) options.lastIndex else idx
}
