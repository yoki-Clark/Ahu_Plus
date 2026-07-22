package com.ahu_plus.ui.navigation

enum class TopLevelDestination {
    HOME,
    MARKET,
    CHAOXING,
    WELEARN,
    APPS,
    PROFILE,
}

enum class NavigationSource {
    INTERNAL,
    TOP_LEVEL,
    NOTIFICATION,
    WIDGET,
    DEEP_LINK,
    RECENT_APP,
    SERVICE,
}

enum class NavigationLaunchMode {
    SINGLE_TOP,
}

sealed interface NavigationTarget {
    val topLevel: TopLevelDestination
}

enum class HomeRoute {
    DASHBOARD,
    SCHEDULE,
    AGENDA,
    GRADE,
    EXAM,
    BILLS,
    NOTICES,
    TRAINING_PLAN,
    EMPTY_CLASSROOM,
    WEATHER,
}

data class HomeTarget(val route: HomeRoute) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.HOME
}

enum class MarketRoute { ROOT, TOPIC, COMPOSE, SETTINGS, HOT_TOPICS, NOTICES }

data class MarketTarget(
    val route: MarketRoute = MarketRoute.ROOT,
    val topicId: String? = null,
) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.MARKET
}

enum class ChaoxingRoute { ROOT, COURSE, HOMEWORK, STUDY }

data class ChaoxingTarget(
    val route: ChaoxingRoute = ChaoxingRoute.ROOT,
    val subTab: String? = null,
    val entityId: String? = null,
) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.CHAOXING
}

enum class WeLearnRoute { ROOT, COURSE, STUDY }

data class WeLearnTarget(
    val route: WeLearnRoute = WeLearnRoute.ROOT,
    val courseId: String? = null,
    val unitIds: List<Int> = emptyList(),
) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.WELEARN
}

enum class AppsRoute { ROOT, APP, EVALUATION_DETAIL }

data class AppsTarget(
    val route: AppsRoute = AppsRoute.ROOT,
    val appKey: String? = null,
    val entityId: String? = null,
) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.APPS
}

enum class ProfileRoute {
    ROOT,
    BILLS,
    CARD_ANALYTICS,
    UTILITY,
    MY_INFO,
    FINANCE,
    SETTINGS,
    CACHE_CLEANUP,
    XZXX,
    GUIDE,
    FAQ,
    ANNOUNCEMENTS,
    LICENSES,
    ABOUT,
}

data class ProfileTarget(
    val route: ProfileRoute = ProfileRoute.ROOT,
    val utility: String? = null,
) : NavigationTarget {
    override val topLevel: TopLevelDestination = TopLevelDestination.PROFILE
}

data class NavigationRequest(
    val target: NavigationTarget,
    val source: NavigationSource = NavigationSource.INTERNAL,
    val launchMode: NavigationLaunchMode = NavigationLaunchMode.SINGLE_TOP,
)

internal fun rootTarget(topLevel: TopLevelDestination): NavigationTarget = when (topLevel) {
    TopLevelDestination.HOME -> HomeTarget(HomeRoute.DASHBOARD)
    TopLevelDestination.MARKET -> MarketTarget()
    TopLevelDestination.CHAOXING -> ChaoxingTarget()
    TopLevelDestination.WELEARN -> WeLearnTarget()
    TopLevelDestination.APPS -> AppsTarget()
    TopLevelDestination.PROFILE -> ProfileTarget()
}

