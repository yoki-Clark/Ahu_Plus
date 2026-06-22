package com.yourname.ahu_plus.ui.widget

import android.content.Context
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.jw.CourseDisplayItem
import com.yourname.ahu_plus.data.model.jw.CourseUnit
import com.yourname.ahu_plus.data.model.jw.ScheduleData
import com.yourname.ahu_plus.data.model.jw.UserScheduleItem
import com.yourname.ahu_plus.data.model.jw.parseTimeMinutes
import com.yourname.ahu_plus.data.repository.CourseRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class TodayScheduleWidgetState(
    val status: TodayScheduleStatus,
    val title: String,
    val subtitle: String,
    val detail: String,
    val todayCount: Int,
    val updatedText: String,
    val upcoming: List<TodayScheduleWidgetCourse>,
)

internal data class TodayScheduleWidgetCourse(
    val name: String,
    val time: String,
    val room: String,
)

internal enum class TodayScheduleStatus {
    InClass,
    Next,
    Finished,
    Empty,
    NoCache,
    Error,
}

internal object TodayScheduleWidgetData {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val updateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    suspend fun load(context: Context): TodayScheduleWidgetState {
        val sessionManager = SessionManager(AppDataStore(context.applicationContext))
        sessionManager.init()

        val scheduleJson = sessionManager.getScheduleJson()
        if (scheduleJson.isNullOrBlank()) {
            return TodayScheduleWidgetState(
                status = TodayScheduleStatus.NoCache,
                title = "今日课表",
                subtitle = "还没有课表缓存",
                detail = "打开应用同步一次课表",
                todayCount = 0,
                updatedText = "",
                upcoming = emptyList(),
            )
        }

        return runCatching {
            val data = GsonProvider.instance.fromJson(scheduleJson, ScheduleData::class.java)
            val week = data.currentWeek.coerceAtLeast(1)
            val systemItems = CourseRepository.toDisplayItems(
                activities = data.activities,
                selectedWeek = week,
                getDataLessons = data.lessons,
                colorMap = CourseRepository.buildColorMap(data.activities),
            )
            val userItems = parseUserItems(sessionManager.getUserScheduleJson(), week)
            val todayWeekday = LocalDate.now().dayOfWeek.value
            val todayItems = (systemItems + userItems)
                .filter { it.weekday == todayWeekday }
                .sortedWith(compareBy({ it.startUnit }, { it.endUnit }, { it.courseName }))

            buildState(
                items = todayItems,
                unitTimes = data.unitTimes,
                updatedAt = sessionManager.getScheduleUpdatedAt(),
            )
        }.getOrElse { error ->
            TodayScheduleWidgetState(
                status = TodayScheduleStatus.Error,
                title = "今日课表",
                subtitle = "课表缓存读取失败",
                detail = error.message.orEmpty().ifBlank { "打开应用刷新后重试" },
                todayCount = 0,
                updatedText = "",
                upcoming = emptyList(),
            )
        }
    }

