package com.ahu_plus.ui.screen.cengke

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.model.jwapp.CengCourse
import com.ahu_plus.data.model.jwapp.CengCourseDetail
import com.ahu_plus.data.model.jwapp.TimeSlot
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CollapsibleSection
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
private const val CENG_KE_DAYS_AHEAD = 365L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CengKeScreen(
    viewModel: CengKeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    if (!state.loggedIn) {
        CengKeLogin(state, viewModel, onBack)
    } else {
        CengKeContent(state, viewModel, onBack)
    }

    if (state.accountChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("选择教务账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accountChoices.forEach { account ->
                        OutlinedButton(
                            onClick = { viewModel.chooseAccount(account) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(account.displayName().ifBlank { "账号 ${account.id.orEmpty()}" })
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CengKeLogin(
    state: CengKeUiState,
    viewModel: CengKeViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("蹭课") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("教务系统登录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "蹭课数据来自教务移动端教室占用,与「教室课表」共用登录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("教务账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("教务系统密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.loginError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = viewModel::login,
                enabled = !state.loginLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loginLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CengKeContent(
    state: CengKeUiState,
    viewModel: CengKeViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("蹭课") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出教务平台")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (state.metaLoading && state.campuses.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CampusSection(state, viewModel)
            DateSection(state, viewModel)
            val selectedFilterCount = state.selectedBuildingIds.size +
                state.selectedRoomTypeIds.size +
                state.selectedSlots.size +
                state.selectedColleges.size
            CollapsibleSection(
                title = "筛选项",
                badge = if (selectedFilterCount == 0) "默认" else "$selectedFilterCount 项",
            ) {
                BuildingSection(state, viewModel)
                RoomTypeSection(state, viewModel)
                TimeSlotSection(state, viewModel)
                if (state.colleges.isNotEmpty()) CollegeSection(state, viewModel)
            }

            Spacer(Modifier.height(8.dp))
            PickButton(state, viewModel)

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            RecommendationArea(state, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 小节标题 + FlowRow 芯片组的通用外壳。 */
@Composable
private fun FilterSection(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
@Composable
private fun CampusSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    FilterSection("校区") {
        state.campuses.forEach { campus ->
            ChoiceChip(
                selected = state.selectedCampusId == campus.id,
                label = campus.nameZh,
                onClick = { viewModel.selectCampus(campus.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val today = DebugClock.todayDate()
    val tomorrow = today.plusDays(1)
    val dayAfter = today.plusDays(2)
    val label = buildString {
        append(state.selectedDate.format(dateFormatter))
        when (state.selectedDate) {
            today -> append(" (今天)")
            tomorrow -> append(" (明天)")
            dayAfter -> append(" (后天)")
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
            ChoiceChip(state.selectedDate == today, "今天", { viewModel.selectDate(today) })
            ChoiceChip(state.selectedDate == tomorrow, "明天", { viewModel.selectDate(tomorrow) })
            ChoiceChip(state.selectedDate == dayAfter, "后天", { viewModel.selectDate(dayAfter) })
        }
    }

    if (showPicker) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val maxDate = today.plusDays(CENG_KE_DAYS_AHEAD)
        val maxMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in todayMillis..maxMillis
                override fun isSelectableYear(year: Int): Boolean = year in today.year..maxDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (picked in today..maxDate) viewModel.selectDate(picked)
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
@Composable
private fun BuildingSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    val subtitle = when {
        state.buildings.isEmpty() -> null
        state.selectedBuildingIds.isEmpty() -> "不选 = 整校区"
        else -> "已选 ${state.selectedBuildingIds.size}"
    }
    FilterSection("教学楼", subtitle) {
        if (state.buildings.isEmpty()) {
            Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.buildings.forEach { building ->
                ChoiceChip(
                    selected = building.id in state.selectedBuildingIds,
                    label = building.nameZh,
                    onClick = { viewModel.toggleBuilding(building.id) },
                )
            }
        }
    }
}

@Composable
private fun RoomTypeSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    if (state.roomTypes.isEmpty()) return
    val subtitle = if (state.selectedRoomTypeIds.isEmpty()) "不选 = 全部" else "已选 ${state.selectedRoomTypeIds.size}"
    FilterSection("教室类型", subtitle) {
        state.roomTypes.forEach { type ->
            ChoiceChip(
                selected = type.id in state.selectedRoomTypeIds,
                label = type.nameZh,
                onClick = { viewModel.toggleRoomType(type.id) },
            )
        }
    }
}

@Composable
private fun TimeSlotSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    val subtitle = if (state.selectedSlots.isEmpty()) "不选 = 全天" else null
    FilterSection("时段", subtitle) {
        TimeSlot.entries.forEach { slot ->
            ChoiceChip(
                selected = slot in state.selectedSlots,
                label = slot.label,
                onClick = { viewModel.toggleTimeSlot(slot) },
            )
        }
    }
}

@Composable
private fun CollegeSection(state: CengKeUiState, viewModel: CengKeViewModel) {
    val subtitle = if (state.selectedColleges.isEmpty()) "不选 = 全部" else "已选 ${state.selectedColleges.size}"
    FilterSection("开课学院", subtitle) {
        state.colleges.forEach { college ->
            ChoiceChip(
                selected = college in state.selectedColleges,
                label = college,
                onClick = { viewModel.toggleCollege(college) },
            )
        }
    }
}
@Composable
private fun PickButton(state: CengKeUiState, viewModel: CengKeViewModel) {
    Button(
        onClick = viewModel::pickCourse,
        enabled = state.hasCampus && !state.picking,
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        if (state.picking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.recommended == null) "帮我选一门课" else "重新挑选")
        }
    }
}

@Composable
private fun RecommendationArea(state: CengKeUiState, viewModel: CengKeViewModel) {
    when {
        state.recommended != null -> RecommendationCard(state.recommended, state, viewModel)
        state.noMatch -> {
            Spacer(Modifier.height(12.dp))
            Text(
                "当前筛选下没有可蹭的课,试试放宽时段或学院。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun RecommendationCard(course: CengCourse, state: CengKeUiState, viewModel: CengKeViewModel) {
    Spacer(Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                course.courseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            InfoRow(Icons.Filled.Person, course.teacher.ifBlank { "教师未知" })
            val location = listOfNotNull(
                course.campusName?.takeIf { it.isNotBlank() },
                course.buildingName?.takeIf { it.isNotBlank() },
                course.roomName.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (location.isNotBlank()) InfoRow(Icons.Filled.LocationOn, location)
            InfoRow(
                Icons.Filled.CalendarMonth,
                "${course.date}  ${course.startTimeString}-${course.endTimeString}  ${course.timeSlot.label}",
            )
            if (course.college.isNotBlank()) InfoRow(Icons.Filled.School, course.college)
            Spacer(Modifier.height(4.dp))
            Text(
                "课程号 ${course.courseCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            EnrichmentSection(state.recommendedDetail, state.detailLoading)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::reshuffle, enabled = !state.picking) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("换一个")
                }
                Text(
                    "候选 ${state.filteredSize} 节",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

/**
 * 富化区(开课查询补全的详情)。三态:
 *  - 加载中:小 spinner + 提示文字
 *  - 有详情:分隔线 + 选课人数(满员标红)+ 学分·性质·类型 + 面向班级 + 考核·语言
 *  - 无详情且不在加载:整块不渲染(静默,卡片保持蹭课原样)
 */
@Composable
private fun EnrichmentSection(detail: CengCourseDetail?, loading: Boolean) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    when {
        loading -> {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = onContainer.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在补充课程详情…",
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.6f),
                )
            }
        }
        detail != null && detail.hasAny -> {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            // 选课人数(满员标红 + "已满"徽标)
            detail.enrollmentText()?.let { text ->
                val full = detail.isFull()
                val color = if (full) MaterialTheme.colorScheme.error else onContainer
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                    if (full) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已满",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            // 学分 · 性质 · 类型
            DetailLine(
                listOfNotNull(
                    detail.credits?.let { "${formatCredits(it)} 学分" },
                    detail.courseProperty,
                    detail.courseType,
                ),
                onContainer,
            )
            // 面向班级
            detail.className?.takeIf { it.isNotBlank() }?.let {
                DetailLine(listOf("面向 $it"), onContainer)
            }
            // 考核 · 语言
            DetailLine(listOfNotNull(detail.examMode, detail.teachLang), onContainer)
        }
    }
}

/** 富化补充信息的一行(片段用 · 连接);片段为空则不渲染。 */
@Composable
private fun DetailLine(segments: List<String>, color: androidx.compose.ui.graphics.Color) {
    if (segments.isEmpty()) return
    Text(
        segments.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = color.copy(alpha = 0.85f),
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** 学分去掉整数的 .0 尾巴(3.0 → "3"),否则原样(1.5 → "1.5")。 */
private fun formatCredits(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
