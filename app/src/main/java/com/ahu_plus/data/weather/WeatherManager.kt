package com.ahu_plus.data.weather

import android.util.Log
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jw.CourseUnit
import com.ahu_plus.data.model.jw.parseTimeMinutes
import com.ahu_plus.data.model.weather.WeatherFeed
import com.ahu_plus.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 天气管理器 - 单例 StateFlow 持有最新 WeatherFeed。
 *
 * 与 AnnouncementManager 同构 (但无弹窗队列, 仅暴露最新数据)。
 * 由 AhuPlusApplication.onCreate 启动 1h Coroutine 循环 + MainActivity 进入首页时触发。
 */
class WeatherManager(
    private val sessionManager: SessionManager,
    private val repository: WeatherRepository,
) {
    private val _feed = MutableStateFlow<WeatherFeed?>(null)
    /** 当前最新天气数据 (缓存优先, 远程成功后覆盖)。 */
    val feed: StateFlow<WeatherFeed?> = _feed.asStateFlow()

    companion object {
        private const val TAG = "WeatherMgr"

        /** PM2.5 > 该阈值视为"污染指数很高", 提醒戴口罩。 */
        const val MASK_PM25_THRESHOLD = 75.0
    }

    init {
        // 进程启动时先把缓存喂进 StateFlow, 秒显
        _feed.value = repository.getCached()
    }

    /**
     * 拉取远程并刷新 StateFlow。失败时保留旧值。
     * 返回是否成功 (UI 用于静默判断)。
     */
    suspend fun refresh(): Boolean {
        val result = repository.fetchRemote()
        result.onSuccess {
            _feed.value = repository.getCached()
            Log.i(TAG, "✓ 天气刷新成功")
        }.onFailure {
            Log.w(TAG, "天气刷新失败: ${it.message}")
        }
        return result.isSuccess
    }

    /**
     * 取某门课程的天气提醒文案。
     *
     * 规则 (用户要求):
     *  - 上课期间天气严重度 >= 2 (雨/雪) → 提醒带伞
     *  - 当前 PM2.5 > 75 → 提醒戴口罩
     *  - 上下课时间天气不一致 → 取严重方 (max severity)
     *  - 课程已结束 → 不提醒
     *  - 无天气数据 → 返回 null
     *
     * @return "带伞" / "戴口罩" / "带伞，戴口罩" / null
     */
    fun getClassWarning(course: CourseDisplayItem, unitTimes: List<CourseUnit>): String? {
        val feed = _feed.value ?: return null

        // 计算课程开始/结束的"小时" (按 unitTimes 表)
        val startText = course.startTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == course.startUnit }?.startTimeStr()
        val endText = course.endTime?.takeIf { it.isNotBlank() }
            ?: unitTimes.firstOrNull { it.indexNo == course.endUnit }?.endTimeStr()
        val startMin = parseTimeMinutes(startText) ?: return null
        val endMin = parseTimeMinutes(endText) ?: return null

        // 已结束不提醒
        val now = DebugClock.nowTime()
        val nowMin = now.hour * 60 + now.minute
        if (endMin <= nowMin) return null

        // 上下课时间的天气码 — 按整点对齐 hourly 数组
        val startHour = (startMin / 60).coerceIn(0, 23)
        val endHour = (endMin / 60).coerceIn(0, 23)

        val startCode = feed.hourly.weatherCode.getOrNull(startHour) ?: feed.current.weatherCode
        val endCode = feed.hourly.weatherCode.getOrNull(endHour) ?: feed.current.weatherCode

        val startSev = WeatherCode.severity(startCode)
        val endSev = WeatherCode.severity(endCode)
        val sev = maxOf(startSev, endSev)

        val pm25 = feed.airQuality?.current?.pm25 ?: 0.0
        val airBad = pm25 > MASK_PM25_THRESHOLD

        val parts = mutableListOf<String>()
        if (sev >= 2) parts += "带伞"
        if (airBad) parts += "戴口罩"
        return parts.takeIf { it.isNotEmpty() }?.joinToString("，")
    }

    /** 缓存年龄 (毫秒), Long.MAX_VALUE 表示无缓存。 */
    fun getCachedAgeMillis(): Long = repository.getCachedAgeMillis()

    suspend fun refreshIfStale(maxAgeMillis: Long = 30L * 60 * 1000): Boolean {
        val plan = planWeatherRefresh(
            currentFeed = _feed.value,
            cachedFeed = repository.getCached(),
            cachedAgeMillis = getCachedAgeMillis(),
            maxAgeMillis = maxAgeMillis,
        )
        if (_feed.value == null && plan.visibleFeed != null) {
            _feed.value = plan.visibleFeed
        }
        if (!plan.shouldRefreshRemote) return true
        return refresh()
    }
}

internal data class WeatherRefreshPlan(
    val visibleFeed: WeatherFeed?,
    val shouldRefreshRemote: Boolean,
)

/**
 * SessionManager restores DataStore asynchronously, so WeatherManager may be created before the
 * cached JSON becomes available. A fresh cache must still hydrate the shared feed; freshness alone
 * is not enough to skip work when both the in-memory feed and cached payload are absent.
 */
internal fun planWeatherRefresh(
    currentFeed: WeatherFeed?,
    cachedFeed: WeatherFeed?,
    cachedAgeMillis: Long,
    maxAgeMillis: Long,
): WeatherRefreshPlan {
    val visibleFeed = currentFeed ?: cachedFeed
    return WeatherRefreshPlan(
        visibleFeed = visibleFeed,
        shouldRefreshRemote = visibleFeed == null || cachedAgeMillis >= maxAgeMillis,
    )
}
