package com.ahu_plus.ui.screen.main

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector
import com.ahu_plus.ui.navigation.HomeRoute
import com.ahu_plus.ui.navigation.TopLevelDestination

internal const val TAB_HOME = 0
internal const val TAB_MARKET = 1
internal const val TAB_CHAOXING = 2
internal const val TAB_WELEARN = 3
internal const val TAB_APPS = 4
internal const val TAB_PROFILE = 5
/**
 * 教育邮箱 Tab(隐藏 Tab,不在底栏显示;通过应用聚合页入口卡片进入)。
 * 保留独立 Tab 是为了导航栈隔离——邮件列表/详情不与 Apps 栈混在一起。
 */
internal const val TAB_EMAIL = 6

internal data class TopLevelNavItem(
    val tab: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

internal const val HOME_DASHBOARD = 0
internal const val HOME_SCHEDULE = 1
internal const val HOME_NOTICE_LIST = 2
internal const val HOME_GRADE = 3
internal const val HOME_EXAM = 4
internal const val HOME_BILLS = 5
internal const val HOME_TRAINING_PLAN = 6
internal const val HOME_EMPTY_CLASSROOM = 7
internal const val HOME_WEATHER = 9
internal const val HOME_AGENDA = 10

internal fun TopLevelDestination.toLegacyTab(): Int = when (this) {
    TopLevelDestination.HOME -> TAB_HOME
    TopLevelDestination.MARKET -> TAB_MARKET
    TopLevelDestination.CHAOXING -> TAB_CHAOXING
    TopLevelDestination.WELEARN -> TAB_WELEARN
    TopLevelDestination.APPS -> TAB_APPS
    TopLevelDestination.EMAIL -> TAB_EMAIL
    TopLevelDestination.PROFILE -> TAB_PROFILE
}

internal fun Int.toTopLevelDestination(): TopLevelDestination = when (this) {
    TAB_MARKET -> TopLevelDestination.MARKET
    TAB_CHAOXING -> TopLevelDestination.CHAOXING
    TAB_WELEARN -> TopLevelDestination.WELEARN
    TAB_APPS -> TopLevelDestination.APPS
    TAB_EMAIL -> TopLevelDestination.EMAIL
    TAB_PROFILE -> TopLevelDestination.PROFILE
    else -> TopLevelDestination.HOME
}

internal fun HomeRoute.toLegacyPage(): Int = when (this) {
    HomeRoute.DASHBOARD -> HOME_DASHBOARD
    HomeRoute.SCHEDULE -> HOME_SCHEDULE
    HomeRoute.NOTICES -> HOME_NOTICE_LIST
    HomeRoute.GRADE -> HOME_GRADE
    HomeRoute.EXAM -> HOME_EXAM
    HomeRoute.BILLS -> HOME_BILLS
    HomeRoute.TRAINING_PLAN -> HOME_TRAINING_PLAN
    HomeRoute.EMPTY_CLASSROOM -> HOME_EMPTY_CLASSROOM
    HomeRoute.WEATHER -> HOME_WEATHER
    HomeRoute.AGENDA -> HOME_AGENDA
}

internal fun Int.toHomeRoute(): HomeRoute = when (this) {
    HOME_SCHEDULE -> HomeRoute.SCHEDULE
    HOME_NOTICE_LIST -> HomeRoute.NOTICES
    HOME_GRADE -> HomeRoute.GRADE
    HOME_EXAM -> HomeRoute.EXAM
    HOME_BILLS -> HomeRoute.BILLS
    HOME_TRAINING_PLAN -> HomeRoute.TRAINING_PLAN
    HOME_EMPTY_CLASSROOM -> HomeRoute.EMPTY_CLASSROOM
    HOME_WEATHER -> HomeRoute.WEATHER
    HOME_AGENDA -> HomeRoute.AGENDA
    else -> HomeRoute.DASHBOARD
}

/** WeLearn 内部三段式导航 (2026-06-28 新增 CourseDetailScreen) */
internal sealed class WeLearnNav {
    object Main : WeLearnNav()
    data class Detail(val course: com.ahu_plus.data.model.WeLearnCourse) : WeLearnNav()
    data class Study(val course: com.ahu_plus.data.model.WeLearnCourse, val unitFilter: IntArray? = null) : WeLearnNav()
}

/**
 * 2026-07-06 P0: WeLearnNav 的 Saver — 让 `welearnScreen` 跨 Tab/分支剔除恢复。
 *
 * 存 [typeInt, cid, unitFilterList?];Detail/Study 恢复时 course 字段只回填 cid,name/per
 * 暂时为空,等用户下拉或 vm.loadCourseTree 完成后续数据补全。这是进程死亡后的已知 trade-off,
 * P0 不做 VM 加 `getCourseByCid` 同步 course 元信息(改动 4 个文件,延后 P1)。
 */
internal val WeLearnNavSaver: Saver<WeLearnNav, List<Any?>> = Saver(
    save = { nav ->
        when (nav) {
            WeLearnNav.Main -> listOf(0)
            is WeLearnNav.Detail -> listOf(1, nav.course.cid)
            is WeLearnNav.Study -> listOf(2, nav.course.cid, nav.unitFilter?.toList())
        }
    },
    restore = { saved ->
        val cid = saved.getOrNull(1) as? String ?: ""
        when (saved.getOrNull(0) as? Int) {
            0 -> WeLearnNav.Main
            1 -> WeLearnNav.Detail(
                com.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0)
            )
            2 -> WeLearnNav.Study(
                com.ahu_plus.data.model.WeLearnCourse(cid = cid, name = "", per = 0),
                (saved.getOrNull(2) as? List<*>)?.filterIsInstance<Int>()?.toIntArray()
            )
            else -> WeLearnNav.Main
        }
    }
)
