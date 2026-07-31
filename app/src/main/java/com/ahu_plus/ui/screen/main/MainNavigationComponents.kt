package com.ahu_plus.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.developer.DeveloperNetworkFault
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.data.developer.DeveloperRuntimeState
import com.ahu_plus.ui.theme.AhuPlusTheme

@Composable
internal fun DeveloperFaultBanner(
    state: DeveloperRuntimeState,
    applyNavigationBarInset: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyNavigationBarInset) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "开发者故障覆盖已启用",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${state.networkFault.title} · ${state.targetHost.ifBlank { "全部主机" }}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = DeveloperRuntime::resetOverrides) {
                Icon(Icons.Filled.Restore, contentDescription = "恢复正常网络")
            }
        }
    }
}

@Composable
internal fun TopLevelNavigationBar(
    destinations: List<TopLevelNavItem>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        destinations.forEach { destination ->
            val selected = selectedTab == destination.tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination.tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        destination.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
internal fun TopLevelNavigationRail(
    destinations: List<TopLevelNavItem>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            destinations.forEach { destination ->
                val selected = selectedTab == destination.tab
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(destination.tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Preview(name = "Developer Fault Banner - Light", showBackground = true)
@Composable
private fun PreviewDeveloperFaultBanner() {
    AhuPlusTheme {
        DeveloperFaultBanner(
            state = DeveloperRuntimeState(
                networkFault = DeveloperNetworkFault.LATENCY,
                targetHost = "jw.ahu.edu.cn"
            ),
            applyNavigationBarInset = false
        )
    }
}

@Preview(name = "Navigation Bar - Home Selected", showBackground = true)
@Composable
private fun PreviewTopLevelNavigationBar() {
    val destinations = listOf(
        TopLevelNavItem(0, "首页", Icons.Filled.Home, Icons.Outlined.Home),
        TopLevelNavItem(1, "集市", Icons.Filled.Apps, Icons.Outlined.Apps),
        TopLevelNavItem(2, "学习", Icons.Filled.School, Icons.Outlined.School),
        TopLevelNavItem(3, "我的", Icons.Filled.Person, Icons.Outlined.Person)
    )
    AhuPlusTheme {
        TopLevelNavigationBar(
            destinations = destinations,
            selectedTab = 0,
            onSelect = {}
        )
    }
}

@Preview(name = "Navigation Rail - Profile Selected", showBackground = true, widthDp = 100)
@Composable
private fun PreviewTopLevelNavigationRail() {
    val destinations = listOf(
        TopLevelNavItem(0, "首页", Icons.Filled.Home, Icons.Outlined.Home),
        TopLevelNavItem(1, "集市", Icons.Filled.Apps, Icons.Outlined.Apps),
        TopLevelNavItem(2, "学习", Icons.Filled.School, Icons.Outlined.School),
        TopLevelNavItem(3, "我的", Icons.Filled.Person, Icons.Outlined.Person)
    )
    AhuPlusTheme {
        TopLevelNavigationRail(
            destinations = destinations,
            selectedTab = 3,
            onSelect = {}
        )
    }
}
