package com.yourname.ahu_plus.data.model.agenda

import java.time.LocalDate

/**
 * 日程事件的来源类型。
 *
 * COURSE/EXAM 由课表/考试缓存派生(只读,不可勾选/删除);
 * HOMEWORK/CUSTOM 是可操作项(可勾选完成、可删除)。
 */
enum class AgendaSource { COURSE, EXAM, HOMEWORK, CUSTOM }

/**
 * 日程页统一展示模型。
 *
 * 由 [com.yourname.ahu_plus.data.agenda.AgendaBuilder] 把课表/考试展开成带**具体日历日**
 * 的事件,再与作业([com.yourname.ahu_plus.data.model.task.HomeworkRecord])、手动日程
 * ([com.yourname.ahu_plus.data.model.task.UserTask]) 合并,按 [date] 分组后交给 UI 渲染。
 *
 * @property id 全局唯一 ID。格式:"course:<name>|<date>|<startUnit>" / "exam:<id>" /
 *              "hw:<id>" / "task:<uuid>"。用于 LazyColumn key。
 * @property source 来源(决定是否可勾选/删除)
 * @property date 事件发生的具体日期
 * @property title 主标题
 * @property location 地点(教室/考场,可空)
 * @property startMinutes 当天开始分钟数(0..1439);null = 全天/仅截止(如作业 deadline)
 * @property endMinutes 当天结束分钟数;null = 无明确结束
 * @property colorIndex 着色索引(课程复用课表配色)
 * @property completed 是否已完成(仅 HOMEWORK/CUSTOM 有意义)
 * @property sourceId 路由回源用的原始 ID(勾选/删除时 removePrefix 后传给对应 Repository)
 */
data class AgendaEvent(
    val id: String,
    val source: AgendaSource,
    val date: LocalDate,
    val title: String,
    val location: String? = null,
    val startMinutes: Int? = null,
    val endMinutes: Int? = null,
    val colorIndex: Int = 0,
    val completed: Boolean = false,
    val sourceId: String? = null,
) {
    /** 可勾选完成的仅限用户可操作项。 */
    val isCheckable: Boolean get() = source == AgendaSource.HOMEWORK || source == AgendaSource.CUSTOM

    /** "HH:mm" 起始时刻;全天/仅截止返回 null。 */
    fun startClock(): String? = startMinutes?.let { "%02d:%02d".format(it / 60, it % 60) }

    fun endClock(): String? = endMinutes?.let { "%02d:%02d".format(it / 60, it % 60) }
}
