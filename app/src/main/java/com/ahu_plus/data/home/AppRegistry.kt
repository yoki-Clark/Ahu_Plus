package com.ahu_plus.data.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import com.ahu_plus.ui.theme.AhuBlue
import com.ahu_plus.ui.theme.AhuGreen
import com.ahu_plus.ui.theme.AhuIndigo
import com.ahu_plus.ui.theme.AhuOrange
import com.ahu_plus.ui.theme.AhuRed
import com.ahu_plus.ui.theme.AhuTeal
import com.ahu_plus.ui.theme.AhuViolet
import com.ahu_plus.ui.theme.AhuGradient

/**
 * 静态应用注册表。
 *
 * 设计动机:首页 [AppDock] 和应用聚合页 [com.ahu_plus.ui.screen.apps.AppHubScreen]
 * 之前各自硬编码 app 列表,新增/删除/调整分组需要改两处。本注册表统一收口:
 *
 *  - **app key**:稳定字符串,用于 SessionManager.recordRecentApp() 追踪
 *  - **title/icon/color**:UI 显示元数据(无回调,便于在 widget/通知等非 Composable 上下文使用)
 *  - **group**:所属分类(学习/通知/校园卡/生活/个人信息),便于 AppHubScreen 分组渲染
 *
 * 真正的点击回调在消费方(Composable)处组装,与静态元数据解耦 —— 这样 widget /
 * 测试代码也可以查询 app 列表而无需构造 OkHttp client。
 *
 * 用法:
 *   val spec = AppRegistry.byId("schedule")
 *   val allApps = AppRegistry.all()
 *   val groupMap = AppRegistry.grouped()
 */
object AppRegistry {

    // ── App keys (稳定字符串,SessionManager.recent_apps 用这个)──────

    const val KEY_SCHEDULE = "schedule"
    const val KEY_AGENDA = "agenda"
    const val KEY_GRADE = "grade"
    const val KEY_EXAM = "exam"
    const val KEY_TRAINING_PLAN = "trainingPlan"
    const val KEY_EMPTY_CLASSROOM = "emptyClassroom"
    const val KEY_CARD = "card"
    const val KEY_CARD_ANALYTICS = "cardAnalytics"
    const val KEY_NOTICE_LIST = "noticeList"
    const val KEY_BATHROOM = "bathroom"
    const val KEY_AC = "ac"
    const val KEY_LIGHTING = "lighting"
    const val KEY_INTERNET = "internet"
    const val KEY_WEATHER = "weather"
    const val KEY_CPROG = "cprog"
    const val KEY_EVALUATION = "evaluation"
    const val KEY_STUDENT_INFO = "studentInfo"
    const val KEY_FINANCE = "finance"
    const val KEY_ATTENDANCE = "attendance"

    /** 默认显示顺序(用于 AppDock 未配置最近使用时的回退顺序) */
    private val DEFAULT_RECENT_KEYS = listOf(KEY_SCHEDULE, KEY_GRADE, KEY_EXAM)

    const val LEARNING = "学习"
    const val NOTIFICATIONS = "通知"
    const val CAMPUS_CARD = "校园卡"
    const val LIFE = "生活"
    const val PERSONAL = "个人信息"

