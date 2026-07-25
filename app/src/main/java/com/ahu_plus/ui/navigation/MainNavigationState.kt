package com.ahu_plus.ui.navigation

import com.ahu_plus.data.GsonProvider

data class MainNavigationState(
    val activeTopLevel: TopLevelDestination,
    val stacks: Map<TopLevelDestination, List<NavigationTarget>>,
    val topLevelHistory: List<TopLevelDestination> = emptyList(),
) {
    val currentTarget: NavigationTarget
        get() = stacks.getValue(activeTopLevel).last()

    fun selectTopLevel(destination: TopLevelDestination): MainNavigationState {
        if (destination == activeTopLevel) {
            return copy(
                stacks = stacks + (destination to listOf(rootTarget(destination))),
                topLevelHistory = emptyList(),
            )
        }
        return copy(activeTopLevel = destination, topLevelHistory = emptyList()).normalized()
    }

    fun navigate(request: NavigationRequest): MainNavigationState {
        val destination = request.target.topLevel
        val currentStack = stacks.getValue(destination)
        val nextStack = if (
            request.launchMode == NavigationLaunchMode.SINGLE_TOP &&
            currentStack.lastOrNull() == request.target
        ) {
            currentStack
        } else {
            currentStack + request.target
        }
        val tracksOrigin = destination != activeTopLevel && request.source != NavigationSource.TOP_LEVEL
        val history = if (tracksOrigin) {
            (topLevelHistory + activeTopLevel).fold(emptyList()) { acc, item ->
                if (acc.lastOrNull() == item) acc else acc + item
            }
        } else {
            topLevelHistory
        }
        return copy(
            activeTopLevel = destination,
            stacks = stacks + (destination to nextStack),
            topLevelHistory = history,
        ).normalized()
    }

    fun back(): BackResult {
        val currentStack = stacks.getValue(activeTopLevel)
        if (currentStack.size > 1) {
            return BackResult.Handled(
                copy(stacks = stacks + (activeTopLevel to currentStack.dropLast(1))).normalized()
            )
        }
        val previous = topLevelHistory.lastOrNull() ?: return BackResult.AtRoot(this)
        return BackResult.Handled(
            copy(
                activeTopLevel = previous,
                topLevelHistory = topLevelHistory.dropLast(1),
            ).normalized()
        )
    }

    fun disable(destination: TopLevelDestination): MainNavigationState {
        if (destination == TopLevelDestination.HOME) return this
        val resetStacks = stacks + (destination to listOf(rootTarget(destination)))
        val history = topLevelHistory.filterNot { it == destination }
        return if (activeTopLevel == destination) {
            copy(
                activeTopLevel = TopLevelDestination.HOME,
                stacks = resetStacks + (
                    TopLevelDestination.HOME to listOf(rootTarget(TopLevelDestination.HOME))
                ),
                topLevelHistory = history,
            ).normalized()
        } else {
            copy(stacks = resetStacks, topLevelHistory = history).normalized()
        }
    }

    fun reset(): MainNavigationState = initial()

    internal fun normalized(): MainNavigationState {
        val normalizedStacks = TopLevelDestination.entries.associateWith { destination ->
            stacks[destination]
                ?.filter { it.topLevel == destination }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(rootTarget(destination))
        }
        val active = activeTopLevel.takeIf { normalizedStacks.containsKey(it) }
            ?: TopLevelDestination.HOME
        return copy(
            activeTopLevel = active,
            stacks = normalizedStacks,
            topLevelHistory = topLevelHistory.filter { normalizedStacks.containsKey(it) },
        )
    }

    companion object {
        fun initial(): MainNavigationState = MainNavigationState(
            activeTopLevel = TopLevelDestination.HOME,
            stacks = TopLevelDestination.entries.associateWith { listOf(rootTarget(it)) },
        )
    }
}

sealed interface BackResult {
    val state: MainNavigationState

    data class Handled(override val state: MainNavigationState) : BackResult
    data class AtRoot(override val state: MainNavigationState) : BackResult
}

internal data class NavigationTargetRecord(
    val topLevel: String,
    val route: String,
    val args: Map<String, String> = emptyMap(),
)

