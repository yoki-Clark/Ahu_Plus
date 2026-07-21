package com.ahu_plus.data.local.module

import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.AppDataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

/**
 * SettingsModule 实现。
 *
 * ponytail: 聚合 20+ 独立 key 为数据类，一次性读写。
 * 新写入用新 JSON key，读取兼容拆分的旧 key。
 */
class SettingsModuleImpl(
    private val appDataStore: AppDataStore
) : SettingsModule {

    private val gson = GsonProvider.instance

    companion object {
        private const val TAG = "SettingsModule"

        // 新 key（JSON 聚合）
        private val THEME_SETTINGS_KEY = stringPreferencesKey("settings_theme_json")
        private val NAVIGATION_SETTINGS_KEY = stringPreferencesKey("settings_navigation_json")
        private val COURSE_REMINDER_SETTINGS_KEY = stringPreferencesKey("settings_course_reminder_json")
        private val AGENDA_SETTINGS_KEY = stringPreferencesKey("settings_agenda_json")
        private val SCHEDULE_DISPLAY_SETTINGS_KEY = stringPreferencesKey("settings_schedule_display_json")
        private val MARKET_SETTINGS_KEY = stringPreferencesKey("settings_market_json")
        private val AI_COMMENT_SETTINGS_KEY = stringPreferencesKey("settings_ai_comment_json")
        private val ELECTRICITY_SETTINGS_KEY = stringPreferencesKey("settings_electricity_json")
        private val HOME_APP_SETTINGS_KEY = stringPreferencesKey("settings_home_app_json")
        private val EVALUATION_SETTINGS_KEY = stringPreferencesKey("settings_evaluation_json")
        private val ONBOARDING_FLAGS_KEY = stringPreferencesKey("settings_onboarding_flags_json")

        // 旧 key（兼容读取）- 主题
        private val OLD_THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        // 旧 key - 导航
        private val OLD_MARKET_ENABLED_KEY = booleanPreferencesKey("market_enabled")
        private val OLD_THIRD_PARTY_SERVICES_ENABLED_KEY = booleanPreferencesKey("third_party_services_enabled")
        private val OLD_MARKET_CHILD_ENABLED_KEY = booleanPreferencesKey("market_child_enabled")
        private val OLD_CHAOXING_CHILD_ENABLED_KEY = booleanPreferencesKey("chaoxing_child_enabled")
        private val OLD_WELEARN_CHILD_ENABLED_KEY = booleanPreferencesKey("welearn_child_enabled")
        private val OLD_BOTTOM_NAV_SERVICES_KEY = stringPreferencesKey("bottom_nav_services")

        // 旧 key - 课程提醒
        private val OLD_COURSE_REMINDER_ENABLED_KEY = stringPreferencesKey("course_reminder_enabled")
        private val OLD_COURSE_REMINDER_LEAD_KEY = stringPreferencesKey("course_reminder_lead_minutes")
        private val OLD_REMINDER_KEYS_KEY = stringPreferencesKey("reminder_keys")

        // 旧 key - 日程
        private val OLD_AGENDA_SHOW_COURSES_KEY = stringPreferencesKey("agenda_show_courses")
        private val OLD_AGENDA_SHOW_EXAMS_KEY = stringPreferencesKey("agenda_show_exams")
        private val OLD_AGENDA_REMINDER_KEYS_KEY = stringPreferencesKey("agenda_reminder_keys")

        // 旧 key - 课表显示（部分，完整列表见审计报告）
        private val OLD_SCHEDULE_COL_WIDTH_KEY = floatPreferencesKey("schedule_col_width")
        private val OLD_SCHEDULE_ROW_HEIGHT_KEY = floatPreferencesKey("schedule_row_height")
        private val OLD_SHOW_SAT_KEY = booleanPreferencesKey("show_sat")
        private val OLD_SHOW_SUN_KEY = booleanPreferencesKey("show_sun")

        // 旧 key - 首页应用
        private val OLD_RECENT_APPS_KEY = stringPreferencesKey("recent_apps")
        private val OLD_FAVORITE_APP_IDS_KEY = stringPreferencesKey("favorite_app_ids")

        // 旧 key - 评教
        private val OLD_EVALUATION_COMMENT_OPTIONS_KEY = stringPreferencesKey("evaluation_comment_options")

        // 旧 key - 引导标记
        private val OLD_GUIDE_INTRO_SEEN_KEY = booleanPreferencesKey("guide_intro_seen")
        private val OLD_ANNOUNCEMENT_IGNORED_IDS_KEY = stringPreferencesKey("announcement_ignored_ids")

        // 旧 key - 集市设置
        private val OLD_MARKET_IDENTITIES_KEY = stringPreferencesKey("market_identities")
        private val OLD_MARKET_BLOCK_PINNED_KEY = stringPreferencesKey("market_block_pinned")
        private val OLD_MARKET_BLOCK_KEYWORDS_KEY = stringPreferencesKey("market_block_keywords")
        private val OLD_MARKET_LIST_LAYOUT_KEY = stringPreferencesKey("market_list_layout_mode")
        private val OLD_MARKET_SCROLL_TO_TOP_KEY = stringPreferencesKey("market_scroll_to_top")

        // 旧 key - 课表显示（剩余）
        private val OLD_SCHEDULE_FONT_SCALE_KEY = floatPreferencesKey("schedule_font_scale")
        private val OLD_SCHEDULE_PALETTE_CONFIG_KEY = stringPreferencesKey("schedule_palette_config")
        private val OLD_SCHEDULE_PAGER_ENABLED_KEY = booleanPreferencesKey("schedule_pager_enabled")
        private val OLD_SCHEDULE_RESET_ON_ENTER_KEY = booleanPreferencesKey("schedule_reset_on_enter")
        private val OLD_SHOW_OTHER_SEMESTERS_KEY = booleanPreferencesKey("show_other_semesters")

        // 旧 key - AI 评论
        private val OLD_AI_COMMENT_ENABLED_KEY = stringPreferencesKey("ai_comment_enabled")
        private val OLD_AI_COMMENT_MODEL_KEY = stringPreferencesKey("ai_comment_model")
        private val OLD_AI_COMMENT_STYLE_KEY = stringPreferencesKey("ai_comment_style")
        private val OLD_AI_COMMENT_OVERALL_PROMPT_KEY = stringPreferencesKey("ai_comment_overall_prompt")
        private val OLD_AI_COMMENT_STYLE_PROMPTS_KEY = stringPreferencesKey("ai_comment_style_prompts")
        private val OLD_AI_COMMENT_TEMPLATES_KEY = stringPreferencesKey("ai_comment_templates_json")
        private val OLD_AI_COMMENT_SELECTED_TEMPLATE_KEY = stringPreferencesKey("ai_comment_selected_template")

        // 旧 key - 电费设置
        private val OLD_AC_CONFIG_KEY = stringPreferencesKey("ac_config")
        private val OLD_LIGHTING_CONFIG_KEY = stringPreferencesKey("lighting_config")
    }

    // ── 主题 ──────────────────────────────────────────
    override suspend fun getThemeSettings(): SettingsModule.ThemeSettings {
        val prefs = appDataStore.dataStore.data.first()

        // 优先读新 key（JSON）
        prefs[THEME_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.ThemeSettings::class.java)
                ?: SettingsModule.ThemeSettings()
        }

        // 兼容读旧 key
        val oldMode = prefs[OLD_THEME_MODE_KEY] ?: "SYSTEM"

        return SettingsModule.ThemeSettings(mode = oldMode)
    }

    override suspend fun saveThemeSettings(settings: SettingsModule.ThemeSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[THEME_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 导航 ──────────────────────────────────────────
    override suspend fun getNavigationSettings(): SettingsModule.NavigationSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[NAVIGATION_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.NavigationSettings::class.java)
                ?: SettingsModule.NavigationSettings()
        }

        // 兼容读旧 key
        return SettingsModule.NavigationSettings(
            marketEnabled = prefs[OLD_MARKET_ENABLED_KEY] ?: false,
            thirdPartyServicesEnabled = prefs[OLD_THIRD_PARTY_SERVICES_ENABLED_KEY] ?: false,
            marketChildEnabled = prefs[OLD_MARKET_CHILD_ENABLED_KEY] ?: true,
            chaoxingChildEnabled = prefs[OLD_CHAOXING_CHILD_ENABLED_KEY] ?: true,
            welearnChildEnabled = prefs[OLD_WELEARN_CHILD_ENABLED_KEY] ?: true,
            bottomNavServices = prefs[OLD_BOTTOM_NAV_SERVICES_KEY]
                ?.let { gson.fromJson(it, Array<String>::class.java).toList() }
                ?: emptyList()
        )
    }

    override suspend fun saveNavigationSettings(settings: SettingsModule.NavigationSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[NAVIGATION_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 课程提醒 ──────────────────────────────────────
    override suspend fun getCourseReminderSettings(): SettingsModule.CourseReminderSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[COURSE_REMINDER_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.CourseReminderSettings::class.java)
                ?: SettingsModule.CourseReminderSettings()
        }

        // 兼容读旧 key
        val enabled = prefs[OLD_COURSE_REMINDER_ENABLED_KEY]?.toBoolean() ?: true
        val leadMinutes = prefs[OLD_COURSE_REMINDER_LEAD_KEY]?.toIntOrNull() ?: 15
        val reminderKeys = prefs[OLD_REMINDER_KEYS_KEY]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        return SettingsModule.CourseReminderSettings(
            enabled = enabled,
            leadMinutes = leadMinutes,
            reminderKeys = reminderKeys
        )
    }

    override suspend fun saveCourseReminderSettings(settings: SettingsModule.CourseReminderSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[COURSE_REMINDER_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 日程 ──────────────────────────────────────────
    override suspend fun getAgendaSettings(): SettingsModule.AgendaSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[AGENDA_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.AgendaSettings::class.java)
                ?: SettingsModule.AgendaSettings()
        }

        // 兼容读旧 key
        val showCourses = prefs[OLD_AGENDA_SHOW_COURSES_KEY]?.toBoolean() ?: true
        val showExams = prefs[OLD_AGENDA_SHOW_EXAMS_KEY]?.toBoolean() ?: true
        val reminderKeys = prefs[OLD_AGENDA_REMINDER_KEYS_KEY]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        return SettingsModule.AgendaSettings(
            showCourses = showCourses,
            showExams = showExams,
            reminderKeys = reminderKeys
        )
    }

    override suspend fun saveAgendaSettings(settings: SettingsModule.AgendaSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[AGENDA_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 课表显示 ──────────────────────────────────────
    override suspend fun getScheduleDisplaySettings(): SettingsModule.ScheduleDisplaySettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[SCHEDULE_DISPLAY_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.ScheduleDisplaySettings::class.java)
                ?: SettingsModule.ScheduleDisplaySettings()
        }

        // 兼容读旧 key（完整列表）
        return SettingsModule.ScheduleDisplaySettings(
            colWidth = prefs[OLD_SCHEDULE_COL_WIDTH_KEY] ?: 64f,
            rowHeight = prefs[OLD_SCHEDULE_ROW_HEIGHT_KEY] ?: 56f,
            fontScale = prefs[OLD_SCHEDULE_FONT_SCALE_KEY] ?: 1.0f,
            paletteConfigJson = prefs[OLD_SCHEDULE_PALETTE_CONFIG_KEY] ?: "{}",
            showSat = prefs[OLD_SHOW_SAT_KEY] ?: true,
            showSun = prefs[OLD_SHOW_SUN_KEY] ?: true,
            pagerEnabled = prefs[OLD_SCHEDULE_PAGER_ENABLED_KEY] ?: true,
            resetOnEnter = prefs[OLD_SCHEDULE_RESET_ON_ENTER_KEY] ?: true,
            showOtherSemesters = prefs[OLD_SHOW_OTHER_SEMESTERS_KEY] ?: true
        )
    }

    override suspend fun saveScheduleDisplaySettings(settings: SettingsModule.ScheduleDisplaySettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[SCHEDULE_DISPLAY_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 集市设置 ──────────────────────────────────────
    override suspend fun getMarketSettings(): SettingsModule.MarketSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[MARKET_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.MarketSettings::class.java)
                ?: SettingsModule.MarketSettings()
        }

        // 兼容读旧 key
        val identitiesJson = prefs[OLD_MARKET_IDENTITIES_KEY] ?: "[]"
        val blockPinned = prefs[OLD_MARKET_BLOCK_PINNED_KEY]?.toBoolean() ?: false
        val blockKeywords = prefs[OLD_MARKET_BLOCK_KEYWORDS_KEY]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val listLayoutMode = prefs[OLD_MARKET_LIST_LAYOUT_KEY] ?: "list"
        val scrollToTop = prefs[OLD_MARKET_SCROLL_TO_TOP_KEY]?.toBoolean() ?: true

        return SettingsModule.MarketSettings(
            identitiesJson = identitiesJson,
            blockPinned = blockPinned,
            blockKeywords = blockKeywords,
            listLayoutMode = listLayoutMode,
            scrollToTop = scrollToTop
        )
    }

    override suspend fun saveMarketSettings(settings: SettingsModule.MarketSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[MARKET_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── AI 评论设置 ──────────────────────────────────────
    override suspend fun getAiCommentSettings(): SettingsModule.AiCommentSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[AI_COMMENT_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.AiCommentSettings::class.java)
                ?: SettingsModule.AiCommentSettings()
        }

        // 兼容读旧 key
        val enabled = prefs[OLD_AI_COMMENT_ENABLED_KEY]?.toBoolean() ?: false
        val model = prefs[OLD_AI_COMMENT_MODEL_KEY] ?: "FLASH"
        val style = prefs[OLD_AI_COMMENT_STYLE_KEY] ?: "GENTLE"
        val overallPrompt = prefs[OLD_AI_COMMENT_OVERALL_PROMPT_KEY] ?: ""
        val stylePromptsJson = prefs[OLD_AI_COMMENT_STYLE_PROMPTS_KEY] ?: "{}"
        val stylePrompts = try {
            gson.fromJson(stylePromptsJson, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        val templatesJson = prefs[OLD_AI_COMMENT_TEMPLATES_KEY] ?: "[]"
        val selectedTemplateId = prefs[OLD_AI_COMMENT_SELECTED_TEMPLATE_KEY] ?: "GENTLE"

        return SettingsModule.AiCommentSettings(
            enabled = enabled,
            model = model,
            style = style,
            overallPrompt = overallPrompt,
            stylePrompts = stylePrompts,
            templatesJson = templatesJson,
            selectedTemplateId = selectedTemplateId
        )
    }

    override suspend fun saveAiCommentSettings(settings: SettingsModule.AiCommentSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[AI_COMMENT_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 电费设置 ──────────────────────────────────────
    override suspend fun getElectricitySettings(): SettingsModule.ElectricitySettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[ELECTRICITY_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.ElectricitySettings::class.java)
                ?: SettingsModule.ElectricitySettings()
        }

        // 兼容读旧 key
        val acConfigJson = prefs[OLD_AC_CONFIG_KEY] ?: "{}"
        val lightingConfigJson = prefs[OLD_LIGHTING_CONFIG_KEY] ?: "{}"

        return SettingsModule.ElectricitySettings(
            acConfigJson = acConfigJson,
            lightingConfigJson = lightingConfigJson
        )
    }

    override suspend fun saveElectricitySettings(settings: SettingsModule.ElectricitySettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[ELECTRICITY_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 首页应用 ──────────────────────────────────────
    override suspend fun getHomeAppSettings(): SettingsModule.HomeAppSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[HOME_APP_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.HomeAppSettings::class.java)
                ?: SettingsModule.HomeAppSettings()
        }

        // 兼容读旧 key
        val recentApps = prefs[OLD_RECENT_APPS_KEY]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val favoriteIds = prefs[OLD_FAVORITE_APP_IDS_KEY]
            ?.let { gson.fromJson(it, Array<String>::class.java).toList() }
            ?: emptyList()

        return SettingsModule.HomeAppSettings(
            recentApps = recentApps,
            favoriteIds = favoriteIds
        )
    }

    override suspend fun saveHomeAppSettings(settings: SettingsModule.HomeAppSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[HOME_APP_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 评教设置 ──────────────────────────────────────
    override suspend fun getEvaluationSettings(): SettingsModule.EvaluationSettings {
        val prefs = appDataStore.dataStore.data.first()

        prefs[EVALUATION_SETTINGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.EvaluationSettings::class.java)
                ?: SettingsModule.EvaluationSettings()
        }

        // 兼容读旧 key
        val commentOptions = prefs[OLD_EVALUATION_COMMENT_OPTIONS_KEY]
            ?.let { gson.fromJson(it, Array<String>::class.java).toList() }
            ?: emptyList()

        return SettingsModule.EvaluationSettings(commentOptions = commentOptions)
    }

    override suspend fun saveEvaluationSettings(settings: SettingsModule.EvaluationSettings) {
        appDataStore.dataStore.edit { prefs ->
            prefs[EVALUATION_SETTINGS_KEY] = gson.toJson(settings)
        }
    }

    // ── 引导标记 ──────────────────────────────────────
    override suspend fun getOnboardingFlags(): SettingsModule.OnboardingFlags {
        val prefs = appDataStore.dataStore.data.first()

        prefs[ONBOARDING_FLAGS_KEY]?.let { json ->
            return gson.fromJson(json, SettingsModule.OnboardingFlags::class.java)
                ?: SettingsModule.OnboardingFlags()
        }

        // 兼容读旧 key
        val guideIntroSeen = prefs[OLD_GUIDE_INTRO_SEEN_KEY] ?: false
        val announcementIgnoredIds = prefs[OLD_ANNOUNCEMENT_IGNORED_IDS_KEY]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        return SettingsModule.OnboardingFlags(
            guideIntroSeen = guideIntroSeen,
            announcementIgnoredIds = announcementIgnoredIds
        )
    }

    override suspend fun saveOnboardingFlags(flags: SettingsModule.OnboardingFlags) {
        appDataStore.dataStore.edit { prefs ->
            prefs[ONBOARDING_FLAGS_KEY] = gson.toJson(flags)
        }
    }
}
