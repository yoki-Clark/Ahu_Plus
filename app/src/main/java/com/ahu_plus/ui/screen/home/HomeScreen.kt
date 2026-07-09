package com.ahu_plus.ui.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.local.ElectricityRoomConfig
import com.ahu_plus.data.model.FeeItemOption
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.data.model.InternetBalanceData
import com.ahu_plus.ui.components.AhuHeroCard
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.components.CountdownArc
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.util.BrowserOpener
import com.ahu_plus.util.QrCodeBitmap
import java.text.DecimalFormat

/**
 * 兼容旧入口的校园卡页面。
 *
 * 新版主流程已迁移到「我的」页,这里保留为独立页面以便后续复用或调试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFullQrCode by rememberSaveable { mutableStateOf(false) }
    // 2026-06-29: 预创建避免 state.visible=true 时首次组合 SheetState 阻塞 2-4s
    val depositSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("校园卡") },
                actions = {
                    IconButton(onClick = viewModel::onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BalanceSummaryCard(
                    balance = uiState.balance,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onRetry = viewModel::loadBalance
                )
            }
            item {
                CampusQrCodeCard(
                    qrCode = uiState.qrCode,
                    balance = uiState.qrBalance,
                    isLoading = uiState.qrLoading,
                    error = uiState.qrError,
                    countdownSeconds = uiState.qrCountdownSeconds,
                    totalCountdownSeconds = 45,
                    isStale = uiState.qrStale,
                    ageSeconds = uiState.qrAgeSeconds,
                    onAutoLogin = viewModel::autoLoginAdwmh,
                    onRefresh = viewModel::loadCampusQrCode,
                    onQrClick = { showFullQrCode = true }
                )
            }
            item {
                BathroomBalanceCard(
                    data = uiState.bathroomData,
                    isLoading = uiState.bathroomLoading,
                    error = uiState.bathroomError,
                    phone = uiState.bathroomPhone,
                    onSavePhone = viewModel::saveBathroomPhone,
                    onRetry = viewModel::loadBathroomBalance,
                    onPay = {
                        val d = uiState.bathroomData ?: return@BathroomBalanceCard
                        viewModel.openDepositSheet(DepositTarget.Bathroom(d))
                    },
                )
            }
            item {
                ElectricityBalanceCard(
                    label = "空调余额",
                    state = uiState.ac,
                    target = ElectricityTarget.AC,
                    onLoadBuildings = { viewModel.loadBuildings(ElectricityTarget.AC) },
                    onSelectBuilding = { viewModel.selectBuilding(ElectricityTarget.AC, it) },
                    onSelectFloor = { viewModel.selectFloor(ElectricityTarget.AC, it) },
                    onSelectRoom = { viewModel.selectRoom(ElectricityTarget.AC, it) },
                    onRetry = { viewModel.loadElectricityBalance(ElectricityTarget.AC) },
                    onPay = {
                        val ac = uiState.ac
                        val data = ac.data ?: return@ElectricityBalanceCard
                        viewModel.openDepositSheet(DepositTarget.Electricity(
                            feeitemid = "408",
                            room = data,
                            buildingName = data.buildingName,
                            areaName = ac.config.campus.substringAfter("&", ""),
                            buildingValue = ac.config.building,
                            floorValue = ac.config.floor,
                            roomValue = ac.config.room,
                            floorName = data.floorName,
                        ))
                    },
                )
            }
            item {
                ElectricityBalanceCard(
                    label = "照明余额",
                    state = uiState.lighting,
                    target = ElectricityTarget.LIGHTING,
                    onLoadBuildings = { viewModel.loadBuildings(ElectricityTarget.LIGHTING) },
                    onSelectBuilding = { viewModel.selectBuilding(ElectricityTarget.LIGHTING, it) },
                    onSelectFloor = { viewModel.selectFloor(ElectricityTarget.LIGHTING, it) },
                    onSelectRoom = { viewModel.selectRoom(ElectricityTarget.LIGHTING, it) },
                    onRetry = { viewModel.loadElectricityBalance(ElectricityTarget.LIGHTING) },
                    onPay = {
                        val light = uiState.lighting
                        val data = light.data ?: return@ElectricityBalanceCard
                        viewModel.openDepositSheet(DepositTarget.Electricity(
                            feeitemid = "428",
                            room = data,
                            buildingName = data.buildingName,
                            areaName = light.config.campus.substringAfter("&", ""),
                            buildingValue = light.config.building,
                            floorValue = light.config.floor,
                            roomValue = light.config.room,
                            floorName = data.floorName,
                        ))
                    },
                )
            }
            item {
                InternetBalanceCard(
                    data = uiState.internetData,
                    isLoading = uiState.internetLoading,
                    error = uiState.internetError,
                    onRetry = viewModel::loadInternetBalance,
                    onPay = {
                        val d = uiState.internetData ?: return@InternetBalanceCard
                        viewModel.openDepositSheet(DepositTarget.Internet(d))
                    },
                )
            }
            item {
                Text(
                    text = "最近账单：${uiState.bills.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val billsError = uiState.billsError
                if (billsError != null) {
                    Text(
                        text = billsError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(onClick = viewModel::loadBills) {
                        Text("重试")
                    }
                }
            }
        }

        // 全屏支付码弹窗
        if (showFullQrCode) {
            QrCodeFullScreenDialog(
                qrCode = uiState.qrCode,
                balance = uiState.qrBalance,
                isLoading = uiState.qrLoading,
                countdownSeconds = uiState.qrCountdownSeconds,
                totalCountdownSeconds = 45,
                qrError = uiState.qrError,
                isStale = uiState.qrStale,
                ageSeconds = uiState.qrAgeSeconds,
                brightnessBoost = viewModel.getQrBrightnessBoost(),
                onDismiss = { showFullQrCode = false },
                onRefresh = viewModel::loadCampusQrCode
            )
        }

        // 水电费充值 sheet (2026-06-29 接入)
        DepositSheet(
            state = uiState.depositSheet,
            sheetState = depositSheetState,
            onAmountChange = viewModel::updateDepositAmount,
            onPasswordChange = viewModel::updateDepositPassword,
            onSubTargetChange = viewModel::updateDepositSubTarget,
            onConfirm = viewModel::submitDeposit,
            onDismiss = viewModel::closeDepositSheet,
        )
    }
}

@Composable
private fun BalanceSummaryCard(
    balance: Double,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && balance == 0.0 -> CircularProgressIndicator()
                error != null && balance == 0.0 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) {
                            Text("重新查询")
                        }
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "校园卡",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = DecimalFormat("¥#,##0.00").format(balance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

