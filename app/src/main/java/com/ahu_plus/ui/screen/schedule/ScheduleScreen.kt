package com.ahu_plus.ui.screen.schedule

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import com.ahu_plus.ui.theme.CoursePalettes
import com.ahu_plus.data.model.schedule.backgroundOrDefault
import coil.compose.AsyncImage
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.ahu_plus.data.model.jw.UserScheduleItem
import com.ahu_plus.ui.screen.schedule.components.ReminderPermissionBanner
import com.ahu_plus.ui.screen.schedule.components.WeekPager
import com.ahu_plus.ui.theme.AhuShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    assessmentRepository: com.ahu_plus.data.repository.AssessmentRepository,
    onBack: () -> Unit,
    onNeedsLogin: () -> Unit,
    onSettingsDismissed: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scheduleBackground = CoursePalettes.backgroundVisuals(
        uiState.paletteConfig,
        MaterialTheme.colorScheme,
        MaterialTheme.colorScheme.background.luminance() < 0.5f,
    )
    val backgroundConfig = uiState.paletteConfig.backgroundOrDefault()

    // 进入 ScheduleScreen 时: 若 resetOnEnter 开启,跳到当前周
    LaunchedEffect(Unit) {
        viewModel.applyEnterReset()
    }

    // 每 60 秒触发一次时间 tick，驱动 WeekGrid 重新计算 currentTimeLineY 横线位置
    var timeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            timeTick++
        }
    }

    // ── 导出当前周课表到相册 ─────────────────────
    val exportContext = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val rootView = LocalView.current          // window-attached, 直接 drawToBitmap
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf(false) }

    fun doExport() {
        if (exporting) return
        exporting = true
        val week = uiState.selectedWeek
        exportScope.launch {
            val ok = withContext(Dispatchers.Main) {
                runCatching {
                    val bitmap = rootView.drawToBitmap()
                    try { saveBitmapToGallery(exportContext, week, bitmap) }
                    finally { bitmap.recycle() }
                }.isSuccess
            }
            exporting = false
            Toast.makeText(
                exportContext,
                if (ok) "已保存到相册" else "保存失败,请重试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingExport) {
            pendingExport = false
            if (granted) doExport()
            else Toast.makeText(exportContext, "需要存储权限以保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    val onExportClick: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            doExport()
        } else if (ContextCompat.checkSelfPermission(
                exportContext, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            doExport()
        } else {
            pendingExport = true
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheduleBackground.canvas)
            .statusBarsPadding()
    ) {
        // 缓存极值,避免每帧 4 次 O(n) 遍历
        val minWeek = remember(uiState.weekIndices) { uiState.weekIndices.minOrNull() ?: 1 }
        val maxWeek = remember(uiState.weekIndices) { uiState.weekIndices.maxOrNull() ?: 20 }

        // ── 顶栏：返回 + 周导航 + 设置（合并为一行）─────
        WeekHeader(
            selectedWeek = uiState.selectedWeek,
            currentWeek = uiState.currentWeek,
            studentName = uiState.studentName,
            minWeek = minWeek,
            maxWeek = maxWeek,
            hasPrevious = uiState.selectedWeek > minWeek,
            hasNext = uiState.selectedWeek < maxWeek,
            onBack = onBack,
            onPrevious = { viewModel.onPreviousWeek() },
            onNext = { viewModel.onNextWeek() },
            onSettings = { viewModel.onToggleSettings() },
            onWeekSelected = { viewModel.onWeekSelected(it) },
            onAddCourse = { viewModel.onToggleAddCourse() },
            onExport = onExportClick,
        )

        // 课程提醒已开启但缺权限时,引导用户授权(通知 + 精确闹钟)
        ReminderPermissionBanner(
            enabled = viewModel.reminderPrefs?.getCourseReminderEnabled() ?: true,
        )

        // ── 学期选择器(2026-06-21 多学期支持;2026-06-25 设置项可隐藏)────────
        // 横向 chip 行:默认本学期选中,切换触发按需加载
        // 仅在设置开启(默认开启)且学期列表非空时渲染
        if (uiState.showOtherSemesters && uiState.availableSemesters.isNotEmpty()) {
            SemesterChips(
                availableIds = uiState.availableSemesters.mapNotNull { it.id },
                selectedId = uiState.selectedSemesterId,
                semesterName = { id ->
                    uiState.availableSemesters.firstOrNull { it.id == id }?.nameZh
                        ?: "学期 $id"
                },
                onSelect = viewModel::selectSemester,
            )
        }

        // 切换非本学期时顶部进度条
        if (uiState.isLoadingSemester) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // ── 内容区（下拉刷新）────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(scheduleBackground.canvas)
        ) {
            if (backgroundConfig.backgroundMode == "image" &&
                backgroundConfig.backgroundImageUri.isNotBlank()
            ) {
                AsyncImage(
                    model = backgroundConfig.backgroundImageUri,
                    contentDescription = null,
                    contentScale = if (backgroundConfig.imageScale == "fit") {
                        ContentScale.Fit
                    } else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundConfig.imageOpacity.coerceIn(0.1f, 1f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = backgroundConfig.overlayStrength.coerceIn(0f, 0.9f)
                            )
                        )
                )
            }
            AhuPullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onRefresh() },
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                uiState.isLoading && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "加载课表中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                uiState.error != null && uiState.allActivities.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = if (uiState.needsLogin) onNeedsLogin else viewModel::onRefresh
                        ) {
                            Text(if (uiState.needsLogin) "去登录" else "重试")
                        }
                    }
                }

                uiState.allActivities.isEmpty() && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "暂无课表数据",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.onRefresh() }) {
                            Text("刷新")
                        }
                    }
                }

                else -> {
                    val maxPage = maxWeek.coerceAtLeast(1)
                    val currentPage = (uiState.selectedWeek - 1).coerceIn(0, maxPage - 1)
                    // 固定时间列与各周网格体共享垂直滚动状态:
                    // 时间列放在 Pager 左侧、不随换页滑动 → 切换周次时保持不动(2026-06-25)。
                    val sharedVerScroll = androidx.compose.foundation.rememberScrollState()
                    Row(modifier = Modifier.fillMaxSize()) {
                        com.ahu_plus.ui.screen.schedule.FixedTimeColumn(
                            unitTimes = uiState.unitTimes,
                            rowHeight = uiState.rowHeightDp.dp,
                            fontScale = uiState.fontScale,
                            verScroll = sharedVerScroll,
                            paletteConfig = uiState.paletteConfig,
                            bottomSpacerHeight = if (uiState.dataStatus != null) {
                                DATA_STATUS_FOOTER_HEIGHT
                            } else {
                                0.dp
                            },
                        )
                        com.ahu_plus.ui.screen.schedule.components.WeekPager(
                            maxPage = maxPage,
                            currentPage = currentPage,
                            enabled = uiState.pagerEnabled,
                            onPageChanged = { page -> viewModel.setSelectedWeek(page + 1) },
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val week = page + 1
                            val items = remember(week, uiState.allActivities, uiState.showSat, uiState.showSun) {
                                viewModel.buildDisplayItemsForWeek(week)
                            }
                            WeekGrid(
                                displayItems = items,
                                unitTimes = uiState.unitTimes,
                                selectedWeek = week,
                                currentWeek = uiState.currentWeek,
                                onCourseClick = viewModel::onCourseClicked,
                                modifier = Modifier.fillMaxSize(),
                                colWidth = uiState.colWidthDp.dp,
                                rowHeight = uiState.rowHeightDp.dp,
                                fontScale = uiState.fontScale,
                                showSat = uiState.showSat,
                                showSun = uiState.showSun,
                                timeTick = timeTick,
                                sharedVerScroll = sharedVerScroll,
                                paletteConfig = uiState.paletteConfig,
                                dataStatus = uiState.dataStatus,
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // ── 悬浮"今"按钮 (非当前周时显示) ───────────
    // 跨学期场景:selectedSemesterId != DEFAULT → jumpToCurrentWeek() 会先切回本学期再 setSelectedWeek
    com.ahu_plus.ui.screen.schedule.components.TodayFloatingButton(
        visible = uiState.selectedWeek != uiState.currentWeek,
        onClick = { viewModel.jumpToCurrentWeek() },
    )

    // ── 课程详情 BottomSheet (2026-06-17 重写为 5 折叠 section) ──────
    uiState.selectedCourseDetail?.let { _ ->
        CourseDetailSheet(
            viewModel = viewModel,
            assessmentRepository = assessmentRepository,
            onDismiss = viewModel::onDismissSheet,
        )
    }

    // ── 课表显示设置 BottomSheet ────────────────────
    if (uiState.showSettings) {
        ScheduleSettingsSheet(
            colWidthDp = uiState.colWidthDp,
            rowHeightDp = uiState.rowHeightDp,
            fontScale = uiState.fontScale,
            showSat = uiState.showSat,
            showSun = uiState.showSun,
            pagerEnabled = uiState.pagerEnabled,
            resetOnEnter = uiState.resetOnEnter,
            showOtherSemesters = uiState.showOtherSemesters,
            onColWidthChanged = viewModel::onColWidthChanged,
            onRowHeightChanged = viewModel::onRowHeightChanged,
            onFontScaleChanged = viewModel::onFontScaleChanged,
            onShowSatChanged = viewModel::setShowSat,
            onShowSunChanged = viewModel::setShowSun,
            onPagerEnabledChanged = viewModel::setPagerEnabled,
            onResetOnEnterChanged = viewModel::setResetOnEnter,
            onShowOtherSemestersChanged = viewModel::setShowOtherSemesters,
            paletteConfig = uiState.paletteConfig,
            onPalettePresetChanged = viewModel::setPalettePreset,
            onCardStyleChanged = viewModel::setCourseCardStyle,
            onCustomPaletteColorChanged = viewModel::setCustomPaletteColor,
            onBackgroundConfigChanged = viewModel::setScheduleBackground,
            onReset = viewModel::onResetSettings,
            onDismiss = {
                viewModel.onToggleSettings()
                onSettingsDismissed?.invoke()
            },
            sessionManager = viewModel.reminderPrefs,
        )
    }

    // ── 添加课程 BottomSheet ──────────────────────────
    if (uiState.showAddCourse) {
        AddCourseSheet(
            unitTimes = uiState.unitTimes,
            onAdd = { item -> viewModel.addUserScheduleItem(item) },
            onDismiss = { viewModel.onToggleAddCourse() },
        )
    }
}

@Composable
private fun WeekHeader(
    selectedWeek: Int,
    currentWeek: Int,
    studentName: String?,
    minWeek: Int,
    maxWeek: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit = {},
    onWeekSelected: (Int) -> Unit = {},
    onAddCourse: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    var showWeekPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回键
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 周切换
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "上一周",
                modifier = Modifier.size(20.dp)
            )
        }

        // 周次显示 —— 点击弹出快速跳转
        Box {
            Row(
                modifier = Modifier.clickable { showWeekPicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 $selectedWeek 周",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                if (selectedWeek == currentWeek) {
                    Text(
                        text = "（本周）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showWeekPicker,
                onDismissRequest = { showWeekPicker = false },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.width(224.dp),
                    shape = AhuShapes.Card,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "快速跳转周次",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.height(192.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items((minWeek..maxWeek).toList()) { week ->
                                val isSelected = week == selectedWeek
                                val isCurrent = week == currentWeek
                                Surface(
                                    modifier = Modifier.clickable {
                                        onWeekSelected(week)
                                        showWeekPicker = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.height(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$week",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isCurrent -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "下一周",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 加课
        IconButton(onClick = onAddCourse, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加课程",
                modifier = Modifier.size(20.dp)
            )
        }
        // 保存当前周课表到相册 (2026-06-28 新增)
        IconButton(
            onClick = onExport,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = "导出图片",
                modifier = Modifier.size(20.dp),
            )
        }
        // 课表设置
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "课表设置",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// (旧的 ScheduleSettingsSheet + SettingSlider 已在 2026-06-17 抽到独立文件
//  ui/screen/schedule/ScheduleSettingsSheet.kt)

/** @deprecated 旧的本地定义;新版本在独立文件中 */
private fun OldSettingSliderPlaceholder() = Unit

// ═══════════════════════ 添加课程 BottomSheet ═══════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseSheet(
    unitTimes: List<com.ahu_plus.data.model.jw.CourseUnit>,
    onAdd: (UserScheduleItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(1) }
    var startUnit by remember { mutableIntStateOf(1) }
    var endUnit by remember { mutableIntStateOf(2) }
    var room by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var selectedWeeks by remember { mutableStateOf((1..18).toSet()) }

    val sortedUnits = remember(unitTimes) {
        unitTimes.filter { it.indexNo != null }.sortedBy { it.indexNo }
    }
    val unitLabels = remember(sortedUnits) {
        sortedUnits.map { "${it.indexNo} (${it.startTimeStr()})" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "添加课程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "周期性上课的课程。一次性事件请用首页「日程」添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课程名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 星期 + 节次
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (weekday in 1..7) "周${listOf("一","二","三","四","五","六","日")[weekday-1]}" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("星期") },
                    modifier = Modifier.weight(1f)
                )
                // 星期快速选择
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..7) {
                        FilterChip(
                            selected = weekday == d,
                            onClick = { weekday = d },
                            label = {
                                Text(
                                    listOf("一","二","三","四","五","六","日")[d-1],
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startUnit.toString(),
                    onValueChange = { startUnit = it.toIntOrNull() ?: 1 },
                    label = { Text("开始节次") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endUnit.toString(),
                    onValueChange = { endUnit = it.toIntOrNull() ?: 2 },
                    label = { Text("结束节次") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 教室
            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                label = { Text("教室 / 地点") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 教师（选填）
            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("教师（选填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 周次选择
            Text(
                text = "适用周次（已选 ${selectedWeeks.size} 周）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items((1..18).toList()) { week ->
                    FilterChip(
                        selected = week in selectedWeeks,
                        onClick = {
                            selectedWeeks = if (week in selectedWeeks) selectedWeeks - week
                            else selectedWeeks + week
                        },
                        label = { Text("$week", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        if (name.isNotBlank() && selectedWeeks.isNotEmpty()) {
                            onAdd(
                                UserScheduleItem(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    type = UserScheduleItem.TYPE_COURSE,
                                    weekday = weekday,
                                    startUnit = startUnit,
                                    endUnit = endUnit,
                                    weeks = selectedWeeks.sorted(),
                                    room = room.trim(),
                                    teacher = teacher.trim(),
                                    note = "",
                                )
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank() && selectedWeeks.isNotEmpty()
                ) {
                    Text("添加")
                }
            }
        }
    }
}

// ═══════════════════════ Semester Chips ═══════════════════════
// 学期选择器(横向滚动 FilterChip)。参照 GradeScreen 的 SemesterChips。
// 切换学期触发 ViewModel.selectSemester(),非本学期按需网络加载。

@Composable
private fun SemesterChips(
    availableIds: List<Int>,
    selectedId: Int?,
    semesterName: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    if (availableIds.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availableIds.forEach { id ->
            val selected = id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(id) },
                label = { Text(semesterName(id)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ═══════════════════════ 保存 PNG 到系统相册 (2026-06-28 修复版)═══════════════════════
// API 29+ MediaStore scoped storage (免权限);≤28 Environment + WRITE_EXTERNAL_STORAGE。
// 不抛异常,失败返回 false。

private fun saveBitmapToGallery(context: Context, week: Int, bitmap: Bitmap): Boolean {
    val name = "schedule_w${week}_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.png"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AhuPlus")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )!!
            context.contentResolver.openOutputStream(uri)!!.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "AhuPlus",
            ).apply { mkdirs() }
            FileOutputStream(File(dir, name)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }.isSuccess
}
