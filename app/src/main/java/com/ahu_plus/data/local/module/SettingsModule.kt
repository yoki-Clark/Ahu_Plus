package com.ahu_plus.data.local.module

import kotlinx.coroutines.flow.Flow

/**
 * 设置模块：管理所有用户可配置项。
 *
 * ponytail: 20+ 独立 key 聚合为数据类，一次性读写，避免 SessionManager 碎片化。
 */
interface SettingsModule {

    // ── 主题与外观 ──────────────────────────────────────
    data class ThemeSettings(
        val mode: String = "SYSTEM"  // "LIGHT" / "DARK" / "SYSTEM"
    )

    suspend fun getThemeSettings(): ThemeSettings
    suspend fun saveThemeSettings(settings: ThemeSettings)

    // ── 导航与功能开关 ──────────────────────────────────
    data class NavigationSettings(
        val marketEnabled: Boolean = false,
        val thirdPartyServicesEnabled: Boolean = false,
        val marketChildEnabled: Boolean = true,
        val chaoxingChildEnabled: Boolean = true,
        val welearnChildEnabled: Boolean = true,
        val bottomNavServices: List<String> = emptyList()  // 最多 2 个
    )

    suspend fun getNavigationSettings(): NavigationSettings
    suspend fun saveNavigationSettings(settings: NavigationSettings)

    // ── 课程提醒 ──────────────────────────────────────
    data class CourseReminderSettings(
        val enabled: Boolean = true,
        val leadMinutes: Int = 15,
        val reminderKeys: Set<String> = emptySet()  // 已注册的提醒 key
    )

    suspend fun getCourseReminderSettings(): CourseReminderSettings
    suspend fun saveCourseReminderSettings(settings: CourseReminderSettings)

    // ── 日程设置 ──────────────────────────────────────
    data class AgendaSettings(
        val showCourses: Boolean = true,
        val showExams: Boolean = true,
        val reminderKeys: Set<String> = emptySet()  // 日程提醒 key
    )

    suspend fun getAgendaSettings(): AgendaSettings
    suspend fun saveAgendaSettings(settings: AgendaSettings)

    // ── 课表显示 ──────────────────────────────────────
    data class ScheduleDisplaySettings(
        val colWidth: Float = 64f,
        val rowHeight: Float = 56f,
        val fontScale: Float = 1.0f,
        val paletteConfigJson: String = "{}",  // JSON 序列化的 SchedulePaletteConfig
        val showSat: Boolean = true,
        val showSun: Boolean = true,
        val pagerEnabled: Boolean = true,
        val resetOnEnter: Boolean = true,
        val showOtherSemesters: Boolean = true
    )

    suspend fun getScheduleDisplaySettings(): ScheduleDisplaySettings
    suspend fun saveScheduleDisplaySettings(settings: ScheduleDisplaySettings)

    // ── 集市设置 ──────────────────────────────────────
    data class MarketSettings(
        val identitiesJson: String = "[]",  // JSON 序列化的 List<MarketIdentity>
        val selectedIdentityIds: Set<String> = emptySet(),
        val blockPinned: Boolean = false,
        val blockKeywords: List<String> = emptyList(),
        val filterNodeIds: List<Long> = emptyList(),
        val listLayoutMode: String = "list",  // "list" / "stagger"
        val scrollToTop: Boolean = true
    )

    suspend fun getMarketSettings(): MarketSettings
    suspend fun saveMarketSettings(settings: MarketSettings)

    // ── AI 评论设置 ──────────────────────────────────────
    data class AiCommentSettings(
        val enabled: Boolean = false,
        val model: String = "FLASH",  // AiCommentModel enum name
        val style: String = "GENTLE",  // AiCommentStyle enum name
        val overallPrompt: String = "",
        val stylePrompts: Map<String, String> = emptyMap(),
        val templatesJson: String = "[]",  // JSON 序列化的 List<AiCommentTemplate>
        val selectedTemplateId: String = "GENTLE"
    )

    suspend fun getAiCommentSettings(): AiCommentSettings
    suspend fun saveAiCommentSettings(settings: AiCommentSettings)

    // ── 电费/空调/照明配置 ──────────────────────────────
    data class ElectricitySettings(
        val acConfigJson: String = "{}",  // JSON 序列化的 ElectricityRoomConfig
        val lightingConfigJson: String = "{}",
        val newCampusConfigJson: String = "{}"
    )

    suspend fun getElectricitySettings(): ElectricitySettings
    suspend fun saveElectricitySettings(settings: ElectricitySettings)

    // ── 首页应用 ──────────────────────────────────────
    data class HomeAppSettings(
        val recentApps: List<String> = emptyList(),  // 最多 5 个
        val favoriteIds: List<String> = emptyList()   // 最多 6 个，退登保留
    )

    suspend fun getHomeAppSettings(): HomeAppSettings
    suspend fun saveHomeAppSettings(settings: HomeAppSettings)

    // ── 评教设置 ──────────────────────────────────────
    data class EvaluationSettings(
        val commentOptions: List<String> = emptyList()  // 用户自定义评语选项，退登保留
    )

    suspend fun getEvaluationSettings(): EvaluationSettings
    suspend fun saveEvaluationSettings(settings: EvaluationSettings)

    // ── 一次性标记（退登保留）──────────────────────────
    data class OnboardingFlags(
        val guideIntroSeen: Boolean = false,
        val announcementIgnoredIds: Set<String> = emptySet()
    )

    suspend fun getOnboardingFlags(): OnboardingFlags
    suspend fun saveOnboardingFlags(flags: OnboardingFlags)
}
