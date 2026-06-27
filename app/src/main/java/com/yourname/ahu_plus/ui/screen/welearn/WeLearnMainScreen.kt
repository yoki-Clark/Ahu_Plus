package com.yourname.ahu_plus.ui.screen.welearn

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.WeLearnCourse

/**
 * WeLearn 主屏:登录态感知。
 *  - 未登录 → 登录卡(账密输入 + 登录按钮 + 提示文案)
 *  - 已登录 → 课程列表(点击进 StudyScreen)
 *
 * 仿照 ChaoxingTabScreen 的两段式结构,但 WeLearn 没那么多二级 tab。
 * 2026-06-27 新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeLearnMainScreen(
    viewModel: WeLearnViewModel,
    onCourseClick: (WeLearnCourse) -> Unit,
) {
    val isLoggedIn = remember { mutableStateOf(viewModel.isLoggedIn) }
    val coursesState by viewModel.coursesState.collectAsState()
    val loggingIn by viewModel.loggingIn.collectAsState()
    val lastLoginError by viewModel.lastLoginError.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showLoginSheet by rememberSaveable { mutableStateOf(!isLoggedIn.value) }

    // 登录成功后自动切到列表
    LaunchedEffect(loggingIn, isLoggedIn.value) {
        if (!loggingIn && lastLoginError == null && viewModel.isLoggedIn) {
            isLoggedIn.value = true
            showLoginSheet = false
            viewModel.consumeLoginResult()
        }
    }

    LaunchedEffect(Unit) {
        if (isLoggedIn.value) viewModel.refreshCourses()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("WeLearn 随行课堂", fontWeight = FontWeight.Bold) },
                actions = {
                    if (isLoggedIn.value) {
                        IconButton(onClick = { viewModel.refreshCourses() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !isLoggedIn.value || showLoginSheet -> {
                    LoginCard(
                        submitting = loggingIn,
                        errorMsg = lastLoginError,
                        onLogin = { u, p -> viewModel.login(u, p) },
                    )
                }
                else -> CourseList(
                    state = coursesState,
                    onCourseClick = onCourseClick,
                    onRefresh = { viewModel.refreshCourses() },
                    onLogout = {
                        viewModel.clearLogin()
                        isLoggedIn.value = false
                        showLoginSheet = true
                    },
                )
            }
        }
    }

    LaunchedEffect(coursesState.error) {
        coursesState.error?.takeIf { it.isNotBlank() }?.let {
            snackbar.showSnackbar(it)
        }
    }
}

@Composable
private fun LoginCard(
    submitting: Boolean,
    errorMsg: String?,
    onLogin: (String, String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Login,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("WeLearn 随行课堂", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "外教社 welearn.sflep.com (与超星/校园账号无关)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("账号 (手机号)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        errorMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                android.util.Log.i("WeLearnUI", "login button tapped u='$username' p.len=${password.length}")
                onLogin(username, password)
            },
            enabled = !submitting && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("登录", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("⚠ 提示", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "本工具仅完成 SCORM 章节(完成度 + 正确率),不做作业答题。\n" +
                        "如需答题请配合 WELearnHelper 油猴脚本使用。\n" +
                        "请遵守学校学术诚信规定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CourseList(
    state: WeLearnViewModel.CoursesUiState,
    onCourseClick: (WeLearnCourse) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    if (state.loading && state.courses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.courses.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("暂无课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRefresh) { Text("刷新") }
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onLogout) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.courses, key = { it.cid }) { course ->
            CourseRow(course = course, onClick = { onCourseClick(course) })
        }
        item {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CourseRow(course: WeLearnCourse, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (course.per >= 100) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(course.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { course.per / 100f },
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${course.per}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}