private data class MainNavigationSnapshot(
    val version: Int,
    val activeTopLevel: String,
    val stacks: Map<String, List<NavigationTargetRecord>>,
    val history: List<String>,
)

object MainNavigationSnapshotCodec {
    private const val VERSION = 1

    fun encode(state: MainNavigationState): String = GsonProvider.instance.toJson(
        MainNavigationSnapshot(
            version = VERSION,
            activeTopLevel = state.activeTopLevel.name,
            stacks = state.stacks.mapKeys { it.key.name }.mapValues { entry ->
                entry.value.map(NavigationTargetCodec::toRecord)
            },
            history = state.topLevelHistory.map { it.name },
        )
    )

    fun decode(raw: String?): MainNavigationState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val snapshot = GsonProvider.instance.fromJson(raw, MainNavigationSnapshot::class.java)
            if (snapshot.version != VERSION) return null
            val stacks = snapshot.stacks.mapNotNull { (key, records) ->
                val destination = enumValueOrNull<TopLevelDestination>(key) ?: return@mapNotNull null
                destination to records.mapNotNull(NavigationTargetCodec::fromRecord)
            }.toMap()
            MainNavigationState(
                activeTopLevel = enumValueOrNull<TopLevelDestination>(snapshot.activeTopLevel)
                    ?: TopLevelDestination.HOME,
                stacks = stacks,
                topLevelHistory = snapshot.history.mapNotNull {
                    enumValueOrNull<TopLevelDestination>(it)
                },
            ).normalized()
        }.getOrNull()
    }
}

internal object NavigationTargetCodec {
    fun toRecord(target: NavigationTarget): NavigationTargetRecord = when (target) {
        is HomeTarget -> NavigationTargetRecord(target.topLevel.name, target.route.name)
        is MarketTarget -> NavigationTargetRecord(
            target.topLevel.name,
            target.route.name,
            mapOfNotNull("topicId" to target.topicId),
        )
        is ChaoxingTarget -> NavigationTargetRecord(
            target.topLevel.name,
            target.route.name,
            mapOfNotNull("subTab" to target.subTab, "entityId" to target.entityId),
        )
        is WeLearnTarget -> NavigationTargetRecord(
            target.topLevel.name,
            target.route.name,
            mapOfNotNull(
                "courseId" to target.courseId,
                "unitIds" to target.unitIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            ),
        )
        is AppsTarget -> NavigationTargetRecord(
            target.topLevel.name,
            target.route.name,
            mapOfNotNull("appKey" to target.appKey, "entityId" to target.entityId),
        )
        is ProfileTarget -> NavigationTargetRecord(
            target.topLevel.name,
            target.route.name,
            mapOfNotNull("utility" to target.utility),
        )
    }

    fun fromRecord(record: NavigationTargetRecord): NavigationTarget? {
        // Gson 反序列化时不走 Kotlin 默认值,args 可能为 null(旧版/手写 JSON 缺该字段时)
        val args = record.args.orEmpty()
        return when (enumValueOrNull<TopLevelDestination>(record.topLevel)) {
            TopLevelDestination.HOME -> enumValueOrNull<HomeRoute>(record.route)?.let(::HomeTarget)
            TopLevelDestination.MARKET -> enumValueOrNull<MarketRoute>(record.route)?.let {
                MarketTarget(it, args["topicId"])
            }
            TopLevelDestination.CHAOXING -> enumValueOrNull<ChaoxingRoute>(record.route)?.let {
                ChaoxingTarget(it, args["subTab"], args["entityId"])
            }
            TopLevelDestination.WELEARN -> enumValueOrNull<WeLearnRoute>(record.route)?.let {
                WeLearnTarget(
                    it,
                    args["courseId"],
                    args["unitIds"].orEmpty().split(',').mapNotNull(String::toIntOrNull),
                )
            }
            TopLevelDestination.APPS -> enumValueOrNull<AppsRoute>(record.route)?.let {
                AppsTarget(it, args["appKey"], args["entityId"])
            }
            TopLevelDestination.PROFILE -> enumValueOrNull<ProfileRoute>(record.route)?.let {
                ProfileTarget(it, args["utility"])
            }
            null -> null
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    value?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } }

private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
    pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