    private val specs: List<AppSpec> = listOf(
        // 学习
        AppSpec(
            key = KEY_SCHEDULE,
            title = "课表",
            icon = Icons.Filled.CalendarMonth,
            tint = AhuBlue,
            group = LEARNING,
            gradient = AhuGradient.Blue.brush,
        ),
        AppSpec(
            key = KEY_AGENDA,
            title = "日程",
            icon = Icons.Filled.EditCalendar,
            tint = AhuTeal,
            group = LEARNING,
            gradient = AhuGradient.Blue.brush,
        ),
        AppSpec(
            key = KEY_GRADE,
            title = "成绩",
            icon = Icons.Filled.Grade,
            tint = AhuRed,
            group = LEARNING,
            gradient = AhuGradient.Violet.brush,
        ),
        AppSpec(
            key = KEY_EXAM,
            title = "考试",
            icon = Icons.AutoMirrored.Filled.EventNote,
            tint = AhuOrange,
            group = LEARNING,
            gradient = AhuGradient.Orange.brush,
        ),
        AppSpec(
            key = KEY_TRAINING_PLAN,
            title = "培养方案",
            icon = Icons.Filled.School,
            tint = Color(0xFF6C63FF),
            group = LEARNING,
            gradient = AhuGradient.Violet.brush,
        ),
        AppSpec(
            key = KEY_EMPTY_CLASSROOM,
            title = "空教室",
            icon = Icons.Filled.Room,
            tint = AhuGreen,
            group = LEARNING,
        ),
        AppSpec(
            key = KEY_CPROG,
            title = "大学计算机平台",
            icon = Icons.Filled.Computer,
            tint = AhuTeal,
            group = LEARNING,
        ),
        AppSpec(
            key = KEY_EVALUATION,
            title = "评教",
            icon = Icons.Filled.RateReview,
            tint = AhuIndigo,
            group = LEARNING,
            gradient = AhuGradient.Blue.brush,
        ),

        // 通知
        AppSpec(
            key = KEY_NOTICE_LIST,
            title = "教务通知",
            icon = Icons.Filled.Campaign,
            tint = AhuViolet,
            group = NOTIFICATIONS,
        ),

        // 校园卡
        AppSpec(
            key = KEY_CARD,
            title = "消费账单",
            icon = Icons.Filled.AccountBalanceWallet,
            tint = AhuGreen,
            group = CAMPUS_CARD,
        ),
        AppSpec(
            key = KEY_CARD_ANALYTICS,
            title = "消费分析",
            icon = Icons.Filled.Assessment,
            tint = AhuViolet,
            group = CAMPUS_CARD,
        ),
        AppSpec(
            key = KEY_BATHROOM,
            title = "浴室",
            icon = Icons.Filled.WaterDrop,
            tint = AhuTeal,
            group = LIFE,
        ),
        AppSpec(
            key = KEY_AC,
            title = "空调",
            icon = Icons.Filled.AcUnit,
            tint = AhuBlue,
            group = LIFE,
        ),
        AppSpec(
            key = KEY_LIGHTING,
            title = "照明",
            icon = Icons.Filled.Lightbulb,
            tint = AhuOrange,
            group = LIFE,
        ),
        AppSpec(
            key = KEY_INTERNET,
            title = "网费",
            icon = Icons.Filled.Wifi,
            tint = AhuIndigo,
            group = LIFE,
        ),
        AppSpec(
            key = KEY_WEATHER,
            title = "天气",
            icon = Icons.Filled.WbSunny,
            tint = AhuBlue,
            group = LIFE,
            gradient = AhuGradient.Blue.brush,
        ),

        // 个人信息
        AppSpec(
            key = KEY_STUDENT_INFO,
            title = "学生信息",
            icon = Icons.Filled.Info,
            tint = AhuBlue,
            group = PERSONAL,
        ),
        AppSpec(
            key = KEY_FINANCE,
            title = "财务汇总",
            icon = Icons.Filled.AccountBalanceWallet,
            tint = AhuGreen,
            group = PERSONAL,
        ),
        AppSpec(
            key = KEY_ATTENDANCE,
            title = "考勤记录",
            icon = Icons.Filled.EventBusy,
            tint = AhuRed,
            group = PERSONAL,
        ),
    )

    private val byKey: Map<String, AppSpec> = specs.associateBy { it.key }

    /** 全部 app 列表(按定义顺序) */
    fun all(): List<AppSpec> = specs

    /** 按 key 查 app,未找到返回 null */
    fun byId(key: String): AppSpec? = byKey[key]

    /**
     * 按 group 分组(保持注册表顺序)。返回 LinkedHashMap 保证顺序稳定。
     */
    fun grouped(): Map<String, List<AppSpec>> =
        specs.groupBy { it.group }

    /**
     * 取最近使用的 app 列表。
     *
     * @param recentKeys SessionManager.getRecentApps() 返回的 key 列表(按时间倒序)
     * @param maxCount 最多返回几个(默认 3,AppDock 用)
     * @return 若 recentKeys 不足 maxCount 个,用默认 app(课表/成绩/考试)补足
     */
    fun pickRecent(recentKeys: List<String>, maxCount: Int = 3): List<AppSpec> {
        val recent = recentKeys.mapNotNull { byKey[it] }
        if (recent.size >= maxCount) return recent.take(maxCount)
        val defaults = DEFAULT_RECENT_KEYS
            .mapNotNull { byKey[it] }
            .filter { d -> recent.none { it.key == d.key } }
        return (recent + defaults).take(maxCount)
    }
}

/**
 * 单个 app 的静态元数据。**不含点击回调**(回调在 Composable 消费方组装)。
 */
data class AppSpec(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val group: String,
    val gradient: Brush? = null,
)
