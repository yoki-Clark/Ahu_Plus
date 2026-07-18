package com.ahu_plus.ui.screen.roomcoursetable

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ahu_plus.data.model.jwapp.JwAppRoom
import com.ahu_plus.data.model.jwapp.JwAppSemester
import com.ahu_plus.ui.screen.schedule.FixedTimeColumn
import com.ahu_plus.ui.screen.schedule.WeekGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCourseTableScreen(
    viewModel: RoomCourseTableViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler {
        if (state.selectedRoom != null) viewModel.backToRooms() else onBack()
    }

    when {
        !state.loggedIn -> RoomCourseTableLogin(state, viewModel, onBack)
        state.selectedRoom != null -> RoomSchedulePage(state, viewModel)
        else -> RoomListPage(state, viewModel, onBack)
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
private fun RoomCourseTableLogin(
    state: RoomCourseTableUiState,
    viewModel: RoomCourseTableViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教室课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("教务系统登录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
private fun RoomListPage(
    state: RoomCourseTableUiState,
    viewModel: RoomCourseTableViewModel,
    onBack: () -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教室课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshRooms) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出教务平台")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.filter.name,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("教室名称或代码") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.filter.name.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchChange(""); viewModel.submitSearch() }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清空")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showFilters = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "筛选")
                }
            }
            Text(
                text = "已加载 ${state.rooms.size} / ${state.totalRooms} 间",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.initialLoading || (state.roomsLoading && state.rooms.isEmpty()) ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.rooms.isEmpty() -> ErrorContent(state.error, viewModel::refreshRooms)
                state.rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有匹配的教室") }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rooms, key = { it.id }) { room ->
                        RoomRow(room = room, onClick = { viewModel.openRoom(room) })
                    }
                    if (state.currentPage < state.totalPages) {
                        item(key = "load-more") {
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        RoomFilterDialog(state, viewModel, onDismiss = { showFilters = false })
    }
}