    private fun parseUserItems(json: String?, week: Int): List<CourseDisplayItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            GsonProvider.instance.fromJson(json, Array<UserScheduleItem>::class.java)
                .filter { week in it.weeks }
                .map { it.toDisplayItem() }
        }.getOrDefault(emptyList())
    }

    private fun buildState(
        items: List<CourseDisplayItem>,
        unitTimes: List<CourseUnit>,
        updatedAt: Long,
    ): TodayScheduleWidgetState {
        val now = com.yourname.ahu_plus.data.debug.DebugClock.nowTime()
        val current = items.firstOrNull { it.isInClass(now, unitTimes) }
        val next = items.firstOrNull { it.isFuture(now, unitTimes) }
        val focused = current ?: next
        val upcoming = items
            .filter { item ->
                val start = item.startMinutes(unitTimes) ?: return@filter true
                current == item || start >= now.toMinutes()
            }
            .take(3)
            .map { it.toWidgetCourse(unitTimes) }

        val updatedText = updatedAt.takeIf { it > 0L }?.let {
            "更新 ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(updateFormatter)}"
        }.orEmpty()

        return when {
            current != null -> {
                val end = current.endMinutes(unitTimes)
                val remaining = end?.minus(now.toMinutes())?.coerceAtLeast(0)
                TodayScheduleWidgetState(
                    status = TodayScheduleStatus.InClass,
                    title = current.courseName,
                    subtitle = "上课中 · ${current.timeText(unitTimes)}",
                    detail = listOfNotNull(
                        current.room?.takeIf { it.isNotBlank() },
                        remaining?.let { "还剩 ${it} 分钟" },
                    ).joinToString(" · "),
                    todayCount = items.size,
                    updatedText = updatedText,
                    upcoming = upcoming,
                )
            }
            next != null -> {
                val start = next.startMinutes(unitTimes)
                val minutes = start?.minus(now.toMinutes())?.coerceAtLeast(0)
                TodayScheduleWidgetState(
                    status = TodayScheduleStatus.Next,
                    title = next.courseName,
                    subtitle = "下节课 · ${next.timeText(unitTimes)}",
                    detail = listOfNotNull(
                        next.room?.takeIf { it.isNotBlank() },
                        minutes?.let { "还有 ${it.formatDuration()}" },
                    ).joinToString(" · "),
                    todayCount = items.size,
                    updatedText = updatedText,
                    upcoming = upcoming,
                )
            }
            items.isNotEmpty() -> TodayScheduleWidgetState(
                status = TodayScheduleStatus.Finished,
                title = "今日课程已结束",
                subtitle = "共 ${items.size} 门课",
                detail = "可以安心切到自己的节奏了",
                todayCount = items.size,
                updatedText = updatedText,
                upcoming = items.takeLast(3).map { it.toWidgetCourse(unitTimes) },
            )
            else -> TodayScheduleWidgetState(
                status = TodayScheduleStatus.Empty,
                title = "今天没课",
                subtitle = "今日课表空空如也",
                detail = "打开应用查看完整周课表",
                todayCount = 0,
                updatedText = updatedText,
                upcoming = emptyList(),
            )
        }
    }

    private fun CourseDisplayItem.toWidgetCourse(unitTimes: List<CourseUnit>) = TodayScheduleWidgetCourse(
        name = courseName,
        time = timeText(unitTimes),
        room = room.orEmpty(),
    )

    private fun CourseDisplayItem.timeText(unitTimes: List<CourseUnit>): String {
        val start = startTime?.takeIf { it.isNotBlank() }?.formatClock()
            ?: unitTimes.firstOrNull { it.indexNo == startUnit }?.startTimeStr()
        val end = endTime?.takeIf { it.isNotBlank() }?.formatClock()
            ?: unitTimes.firstOrNull { it.indexNo == endUnit }?.endTimeStr()
        return if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
            "$start-$end"
        } else {
            "第${startUnit}-${endUnit}节"
        }
    }

    private fun CourseDisplayItem.isInClass(now: LocalTime, unitTimes: List<CourseUnit>): Boolean {
        val start = startMinutes(unitTimes) ?: return false
        val end = endMinutes(unitTimes) ?: return false
        val nowMin = now.toMinutes()
        return nowMin in start..end
    }

    private fun CourseDisplayItem.isFuture(now: LocalTime, unitTimes: List<CourseUnit>): Boolean {
        val start = startMinutes(unitTimes) ?: return false
        return start > now.toMinutes()
    }

    private fun CourseDisplayItem.startMinutes(unitTimes: List<CourseUnit>): Int? {
        val text = startTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == startUnit }?.startTimeStr()
        return parseTimeMinutes(text)
    }

    private fun CourseDisplayItem.endMinutes(unitTimes: List<CourseUnit>): Int? {
        val text = endTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == endUnit }?.endTimeStr()
        return parseTimeMinutes(text)
    }

    private fun String.formatClock(): String {
        val parsed = parseTimeMinutes(this) ?: return this
        return LocalTime.of(parsed / 60, parsed % 60).format(timeFormatter)
    }

    private fun LocalTime.toMinutes(): Int = hour * 60 + minute

    private fun Int.formatDuration(): String = when {
        this < 60 -> "${this} 分钟"
        this % 60 == 0 -> "${this / 60} 小时"
        else -> "${this / 60} 小时 ${this % 60} 分钟"
    }
}
