package com.ahu_plus.ui.screen.chaoxing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CxAttachment
import com.ahu_plus.data.model.CxCourse
import com.ahu_plus.data.model.CxCourseProgress
import com.ahu_plus.data.model.CxHomeworkItem
import com.ahu_plus.data.model.CxMessage
import com.ahu_plus.data.model.CxMessageSource
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.ChaoxingColors
import com.ahu_plus.service.ChaoxingStudyService
import com.ahu_plus.util.OverlayWindow
import kotlinx.coroutines.launch

/**
 * 学习通底部 Tab 主屏(2026-06-20 v3)。
 *
 * 3 个 tab：课程 | 消息 | 设置
 * 课程详情 / 学习进度为全屏覆盖层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingTabScreen(
    viewModel: ChaoxingViewModel,
    onSwitchToAppsTab: () -> Unit,
    requestedSubTab: ChaoxingSubTab? = null,
    onRequestedSubTabConsumed: () -> Unit = {},
    onSettingsBack: (() -> Unit)? = null,
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable {
        mutableStateOf(requestedSubTab?.ordinal ?: ChaoxingSubTab.COURSES.ordinal)
    }
    val pagerState = rememberPagerState(
        initialPage = selectedTab,
        pageCount = { ChaoxingSubTab.entries.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedSubTab) {
        val target = requestedSubTab ?: return@LaunchedEffect
        selectedTab = target.ordinal
        pagerState.scrollToPage(target.ordinal)
        onRequestedSubTabConsumed()
    }

    // 课程详情 / 学习进度 / 作业详情 覆盖层
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<CxCourse?>(null) }
    var showStudySheet by rememberSaveable { mutableStateOf(false) }
    // 2026-07-06 修复: 提升到 ChaoxingTabScreen 顶层(不在 CoursesTabContent 子 Composable 内),
    // 避免 showDetail/showStudyScreen/showHomeworkDetail 短路时 CoursesTabContent 销毁重建导致滚动丢。
    val coursesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var showStudyScreen by rememberSaveable { mutableStateOf(false) }
    // 作业模型没有 Saver；进程/Activity 重建时关闭详情页，避免 rememberSaveable 保存崩溃。
    var showHomeworkDetail by remember { mutableStateOf(false) }
    var selectedHomework by remember { mutableStateOf<CxHomeworkItem?>(null) }

    // 首次进入时检查登录状态
    LaunchedEffect(Unit) {
        if (!loginState.isLoggedIn) {
            viewModel.checkLogin()
        }
    }

    // 同步 pager ↔ selectedTab
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTab) {
            selectedTab = pagerState.currentPage
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // 系统返回键处理
    BackHandler {
        when {
            showStudyScreen -> showStudyScreen = false
            showDetail -> { showDetail = false; selectedCourse = null }
            onSettingsBack != null -> onSettingsBack()
            selectedTab != ChaoxingSubTab.COURSES.ordinal -> {
                selectedTab = ChaoxingSubTab.COURSES.ordinal
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            else -> onSwitchToAppsTab()
        }
    }

    // ── 全屏覆盖层: 学习进度 ──────────────────────────────────
    if (showStudyScreen) {
        ChaoxingStudyScreen(
            studyState = studyState,
            onStop = {
                viewModel.stopStudy()
                ChaoxingStudyService.stop(context)  // 同时停止 Service 释放悬浮窗
            },
            onBack = { showStudyScreen = false },
        )
        return
    }

    // ── 全屏覆盖层: 课程详情 ──────────────────────────────────
    if (showDetail && selectedCourse != null) {
        ChaoxingCourseDetailScreen(
            viewModel = viewModel,
            course = selectedCourse!!,
            onBack = { showDetail = false; selectedCourse = null },
            onStartStudy = { course ->
                // 2026-06-22: 单课程入口 — Service 后台学习，showStudyScreen 仅显示进度
                ChaoxingStudyService.start(
                    context = context,
                    courseIds = listOf(course.courseId),
                    speed = settingsState.speed,
                    concurrency = settingsState.concurrency,
                    autoSubmit = settingsState.submitMode == "auto",
                    enabledTaskTypes = settingsState.enabledTaskTypes,
                )
                showStudyScreen = true
            },
        )
        return
    }

    // ── 全屏覆盖层: 作业详情 ──────────────────────────────────
    if (showHomeworkDetail && selectedHomework != null) {
        HomeworkDetailScreen(
            viewModel = viewModel,
            homework = selectedHomework!!,
            onBack = {
                showHomeworkDetail = false
                selectedHomework = null
            },
        )
        return
    }

    // 悬浮窗权限引导
    var showOverlayPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var pendingStartService by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ── 确认学习弹窗 ─────────────────────────────────────────
    if (showStudySheet) {
        ChaoxingStudySheet(
            courses = viewModel.getSelectedCourses(),
            settingsState = settingsState,
            onConfirm = {
                showStudySheet = false
                val selectedCourses = viewModel.getSelectedCourses()
                val courseIds = selectedCourses.mapNotNull { it.courseId }
                if (courseIds.isEmpty()) return@ChaoxingStudySheet

                val startService: () -> Unit = {
                    ChaoxingStudyService.start(
                        context = context,
                        courseIds = courseIds,
                        speed = settingsState.speed,
                        concurrency = settingsState.concurrency,
                        autoSubmit = settingsState.submitMode == "auto",
                        enabledTaskTypes = settingsState.enabledTaskTypes,
                    )
                    showStudyScreen = true
                }

                // 权限未授权时先弹引导对话框,用户确认后再启动 Service
                if (!OverlayWindow.hasOverlayPermission(context)) {
                    pendingStartService = startService
                    showOverlayPermissionDialog = true
                } else {
                    startService()
                }
            },
            onDismiss = { showStudySheet = false },
        )
    }

    // ── 悬浮窗权限引导对话框 ──────────────────────────────
    if (showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            onDismiss = {
                showOverlayPermissionDialog = false
                pendingStartService?.invoke()
                pendingStartService = null
            },
            onGranted = {
                showOverlayPermissionDialog = false
                pendingStartService?.invoke()
                pendingStartService = null
            },
            onSkipped = {
                showOverlayPermissionDialog = false
                pendingStartService?.invoke()
                pendingStartService = null
            },
        )
    }

    // ── 主界面 ────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {
        // 状态栏占位
        Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))

        if (onSettingsBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSettingsBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回聚合设置")
                }
                Text("学习通设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Tab 栏 ─────────────────────────────────────────
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            ChaoxingSubTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            selectedTab = index
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        text = { Text(tab.label, fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal) },
                    )
                }
            }

            // ── Pager ──────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (ChaoxingSubTab.entries[page]) {
                    ChaoxingSubTab.COURSES -> CoursesTabContent(
                        viewModel = viewModel,
                        loginState = loginState,
                        coursesState = coursesState,
                        onShowStudySheet = { showStudySheet = true },
                        onCourseClick = { selectedCourse = it; showDetail = true },
                        onNavigateToSettings = {
                            selectedTab = ChaoxingSubTab.SETTINGS.ordinal
                            scope.launch { pagerState.animateScrollToPage(ChaoxingSubTab.SETTINGS.ordinal) }
                        },
                        listState = coursesListState,
                    )
                    ChaoxingSubTab.HOMEWORK -> HomeworkTabContent(
                        viewModel = viewModel,
                        loginState = loginState,
                        onHomeworkClick = { hw ->
                            selectedHomework = hw
                            showHomeworkDetail = true
                        },
                    )
                    ChaoxingSubTab.MESSAGES -> MessagesTabContent(
                        viewModel = viewModel,
                        loginState = loginState,
                    )
                    ChaoxingSubTab.SETTINGS -> ChaoxingSettingsScreen(
                        loginState = loginState,
                        settingsState = settingsState,
                        viewModel = viewModel,
                        onBack = { /* 在 tab 内无需 back */ },
                        onLogout = { viewModel.logout() },
                        onLogin = { u, p -> viewModel.login(u, p) },
                        isEmbedded = true,
                    )
                }
            }
        }
    }

// ══════════════════════════════════════════════════════════════
//  消息 Tab (2026-06-21)
// ══════════════════════════════════════════════════════════════
