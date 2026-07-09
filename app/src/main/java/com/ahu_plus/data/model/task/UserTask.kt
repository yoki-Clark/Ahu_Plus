package com.ahu_plus.data.model.task

/**
 * 用户自定义待办 / 手动日程 (与课程/作业无关的纯个人事项)。
 *
 * 2026-07-06:承载"日程"语义,新增 [endAt]/[allDay]/[location]/[reminderMinutes]。
 * 均可空 → Gson 反序列化旧数据(无这些字段)时自动取默认值,向后兼容。
 *
 * 时间语义:[dueAt] 作事件**开始**时刻;[endAt] 为结束时刻(可空)。
 *
 * @property id 唯一 ID (UUID)
 * @property title 标题
 * @property subtitle 副标题/说明/备注
 * @property dueAt 开始时间 (epoch millis,可空;全天日程可只给日期 0 点)
 * @property completed 是否已完成
 * @property completedAt 完成时间
 * @property createdAt 创建时间
 * @property endAt 结束时间 (epoch millis,可空)
 * @property allDay 是否全天日程
 * @property location 地点 (可空)
 * @property reminderMinutes 提前多少分钟提醒;null = 不提醒
 */
data class UserTask(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val dueAt: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val endAt: Long? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val reminderMinutes: Int? = null,
)
