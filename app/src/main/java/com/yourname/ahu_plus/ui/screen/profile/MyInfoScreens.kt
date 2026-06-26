package com.yourname.ahu_plus.ui.screen.profile

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.local.AppThemeMode
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.CheckResult
import com.yourname.ahu_plus.data.model.FinanceSummary
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.model.StudentInfoCodeLookup
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.repository.AdwmhQrCode
import com.yourname.ahu_plus.ui.components.AhuInfoRow
import com.yourname.ahu_plus.ui.components.AhuSectionHeader
import com.yourname.ahu_plus.ui.theme.AhuShapes
import com.yourname.ahu_plus.ui.components.AhuStatusCard
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.data.model.ElectricityUiData
import com.yourname.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBillRecord
import com.yourname.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.yourname.ahu_plus.ui.screen.home.ElectricityBillRange
import com.yourname.ahu_plus.ui.screen.home.ElectricityState
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.home.ElectricityTarget
import com.yourname.ahu_plus.ui.screen.home.InternetBalanceCard
import com.yourname.ahu_plus.ui.screen.home.QrCodeFullScreenDialog
import com.yourname.ahu_plus.ui.screen.market.MarketIdentityEditor
import com.yourname.ahu_plus.ui.screen.market.MarketSettingsScreen
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleUiState
import com.yourname.ahu_plus.ui.theme.AhuGreen
import com.yourname.ahu_plus.util.BrowserOpener
import com.yourname.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDetailScreen(
    uiState: FinanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的财务") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                uiState.isLoading && uiState.summary == null -> {
                    item { LoadingBlock("正在加载财务信息...") }
                }
                uiState.error != null && uiState.summary == null -> {
                    item { ErrorBlock(error = uiState.error, onRefresh = onRefresh) }
                }
                uiState.summary == null -> {
                    item {
                        FinanceEmptyWithUpdate(
                            isLoading = uiState.isLoading,
                            onRefresh = onRefresh
                        )
                    }
                }
                else -> {
                    val s = uiState.summary!!
                    item { StudentInfoSection("奖学金数据", s.scholarship) }
                    item { StudentInfoSection("助学金数据", s.grant) }
                    item { StudentInfoSection("临时困难补助数据", s.hardshipGrant) }
                    item { StudentInfoSection("勤工助学数据", s.workStudy) }
                    item { StudentInfoSection("欠费状态", s.arrearsStatus) }
                    item { StudentInfoSection("贷款数据", s.loan) }
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
private fun FinanceEmptyWithUpdate(
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
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "本地暂无财务数据",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyInfoHubScreen(
    studentInfoUiState: StudentInfoUiState,
    financeUiState: FinanceUiState,
    onBack: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenBasicInfo: () -> Unit,
    onOpenHousing: () -> Unit,
    onOpenAcademicWarning: () -> Unit,
    onOpenFinance: () -> Unit
) {
    val info = studentInfoUiState.info
    val basicCount = info?.basicFields?.size ?: 0
    val housingCount = info?.housingFields?.size ?: 0
    val warningCount = info?.academicWarningFields?.size ?: 0
    val financeCount = financeUiState.summary?.let { s ->
        s.scholarship.size + s.grant.size + s.hardshipGrant.size +
            s.workStudy.size + s.loan.size + s.arrearsStatus.size
    } ?: 0
    val isRefreshing = studentInfoUiState.isLoading || financeUiState.isLoading

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ProfileSection {
                    Column {
                        MyInfoHubRow(
                            title = "学生基本信息",
                            summary = if (basicCount > 0) "$basicCount 项" else "暂无数据",
                            icon = Icons.Filled.Person,
                            iconColor = Color(0xFF2F80ED),
                            onClick = onOpenBasicInfo
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "住宿数据",
                            summary = if (housingCount > 0) "$housingCount 项" else "暂无数据",
                            icon = Icons.Filled.Info,
                            iconColor = Color(0xFF27AE60),
                            onClick = onOpenHousing
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "学业预警信息",
                            summary = if (warningCount > 0) "$warningCount 项" else "无记录",
                            icon = Icons.Filled.Info,
                            iconColor = Color(0xFFE67E22),
                            onClick = onOpenAcademicWarning
                        )
                        HorizontalDivider()
                        MyInfoHubRow(
                            title = "我的财务",
                            summary = if (financeCount > 0) "$financeCount 项" else "暂无数据",
                            icon = Icons.Filled.AccountBalanceWallet,
                            iconColor = Color(0xFFB7791F),
                            onClick = onOpenFinance
                        )

                    }
                }
            }

            // 刷新全部按钮
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onRefreshAll,
                        enabled = !isRefreshing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (isRefreshing) "正在更新数据..." else "更新全部数据")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MyInfoHubRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(AhuShapes.IconBox)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ─── 通用分类详情页（学生基本信息 / 住宿数据 / 学业预警信息）───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    title: String,
    fields: List<StudentInfoField>,
    isLoading: Boolean,
    error: String?,
    lastUpdatedAt: Long,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                isLoading && fields.isEmpty() -> {
                    item { LoadingBlock("正在加载...") }
                }
                error != null && fields.isEmpty() -> {
                    item { ErrorBlock(error = error, onRefresh = onRefresh) }
                }
                fields.isEmpty() -> {
                    item {
                        ProfileSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (title == "学业预警信息") "暂无预警信息" else "暂无数据",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    item { StudentInfoSection(title = title, fields = fields) }
                    item {
                        StudentInfoFooter(
                            lastUpdatedAt = lastUpdatedAt,
                            isLoading = isLoading,
                            error = error,
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
fun StudentInfoFooter(
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

private fun StudentInfoField.toDisplayField(): StudentInfoField? {
    if (StudentInfoCodeLookup.isHiddenField(label)) return null
    val decoded = StudentInfoCodeLookup.decode(label, value)
    return if (decoded != null) StudentInfoField(decoded.first, decoded.second) else this
}

@Composable
private fun StudentInfoRow(field: StudentInfoField) {
    AhuInfoRow(label = field.label, value = field.value)
}

