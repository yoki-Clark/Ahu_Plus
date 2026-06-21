package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.CxCourse
import com.yourname.ahu_plus.ui.components.AhuShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingMainScreen(
    viewModel: ChaoxingViewModel,
    onBack: () -> Unit,
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()

    var showDetail by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<CxCourse?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showStudySheet by remember { mutableStateOf(false) }
    var showStudyScreen by remember { mutableStateOf(false) }

    // 返回逻辑：子页面 → 主页面 → 退出模块
    BackHandler {
        when {
            showStudyScreen -> showStudyScreen = false
            showSettings -> showSettings = false
            showDetail -> showDetail = false
            else -> onBack()
        }
    }

    when {
        showStudyScreen -> {
            ChaoxingStudyScreen(
                studyState = studyState,
                onStop = { viewModel.stopStudy() },
                onBack = { showStudyScreen = false },
            )
        }
        showSettings -> {
            ChaoxingSettingsScreen(
                loginState = loginState,
                settingsState = settingsState,
                viewModel = viewModel,
                onBack = { showSettings = false },
                onLogout = { viewModel.logout(); showSettings = false },
                onLogin = { u, p -> viewModel.login(u, p) },
            )
        }
        showDetail && selectedCourse != null -> {
            ChaoxingCourseDetailScreen(
                viewModel = viewModel,
                course = selectedCourse!!,
                onBack = { showDetail = false },
                onStartStudy = { course ->
                    viewModel.studySingleCourse(course)
                    showStudyScreen = true
                },
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("超星学习通") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.loadCourses() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                        },
                    )
                },
                floatingActionButton = {
                    val selected = coursesState.selectedCourseIds.size
                    if (selected > 0) {
                        FloatingActionButton(
                            onClick = { showStudySheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("学习 ($selected)")
                            }
                        }
                    }
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    // 账户卡片
                    item {
                        Spacer(Modifier.height(8.dp))
                        AccountCard(
                            courseCount = coursesState.courses.size,
                            onLogout = { viewModel.logout() },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // 课程列表
                    when {
                        coursesState.isLoading -> {
                            item {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        coursesState.error != null -> {
                            item {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(coursesState.error!!, color = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.height(16.dp))
                                        OutlinedButton(onClick = { viewModel.loadCourses() }) { Text("重试") }
                                    }
                                }
                            }
                        }
                        else -> {
                            items(coursesState.courses, key = { it.courseId + "_" + it.clazzId }) { course ->
                                val key = course.courseId + "_" + course.clazzId
                                CourseCard(
                                    course = course,
                                    isSelected = coursesState.selectedCourseIds.contains(key),
                                    onToggle = { viewModel.toggleCourseSelection(key) },
                                    onClick = { selectedCourse = course; showDetail = true },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            if (showStudySheet) {
                ChaoxingStudySheet(
                    courses = viewModel.getSelectedCourses(),
                    settingsState = settingsState,
                    onConfirm = {
                        showStudySheet = false
                        viewModel.studyCourses(viewModel.getSelectedCourses())
                        showStudyScreen = true
                    },
                    onDismiss = { showStudySheet = false },
                )
            }
        }
    }
}

@Composable
private fun AccountCard(courseCount: Int, onLogout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("超星学习通", style = MaterialTheme.typography.titleSmall)
                Text("$courseCount 门课程", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "退出", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: CxCourse,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(course.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                if (course.teacher.isNotBlank()) {
                    Text(course.teacher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
