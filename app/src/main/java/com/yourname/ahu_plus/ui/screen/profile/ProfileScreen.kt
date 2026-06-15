package com.yourname.ahu_plus.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoCodeLookup
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketIdentityEditor
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProfileScreen(
    cardViewModel: HomeViewModel,
    marketViewModel: MarketViewModel,
    studentInfoViewModel: StudentInfoViewModel,
    scheduleUiState: ScheduleUiState,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLogout: () -> Unit
) {
    var showBills by rememberSaveable { mutableStateOf(false) }
    var showMarketSettings by rememberSaveable { mutableStateOf(false) }
    var showStudentInfo by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val cardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val marketUiState by marketViewModel.uiState.collectAsStateWithLifecycle()
    val studentInfoUiState by studentInfoViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(cardUiState.needsLogin) {
        if (cardUiState.needsLogin) onLogout()
    }
    LaunchedEffect(studentInfoUiState.needsLogin) {
        if (studentInfoUiState.needsLogin) onLogout()
    }

    if (showBills) {
        BillDetailScreen(
            bills = cardUiState.bills,
            isLoading = cardUiState.billsLoading,
            error = cardUiState.billsError,
            onBack = { showBills = false },
            onRefresh = cardViewModel::onRefresh
        )
    } else if (showMarketSettings) {
        MarketIdentitySettingsScreen(
            uiState = marketUiState,
            onIdentityChanged = marketViewModel::onIdentityInputChanged,
            onSave = marketViewModel::saveIdentity,
            onClear = marketViewModel::clearIdentity,
            onBack = { showMarketSettings = false }
        )
    } else if (showStudentInfo) {
        StudentInfoDetailScreen(
            uiState = studentInfoUiState,
            onBack = { showStudentInfo = false },
            onRefresh = studentInfoViewModel::refreshStudentInfo
        )
    } else if (showSettings) {
        AppSettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBack = { showSettings = false }
        )
    } else {
        val studentInfo = studentInfoUiState.info
        ProfileHomeScreen(
            studentName = studentInfo?.displayName() ?: scheduleUiState.studentName,
            department = studentInfo?.department() ?: scheduleUiState.department,
            className = studentInfo?.classOrMajor() ?: scheduleUiState.className,
            studentInfoLoading = studentInfoUiState.isLoading,
            studentInfoError = studentInfoUiState.error,
            hasStudentInfo = studentInfo != null,
            balance = cardUiState.balance,
            balanceLoading = cardUiState.isLoading,
            balanceError = cardUiState.error,
            timestamp = cardUiState.timestamp,
            marketSchool = marketUiState.school,
            hasMarketIdentity = marketUiState.hasSavedIdentity,
            onRefresh = {
                cardViewModel.onRefresh()
            },
            onOpenBills = { showBills = true },
            onOpenStudentInfo = { showStudentInfo = true },
            onOpenMarketSettings = { showMarketSettings = true },
            themeMode = themeMode,
            onOpenSettings = { showSettings = true },
            onLogout = onLogout
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHomeScreen(
    studentName: String?,
    department: String?,
    className: String?,
    studentInfoLoading: Boolean,
    studentInfoError: String?,
    hasStudentInfo: Boolean,
    balance: Double,
    balanceLoading: Boolean,
    balanceError: String?,
    timestamp: Long,
    marketSchool: String?,
    hasMarketIdentity: Boolean,
    onRefresh: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenStudentInfo: () -> Unit,
    onOpenMarketSettings: () -> Unit,
    themeMode: AppThemeMode,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = studentName?.takeIf { it.isNotBlank() } ?: "未命名同学"
    val subtitle = listOfNotNull(
        department?.takeIf { it.isNotBlank() },
        className?.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { "学生信息接口待接入" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeader(
                    displayName = displayName,
                    subtitle = subtitle
                )
            }

            item {
                BalanceCard(
                    balance = balance,
                    isLoading = balanceLoading,
                    error = balanceError,
                    timestamp = timestamp,
                    onClick = onOpenBills
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "集市身份字段",
                        description = if (hasMarketIdentity) {
                            marketSchool?.let { "已配置：$it" } ?: "已配置，可在这里修改"
                        } else {
                            "未配置，填写后即可浏览校园集市"
                        },
                        iconColor = Color(0xFFB7791F),
                        icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                        onClick = onOpenMarketSettings
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "我的信息",
                        description = when {
                            hasStudentInfo -> "学生基本信息与住宿数据"
                            studentInfoLoading -> "正在从学生一张表同步..."
                            studentInfoError != null -> studentInfoError
                            else -> "本地无缓存，进入后点击「更新数据」同步"
                        },
                        iconColor = Color(0xFF2F80ED),
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = onOpenStudentInfo
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "设置",
                        description = "外观：${themeMode.titleText()}",
                        iconColor = Color(0xFF6C63FF),
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = onOpenSettings
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "退出登录",
                        description = "清除本地凭据与登录会话",
                        iconColor = MaterialTheme.colorScheme.error,
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = onLogout
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProfileSection {
                    Column {
                        Text(
                            text = "外观",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppThemeMode.entries.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider()
                            ThemeModeRow(
                                themeMode = option,
                                selected = themeMode == option,
                                onClick = { onThemeModeChange(option) }
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ThemeModeRow(
    themeMode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = themeMode.titleText(),
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(text = themeMode.descriptionText())
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun AppThemeMode.titleText(): String = when (this) {
    AppThemeMode.DAY -> "白天"
    AppThemeMode.DARK -> "深色"
    AppThemeMode.SYSTEM -> "跟随系统"
}

private fun AppThemeMode.descriptionText(): String = when (this) {
    AppThemeMode.DAY -> "始终使用浅色界面"
    AppThemeMode.DARK -> "始终使用深色界面"
    AppThemeMode.SYSTEM -> "根据系统深色模式自动切换"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentInfoDetailScreen(
    uiState: StudentInfoUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                uiState.isLoading && uiState.info == null -> {
                    item { LoadingBlock("正在加载个人信息...") }
                }
                uiState.error != null && uiState.info == null -> {
                    item { ErrorBlock(error = uiState.error, onRefresh = onRefresh) }
                }
                uiState.info == null -> {
                    item {
                        StudentInfoEmptyWithUpdate(
                            isLoading = uiState.isLoading,
                            onRefresh = onRefresh
                        )
                    }
                }
                else -> {
                    val info = uiState.info!!
                    item {
                        StudentInfoSection(
                            title = "学生基本信息",
                            fields = info.basicFields
                        )
                    }
                    item {
                        StudentInfoSection(
                            title = "住宿数据",
                            fields = info.housingFields
                        )
                    }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = uiState.lastUpdatedAt,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StudentInfoEmptyWithUpdate(
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    ProfileSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "本地暂无个人信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "点击下方按钮从学生一张表同步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text("更新数据")
            }
        }
    }
}

@Composable
private fun StudentInfoFooter(
    lastUpdatedAt: Long,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (lastUpdatedAt > 0) {
            Text(
                text = studentInfoUpdatedText(lastUpdatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )
        }
        if (error != null) {
            Text(
                text = "更新失败:$error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Button(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.size(8.dp))
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text("更新数据")
        }
    }
}

private fun studentInfoUpdatedText(timestamp: Long): String {
    val text = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
    return if (text.isBlank()) "本地缓存" else "最后更新于 $text"
}

@Composable
private fun StudentInfoSection(
    title: String,
    fields: List<StudentInfoField>
) {
    val displayFields = fields.mapNotNull { it.toDisplayField() }
    ProfileSection {
        Column {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (displayFields.isEmpty()) {
                Text(
                    text = "暂无数据",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayFields.forEachIndexed { index, field ->
                    if (index > 0) HorizontalDivider()
                    StudentInfoRow(field = field)
                }
            }
        }
    }
}

/**
 * 把原始字段转换成可直接展示的字段:
 * - 命中隐藏黑名单(数据来源) → 返回 null
 * - 命中码值字典(性别码/民族码/...) → 解码标签与值
 * - 其他原样返回
 */
private fun StudentInfoField.toDisplayField(): StudentInfoField? {
    if (StudentInfoCodeLookup.isHiddenField(label)) return null
    val decoded = StudentInfoCodeLookup.decode(label, value)
    return if (decoded != null) StudentInfoField(decoded.first, decoded.second) else this
}

@Composable
private fun StudentInfoRow(field: StudentInfoField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = field.value,
            modifier = Modifier
                .weight(0.62f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketIdentitySettingsScreen(
    uiState: com.yourname.ahu_plus.ui.screen.market.MarketUiState,
    onIdentityChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("集市身份字段") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MarketIdentityEditor(
                    uiState = uiState,
                    onIdentityChanged = onIdentityChanged,
                    onSave = onSave,
                    onClear = onClear
                )
            }
            item {
                Text(
                    text = "字段保存后，集市页会自动隐藏输入框。字段失效时可回到这里重新粘贴。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    isLoading: Boolean,
    error: String?,
    timestamp: Long,
    onClick: () -> Unit
) {
    val formatter = DecimalFormat("¥#,##0.00")
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A9D8F).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color(0xFF2A9D8F)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "我的校园卡余额",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = updatedText(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                isLoading && balance == 0.0 -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                error != null && balance == 0.0 -> {
                    Text(
                        text = "获取失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        text = formatter.format(balance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ProfileSection(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    iconColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconColor
                ) {
                    icon()
                }
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillDetailScreen(
    bills: List<BillRecord>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园卡账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            when {
                isLoading && bills.isEmpty() -> {
                    item {
                        LoadingBlock("正在加载账单...")
                    }
                }
                error != null && bills.isEmpty() -> {
                    item {
                        ErrorBlock(error = error, onRefresh = onRefresh)
                    }
                }
                bills.isEmpty() -> {
                    item {
                        EmptyBlock("暂无账单记录")
                    }
                }
                else -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                bills.forEachIndexed { index, bill ->
                                    BillRow(bill = bill)
                                    if (index != bills.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun BillRow(bill: BillRecord) {
    val typeText = (bill.turnoverType + " " + bill.consumeTypeName.orEmpty()).trim()
    val isPayment = when {
        bill.tranAmt < 0 -> true
        typeText.contains("充值") -> false
        typeText.contains("退款") || typeText.contains("退费") -> false
        typeText.contains("转入") || typeText.contains("入账") -> false
        typeText.contains("消费") || typeText.contains("支付") || typeText.contains("扣款") -> true
        else -> false
    }
    val amount = abs(bill.tranAmt) / 100.0
    val formatter = DecimalFormat("¥#0.00")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isPayment) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else Color(0xFF2A9D8F).copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPayment) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = bill.resume.ifBlank { bill.turnoverType.ifBlank { "校园卡交易" } },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bill.effectDateStr.ifBlank { bill.jndatetimeStr },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isPayment) "-${formatter.format(amount)}" else "+${formatter.format(amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
        )
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBlock(error: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(onClick = onRefresh) {
            Text("重试")
        }
    }
}

@Composable
private fun EmptyBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun updatedText(timestamp: Long): String {
    if (timestamp <= 0) return "点击查看账单明细"
    val text = try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
    return if (text.isBlank()) "点击查看账单明细" else "更新于 $text · 点击查看账单"
}

private fun StudentInfo.displayName(): String? {
    return firstValueOf("姓名", "学生姓名", "本人姓名")
}

private fun StudentInfo.department(): String? {
    return firstValueOf("学院", "院系", "院系名称", "所在院系", "培养单位")
}

private fun StudentInfo.classOrMajor(): String? {
    val major = firstValueOf("专业", "专业名称", "所在专业")
    val className = firstValueOf("班级", "行政班", "自然班")
    return listOfNotNull(major, className)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}
