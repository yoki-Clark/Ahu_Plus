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
fun WaterElectricityUtilityDetailScreen(
    bathroomData: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    bathroomLoading: Boolean,
    bathroomError: String?,
    bathroomPhone: String,
    acState: ElectricityState,
    acBills: List<ElectricityDailyRecord>,
    acBillRange: ElectricityBillRange,
    acBillsLoading: Boolean,
    acBillsError: String?,
    lightingState: ElectricityState,
    lightingBills: List<ElectricityDailyRecord>,
    lightingBillRange: ElectricityBillRange,
    lightingBillsLoading: Boolean,
    lightingBillsError: String?,
    internetData: InternetBalanceData?,
    internetLoading: Boolean,
    internetError: String?,
    internetBills: List<InternetBillRecord>,
    internetBillsLoading: Boolean,
    internetBillsError: String?,
    onBack: () -> Unit,
    onSaveBathroomPhone: (String) -> Unit,
    onSaveElectricityConfig: (ElectricityRoomConfig, ElectricityTarget) -> Unit,
    onRefreshBathroom: () -> Unit,
    onRefreshAcBalance: () -> Unit,
    onRefreshLightingBalance: () -> Unit,
    onRefreshInternetBalance: () -> Unit,
    onRefreshAcBills: () -> Unit,
    onRefreshLightingBills: () -> Unit,
    onRefreshInternetBills: () -> Unit,
    onAcBillRangeSelected: (ElectricityBillRange) -> Unit,
    onLightingBillRangeSelected: (ElectricityBillRange) -> Unit,
    cardViewModel: HomeViewModel,
    initialUtility: String? = null
) {
    var selectedUtility by rememberSaveable { mutableStateOf(initialUtility) }

    LaunchedEffect(Unit) {
        onRefreshAcBills()
        onRefreshLightingBills()
        onRefreshInternetBills()
    }

    when (selectedUtility) {
        "bathroom" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            BathroomUtilityDetailScreen(
                data = bathroomData,
                isLoading = bathroomLoading,
                error = bathroomError,
                phone = bathroomPhone,
                onBack = { selectedUtility = null },
                onSavePhone = onSaveBathroomPhone,
                onRefresh = onRefreshBathroom
            )
            return
        }
        "ac" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            ElectricityUtilityDetailScreen(
                title = "空调余额",
                state = acState,
                target = ElectricityTarget.AC,
                cardViewModel = cardViewModel,
                bills = acBills,
                billRange = acBillRange,
                billsLoading = acBillsLoading,
                billsError = acBillsError,
                onBack = { selectedUtility = null },
                onSaveConfig = onSaveElectricityConfig,
                onRefreshBalance = onRefreshAcBalance,
                onRefreshBills = onRefreshAcBills,
                onBillRangeSelected = onAcBillRangeSelected
            )
            return
        }
        "lighting" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            ElectricityUtilityDetailScreen(
                title = "照明余额",
                state = lightingState,
                target = ElectricityTarget.LIGHTING,
                cardViewModel = cardViewModel,
                bills = lightingBills,
                billRange = lightingBillRange,
                billsLoading = lightingBillsLoading,
                billsError = lightingBillsError,
                onBack = { selectedUtility = null },
                onSaveConfig = onSaveElectricityConfig,
                onRefreshBalance = onRefreshLightingBalance,
                onRefreshBills = onRefreshLightingBills,
                onBillRangeSelected = onLightingBillRangeSelected
            )
            return
        }
        "internet" -> {
            BackHandler(enabled = true) { selectedUtility = null }
            InternetUtilityDetailScreen(
                data = internetData,
                isLoading = internetLoading,
                error = internetError,
                bills = internetBills,
                billsLoading = internetBillsLoading,
                billsError = internetBillsError,
                onBack = { selectedUtility = null },
                onRefreshBalance = onRefreshInternetBalance,
                onRefreshBills = onRefreshInternetBills
            )
            return
        }
    }

    UtilityDetailScaffold(
        title = "水电费查询",
        onBack = onBack,
        onRefresh = {
            onRefreshBathroom()
            onRefreshAcBalance()
            onRefreshLightingBalance()
            onRefreshInternetBalance()
            onRefreshAcBills()
            onRefreshLightingBills()
            onRefreshInternetBills()
        }
    ) {
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "bathroom" }) {
                BathroomBalanceCard(
                    data = bathroomData,
                    isLoading = bathroomLoading,
                    error = bathroomError,
                    phone = bathroomPhone,
                    onSavePhone = onSaveBathroomPhone,
                    onRetry = onRefreshBathroom
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "ac" }) {
                ElectricityBalanceCard(
                    label = "空调余额",
                    state = acState,
                    target = ElectricityTarget.AC,
                    onLoadBuildings = { cardViewModel.loadBuildings(ElectricityTarget.AC) },
                    onSelectBuilding = { cardViewModel.selectBuilding(ElectricityTarget.AC, it) },
                    onSelectFloor = { cardViewModel.selectFloor(ElectricityTarget.AC, it) },
                    onSelectRoom = { cardViewModel.selectRoom(ElectricityTarget.AC, it) },
                    onRetry = onRefreshAcBalance
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "lighting" }) {
                ElectricityBalanceCard(
                    label = "照明余额",
                    state = lightingState,
                    target = ElectricityTarget.LIGHTING,
                    onLoadBuildings = { cardViewModel.loadBuildings(ElectricityTarget.LIGHTING) },
                    onSelectBuilding = { cardViewModel.selectBuilding(ElectricityTarget.LIGHTING, it) },
                    onSelectFloor = { cardViewModel.selectFloor(ElectricityTarget.LIGHTING, it) },
                    onSelectRoom = { cardViewModel.selectRoom(ElectricityTarget.LIGHTING, it) },
                    onRetry = onRefreshLightingBalance
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "internet" }) {
                InternetBalanceCard(
                    data = internetData,
                    isLoading = internetLoading,
                    error = internetError,
                    onRetry = onRefreshInternetBalance
                )
            }
        }
        item {
            AhuStatusCard(
                text = "自动更新依赖学生信息里的手机号和宿舍数据。更换宿舍或余额无法自动刷新时，请先更新学生信息，再回到这里刷新余额。",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ClickableUtilityCard(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BathroomUtilityDetailScreen(
    data: com.yourname.ahu_plus.data.model.BathroomBalanceData?,
    isLoading: Boolean,
    error: String?,
    phone: String,
    onBack: () -> Unit,
    onSavePhone: (String) -> Unit,
    onRefresh: () -> Unit
) {
    UtilityDetailScaffold(
        title = "浴室余额",
        onBack = onBack,
        onRefresh = onRefresh
    ) {
        item {
            BathroomBalanceCard(
                data = data,
                isLoading = isLoading,
                error = error,
                phone = phone,
                onSavePhone = onSavePhone,
                onRetry = onRefresh
            )
        }
        item {
            ProfileSection {
                EmptyBlock("智慧安大里只有每次给浴室充钱的记录，所以不做。")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityUtilityDetailScreen(
    title: String,
    state: ElectricityState,
    target: ElectricityTarget,
    cardViewModel: HomeViewModel,
    bills: List<ElectricityDailyRecord>,
    billRange: ElectricityBillRange,
    billsLoading: Boolean,
    billsError: String?,
    onBack: () -> Unit,
    onSaveConfig: (ElectricityRoomConfig, ElectricityTarget) -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshBills: () -> Unit,
    onBillRangeSelected: (ElectricityBillRange) -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshBills()
    }
    UtilityDetailScaffold(
        title = title,
        onBack = onBack,
        onRefresh = {
            onRefreshBalance()
            onRefreshBills()
        }
    ) {
        item {
            ElectricityBalanceCard(
                label = title,
                state = state,
                target = target,
                onLoadBuildings = { cardViewModel.loadBuildings(target) },
                onSelectBuilding = { cardViewModel.selectBuilding(target, it) },
                onSelectFloor = { cardViewModel.selectFloor(target, it) },
                onSelectRoom = { cardViewModel.selectRoom(target, it) },
                onRetry = onRefreshBalance
            )
        }
        item {
            ElectricityBillsSection(
                title = "${title}账单查询",
                records = bills,
                range = billRange,
                isLoading = billsLoading,
                error = billsError,
                onRefresh = onRefreshBills,
                onRangeSelected = onBillRangeSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetUtilityDetailScreen(
    data: InternetBalanceData?,
    isLoading: Boolean,
    error: String?,
    bills: List<InternetBillRecord>,
    billsLoading: Boolean,
    billsError: String?,
    onBack: () -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshBills: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshBills()
    }
    UtilityDetailScaffold(
        title = "网费余额",
        onBack = onBack,
        onRefresh = {
            onRefreshBalance()
            onRefreshBills()
        }
    ) {
        item {
            InternetBalanceCard(
                data = data,
                isLoading = isLoading,
                error = error,
                onRetry = onRefreshBalance
            )
        }
        item {
            InternetBillsSection(
                records = bills,
                isLoading = billsLoading,
                error = billsError,
                onRefresh = onRefreshBills
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UtilityDetailScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                content()
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        )
    }
}

@Composable
private fun ElectricityBillsSection(
    title: String,
    records: List<ElectricityDailyRecord>,
    range: ElectricityBillRange,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRangeSelected: (ElectricityBillRange) -> Unit
) {
    val sortedRecords = remember(records) {
        records.sortedByDescending { it.date }
    }
    ProfileSection {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElectricityBillRange.entries.forEach { option ->
                        FilterChip(
                            selected = range == option,
                            onClick = {
                                if (range != option) {
                                    onRangeSelected(option)
                                }
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
            HorizontalDivider()
            when {
                isLoading && sortedRecords.isEmpty() -> LoadingInline(range.loadingText)
                error != null && sortedRecords.isEmpty() -> ErrorInline(error = error, onRefresh = onRefresh)
                sortedRecords.isEmpty() -> EmptyBlock(range.emptyText)
                else -> {
                    sortedRecords.forEachIndexed { index, record ->
                        ElectricityBillRow(record)
                        if (index != sortedRecords.lastIndex) HorizontalDivider()
                    }
                    if (isLoading) {
                        HorizontalDivider()
                        LoadingInline(range.loadingText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectricityBillRow(record: ElectricityDailyRecord) {
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
                .background(Color(0xFF00A6A6).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFF00A6A6))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = record.date.ifBlank { "未知日期" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "日用电明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = record.kwh?.let { "${DecimalFormat("#,##0.00").format(it)} 度" }
                ?: record.degreeText.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InternetBillsSection(
    records: List<InternetBillRecord>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    val sortedRecords = remember(records) {
        records.sortedByDescending { it.successDate.ifBlank { it.itemName } }
    }
    when {
        isLoading && sortedRecords.isEmpty() -> LoadingBlock("正在加载网费明细...")
        error != null && sortedRecords.isEmpty() -> ErrorBlock(error = error, onRefresh = onRefresh)
        sortedRecords.isEmpty() -> ProfileSection { EmptyBlock("暂无网费充值明细") }
        else -> ProfileSection {
            Column {
                sortedRecords.forEachIndexed { index, record ->
                    InternetBillRow(record)
                    if (index != sortedRecords.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InternetBillRow(record: InternetBillRecord) {
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
                .background(Color(0xFF2A9D8F).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color(0xFF2A9D8F))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = record.abstracts.ifBlank { record.typeName.ifBlank { "网费充值" } },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.successDate.ifBlank { record.itemName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "+${DecimalFormat("¥#,##0").format(record.tranAmt)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A9D8F)
        )
    }
}