@Composable
private fun RoomRow(room: JwAppRoom, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(room.nameZh, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val location = listOfNotNull(room.building?.campus?.nameZh, room.building?.nameZh)
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (location.isNotBlank()) {
                Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val meta = buildList {
                room.floor?.let { add("${it}层") }
                room.roomType?.nameZh?.let(::add)
                room.seatsForLesson?.let { add("$it 座") }
                room.code?.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoomFilterDialog(
    state: RoomCourseTableUiState,
    viewModel: RoomCourseTableViewModel,
    onDismiss: () -> Unit,
) {
    var floor by remember { mutableStateOf(state.filter.floor?.toString().orEmpty()) }
    var seatsLower by remember { mutableStateOf(state.filter.seatsLower?.toString().orEmpty()) }
    var seatsUpper by remember { mutableStateOf(state.filter.seatsUpper?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选教室") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectorField(
                    label = "校区",
                    value = state.campuses.firstOrNull { it.id == state.filter.campusId }?.nameZh ?: "全部校区",
                    options = listOf(null to "全部校区") + state.campuses.map { it.id to it.nameZh },
                    onSelect = viewModel::setCampus,
                )
                SelectorField(
                    label = "教学楼",
                    value = state.buildings.firstOrNull { it.id == state.filter.buildingId }?.nameZh ?: "全部教学楼",
                    options = listOf(null to "全部教学楼") + state.buildings.map { it.id to it.nameZh },
                    onSelect = viewModel::setBuilding,
                    enabled = state.filter.campusId != null,
                )
                SelectorField(
                    label = "教室类型",
                    value = state.roomTypes.firstOrNull { it.id == state.filter.roomTypeId }?.nameZh ?: "全部类型",
                    options = listOf(null to "全部类型") + state.roomTypes.map { it.id to it.nameZh },
                    onSelect = viewModel::setRoomType,
                )
                NumberField("楼层", floor, { floor = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("最少座位", seatsLower, { seatsLower = it }, Modifier.weight(1f))
                    NumberField("最多座位", seatsUpper, { seatsUpper = it }, Modifier.weight(1f))
                }
                CheckRow("仅显示本学期有课教室", state.filter.occupied == true, viewModel::setOccupiedOnly)
                CheckRow("包含线上教室", state.filter.includeVirtual, viewModel::setIncludeVirtual)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setFloor(floor)
                viewModel.setSeatsLower(seatsLower)
                viewModel.setSeatsUpper(seatsUpper)
                viewModel.applyFilters()
                onDismiss()
            }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.resetFilters(); onDismiss() }) { Text("重置") }
        },
    )
}

@Composable
private fun SelectorField(
    label: String,
    value: String,
    options: List<Pair<Int?, String>>,
    onSelect: (Int?) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label：$value", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSchedulePage(state: RoomCourseTableUiState, viewModel: RoomCourseTableViewModel) {
    val schedule = state.schedule
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedRoom?.nameZh ?: "教室课表", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = viewModel::backToRooms) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回教室列表")
                    }
                },
                actions = {
                    IconButton(onClick = { state.selectedRoom?.let(viewModel::openRoom) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SemesterSelector(
                    semesters = state.semesters,
                    selected = state.selectedSemester,
                    onSelect = viewModel::selectSemester,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.setSelectedWeek(state.selectedWeek - 1) },
                    enabled = schedule != null && state.selectedWeek > (schedule.weekIndices.minOrNull() ?: 1),
                ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周") }
                IconButton(
                    onClick = { viewModel.setSelectedWeek(state.selectedWeek + 1) },
                    enabled = schedule != null && state.selectedWeek < (schedule.weekIndices.maxOrNull() ?: 1),
                ) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一周") }
            }
            val isCurrentSemester = state.selectedSemester?.id == state.currentSemesterId
            Text(
                "第 ${state.selectedWeek} 周" + if (isCurrentSemester && schedule?.currentWeek == state.selectedWeek) " · 当前周" else "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            when {
                state.scheduleLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.scheduleError != null -> ErrorContent(state.scheduleError) {
                    state.selectedRoom?.let(viewModel::openRoom)
                }
                schedule == null -> Unit
                else -> {
                    val items = remember(schedule.displayItems, state.selectedWeek) {
                        schedule.displayItems.filter { state.selectedWeek in it.weekIndexes }
                    }
                    if (items.isEmpty()) {
                        Text(
                            "本周无课程",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val scroll = rememberScrollState()
                    Row(Modifier.fillMaxSize()) {
                        FixedTimeColumn(
                            unitTimes = schedule.unitTimes,
                            rowHeight = 56.dp,
                            fontScale = 1f,
                            verScroll = scroll,
                        )
                        WeekGrid(
                            displayItems = items,
                            unitTimes = schedule.unitTimes,
                            selectedWeek = state.selectedWeek,
                            currentWeek = if (isCurrentSemester) schedule.currentWeek else -1,
                            onCourseClick = viewModel::selectCourse,
                            modifier = Modifier.weight(1f),
                            rowHeight = 56.dp,
                            sharedVerScroll = scroll,
                            semesterStartDate = schedule.semester.startDate?.let { date ->
                                runCatching { java.time.LocalDate.parse(date) }.getOrNull()
                            },
                        )
                    }
                }
            }
        }
    }
    state.selectedCourse?.let { item ->
        val lesson = viewModel.selectedLesson()
        AlertDialog(
            onDismissRequest = viewModel::dismissCourse,
            title = { Text(item.courseName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("课程代码", item.courseCode)
                    DetailLine("教师", item.teacherNames)
                    DetailLine("时间", "第 ${item.startUnit}-${item.endUnit} 节  ${item.startTime.orEmpty()}-${item.endTime.orEmpty()}")
                    DetailLine("周次", item.weeksStr)
                    DetailLine("开课院系", lesson?.openDepartment?.nameZh)
                    DetailLine("课程类型", lesson?.courseType?.nameZh)
                    DetailLine("选课人数", lesson?.stdCount?.toString())
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissCourse) { Text("关闭") } },
        )
    }
}

@Composable
private fun SemesterSelector(
    semesters: List<JwAppSemester>,
    selected: JwAppSemester?,
    onSelect: (JwAppSemester) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.nameZh ?: "选择学期", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            semesters.forEach { semester ->
                DropdownMenuItem(
                    text = { Text(semester.nameZh) },
                    onClick = { onSelect(semester); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Text("$label：$value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
