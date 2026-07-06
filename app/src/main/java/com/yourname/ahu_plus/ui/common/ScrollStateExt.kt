package com.yourname.ahu_plus.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 2026-07-06 P0/P1: rememberSaveable 版本的 LazyListState / ScrollState 工厂。
 *
 * 解决: 当 LazyColumn/Column 所在 Composable 在 `when` / `if-return` 链切换时被销毁重建,
 * 默认 `rememberLazyListState()` 内部用 `rememberSaveable` 注册到当前 registry,但
 * inner Composable 离开时 SavedStateHolder 不会自动迁移 registry,跨 Composable 切换
 * 滚动位置丢失(见 ProfileScreen / XzxxScreen / ChaoxingTabScreen 修复 commit)。
 *
 * 使用: 与原 `rememberLazyListState()` / `rememberScrollState()` 替换即可。
 *  - 同 Composable 内部使用: 跨 Activity 重建、跨 LazyColumn 销毁重建保留位置
 *  - inner Composable 切页场景: 仍需将 listState 提升到外层 Composable(参考 ProfileScreen 修复)
 */
@Composable
fun rememberSaveableLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState = rememberSaveable(saver = LazyListState.Saver) {
    LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)
}

@Composable
fun rememberSaveableScrollState(
    initial: Int = 0,
): ScrollState = rememberSaveable(saver = ScrollState.Saver) {
    ScrollState(initial = initial)
}
