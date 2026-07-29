package com.ahu_plus.ui.screen.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.local.HomeDockMode
import com.ahu_plus.ui.components.AhuStickyHeader
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.theme.AhuShapes

/**
 * 首页设置页:首页快捷栏模式(最近使用 / 收藏应用)与焦点轮播开关。
 *
 * 由首页顶栏齿轮入口打开,作为全屏覆盖渲染在首页内容区之上(底栏仍可见可切 Tab)。
 * 当前值与回调由 [DashboardScreen] 透传,写入 SessionManager 后经 Flow 回流即时生效。
 * 预留扩展位:后续首页相关设置项可在此追加分区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsScreen(
    dockMode: HomeDockMode,
    onDockModeChange: (HomeDockMode) -> Unit,
    focusPagerEnabled: Boolean,
    onFocusPagerEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("首页设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // 覆盖在首页内容区之上,顶栏由 AhuTopAppBar 处理、底栏由外层 MainScreen 处理。
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AhuStickyHeader("快捷栏")
            }
            item {
                Card(
                    shape = AhuShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HomeDockMode.entries.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider()
                        DockModeRow(
                            mode = option,
                            selected = dockMode == option,
                            onClick = { onDockModeChange(option) },
                        )
                    }
                }
            }
            item {
                AhuStickyHeader("焦点轮播")
            }
            item {
                Card(
                    shape = AhuShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HomeSwitchRow(
                        title = "显示焦点轮播",
                        description = if (focusPagerEnabled) {
                            "首页展示今日日程 / 天气 / 最新通知横滑卡"
                        } else {
                            "隐藏首页焦点轮播卡"
                        },
                        checked = focusPagerEnabled,
                        onCheckedChange = onFocusPagerEnabledChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockModeRow(
    mode: HomeDockMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(mode.titleText(), fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(mode.descriptionText())
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    )
}

@Composable
private fun HomeSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

private fun HomeDockMode.titleText(): String = when (this) {
    HomeDockMode.RECENT -> "最近使用"
    HomeDockMode.FAVORITE -> "收藏应用"
}

private fun HomeDockMode.descriptionText(): String = when (this) {
    HomeDockMode.RECENT -> "按使用时间倒序展示最近打开的应用"
    HomeDockMode.FAVORITE -> "展示手动收藏的应用,可长按拖拽排序"
}
