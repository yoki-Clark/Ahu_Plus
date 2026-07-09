package com.ahu_plus.ui.screen.chaoxing.sign

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CAMPUS_NAMES
import com.ahu_plus.data.model.campusLocationsByCampus
import com.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.ahu_plus.util.LocationProvider
import kotlinx.coroutines.launch

/**
 * 共享签到位置选择器(2026-06-24)。
 *
 * 四种来源:
 *  - 使用当前位置(GPS,已转 GCJ-02)
 *  - 预设教学楼(校区 → 楼 二级选择)
 *  - 自定义位置(用户增删 + 命名,持久化)
 *  - 手动输入经纬度(高德坐标)
 *
 * 选定后回调 [onPicked](lat, lon, name)。自定义位置增删走 [viewModel]。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationPicker(
    viewModel: ChaoxingViewModel,
    onPicked: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customLocations by viewModel.customLocations.collectAsStateWithLifecycle()

    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 校区二级选择
    var selectedCampus by remember { mutableStateOf(CAMPUS_NAMES.firstOrNull() ?: "") }
    // 手动 / 添加自定义的输入
    var showAddCustom by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newLat by remember { mutableStateOf("") }
    var newLon by remember { mutableStateOf("") }
    var useCurrentForCustom by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadCustomLocations() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) error = "未授予定位权限" }

    fun locateThen(action: (Double, Double) -> Unit) {
        if (!LocationProvider.hasLocationPermission(context)) {
            permLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        locating = true
        error = null
        scope.launch {
            val r = LocationProvider.getCurrentLocation(context)
            locating = false
            r.onSuccess { (la, lo) -> action(la, lo) }
            r.onFailure { error = it.message ?: "定位失败" }
        }
    }

    Column(modifier = modifier) {
        // ① 当前位置
        Button(
            onClick = { locateThen { la, lo -> onPicked(la, lo, "当前位置") } },
            enabled = !locating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (locating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
                Text("正在定位…")
            } else Text("使用当前位置 (GPS)")
        }

        Spacer(Modifier.height(12.dp))

        // ② 预设教学楼:校区 → 楼 二级
        SectionLabel("预设教学楼")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CAMPUS_NAMES.forEach { campus ->
                FilterChip(
                    selected = selectedCampus == campus,
                    onClick = { selectedCampus = campus },
                    label = { Text(campus, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val buildings = campusLocationsByCampus()[selectedCampus].orEmpty()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            buildings.forEach { loc ->
                OutlinedButton(onClick = { onPicked(loc.latitude, loc.longitude, "${loc.campus}·${loc.name}") }) {
                    Text(loc.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ③ 自定义位置
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("自定义位置", modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddCustom = !showAddCustom }) {
                Icon(Icons.Filled.Add, contentDescription = "添加自定义位置")
            }
        }
        if (customLocations.isEmpty() && !showAddCustom) {
            Text(
                "暂无,点 + 添加常用地点(可命名)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        customLocations.forEach { loc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPicked(loc.latitude, loc.longitude, loc.name) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(loc.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "%.5f, %.5f".format(loc.latitude, loc.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.removeCustomLocation(loc) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 添加自定义表单
        AnimatedVisibility(visible = showAddCustom) {
            Column {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("名称") }, placeholder = { Text("如:三教 201") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newLat, onValueChange = { newLat = it },
                        label = { Text("纬度") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !useCurrentForCustom,
                    )
                    OutlinedTextField(
                        value = newLon, onValueChange = { newLon = it },
                        label = { Text("经度") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !useCurrentForCustom,
                    )
                }
                Text(
                    "坐标请填高德地图读数;或点下方按钮用当前定位自动填充。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { locateThen { la, lo -> newLat = "%.6f".format(la); newLon = "%.6f".format(lo) } },
                        enabled = !locating,
                        modifier = Modifier.weight(1f),
                    ) { Text("用当前定位") }
                    Button(
                        onClick = {
                            val la = newLat.trim().toDoubleOrNull()
                            val lo = newLon.trim().toDoubleOrNull()
                            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                                error = "经纬度格式不正确"
                            } else {
                                viewModel.addCustomLocation(newName, la, lo)
                                newName = ""; newLat = ""; newLon = ""; showAddCustom = false; error = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}
