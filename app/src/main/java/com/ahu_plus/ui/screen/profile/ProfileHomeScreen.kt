package com.ahu_plus.ui.screen.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.home.AppHubLayoutConfig
import com.ahu_plus.data.local.AppThemeMode
import com.ahu_plus.data.local.BottomNavService
import com.ahu_plus.data.developer.DeveloperRuntime
import com.ahu_plus.data.model.BathroomBalanceData
import com.ahu_plus.data.model.InternetBalanceData
import com.ahu_plus.data.model.StudentInfo
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.notification.CardBalanceAlertMode
import com.ahu_plus.notification.recentCanteenDailyAverage
import com.ahu_plus.ui.components.AhuHeroCard
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.AhuPullToRefreshBox
import com.ahu_plus.ui.components.AhuSectionHeader
import com.ahu_plus.ui.components.LoginRequiredCard
import com.ahu_plus.ui.components.rememberQrCodeImage
import com.ahu_plus.ui.screen.home.AdwmhCaptchaDialog
import com.ahu_plus.ui.screen.home.BathroomBalanceCard
import com.ahu_plus.ui.screen.home.ElectricityBalanceCard
import com.ahu_plus.ui.screen.home.ElectricityBillRange
import com.ahu_plus.ui.screen.home.ElectricityState
import com.ahu_plus.ui.screen.home.HomeViewModel
import com.ahu_plus.ui.screen.home.InternetBalanceCard
import com.ahu_plus.ui.screen.home.QrCodeFullScreenDialog
import com.ahu_plus.ui.screen.market.MarketViewModel
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import com.ahu_plus.ui.theme.AhuStatusColors
import com.ahu_plus.ui.theme.AhuToneColors
import com.ahu_plus.ui.theme.tabularFigures
import com.ahu_plus.util.BrowserOpener
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val balanceFormatter = DecimalFormat("¥#,##0.00")
private val qrBalanceFormatter = DecimalFormat("¥#,##0.00")
private val timestampFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileHomeScreen(
    listState: LazyListState,
    studentName: String?,
    department: String?,
    className: String?,
    hasStudentInfo: Boolean,
    financeItemCount: Int,
    balance: Double,
    balanceLoading: Boolean,
    balanceError: String?,
    timestamp: Long,
    qrCode: AdwmhQrCode?,
    qrBalance: Double?,
    qrLoading: Boolean,
    qrError: String?,
    qrCountdownSeconds: Int,
    onQrRefresh: () -> Unit,
    onQrEnsure: () -> Unit,
    onQrClick: () -> Unit,
    identityCount: Int,
    hasMarketIdentity: Boolean,
    thirdPartyEnabled: Boolean,
    marketChildEnabled: Boolean,
    chaoxingChildEnabled: Boolean,
    welearnChildEnabled: Boolean,
    bottomNavServices: List<String>,
    onThirdPartyEnabledChanged: (Boolean) -> Unit,
    onMarketChildEnabledChanged: (Boolean) -> Unit,
    onChaoxingChildEnabledChanged: (Boolean) -> Unit,
    onWelearnChildEnabledChanged: (Boolean) -> Unit,
    bathroomData: BathroomBalanceData?,
    bathroomLoading: Boolean,
    bathroomError: String?,
    bathroomPhone: String,
    onSaveBathroomPhone: (String) -> Unit,
    onRetryBathroom: () -> Unit,
    internetData: InternetBalanceData?,
    internetLoading: Boolean,
    internetError: String?,
    onRetryInternet: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenUtilityOverview: () -> Unit,
    onOpenBathroom: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenInternet: () -> Unit,
    onOpenMyInfoHub: () -> Unit,
    themeMode: AppThemeMode,
    onOpenSettings: () -> Unit,
    onOpenXzxx: () -> Unit,
    onOpenAbout: () -> Unit,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val displayName = if (isLoggedIn) {
        studentName?.takeIf { it.isNotBlank() } ?: "未命名同学"
    } else {
        "未登录"
    }
    val subtitle = if (isLoggedIn) {
        listOfNotNull(
            department?.takeIf { it.isNotBlank() },
            className?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "学生信息加载中" }
    } else {
        "登录后查看校园账户与个人数据"
    }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showThirdPartyDialog by rememberSaveable { mutableStateOf(false) }
    var unpinnedServiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeveloperContact by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var showQrCard by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // 预加载支付码：进入「我的」页即开始取码（对齐首页），用户展开支付码卡片时
    // 大概率命中 45s 内的缓存瞬间出码。ensureCampusQrCode 内部有新鲜度决策，
    // 重复调用无副作用；未登录不发请求。
    LaunchedEffect(Unit) {
        if (isLoggedIn) onQrEnsure()
    }

    LaunchedEffect(showQrCard) {
        if (shouldEnsureProfileQr(showQrCard)) onQrEnsure()
    }

    if (showThirdPartyDialog) {
        ThirdPartyEnableDialog(
            onConfirm = {
                onThirdPartyEnabledChanged(true)
                showThirdPartyDialog = false
            },
            onDismiss = { showThirdPartyDialog = false }
        )
    }

    unpinnedServiceName?.let { serviceName ->
        AlertDialog(
            onDismissRequest = { unpinnedServiceName = null },
            title = { Text("$serviceName 已启用") },
            text = {
                Text("底部栏最多固定两个第三方服务，因此该服务会显示在\"应用\"页。可在\"设置 > 底部栏服务\"中调整固定项。")
            },
            confirmButton = {
                TextButton(onClick = { unpinnedServiceName = null }) { Text("知道了") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        unpinnedServiceName = null
                        onOpenSettings()
                    }
                ) { Text("去设置") }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("确认退出登录？") },
            text = { Text("将清除本地凭据与账户数据。退出后仍可使用无需认证的功能。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeveloperContact) {
        DeveloperContactDialog(onDismiss = { showDeveloperContact = false })
    }

    if (showShareSheet) {
        ShareSheet(
            onDismiss = { showShareSheet = false },
        )
    }

    // Hero 卡片置于实色顶栏下方(取消沉浸式顶满状态栏),listState 由外部传入供滚动。

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("我的") },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { showLogoutConfirm = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "退出登录",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onLogin) {
                            Icon(
                                Icons.AutoMirrored.Filled.Login,
                                contentDescription = "登录",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // 顶层 MainScreen 已处理底栏 inset,这里归零避免双重底部 padding;
        // 顶部 inset 由 AhuTopAppBar 自带 statusBarsPadding 计入 innerPadding.top,
        // 内容应用 innerPadding 后位于顶栏下方(取消沉浸式顶满)。
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(balanceLoading) {
            if (!balanceLoading) isRefreshing = false
        }
        AhuPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            modifier = Modifier
                .fillMaxSize()
                // 应用完整 innerPadding(顶部让出状态栏+顶栏),水平边距由 LazyColumn 自身加。
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = AhuSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AhuSpacing.CardGap)
        ) {
            item {
                ProfileHeader(
                    displayName = displayName,
                    subtitle = subtitle,
                    onClick = if (isLoggedIn) onOpenMyInfoHub else onLogin,
                )
            }

            if (!isLoggedIn) {
                item {
                    LoginRequiredCard(onLogin = onLogin)
                }
            }

            item {
                AhuSectionHeader(
                    title = "校园卡",
                    subtitle = "余额、账单与支付码"
                )
            }

            item {
                if (showQrCard) {
                    ProfileQrCard(
                        qrCode = qrCode,
                        qrBalance = qrBalance,
                        qrLoading = qrLoading,
                        qrError = qrError,
                        qrCountdownSeconds = qrCountdownSeconds,
                        onBack = { showQrCard = false },
                        onQrClick = onQrClick,
                        onRefresh = onQrRefresh,
                    )
                } else {
                    BalanceCard(
                        balance = balance,
                        qrBalance = qrBalance,
                        isLoading = balanceLoading,
                        error = balanceError,
                        timestamp = timestamp,
                        qrAuthUrl = "",
                        qrCode = qrCode,
                        onQrClick = { showQrCard = true },
                        onClick = onOpenBills
                    )
                }
            }

            item {
                AhuSectionHeader(
                    title = "校园服务",
                    subtitle = "生活保障服务"
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "水电费查询",
                        description = "浴室、空调、照明、网费余额",
                        iconColor = AhuStatusColors.ActionBlue,
                        icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                        onClick = onOpenUtilityOverview
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "校长信箱",
                        description = "向校长反映问题、提交建议与诉求",
                        iconColor = AhuStatusColors.AppIndigo,
                        icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        onClick = onOpenXzxx
                    )
                }
            }

            item {
                AhuSectionHeader(
                    title = "第三方服务",
                    subtitle = "非安大官方平台,启用前需确认风险声明"
                )
            }

            item {
                var expandedThirdParty by rememberSaveable { mutableStateOf(false) }
                ProfileSection {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedThirdParty = !expandedThirdParty }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(AhuShapes.IconBox)
                                    .background(AhuGradient.forTint(AhuToneColors.ThirdPartyPurple.current)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "第三方服务",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "非安大官方平台,启用前需确认风险声明",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (expandedThirdParty) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expandedThirdParty) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = expandedThirdParty,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                SettingsSwitchRow(
                                    title = "是否开启",
                                    description = if (thirdPartyEnabled) {
                                        "已开启：前两个启用项自动固定到底部栏，其余显示在应用页"
                                    } else {
                                        "默认关闭，启用需阅读并确认风险声明"
                                    },
                                    checked = thirdPartyEnabled,
                                    onCheckedChange = { wantEnable ->
                                        if (wantEnable) {
                                            showThirdPartyDialog = true
                                        } else {
                                            onThirdPartyEnabledChanged(false)
                                        }
                                    }
                                )
                                if (thirdPartyEnabled) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                        title = "校园集市",
                                        description = if (marketChildEnabled) {
                                            if (BottomNavService.MARKET in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地 token/设置保留"
                                        },
                                        checked = marketChildEnabled,
                                        onCheckedChange = { enabled ->
                                            onMarketChildEnabledChanged(enabled)
                                            if (enabled && BottomNavService.MARKET !in bottomNavServices && bottomNavServices.size >= 2) {
                                                unpinnedServiceName = "校园集市"
                                            }
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                        title = "超星学习通",
                                        description = if (chaoxingChildEnabled) {
                                            if (BottomNavService.CHAOXING in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地登录态保留"
                                        },
                                        checked = chaoxingChildEnabled,
                                        onCheckedChange = { enabled ->
                                            onChaoxingChildEnabledChanged(enabled)
                                            if (enabled && BottomNavService.CHAOXING !in bottomNavServices && bottomNavServices.size >= 2) {
                                                unpinnedServiceName = "超星学习通"
                                            }
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsSwitchRow(
                                        title = "WeLearn 随行课堂",
                                        description = if (welearnChildEnabled) {
                                            if (BottomNavService.WELEARN in bottomNavServices) {
                                                "已固定在底部栏"
                                            } else {
                                                "已启用，可从应用页进入"
                                            }
                                        } else {
                                            "已关闭，本地登录态保留"
                                        },
                                        checked = welearnChildEnabled,
                                        onCheckedChange = { enabled ->
                                            onWelearnChildEnabledChanged(enabled)
                                            if (enabled && BottomNavService.WELEARN !in bottomNavServices && bottomNavServices.size >= 2) {
                                                unpinnedServiceName = "WeLearn 随行课堂"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AhuSectionHeader(
                    title = "其他",
                    subtitle = "设置、关于与推荐"
                )
            }

            item {
                ProfileSection {
                    SettingsRow(
                        title = "设置",
                        description = "外观：${themeMode.titleText()}、功能设置",
                        iconColor = AhuStatusColors.AppIndigo,
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = onOpenSettings
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "关于",
                        description = "软件信息、公告、帮助与协议",
                        iconColor = AhuToneColors.AboutSlate.current,
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = onOpenAbout
                    )
                    HorizontalDivider()
                    SettingsRow(
                        title = "推荐",
                        description = "分享下载链接或安装包给好友",
                        iconColor = AhuStatusColors.ActionBlue,
                        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { showShareSheet = true }
                    )
                }
            }

            item {
                Text(
                    text = "联系开发者",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeveloperContact = true }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = AhuStatusColors.ActionBlue
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
        } // AhuPullToRefreshBox
    }
}

private fun buildMyInfoDescription(
    hasStudentInfo: Boolean,
    financeItemCount: Int
): String {
    val parts = mutableListOf<String>()
    if (hasStudentInfo) parts.add("基本信息")
    if (financeItemCount > 0) parts.add("财务")
    return if (parts.isEmpty()) "学生基本信息、住宿、财务等" else parts.joinToString("、") + "等"
}

@Composable
private fun DeveloperContactDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val devEmail = "2867299793@qq.com"

    fun sendEmail() {
        val ok = com.ahu_plus.util.BrowserOpener.openEmail(
            context = context,
            email = devEmail,
            subject = "[Ahu_Plus 反馈] "
        )
        if (!ok) {
            Toast.makeText(context, "未检测到邮件客户端,请手动发送至 $devEmail", Toast.LENGTH_LONG).show()
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("联系开发者") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ContactMethodRow(
                    title = "发送邮件给开发者",
                    value = devEmail,
                    onClick = { sendEmail() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ContactMethodRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    subtitle: String,
    onClick: () -> Unit = {},
) {
    AhuHeroCard(
        gradient = AhuGradient.Blue.brush,
        modifier = Modifier.clickable(
            onClick = onClick,
            role = Role.Button,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    qrBalance: Double? = null,
    isLoading: Boolean,
    error: String?,
    timestamp: Long,
    qrAuthUrl: String,
    qrCode: AdwmhQrCode?,
    onQrClick: () -> Unit,
    onClick: () -> Unit
) {
    val displayBalance = qrBalance ?: balance
    val formatter = DecimalFormat("¥#,##0.00")

    AhuHeroCard(
        gradient = AhuGradient.Green.brush,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AhuShapes.IconBox)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "校园卡",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val hasBalance = displayBalance > 0.0
                    val subText = when {
                        hasBalance -> null
                        isLoading -> null
                        error != null -> null
                        else -> updatedText(timestamp)
                    }
                    if (subText != null) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
                when {
                    isLoading && displayBalance == 0.0 -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                    displayBalance > 0.0 -> {
                        Text(
                            text = formatter.format(displayBalance),
                            style = MaterialTheme.typography.titleLarge.tabularFigures(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            IconButton(
                onClick = onQrClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = "智慧安大支付码",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProfileQrCard(
    qrCode: AdwmhQrCode?,
    qrBalance: Double?,
    qrLoading: Boolean,
    qrError: String?,
    qrCountdownSeconds: Int,
    onBack: () -> Unit,
    onQrClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "智慧安大支付码",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onBack) {
                    Text("返回余额")
                }
            }

            when {
                qrCode != null -> {
                    // 卡片态用 480px（显示 200dp 足够清晰），全屏态才用 720；
                    // 配合 ViewModel 预生成，点开即命中缓存秒出。
                    val image = rememberQrCodeImage(qrCode.payload, 480)
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clickable { onQrClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null) {
                            Image(bitmap = image, contentDescription = "支付码 — 点击放大")
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${qrCountdownSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (qrBalance != null) {
                            Text(
                                text = DecimalFormat("¥#,##0.00").format(qrBalance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = qrCode.statusMsg.ifBlank { "已刷新" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                qrLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    Text("加载中...", style = MaterialTheme.typography.bodySmall)
                }
                qrError != null -> {
                    Text(qrError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            }

            if (qrCode != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = onRefresh) { Text("刷新") }
                    TextButton(onClick = onQrClick) { Text("放大") }
                }
            }
        }
    }
}

internal fun shouldEnsureProfileQr(isPanelVisible: Boolean): Boolean = isPanelVisible

@Composable
fun ProfileSection(content: @Composable () -> Unit) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
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

internal fun StudentInfo.displayName(): String? {
    return firstValueOf("姓名", "学生姓名", "本人姓名", "USER_NAME")
}

internal fun StudentInfo.department(): String? {
    return firstValueOf("学院", "院系", "院系名称", "所在院系", "培养单位", "UNIT_NAME")
}

internal fun StudentInfo.classOrMajor(): String? {
    val major = firstValueOf("专业", "专业名称", "所在专业")
    val className = firstValueOf("班级", "行政班", "自然班")
    return listOfNotNull(major, className)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}
