package com.ahu_plus.ui.screen.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.agenda.AgendaBuilder
import com.ahu_plus.data.calendar.SystemCalendarSync
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.agenda.AgendaEvent
import com.ahu_plus.data.model.agenda.AgendaSource
import com.ahu_plus.data.model.jw.Exam
import com.ahu_plus.data.model.jw.ScheduleData
import com.ahu_plus.data.model.task.HomeworkRecord
import com.ahu_plus.data.model.task.UserTask
import com.ahu_plus.data.repository.HomeworkRepository
import com.ahu_plus.data.repository.UserTaskRepository
import com.ahu_plus.notification.AgendaReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程页 ViewModel。合并四源事件 → 按 [LocalDate] 分组:
 *  - 课程(COURSE):从课表缓存 JSON 展开(受"课程自动入日程"开关控制)
 *  - 考试(EXAM):从考试缓存 JSON 展开(受"考试自动入日程"开关控制)
 *  - 作业(HOMEWORK):[HomeworkRepository] flow(截止日为事件日)
 *  - 手动日程(CUSTOM):[UserTaskRepository] flow
 *
 * 课程/考试是缓存快照(非 flow),用 [refreshTrigger] 驱动重算;作业/手动日程是 flow。
 */
class AgendaViewModel(
    application: Application,
    private val sessionManager: SessionManager,
    private val userTaskRepository: UserTaskRepository,
    private val homeworkRepository: HomeworkRepository,
) : AndroidViewModel(application) {

    private val gson = GsonProvider.instance
    private val zone: ZoneId = ZoneId.systemDefault()

    /** 展开窗口:今天起 [RANGE_DAYS] 天。 */
    private val today: LocalDate get() = DebugClock.todayDate()

    /** 缓存快照重算触发器(改开关 / 手动刷新时 bump)。 */
    private val refreshTrigger = MutableStateFlow(0)

    private val _selectedDate = MutableStateFlow(DebugClock.todayDate())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _showCourses = MutableStateFlow(sessionManager.getAgendaShowCourses())
    val showCourses: StateFlow<Boolean> = _showCourses.asStateFlow()

    private val _showExams = MutableStateFlow(sessionManager.getAgendaShowExams())
    val showExams: StateFlow<Boolean> = _showExams.asStateFlow()

    private val _calendarSyncState = MutableStateFlow(CalendarSyncUiState())
    val calendarSyncState: StateFlow<CalendarSyncUiState> = _calendarSyncState.asStateFlow()

    /**
     * 所有事件按日期分组,组内按开始时间排序(全天/无时间的排最前)。
     * 合并 tasks + homework 两个 flow,叠加课程/考试快照(由 trigger 驱动重算)。
     */
    val eventsByDate: StateFlow<Map<LocalDate, List<AgendaEvent>>> =
        combine(
            userTaskRepository.tasks,
            homeworkRepository.homework,
            refreshTrigger,
            _showCourses,
            _showExams,
        ) { tasks, homework, _, showCourses, showExams ->
            buildEvents(tasks, homework, showCourses, showExams)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun buildEvents(
        tasks: List<UserTask>,
        homework: List<HomeworkRecord>,
        showCourses: Boolean,
        showExams: Boolean,
    ): Map<LocalDate, List<AgendaEvent>> {
        // 往前也展开:月视图可翻看历史,上周/更早的课程与手动日程都要能显示
        val from = today.minusDays(PAST_DAYS)
        val to = today.plusDays(RANGE_DAYS)
        val events = ArrayList<AgendaEvent>()

        if (showCourses) {
            loadScheduleData()?.let { data ->
                events += AgendaBuilder.expandCourses(data, today, from, to)
            }
        }
        if (showExams) {
            events += AgendaBuilder.expandExams(loadExams(), from, to)
        }
        homework.forEach { hw -> homeworkEvent(hw, from, to)?.let { events += it } }
        tasks.forEach { t -> customEvent(t, from, to)?.let { events += it } }

        return events
            .groupBy { it.date }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<AgendaEvent> { it.startMinutes == null } // 全天在前
                        .thenBy { it.startMinutes ?: 0 }
                        .thenBy { it.title }
                )
            }
    }

    private fun homeworkEvent(hw: HomeworkRecord, from: LocalDate, to: LocalDate): AgendaEvent? {
        val deadline = hw.deadline ?: return null
        val dt = Instant.ofEpochMilli(deadline).atZone(zone)
        val date = dt.toLocalDate()
        if (date.isBefore(from) || date.isAfter(to)) return null
        return AgendaEvent(
            id = "hw:${hw.id}",
            source = AgendaSource.HOMEWORK,
            date = date,
            title = "${hw.courseName.ifBlank { "作业" }} · ${hw.title}",
            location = hw.notes?.takeIf { it.isNotBlank() },
            startMinutes = dt.hour * 60 + dt.minute,
            completed = hw.completed,
            sourceId = hw.id,
        )
    }

    private fun customEvent(t: UserTask, from: LocalDate, to: LocalDate): AgendaEvent? {
        val start = t.dueAt ?: return null
        val startDt = Instant.ofEpochMilli(start).atZone(zone)
        val date = startDt.toLocalDate()
        if (date.isBefore(from) || date.isAfter(to)) return null
        val endMin = t.endAt?.let {
            val e = Instant.ofEpochMilli(it).atZone(zone)
            if (e.toLocalDate() == date) e.hour * 60 + e.minute else null
        }
        return AgendaEvent(
            id = "task:${t.id}",
            source = AgendaSource.CUSTOM,
            date = date,
            title = t.title,
            location = t.location?.takeIf { it.isNotBlank() } ?: t.subtitle?.takeIf { it.isNotBlank() },
            startMinutes = if (t.allDay) null else startDt.hour * 60 + startDt.minute,
            endMinutes = if (t.allDay) null else endMin,
            completed = t.completed,
            sourceId = t.id,
        )
    }

    // ── 操作 ─────────────────────────────────────────────

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setShowCourses(v: Boolean) {
        _showCourses.value = v
        viewModelScope.launch { sessionManager.saveAgendaShowCourses(v) }
    }

    fun setShowExams(v: Boolean) {
        _showExams.value = v
        viewModelScope.launch { sessionManager.saveAgendaShowExams(v) }
    }

    /** 重新读课程/考试缓存快照(进入页面 / 手动刷新时调用)。 */
    fun refresh() {
        refreshTrigger.value += 1
    }

    fun syncSystemCalendar() {
        if (_calendarSyncState.value.isSyncing) return
        viewModelScope.launch {
            _calendarSyncState.value = CalendarSyncUiState(isSyncing = true)
            runCatching {
                val events = buildSystemCalendarEvents(_showCourses.value, _showExams.value)
                withContext(Dispatchers.IO) {
                    SystemCalendarSync(getApplication()).sync(events)
                }
            }.onSuccess { result ->
                _calendarSyncState.value = CalendarSyncUiState(
                    message = "已同步到${result.calendarName}：新增 ${result.inserted}，更新 ${result.updated}，移除 ${result.removed}",
                )
            }.onFailure { error ->
                _calendarSyncState.value = CalendarSyncUiState(
                    message = error.message ?: "系统日历同步失败",
                    isError = true,
                )
            }
        }
    }

    private fun buildSystemCalendarEvents(
        includeCourses: Boolean,
        includeExams: Boolean,
    ): List<AgendaEvent> {
        val from = today.minusDays(PAST_DAYS)
        val to = today.plusDays(RANGE_DAYS)
        return buildList {
            if (includeCourses) {
                loadScheduleData()?.let { addAll(AgendaBuilder.expandCourses(it, today, from, to)) }
            }
            if (includeExams) addAll(AgendaBuilder.expandExams(loadExams(), from, to))
        }
    }

    /** 按 sourceId 反查手动日程原始 [UserTask](编辑时预填 sheet 用)。 */
    fun findTask(id: String): UserTask? =
        userTaskRepository.tasksSnapshot().firstOrNull { it.id == id }

    /** 新增或编辑手动日程,并重排提醒。 */
    fun upsertEvent(task: UserTask) {
        viewModelScope.launch {
            userTaskRepository.upsert(task)
            AgendaReminderScheduler.scheduleAll(getApplication())
        }
    }

    /** 勾选/取消完成(仅作业 / 手动日程)。 */
    fun toggleComplete(event: AgendaEvent) {
        val id = event.sourceId ?: return
        viewModelScope.launch {
            when (event.source) {
                AgendaSource.CUSTOM -> {
                    userTaskRepository.setCompleted(id, !event.completed)
                    AgendaReminderScheduler.scheduleAll(getApplication())
                }
                AgendaSource.HOMEWORK -> homeworkRepository.setCompleted(id, !event.completed)
                else -> Unit
            }
        }
    }

    /** 删除(仅作业 / 手动日程)。 */
    fun deleteEvent(event: AgendaEvent) {
        val id = event.sourceId ?: return
        viewModelScope.launch {
            when (event.source) {
                AgendaSource.CUSTOM -> {
                    userTaskRepository.delete(id)
                    AgendaReminderScheduler.scheduleAll(getApplication())
                }
                AgendaSource.HOMEWORK -> homeworkRepository.delete(id)
                else -> Unit
            }
        }
    }

    private fun loadScheduleData(): ScheduleData? {
        val json = sessionManager.getScheduleJson() ?: return null
        return runCatching { gson.fromJson(json, ScheduleData::class.java) }.getOrNull()
    }

    private fun loadExams(): List<Exam> {
        val json = sessionManager.getExamsJson() ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<Exam>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val RANGE_DAYS = 90L   // 今天往后
        const val PAST_DAYS = 120L   // 今天往前(约一学期,够翻看历史课程)
    }
}

data class CalendarSyncUiState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)
