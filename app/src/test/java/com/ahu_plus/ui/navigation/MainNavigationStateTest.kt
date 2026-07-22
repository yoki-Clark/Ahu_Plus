package com.ahu_plus.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationStateTest {
    @Test
    fun `switching tabs preserves each stack and reselecting pops selected tab to root`() {
        var state = MainNavigationState.initial()
            .navigate(NavigationRequest(HomeTarget(HomeRoute.GRADE)))
            .selectTopLevel(TopLevelDestination.APPS)
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "weather")))

        state = state.selectTopLevel(TopLevelDestination.HOME)
        assertEquals(HomeTarget(HomeRoute.GRADE), state.currentTarget)

        state = state.selectTopLevel(TopLevelDestination.HOME)
        assertEquals(HomeTarget(HomeRoute.DASHBOARD), state.currentTarget)
        assertEquals(AppsTarget(AppsRoute.APP, appKey = "weather"), state.stacks.getValue(TopLevelDestination.APPS).last())
    }

    @Test
    fun `programmatic cross tab navigation returns to origin after target stack pops`() {
        var state = MainNavigationState.initial()
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "schedule")))

        assertEquals(TopLevelDestination.APPS, state.activeTopLevel)
        state = (state.back() as BackResult.Handled).state
        assertEquals(AppsTarget(), state.currentTarget)
        state = (state.back() as BackResult.Handled).state
        assertEquals(TopLevelDestination.HOME, state.activeTopLevel)
        assertTrue(state.back() is BackResult.AtRoot)
    }

    @Test
    fun `single top does not duplicate repeated external target`() {
        val request = NavigationRequest(
            HomeTarget(HomeRoute.SCHEDULE),
            NavigationSource.NOTIFICATION,
        )
        val state = MainNavigationState.initial().navigate(request).navigate(request)

        assertEquals(2, state.stacks.getValue(TopLevelDestination.HOME).size)
    }

    @Test
    fun `disabled third party target falls back to dashboard and removes history`() {
        val state = MainNavigationState.initial()
            .navigate(NavigationRequest(ChaoxingTarget()))
            .disable(TopLevelDestination.CHAOXING)

        assertEquals(TopLevelDestination.HOME, state.activeTopLevel)
        assertEquals(HomeTarget(HomeRoute.DASHBOARD), state.currentTarget)
        assertFalse(state.topLevelHistory.contains(TopLevelDestination.CHAOXING))
    }

    @Test
    fun `snapshot round trip uses stable ids and drops corrupt snapshot`() {
        val expected = MainNavigationState.initial()
            .navigate(NavigationRequest(ProfileTarget(ProfileRoute.UTILITY, "internet")))
        val restored = MainNavigationSnapshotCodec.decode(MainNavigationSnapshotCodec.encode(expected))

        assertEquals(expected, restored)
        assertEquals(null, MainNavigationSnapshotCodec.decode("not-json"))
    }

    @Test
    fun `saved state view model restores process state`() {
        val handle = SavedStateHandle()
        val first = MainNavigationViewModel(handle)
        first.navigate(NavigationRequest(HomeTarget(HomeRoute.AGENDA)))

        val restored = MainNavigationViewModel(handle)
        assertEquals(HomeTarget(HomeRoute.AGENDA), restored.state.value.currentTarget)
        assertTrue(restored.back())
        assertFalse(restored.back())
    }

    // ── normalized():损坏栈自愈 ───────────────────────────────

    @Test
    fun `normalized drops targets with mismatched top level from each stack`() {
        // 手工构造一份损坏状态:HOME 栈里混入了 AppsTarget,APPS 栈里混入了 HomeTarget
        val corrupt = MainNavigationState(
            activeTopLevel = TopLevelDestination.HOME,
            stacks = mapOf(
                TopLevelDestination.HOME to listOf(
                    HomeTarget(HomeRoute.DASHBOARD),
                    AppsTarget(AppsRoute.APP, appKey = "leak"),  // 不属于 HOME
                ),
                TopLevelDestination.APPS to listOf(
                    AppsTarget(),
                    HomeTarget(HomeRoute.SCHEDULE),  // 不属于 APPS
                ),
            ),
        )

        val normalized = corrupt.normalized()

        // 损坏条目被过滤后,每个栈只剩同 topLevel 的目标
        assertEquals(
            listOf<NavigationTarget>(HomeTarget(HomeRoute.DASHBOARD)),
            normalized.stacks.getValue(TopLevelDestination.HOME),
        )
        assertEquals(
            listOf<NavigationTarget>(AppsTarget()),
            normalized.stacks.getValue(TopLevelDestination.APPS),
        )
    }

    @Test
    fun `normalized replaces empty stack with root target and fills missing destinations`() {
        // 缺少 CHAOXING/MARKET 等栈,且 HOME 栈被过滤后变空
        val partial = MainNavigationState(
            activeTopLevel = TopLevelDestination.HOME,
            stacks = mapOf(
                TopLevelDestination.HOME to listOf(AppsTarget()),  // 全部不属于 HOME,过滤后为空
            ),
        )

        val normalized = partial.normalized()

        // 所有 TopLevelDestination 都应该有栈,空栈回填为 rootTarget
        TopLevelDestination.entries.forEach { destination ->
            assertEquals(1, normalized.stacks.getValue(destination).size)
            assertEquals(rootTarget(destination), normalized.stacks.getValue(destination).single())
        }
        assertEquals(HomeTarget(HomeRoute.DASHBOARD), normalized.currentTarget)
    }

    @Test
    fun `normalized preserves history entries after filling missing stacks`() {
        // 历史里引用了 CHAOXING/APPS,但原始状态缺少这些栈
        // normalized() 会用 rootTarget 填充所有缺失的栈,因此历史条目应被保留(不会因栈缺失而丢弃)
        val corrupt = MainNavigationState(
            activeTopLevel = TopLevelDestination.HOME,
            stacks = mapOf(
                TopLevelDestination.HOME to listOf(HomeTarget(HomeRoute.DASHBOARD)),
            ),
            topLevelHistory = listOf(TopLevelDestination.CHAOXING, TopLevelDestination.APPS),
        )

        val normalized = corrupt.normalized()

        // 所有 TopLevelDestination 都有栈(包括之前缺失的 CHAOXING/APPS),历史条目全部保留
        assertEquals(
            listOf(TopLevelDestination.CHAOXING, TopLevelDestination.APPS),
            normalized.topLevelHistory,
        )
        // 被填充的栈使用 rootTarget
        assertEquals(listOf<NavigationTarget>(ChaoxingTarget()), normalized.stacks.getValue(TopLevelDestination.CHAOXING))
        assertEquals(listOf<NavigationTarget>(AppsTarget()), normalized.stacks.getValue(TopLevelDestination.APPS))
    }

    // ── back():AtRoot 边界 ──────────────────────────────────────

    @Test
    fun `back at root returns AtRoot and repeated back stays at root`() {
        val initial = MainNavigationState.initial()
        // 初始状态没有任何历史,back() 立即返回 AtRoot
        val firstBack = initial.back()
        assertTrue(firstBack is BackResult.AtRoot)

        // 在 AtRoot 状态上再次 back(),仍应返回 AtRoot 且状态不变
        val atRoot = firstBack as BackResult.AtRoot
        val secondBack = atRoot.state.back()
        assertTrue(secondBack is BackResult.AtRoot)
        assertEquals(atRoot.state, (secondBack as BackResult.AtRoot).state)
    }

    @Test
    fun `back pops cross tab history after current stack reaches root`() {
        // HOME → navigate AppsTarget (跨 Tab,记录历史) → back 回到 Apps root → back 回到 HOME
        var state = MainNavigationState.initial()
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "weather")))

        assertEquals(TopLevelDestination.APPS, state.activeTopLevel)
        assertEquals(listOf(TopLevelDestination.HOME), state.topLevelHistory)

        // 第一次 back:APPS 栈有 2 个元素,弹出 weather
        state = (state.back() as BackResult.Handled).state
        assertEquals(AppsTarget(), state.currentTarget)
        assertEquals(TopLevelDestination.APPS, state.activeTopLevel)

        // 第二次 back:APPS 栈到 root,跨 Tab 回到 HOME
        state = (state.back() as BackResult.Handled).state
        assertEquals(TopLevelDestination.HOME, state.activeTopLevel)
        assertEquals(HomeTarget(HomeRoute.DASHBOARD), state.currentTarget)
        assertTrue(state.topLevelHistory.isEmpty())

        // 第三次 back:AtRoot
        assertTrue(state.back() is BackResult.AtRoot)
    }

    // ── disable():非当前 Tab 与 HOME 边界 ─────────────────────

    @Test
    fun `disable home is a no op`() {
        val state = MainNavigationState.initial()
            .navigate(NavigationRequest(HomeTarget(HomeRoute.SCHEDULE)))

        val after = state.disable(TopLevelDestination.HOME)

        // disable(HOME) 直接返回原状态
        assertEquals(state, after)
        assertEquals(HomeTarget(HomeRoute.SCHEDULE), after.currentTarget)
    }

    @Test
    fun `disable non active tab resets that tab stack and keeps current active`() {
        // 当前在 HOME,APPS 栈有非 root 目标,disable(APPS) 应清空 APPS 栈但保持 HOME 激活
        val state = MainNavigationState.initial()
            .selectTopLevel(TopLevelDestination.APPS)
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "weather")))
            .selectTopLevel(TopLevelDestination.HOME)
            .navigate(NavigationRequest(HomeTarget(HomeRoute.GRADE)))

        val after = state.disable(TopLevelDestination.APPS)

        assertEquals(TopLevelDestination.HOME, after.activeTopLevel)
        assertEquals(HomeTarget(HomeRoute.GRADE), after.currentTarget)
        // APPS 栈被重置为 root
        assertEquals(listOf<NavigationTarget>(AppsTarget()), after.stacks.getValue(TopLevelDestination.APPS))
        // APPS 从历史中移除
        assertFalse(after.topLevelHistory.contains(TopLevelDestination.APPS))
    }

    // ── navigate():TOP_LEVEL source 不记录来源 ─────────────────

    @Test
    fun `navigate with top level source does not track origin history`() {
        // 从 HOME 用 TOP_LEVEL source 跳到 APPS,不应在 topLevelHistory 里记录 HOME
        val state = MainNavigationState.initial()
            .navigate(
                NavigationRequest(
                    AppsTarget(AppsRoute.APP, appKey = "weather"),
                    NavigationSource.TOP_LEVEL,
                )
            )

        assertEquals(TopLevelDestination.APPS, state.activeTopLevel)
        assertTrue(state.topLevelHistory.isEmpty())
    }

    @Test
    fun `navigate with internal source tracks origin history`() {
        // 对照:INTERNAL source 跳到 APPS,应在 topLevelHistory 里记录 HOME
        val state = MainNavigationState.initial()
            .navigate(
                NavigationRequest(
                    AppsTarget(AppsRoute.APP, appKey = "weather"),
                    NavigationSource.INTERNAL,
                )
            )

        assertEquals(TopLevelDestination.APPS, state.activeTopLevel)
        assertEquals(listOf(TopLevelDestination.HOME), state.topLevelHistory)
    }

    @Test
    fun `navigate to same active tab does not track origin`() {
        // 当前在 HOME,继续在 HOME 上 navigate,不应记录 HOME 到 history
        val state = MainNavigationState.initial()
            .navigate(NavigationRequest(HomeTarget(HomeRoute.SCHEDULE), NavigationSource.INTERNAL))

        assertEquals(TopLevelDestination.HOME, state.activeTopLevel)
        assertTrue(state.topLevelHistory.isEmpty())
    }

    // ── selectTopLevel():历史清理 ───────────────────────────────

    @Test
    fun `select different tab clears cross tab history`() {
        // HOME → Apps(记录历史) → 切到 PROFILE,历史应被清空
        val state = MainNavigationState.initial()
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "weather")))

        assertEquals(listOf(TopLevelDestination.HOME), state.topLevelHistory)

        val afterSwitch = state.selectTopLevel(TopLevelDestination.PROFILE)
        assertTrue(afterSwitch.topLevelHistory.isEmpty())
        assertEquals(TopLevelDestination.PROFILE, afterSwitch.activeTopLevel)
    }

    // ── reset():回到初始状态 ────────────────────────────────────

    @Test
    fun `reset returns to initial state`() {
        val state = MainNavigationState.initial()
            .navigate(NavigationRequest(HomeTarget(HomeRoute.SCHEDULE)))
            .navigate(NavigationRequest(HomeTarget(HomeRoute.GRADE)))
            .selectTopLevel(TopLevelDestination.APPS)
            .navigate(NavigationRequest(AppsTarget(AppsRoute.APP, appKey = "weather")))

        val reset = state.reset()

        assertEquals(MainNavigationState.initial(), reset)
        assertEquals(TopLevelDestination.HOME, reset.activeTopLevel)
        assertEquals(HomeTarget(HomeRoute.DASHBOARD), reset.currentTarget)
        assertTrue(reset.topLevelHistory.isEmpty())
    }
}

