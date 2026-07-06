package com.yourname.ahu_plus.ui.screen.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.components.AhuStatusCard
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

