package com.yourname.ahu_plus.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.components.AhuStatusCard
import com.yourname.ahu_plus.ui.theme.AhuShapes
import com.yourname.ahu_plus.data.debug.DebugClock
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBillRecord
import com.yourname.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.yourname.ahu_plus.ui.screen.home.ElectricityBillRange
import com.yourname.ahu_plus.ui.screen.home.ElectricityState
import com.yourname.ahu_plus.ui.screen.home.DepositSheet
import com.yourname.ahu_plus.ui.screen.home.DepositTarget
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.home.ElectricityTarget
import com.yourname.ahu_plus.ui.screen.home.InternetBalanceCard
import java.text.DecimalFormat

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
    // 2026-07-06 修复: 提升到 WaterElectricityUtilityDetailScreen 顶层(if-return 短路之外),
    // 避免 selectedUtility 切到具体 utility 子页时列表子 Composable 销毁重建导致滚动丢。
    val utilityListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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
                onRefreshBills = onRefreshInternetBills,
                cardViewModel = cardViewModel,
            )
            return
        }
    }

    UtilityDetailScaffold(
        listState = utilityListState,
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
                    onRetry = onRefreshBathroom,
                    onPay = {
                        val d = bathroomData ?: return@BathroomBalanceCard
                        cardViewModel.openDepositSheet(DepositTarget.Bathroom(d))
                    },
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
                    onRetry = onRefreshAcBalance,
                    onPay = {
                        val d = acState.data ?: return@ElectricityBalanceCard
                        cardViewModel.openDepositSheet(DepositTarget.Electricity(
                            feeitemid = "408",
                            room = d,
                            buildingName = d.buildingName,
                            areaName = acState.config.campus.substringAfter("&", ""),
                            buildingValue = acState.config.building,
                            floorValue = acState.config.floor,
                            roomValue = acState.config.room,
                            floorName = d.floorName,
                        ))
                    },
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
                    onRetry = onRefreshLightingBalance,
                    onPay = {
                        val d = lightingState.data ?: return@ElectricityBalanceCard
                        cardViewModel.openDepositSheet(DepositTarget.Electricity(
                            feeitemid = "428",
                            room = d,
                            buildingName = d.buildingName,
                            areaName = lightingState.config.campus.substringAfter("&", ""),
                            buildingValue = lightingState.config.building,
                            floorValue = lightingState.config.floor,
                            roomValue = lightingState.config.room,
                            floorName = d.floorName,
                        ))
                    },
                )
            }
        }
        item {
            ClickableUtilityCard(onClick = { selectedUtility = "internet" }) {
                InternetBalanceCard(
                    data = internetData,
                    isLoading = internetLoading,
                    error = internetError,
                    onRetry = onRefreshInternetBalance,
                    onPay = {
                        val d = internetData ?: return@InternetBalanceCard
                        cardViewModel.openDepositSheet(DepositTarget.Internet(d))
                    },
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

    // 概览页自身的卡片(浴室/空调/照明)点充值会 openDepositSheet,这里必须挂 sheet 才弹得出来。
    // 子页 selectedUtility != null 时已提前 return,不会和子页的 DepositSheet 重复挂载。
    val uiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val depositSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DepositSheet(
        state = uiState.depositSheet,
        sheetState = depositSheetState,
        onAmountChange = cardViewModel::updateDepositAmount,
        onPasswordChange = cardViewModel::updateDepositPassword,
        onSubTargetChange = cardViewModel::updateDepositSubTarget,
        onConfirm = cardViewModel::submitDeposit,
        onDismiss = cardViewModel::closeDepositSheet,
    )
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
        // 详情页里要绘制近 30 天曲线,如果当前仍是近 7 天,先切到近一个月再拉数据。
        // 避免下面的分析卡片显示 7 天的曲线给"日均"算错。
        if (billRange != ElectricityBillRange.LAST_MONTH) {
            onBillRangeSelected(ElectricityBillRange.LAST_MONTH)
        } else {
            onRefreshBills()
        }
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
                onRetry = onRefreshBalance,
                onPay = {
                    val d = state.data ?: return@ElectricityBalanceCard
                    cardViewModel.openDepositSheet(DepositTarget.Electricity(
                        feeitemid = if (target == ElectricityTarget.LIGHTING) "428" else "408",
                        room = d,
                        buildingName = d.buildingName,
                        areaName = state.config.campus.substringAfter("&", ""),
                        buildingValue = state.config.building,
                        floorValue = state.config.floor,
                        roomValue = state.config.room,
                        floorName = d.floorName,
                    ))
                },
            )
        }
        item {
            ElectricityAnalyticsCard(
                title = "${title}用量分析",
                records = bills,
                remainingKwh = state.data?.remainingKwh,
                range = billRange
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

    val uiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    // 2026-06-29: 预创建避免 state.visible=true 时首次组合 SheetState 阻塞 2-4s
    val depositSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DepositSheet(
        state = uiState.depositSheet,
        sheetState = depositSheetState,
        onAmountChange = cardViewModel::updateDepositAmount,
        onPasswordChange = cardViewModel::updateDepositPassword,
        onSubTargetChange = cardViewModel::updateDepositSubTarget,
        onConfirm = cardViewModel::submitDeposit,
        onDismiss = cardViewModel::closeDepositSheet,
    )
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
    onRefreshBills: () -> Unit,
    cardViewModel: HomeViewModel,
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
                onRetry = onRefreshBalance,
                onPay = {
                    val d = data ?: return@InternetBalanceCard
                    cardViewModel.openDepositSheet(DepositTarget.Internet(d))
                },
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

    val uiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val depositSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DepositSheet(
        state = uiState.depositSheet,
        sheetState = depositSheetState,
        onAmountChange = cardViewModel::updateDepositAmount,
        onPasswordChange = cardViewModel::updateDepositPassword,
        onSubTargetChange = cardViewModel::updateDepositSubTarget,
        onConfirm = cardViewModel::submitDeposit,
        onDismiss = cardViewModel::closeDepositSheet,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UtilityDetailScaffold(
    listState: LazyListState = rememberLazyListState(),
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                content()
                item { Spacer(modifier = Modifier.height(24.dp)) }
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

// ════════════════════════════════════════════════════════════════
// 用量分析卡片(空调 / 照明 详情页共用)
//
// 显示:
//   1) 近 30 天每日用电曲线(Canvas 平滑曲线)
//   2) 日均消耗
//   3) 按日均推算剩余电量还能用多久
//
// 仅当 [range] == LAST_MONTH 时计算,7 天模式下提示用户切到近一个月。
// ════════════════════════════════════════════════════════════════

/** 近 30 天用电分析所需的原始聚合结果。 */
private data class ElectricityUsageWindow(
    val points: List<DailyKwhPoint>,
    val totalKwh: Double,
    val dailyAvg: Double,
    val daysRemaining: Int?
)

/** 分析用的逐日数据点(避免复用一卡通 DailyPoint 语义混淆,amount→kwh)。 */
private data class DailyKwhPoint(val date: String, val kwh: Double)

@Composable
private fun ElectricityAnalyticsCard(
    title: String,
    records: List<ElectricityDailyRecord>,
    remainingKwh: Double?,
    range: ElectricityBillRange
) {
    ProfileSection {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (range != ElectricityBillRange.LAST_MONTH) {
                EmptyBlock("当前时段为${range.label},请切换到「近一个月」查看用量分析。")
                return@Column
            }
            val window = remember(records, remainingKwh) {
                buildUsageWindow(records = records, remainingKwh = remainingKwh)
            }
            if (window.points.isEmpty() || window.totalKwh <= 0.0) {
                EmptyBlock("近 30 天暂无用量明细")
                return@Column
            }
            ElectricityUsageBody(window = window)
        }
    }
}

@Composable
private fun ElectricityUsageBody(window: ElectricityUsageWindow) {
    Text(
        text = "${window.points.first().date} 至 ${window.points.last().date}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    ElectricityUsageSmoothChart(points = window.points)
    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        UsageMetricCard(
            label = "日均消耗",
            value = "${formatKwh(window.dailyAvg)} 度",
            sub = "近 30 天 / 30",
            modifier = Modifier.weight(1f)
        )
        UsageMetricCard(
            label = "还能用",
            value = window.daysRemaining?.let { "≈ $it 天" } ?: "—",
            sub = "按日均推算",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UsageMetricCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 复用一卡通分析里 SmoothTrendChart 的 Canvas 平滑曲线绘制方式(同款贝塞尔)。 */
@Composable
private fun ElectricityUsageSmoothChart(points: List<DailyKwhPoint>) {
    val primary = Color(0xFF00A6A6)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        val top = 12f
        val bottom = size.height - 24f
        val left = 8f
        val right = size.width - 8f
        val chartHeight = bottom - top
        val chartWidth = right - left
        val maxValue = points.maxOf { it.kwh }.coerceAtLeast(0.01).toFloat()

        repeat(4) { index ->
            val y = top + chartHeight * (index + 1) / 5f
            drawLine(
                color = grid.copy(alpha = 0.55f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f
            )
        }

        val offsets = points.mapIndexed { index, point ->
            val x = left + chartWidth * index / (points.lastIndex.coerceAtLeast(1)).toFloat()
            val y = bottom - (point.kwh.toFloat() / maxValue) * chartHeight
            Offset(x, y.coerceIn(top, bottom))
        }
        val curve = smoothUsagePath(offsets)
        val area = Path().apply {
            addPath(curve)
            lineTo(offsets.last().x, bottom)
            lineTo(offsets.first().x, bottom)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.17f),
                    primary.copy(alpha = 0.06f),
                    primary.copy(alpha = 0.015f)
                ),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path = curve,
            color = primary,
            style = Stroke(width = 4.2f, cap = StrokeCap.Round)
        )
        offsets.forEachIndexed { index, offset ->
            if (points[index].kwh > 0.0) {
                drawCircle(
                    color = primary.copy(alpha = 0.28f),
                    radius = 3.4f,
                    center = offset
                )
            }
        }
    }
}

private fun smoothUsagePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points.last().x, points.last().y)
        return path
    }
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
        path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
    }
    val beforeLast = points[points.lastIndex - 1]
    val last = points.last()
    path.quadraticTo(beforeLast.x, beforeLast.y, last.x, last.y)
    return path
}

/** 把记录过滤并填充成「近 30 天」逐日序列。空缺日按 0 填充,保证曲线宽 30 个点。 */
private fun buildUsageWindow(
    records: List<ElectricityDailyRecord>,
    remainingKwh: Double?
): ElectricityUsageWindow {
    val today = DebugClock.todayDate()
    val windowStart = today.minusDays(29) // 30 天窗口(含今天)
    val startKey = windowStart.toString() // ISO yyyy-MM-dd
    val endKey = today.toString()
    val perDay = records
        .mapNotNull { record ->
            val kwh = record.kwh ?: return@mapNotNull null
            val date = record.date.takeIf { it.length == 10 } ?: return@mapNotNull null
            date to kwh
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

    val points = mutableListOf<DailyKwhPoint>()
    var day = windowStart
    while (!day.isAfter(today)) {
        val key = day.toString()
        points += DailyKwhPoint(date = key, kwh = perDay[key] ?: 0.0)
        day = day.plusDays(1)
    }
    val totalKwh = points.sumOf { it.kwh }
    val dailyAvg = totalKwh / 30.0
    val daysRemaining = computeDaysRemaining(remainingKwh = remainingKwh, dailyAvg = dailyAvg)
    return ElectricityUsageWindow(
        points = points,
        totalKwh = totalKwh,
        dailyAvg = dailyAvg,
        daysRemaining = daysRemaining
    )
}

/**
 * 按日均消耗推算还能用几天。
 * - remainingKwh 缺失 / dailyAvg==0 → 返回 null(在 UI 显示 "—")
 * - 结果 < 1 天 → 显示 "≈ 0 天" 提醒赶紧充值
 * - 结果 > 365 天 → 截断到 ">365 天"
 */
private fun computeDaysRemaining(remainingKwh: Double?, dailyAvg: Double): Int? {
    if (remainingKwh == null || remainingKwh <= 0.0 || dailyAvg <= 0.0) return null
    val raw = (remainingKwh / dailyAvg).toInt()
    return raw.coerceIn(0, 365)
}

private val KwhFormat = DecimalFormat("0.00")
private fun formatKwh(value: Double): String = KwhFormat.format(value)


