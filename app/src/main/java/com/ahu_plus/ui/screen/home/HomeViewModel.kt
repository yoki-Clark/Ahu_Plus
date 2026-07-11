package com.ahu_plus.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.ElectricityRoomConfig
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.model.BathroomBalanceData
import com.ahu_plus.data.model.BillRecord
import com.ahu_plus.data.model.DormHint
import com.ahu_plus.data.model.ElectricityBalanceData
import com.ahu_plus.data.model.ElectricityDailyRecord
import com.ahu_plus.data.model.ElectricityUiData
import com.ahu_plus.data.model.FeeItemOption
import com.ahu_plus.data.model.InternetBalanceData
import com.ahu_plus.data.model.InternetBillRecord
import com.ahu_plus.data.model.StudentInfo
import com.ahu_plus.data.repository.AdwmhCardRepository
import com.ahu_plus.data.repository.AdwmhLoginInfo
import com.ahu_plus.data.repository.AdwmhQrCode
import com.ahu_plus.data.repository.CardRepository
import com.ahu_plus.data.repository.CasAuthRepository
import com.ahu_plus.data.repository.SessionExpiredException
import com.ahu_plus.data.repository.StudentInfoRepository
import com.ahu_plus.data.repository.YcardAuthExpiredException
import com.ahu_plus.data.repository.YcardRepository
import com.ahu_plus.data.repository.YcardPayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HomeViewModel(
    private val repository: CardRepository,
    private val casAuthRepository: CasAuthRepository,
    private val ycardRepository: YcardRepository,
    private val sessionManager: SessionManager,
    private val studentInfoRepository: StudentInfoRepository? = null,
    private val adwmhCardRepository: AdwmhCardRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 充值仓库 (2026-06-29 接入,懒构造,直接复用 ycardRepository 的 client/JWT/cookie) */
    private val ycardPayRepository: YcardPayRepository by lazy { YcardPayRepository(ycardRepository) }

    // 级联加载 Job 引用,用于取消过期请求避免竞态
    private var cascadeJobAc: Job? = null
    private var cascadeJobLighting: Job? = null
    private var qrRefreshJob: Job? = null
    private var visible = false

    init {
        val savedBathroomPhone = sessionManager.getBathroomPhone().orEmpty()
        // 空调 (408) + 照明 (428) 配置独立加载,避免互相覆盖
        val acConfig = sessionManager.getAcConfig()
        val lightingConfig = sessionManager.getLightingConfig()
        val acFeeitemid = deriveFeeitemid(acConfig.building, acConfig.building.substringAfter("&"))
        val lightingFeeitemid = deriveFeeitemid(lightingConfig.building, lightingConfig.building.substringAfter("&"))
        _uiState.update {
            it.copy(
                bathroomPhone = savedBathroomPhone,
                ac = it.ac.copy(
                    config = acConfig,
                    cascade = it.ac.cascade.copy(
                        selectedBuilding = acConfig.building.takeIf { b -> b.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedFloor = acConfig.floor.takeIf { f -> f.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedRoom = acConfig.room.takeIf { r -> r.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedFeeitemid = acFeeitemid
                    )
                ),
                lighting = it.lighting.copy(
                    config = lightingConfig,
                    cascade = it.lighting.cascade.copy(
                        selectedBuilding = lightingConfig.building.takeIf { b -> b.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedFloor = lightingConfig.floor.takeIf { f -> f.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedRoom = lightingConfig.room.takeIf { r -> r.isNotBlank() }
                            ?.let { v -> FeeItemOption(v.substringAfter("&"), v) },
                        selectedFeeitemid = lightingFeeitemid
                    )
                )
            )
        }
        applyStudentInfoPrefill(studentInfoRepository?.readCachedStudentInfo(), loadAfterApply = false)
        restoreCachedQr()
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) {
            loadBalanceAndBills()
            startQrAutoRefresh()
        } else {
            qrRefreshJob?.cancel()
            qrRefreshJob = null
        }
    }

    /** 从 building value/name 推导 feeitemid。 */
    private fun deriveFeeitemid(buildingValue: String, buildingName: String? = null): String? {
        if (buildingValue.isBlank()) return null
        if (buildingValue.startsWith("ul")) return "488"
        // 老区:按 name 后缀判断 (name 可从 value 的 "code&name" 格式中提取)
        val name = buildingName ?: buildingValue.substringAfter("&", "")
        if (name.endsWith("照明")) return "428"
        return "408" // 默认空调
    }

    /**
     * 从"我的信息"中提取浴室手机号 + 住宿信息,作为浴室手机号预填 + 电费房间自动选择提示。
     */
    fun applyStudentInfoPrefill(info: StudentInfo?, loadAfterApply: Boolean = true) {
        if (info == null) return

        val phone = info.firstValueOf("手机号", "手机号码", "联系电话", "移动电话")
            ?.filter(Char::isDigit)
            ?.takeIf { it.length == 11 }

        val buildingName = info.firstValueOf("楼栋", "所在楼栋", "楼栋号")
        val roomNumber = info.firstValueOf("宿舍房间", "房间号", "寝室号")
            ?.filter(Char::isDigit)

        val dormHint = if (!buildingName.isNullOrBlank() && !roomNumber.isNullOrBlank()) {
            val digits = roomNumber.filter(Char::isDigit)
            val buildingDigit = digits.getOrNull(0)?.digitToIntOrNull() ?: 1
            // 楼层取次位 (2514→5, 301→0); 次位为 0 时视为 1 层 (地面层标为"一层")
            val rawFloor = digits.getOrNull(1)?.digitToIntOrNull() ?: 1
            val floorDigit = if (rawFloor == 0) 1 else rawFloor
            DormHint(
                buildingName = buildingName,
                roomNumber = roomNumber,
                buildingDigit = buildingDigit,
                floorDigit = floorDigit
            )
        } else null

        val before = _uiState.value
        _uiState.update { state ->
            state.copy(
                bathroomPhone = phone ?: state.bathroomPhone,
                ac = state.ac.copy(cascade = state.ac.cascade.copy(dormHint = dormHint)),
                lighting = state.lighting.copy(cascade = state.lighting.cascade.copy(dormHint = dormHint))
            )
        }
        if (loadAfterApply && phone != null && before.bathroomPhone.isBlank()) {
            loadBathroomBalance()
        }
        if (loadAfterApply && dormHint != null) {
            // 仅当本地 config 不完整时才触发级联匹配,避免重复请求
            val currentState = _uiState.value
            if (!currentState.ac.config.isComplete) {
                loadBuildings(ElectricityTarget.AC)
            }
            if (!currentState.lighting.config.isComplete) {
                loadBuildings(ElectricityTarget.LIGHTING)
            }
        }
    }

    private fun loadBalanceAndBills(forceBills: Boolean = false) {
        viewModelScope.launch {
            coroutineScope {
                val balanceJob = async { loadBalance() }
                val billsJob = async { loadBills(force = forceBills) }
                val bathroomJob = async { loadBathroomBalance() }
                val acJob = async { loadElectricityBalance(ElectricityTarget.AC) }
                val lightingJob = async { loadElectricityBalance(ElectricityTarget.LIGHTING) }
                val internetJob = async { loadInternetBalance() }
                balanceJob.await()
                billsJob.await()
                bathroomJob.await()
                acJob.await()
                lightingJob.await()
                internetJob.await()
            }
        }
    }

    // ── 校园卡余额 ──────────────────────────────────────
    //
    // 2026-06-22: 改用 adwmh 智慧安大支付码余额作为主源（与支付码页面数字一致）。
    // portal 余额作为 fallback (智慧安大登录失败/超时场景)。
    //
    // 用户反馈：portal 余额数字与支付码页面右下角"校园卡余额"不一致，
    // 是因为 portal (one.ahu.edu.cn) 走的是单卡余额（不含补贴/钱包合并），
    // 而 adwmh 显示的是完整余额。优先用 adwmh 体验更准。

    fun loadBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                // 主源：智慧安大支付码余额（与支付码页面数字一致）
                val qrRepo = adwmhCardRepository
                val adwmhBalance: Result<Double> = if (qrRepo != null) {
                    qrRepo.getBalance()
                } else {
                    Result.failure(IllegalStateException("智慧安大未登录"))
                }

                if (adwmhBalance.isSuccess) {
                    _uiState.update {
                        it.copy(
                            balance = adwmhBalance.getOrThrow(),
                            timestamp = DebugClock.nowMillis(),
                            isLoading = false,
                            error = null,
                        )
                    }
                    return@withContext
                }

                // 兜底：portal 一卡通余额（保持旧逻辑兼容）
                Log.w("HomeVM", "支付码余额获取失败，fallback 到 portal: ${adwmhBalance.exceptionOrNull()?.message}")
                var portalResult: Result<CardRepository.PortalBalance> = repository.getPortalBalance()
                if (portalResult.exceptionOrNull() is SessionExpiredException) {
                    Log.w("HomeVM", "余额接口报 session 失效，尝试重新登录")
                    val reLogin = casAuthRepository.ensureValidSession()
                    portalResult = if (reLogin.isSuccess) {
                        repository.getPortalBalance()
                    } else {
                        Result.failure(reLogin.exceptionOrNull() ?: Exception("登录失败"))
                    }
                }
                portalResult.fold(
                    onSuccess = { portal ->
                        _uiState.update {
                            it.copy(balance = portal.balance, timestamp = portal.timestamp, isLoading = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message ?: "查询失败") }
                    }
                )
            }
        }
    }

    // ── 账单 ────────────────────────────────────────────

    fun loadBills(force: Boolean = false) {
        viewModelScope.launch {
            // 先从缓存加载
            val cached = withContext(Dispatchers.IO) { sessionManager.getBillsJson() }
            if (!cached.isNullOrBlank()) {
                runCatching {
                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java, com.ahu_plus.data.model.BillRecord::class.java
                    ).type
                    val records: List<com.ahu_plus.data.model.BillRecord> =
                        com.ahu_plus.data.GsonProvider.instance.fromJson(cached, type)
                    _uiState.update { it.copy(bills = records, billsLoading = false) }
                }
            } else {
                _uiState.update { it.copy(billsLoading = true) }
            }
            if (!force && !DataRefreshPolicy.isStale(
                    sessionManager.getBillsUpdatedAt(), 24L * 60 * 60 * 1000
                )) return@launch
            // 后台刷新
            withContext(Dispatchers.IO) {
                val knownIds = _uiState.value.bills.mapNotNull { it.orderId.takeIf(String::isNotBlank) }.toSet()
                withYcardRelogin { ycardRepository.getIncrementalBills(knownIds) }.fold(
                    onSuccess = { records ->
                        // 合并：新数据 + 旧缓存中不重复的记录
                        val oldJson = sessionManager.getBillsJson()
                        val merged = if (!oldJson.isNullOrBlank()) {
                            val type = com.google.gson.reflect.TypeToken.getParameterized(
                                List::class.java, com.ahu_plus.data.model.BillRecord::class.java
                            ).type
                            val oldList: List<com.ahu_plus.data.model.BillRecord> =
                                com.ahu_plus.data.GsonProvider.instance.fromJson(oldJson, type)
                            val existingIds = records.map { it.orderId }.toSet()
                            val newOld = oldList.filter { it.orderId !in existingIds }
                            records + newOld
                        } else {
                            records
                        }
                        val json = com.ahu_plus.data.GsonProvider.instance.toJson(merged)
                        sessionManager.saveBillsJson(json)
                        _uiState.update { it.copy(bills = merged, billsLoading = false, billsError = null) }
                    },
                    onFailure = { e ->
                        // 缓存已有数据时不显示错误
                        if (_uiState.value.bills.isEmpty()) {
                            _uiState.update { it.copy(billsLoading = false, billsError = e.message ?: "账单查询失败") }
                        } else {
                            _uiState.update { it.copy(billsLoading = false) }
                        }
                    }
                )
            }
        }
    }

    // ── 浴室余额 ────────────────────────────────────────

    fun loadBathroomBalance() {
        viewModelScope.launch {
            val phone = _uiState.value.bathroomPhone.ifBlank {
                sessionManager.getBathroomPhone().orEmpty()
            }
            if (phone.isBlank()) {
                _uiState.update { it.copy(bathroomLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(bathroomLoading = true, bathroomError = null) }
            withContext(Dispatchers.IO) {
                withYcardRelogin { ycardRepository.getBathroomBalance(phone) }.fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(bathroomData = data, bathroomLoading = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(bathroomLoading = false, bathroomError = e.message ?: "浴室查询失败") }
                    }
                )
            }
        }
    }

    fun saveBathroomPhone(phone: String) {
        viewModelScope.launch {
            sessionManager.saveBathroomPhone(phone)
            _uiState.update { it.copy(bathroomPhone = phone) }
            loadBathroomBalance()
        }
    }

    // ── 电费余额 ────────────────────────────────────────

    fun loadElectricityBalance() {
        // 默认查空调 (首页电费卡片保持空调入口)
        loadElectricityBalance(ElectricityTarget.AC)
    }

    fun loadElectricityBalance(target: ElectricityTarget) {
        val state = _uiState.value.stateFor(target)
        val feeitemid = state.cascade.selectedFeeitemid ?: defaultFeeitemidFor(target)
        loadElectricityBalanceForFeeitemid(
            feeitemid = feeitemid,
            configProvider = { _uiState.value.stateFor(target).config },
            stateUpdater = { s, d, e -> s.updateState(target, d, e) },
            loadingUpdater = { s -> s.updateLoading(target) }
        )
    }

    fun saveElectricityConfig(config: ElectricityRoomConfig) {
        // 兼容旧接口: 默认存为空调配置
        saveElectricityConfig(config, ElectricityTarget.AC)
    }

    fun saveElectricityConfig(config: ElectricityRoomConfig, target: ElectricityTarget) {
        viewModelScope.launch {
            when (target) {
                ElectricityTarget.AC -> sessionManager.saveAcConfig(config)
                ElectricityTarget.LIGHTING -> sessionManager.saveLightingConfig(config)
            }
            _uiState.update { it.updateConfigFor(target, config) }
            loadElectricityBalance(target)
            loadElectricityBills(target)
        }
    }

    fun setAcBillRange(range: ElectricityBillRange) {
        _uiState.update { it.copy(acBillRange = range) }
        loadElectricityBills(ElectricityTarget.AC, range)
    }

    fun setLightingBillRange(range: ElectricityBillRange) {
        _uiState.update { it.copy(lightingBillRange = range) }
        loadElectricityBills(ElectricityTarget.LIGHTING, range)
    }

    fun loadElectricityBills() {
        loadElectricityBills(ElectricityTarget.AC)
    }

    fun loadElectricityBills(
        target: ElectricityTarget,
        range: ElectricityBillRange = _uiState.value.billRangeFor(target)
    ) {
        viewModelScope.launch {
            val state = _uiState.value.stateFor(target)
            val config = state.config
            val feeitemid = state.cascade.selectedFeeitemid ?: defaultFeeitemidFor(target)
            if (!config.isComplete) {
                _uiState.update { it.updateBillsLoading(target, range, false, null) }
                return@launch
            }
            _uiState.update { it.updateBillsLoading(target, range, true, null) }
            val endDate = DebugClock.todayDate()
            withContext(Dispatchers.IO) {
                getElectricityBillsByRange(
                    feeitemid = feeitemid,
                    config = config,
                    endDate = endDate,
                    range = range
                ).fold(
                    onSuccess = { records ->
                        _uiState.update {
                            it.updateBillsFor(target, records.sortedByDescending { r -> r.date }, false, null)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.updateBillsFor(target, emptyList(), false, e.message)
                        }
                    }
                )
            }
        }
    }

    // ── 电费房间级联元数据 (老区+新区合并) ─────────────────────

    /**
     * 合并加载三合一楼栋列表: 408 空调 + 428 照明 + 488 新区(空调+照明)。
     */
    fun loadBuildings(target: ElectricityTarget) {
        // 取消该 target 的旧级联请求,避免竞态覆盖
        when (target) {
            ElectricityTarget.AC -> cascadeJobAc?.cancel()
            ElectricityTarget.LIGHTING -> cascadeJobLighting?.cancel()
        }
        val job = viewModelScope.launch {
            updateCascade(target) { it.copy(loadingBuildings = true, buildingsError = null) }
            withContext(Dispatchers.IO) {
                // 并行拉老区空调 + 老区照明 + 新区校区
                val ac408Deferred = async {
                    withYcardRelogin { ycardRepository.getFeeItemBuildings("408", null) }
                }
                val light428Deferred = async {
                    withYcardRelogin { ycardRepository.getFeeItemBuildings("428", null) }
                }
                val campusDeferred = async {
                    withYcardRelogin { ycardRepository.getFeeItemCampuses("488") }
                }

                val ac408List = ac408Deferred.await().getOrDefault(emptyList())
                val light428List = light428Deferred.await().getOrDefault(emptyList())
                val campusResult = campusDeferred.await()
                val targetSuffix = target.suffix

                // 遍历所有新区校区拉 488 楼栋(空调+照明都收) — 并行
                val new488Buildings = mutableListOf<FeeItemOption>()
                val campusMap = mutableMapOf<String, String>()
                campusResult.onSuccess { campuses ->
                    // 先填充 campusMap 的校区条目
                    for (campus in campuses) {
                        val campusCode = campus.value.substringBefore("&")
                        campusMap[campusCode] = campus.value
                    }
                    // 并行拉取每个校区的楼栋,限制并发 3 避免一瞬间打爆 ycard
                    val campusSem = Semaphore(3)
                    val campusJobs = campuses.map { campus ->
                        async {
                            campusSem.withPermit {
                                withYcardRelogin {
                                    ycardRepository.getFeeItemBuildings("488", campus.value)
                                }.getOrDefault(emptyList()) to campus.value
                            }
                        }
                    }
                    for (deferred in campusJobs) {
                        val (raw, campusValue) = deferred.await()
                        for (b in raw) {
                            val bCode = b.value.substringBefore("&").take(11)
                            if (bCode !in campusMap) campusMap[bCode] = campusValue
                        }
                        new488Buildings.addAll(raw.filterFor(target))
                    }
                }

                val targetBuildings = when (target) {
                    ElectricityTarget.AC -> ac408List
                    ElectricityTarget.LIGHTING -> light428List
                }
                val mergedBuildings = targetBuildings + new488Buildings

                // 自动填充: 按当前卡片类型匹配,避免照明卡选到空调楼栋。
                val dormHint = _uiState.value.cascadeFor(target).dormHint
                val autoMatched = if (dormHint != null) {
                    matchBuilding(mergedBuildings, dormHint, targetSuffix)
                } else null

                val autoFeeitemid = autoMatched?.let {
                    if (it.value.startsWith("ul")) "488"
                    else if (it.name.endsWith("照明")) "428"
                    else "408"
                }

                val oldSelected = _uiState.value.cascadeFor(target).selectedBuilding
                _uiState.update { state ->
                    val cascade = state.cascadeFor(target).copy(
                        buildings = mergedBuildings, loadingBuildings = false,
                        floors = emptyList(), rooms = emptyList(),
                        floorsError = null, roomsError = null,
                        campusMap = campusMap
                    )
                    // API 匹配结果优先覆盖旧 config
                    val finalCascade = if (autoMatched != null) {
                        cascade.copy(selectedBuilding = autoMatched, selectedFeeitemid = autoFeeitemid)
                    } else cascade
                    state.applyCascade(target, finalCascade)
                }

                val selected = _uiState.value.cascadeFor(target).selectedBuilding
                if (selected != null && autoMatched != null) {
                    // 仅当楼栋变化或首次匹配时才继续拉楼层,避免重复请求
                    if (oldSelected == null || oldSelected.value != selected.value) {
                        loadFloorsInternal(target, selected)
                    }
                }
            }
        }
        when (target) {
            ElectricityTarget.AC -> cascadeJobAc = job
            ElectricityTarget.LIGHTING -> cascadeJobLighting = job
        }
    }

    /**
     * 多策略楼栋匹配(来自 Python 验证)。
     *
     * 策略:
     * 1. "{dormName}{中文数字}号楼{suffix}"  精确匹配 (榴园二号楼空调)
     * 2. "{dormName}{阿拉伯数字}号楼{suffix}" 精确匹配 (枫园1号楼空调)
     * 3. 候选名包含匹配
     * 4. name 以 dormName 开头且以 suffix 结尾 (新区兼容:兰园A楼空调)
     */
    private fun matchBuilding(
        buildings: List<FeeItemOption>,
        hint: DormHint,
        suffix: String
    ): FeeItemOption? {
        val cnDigits = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "七", 8 to "八", 9 to "九")
        val digitCn = cnDigits[hint.buildingDigit] ?: hint.buildingDigit.toString()
        val digitAr = hint.buildingDigit.toString()
        val base = hint.buildingName.trim()

        val candidates = listOf(
            "${base}${digitCn}号楼${suffix}",
            "${base}${digitAr}号楼${suffix}"
        )

        // 策略 1: 精确匹配
        for (c in candidates) {
            buildings.firstOrNull { it.name == c }?.let { return it }
        }
        // 策略 2: 候选名包含匹配
        for (c in candidates) {
            buildings.firstOrNull { c in it.name && it.name.endsWith(suffix) }?.let { return it }
        }
        // 策略 3: 前缀+后缀匹配 (新区)
        buildings.firstOrNull {
            it.name.startsWith(base) && it.name.endsWith(suffix)
        }?.let { return it }

        return null
    }

    fun selectBuilding(target: ElectricityTarget, building: FeeItemOption) {
        loadFloorsInternal(target, building)
    }

    /**
     * 自动填充:从 dormHint 匹配楼层,匹配到则自动拉房间→匹配房间→落盘。
     * 是自动填充链条的第2步(第1步楼栋匹配在 loadBuildings 中完成)。
     */
    private fun loadFloorsInternal(target: ElectricityTarget, building: FeeItemOption) {
        viewModelScope.launch {
            val cascade = _uiState.value.cascadeFor(target)
            val feeitemid = deriveFeeitemid(building.value, building.name) ?: "408"
            val campus = if (feeitemid == "488") resolveCampus(cascade.campusMap, building.value) else null

            _uiState.update { state ->
                val updated = state.cascadeFor(target).copy(
                    selectedBuilding = building,
                    selectedFeeitemid = feeitemid,
                    selectedFloor = null,
                    selectedRoom = null,
                    floors = emptyList(),
                    rooms = emptyList(),
                    loadingFloors = true,
                    floorsError = null,
                    roomsError = null
                )
                state.applyCascade(target, updated)
            }
            withContext(Dispatchers.IO) {
                withYcardRelogin {
                    ycardRepository.getFeeItemFloors(feeitemid, campus, building.value)
                }.fold(
                    onSuccess = { floors ->
                        val dormHint = _uiState.value.cascadeFor(target).dormHint
                        // 自动匹配楼层: 同时尝试中文(五层)和阿拉伯(5层)
                        val floorMatch = if (dormHint != null) {
                            val cnDigits = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "七", 8 to "八", 9 to "九")
                            val fd = dormHint.floorDigit
                            val cnName = "${cnDigits[fd] ?: fd}层"
                            val arName = "${fd}层"
                            floors.firstOrNull { it.name == cnName }
                                ?: floors.firstOrNull { it.name == arName }
                        } else null

                        _uiState.update { state ->
                            val cascade = state.cascadeFor(target).copy(
                                floors = floors,
                                loadingFloors = false,
                                selectedFloor = floorMatch
                            )
                            state.applyCascade(target, cascade)
                        }

                        // 匹配到楼层 → 自动拉房间
                        if (floorMatch != null) {
                            loadRoomsInternal(target, building, feeitemid, campus, floorMatch)
                        }
                    },
                    onFailure = { e ->
                        updateCascade(target) {
                            it.copy(loadingFloors = false, floorsError = e.message ?: "楼层列表加载失败")
                        }
                    }
                )
            }
        }
    }

    /**
     * 自动填充链条第3步:拉房间→匹配房间→落盘+查余额。
     */
    private fun loadRoomsInternal(
        target: ElectricityTarget,
        building: FeeItemOption,
        feeitemid: String,
        campus: String?,
        floor: FeeItemOption
    ) {
        viewModelScope.launch {
            updateCascade(target) { it.copy(loadingRooms = true, roomsError = null) }
            withContext(Dispatchers.IO) {
                withYcardRelogin {
                    ycardRepository.getFeeItemRooms(feeitemid, campus, building.value, floor.value)
                }.fold(
                    onSuccess = { rooms ->
                        val dormHint = _uiState.value.cascadeFor(target).dormHint
                        val roomMatch = if (dormHint != null) {
                            matchRoomFlexible(rooms, dormHint.roomNumber)
                        } else null

                        _uiState.update { state ->
                            val cascade = state.cascadeFor(target).copy(
                                rooms = rooms,
                                loadingRooms = false,
                                selectedRoom = roomMatch
                            )
                            state.applyCascade(target, cascade)
                        }

                        // 匹配到房间 → 自动落盘 + 查余额 (按 feeitemid 分发到 AC/Lighting)
                        if (roomMatch != null) {
                            val cascade = _uiState.value.cascadeFor(target)
                            val campus = if (feeitemid == "488") resolveCampus(cascade.campusMap, building.value) else null
                            val config = ElectricityRoomConfig(
                                building = building.value,
                                floor = floor.value,
                                room = roomMatch.value,
                                campus = campus ?: ""
                            )
                            when (target) {
                                ElectricityTarget.AC -> sessionManager.saveAcConfig(config)
                                ElectricityTarget.LIGHTING -> sessionManager.saveLightingConfig(config)
                            }
                            _uiState.update { it.updateConfigFor(target, config) }
                            loadElectricityBalance(target)
                            loadElectricityBills(target)
                        }
                    },
                    onFailure = { e ->
                        updateCascade(target) {
                            it.copy(loadingRooms = false, roomsError = e.message ?: "房间列表加载失败")
                        }
                    }
                )
            }
        }
    }

    fun selectFloor(target: ElectricityTarget, floor: FeeItemOption) {
        val cascade = _uiState.value.cascadeFor(target)
        val building = cascade.selectedBuilding ?: return
        val fid = cascade.selectedFeeitemid ?: return
        val campus = if (fid == "488") resolveCampus(cascade.campusMap, building.value) else null
        _uiState.update { state ->
            val updated = state.cascadeFor(target).copy(
                selectedFloor = floor, selectedRoom = null,
                rooms = emptyList(), loadingRooms = true, roomsError = null
            )
            state.applyCascade(target, updated)
        }
        loadRoomsInternal(target, building, fid, campus, floor)
    }


    fun selectRoom(target: ElectricityTarget, room: FeeItemOption) {
        viewModelScope.launch {
            val cascade = _uiState.value.cascadeFor(target)
            val building = cascade.selectedBuilding ?: return@launch
            val floor = cascade.selectedFloor ?: return@launch
            val feeitemid = cascade.selectedFeeitemid ?: defaultFeeitemidFor(target)
            val campus = if (feeitemid == "488") resolveCampus(cascade.campusMap, building.value) else null
            val config = ElectricityRoomConfig(
                building = building.value,
                floor = floor.value,
                room = room.value,
                campus = campus ?: ""
            )
            saveElectricityConfig(config, target)
        }
    }

    /**
     * 多策略房间匹配 (验证过 13 个用例,12 pass)。
     *
     * 策略:
     * 1. 完整包含: room.name 含 roomNumber (2514 in "2514")
     * 2. 纯数字精确: room 名提取数字后 == roomNumber
     * 3. 后 N 位: room 名提取数字后取后 3/2 位精确匹配 (8301→301, 3101→101)
     * 4. 去首位前缀: 去掉 1 位前缀后精确匹配 (3101→101)
     */
    private fun matchRoomFlexible(rooms: List<FeeItemOption>, roomNumber: String): FeeItemOption? {
        // 策略1: 完整包含
        rooms.firstOrNull { roomNumber in it.name }?.let { return it }

        // 策略2: 纯数字精确匹配
        rooms.firstOrNull {
            it.name.filter(Char::isDigit) == roomNumber
        }?.let { return it }

        // 策略3: 后N位精确匹配 (蕙园 8301→301, 江园 3101→101)
        // 仅当位数差为 1 时使用 (避免 4 位→2 位的过度退让)
        for (suffixLen in listOf(3, 2)) {
            if (roomNumber.length > suffixLen && roomNumber.length - suffixLen <= 1) {
                val suffix = roomNumber.takeLast(suffixLen)
                rooms.firstOrNull {
                    it.name.filter(Char::isDigit) == suffix
                }?.let { return it }
            }
        }

        // 策略4: 去首位前缀 (3101→101), 仅去 1 位
        if (roomNumber.length >= 3) {
            val sub = roomNumber.substring(1)
            rooms.firstOrNull {
                it.name.filter(Char::isDigit) == sub
            }?.let { return it }
        }

        return null
    }

    /** 从 campusMap 查找 building 对应的 campus 全值。 */
    private fun resolveCampus(campusMap: Map<String, String>, buildingValue: String): String? {
        val campusCode = buildingValue.substringBefore("&").take(11) // "ul001002002075" → "ul001002002"
        return campusMap[campusCode]
    }

    private fun loadElectricityBalanceForFeeitemid(
        feeitemid: String,
        configProvider: () -> ElectricityRoomConfig,
        stateUpdater: (HomeUiState, ElectricityUiData?, String?) -> HomeUiState,
        loadingUpdater: (HomeUiState) -> HomeUiState
    ) {
        viewModelScope.launch {
            val config = configProvider()
            if (!config.isComplete) {
                _uiState.update { s -> stateUpdater(s, null, null) }
                return@launch
            }
            _uiState.update(loadingUpdater)
            withContext(Dispatchers.IO) {
                val isNewCampus = feeitemid == "488"
                withYcardRelogin {
                    ycardRepository.getElectricityBalance(
                        feeitemid = feeitemid,
                        building = config.building,
                        floor = config.floor,
                        room = config.room,
                        level = if (isNewCampus) "4" else "3",
                        campus = if (isNewCampus) config.campus.takeIf { c -> c.isNotBlank() } else null
                    )
                }.fold(
                    onSuccess = { data ->
                        _uiState.update { s -> stateUpdater(s, data, null) }
                    },
                    onFailure = { e ->
                        _uiState.update { s -> stateUpdater(s, null, e.message ?: "电费查询失败") }
                    }
                )
            }
        }
    }

    private suspend fun getElectricityBillsByRange(
        feeitemid: String,
        config: ElectricityRoomConfig,
        endDate: LocalDate,
        range: ElectricityBillRange
    ): Result<List<ElectricityDailyRecord>> {
        val campus = if (feeitemid == "488") config.campus.takeIf { c -> c.isNotBlank() } else null
        if (range == ElectricityBillRange.SEVEN_DAYS) {
            return withYcardRelogin {
                ycardRepository.getElectricityBills(
                    feeitemid = feeitemid,
                    building = config.building,
                    floor = config.floor,
                    room = config.room,
                    startDate = endDate.minusDays(7).toString(),
                    endDate = endDate.toString(),
                    campus = campus
                )
            }
        }

        val allRecords = mutableListOf<ElectricityDailyRecord>()
        repeat(4) { index ->
            val chunkEnd = endDate.minusDays((index * 7).toLong())
            val chunkStart = chunkEnd.minusDays(7)
            val result = withYcardRelogin {
                ycardRepository.getElectricityBills(
                    feeitemid = feeitemid,
                    building = config.building,
                    floor = config.floor,
                    room = config.room,
                    startDate = chunkStart.toString(),
                    endDate = chunkEnd.toString(),
                    campus = campus
                )
            }
            result.fold(
                onSuccess = { allRecords += it },
                onFailure = { return Result.failure(it) }
            )
        }

        return Result.success(
            allRecords
                .distinctBy { it.date to it.degreeText }
                .sortedByDescending { it.date }
        )
    }

    private fun updateCascade(target: ElectricityTarget, transform: (FeeItemCascadeState) -> FeeItemCascadeState) {
        _uiState.update { state ->
            val cascade = transform(state.cascadeFor(target))
            state.applyCascade(target, cascade)
        }
    }

    private fun HomeUiState.stateFor(target: ElectricityTarget): ElectricityState = when (target) {
        ElectricityTarget.AC -> ac
        ElectricityTarget.LIGHTING -> lighting
    }

    private fun HomeUiState.updateState(
        target: ElectricityTarget,
        data: ElectricityUiData?,
        error: String?
    ): HomeUiState = when (target) {
        ElectricityTarget.AC -> copy(ac = ac.copy(data = data, loading = false, error = error))
        ElectricityTarget.LIGHTING -> copy(lighting = lighting.copy(data = data, loading = false, error = error))
    }

    private fun HomeUiState.updateLoading(target: ElectricityTarget): HomeUiState = when (target) {
        ElectricityTarget.AC -> copy(ac = ac.copy(loading = true, error = null))
        ElectricityTarget.LIGHTING -> copy(lighting = lighting.copy(loading = true, error = null))
    }

    private fun HomeUiState.updateConfigFor(target: ElectricityTarget, config: ElectricityRoomConfig): HomeUiState =
        when (target) {
            ElectricityTarget.AC -> copy(ac = ac.copy(config = config))
            ElectricityTarget.LIGHTING -> copy(lighting = lighting.copy(config = config))
        }

    private fun HomeUiState.billRangeFor(target: ElectricityTarget): ElectricityBillRange = when (target) {
        ElectricityTarget.AC -> acBillRange
        ElectricityTarget.LIGHTING -> lightingBillRange
    }

    private fun HomeUiState.updateBillsLoading(
        target: ElectricityTarget,
        range: ElectricityBillRange,
        loading: Boolean,
        error: String?
    ): HomeUiState =
        when (target) {
            ElectricityTarget.AC -> copy(
                acBillRange = range,
                acBillsLoading = loading,
                acBillsError = error
            )
            ElectricityTarget.LIGHTING -> copy(
                lightingBillRange = range,
                lightingBillsLoading = loading,
                lightingBillsError = error
            )
        }

    private fun HomeUiState.updateBillsFor(
        target: ElectricityTarget,
        records: List<ElectricityDailyRecord>,
        loading: Boolean,
        error: String?
    ): HomeUiState = when (target) {
        ElectricityTarget.AC -> copy(
            acBills = records,
            acBillsLoading = loading,
            acBillsError = error
        )
        ElectricityTarget.LIGHTING -> copy(
            lightingBills = records,
            lightingBillsLoading = loading,
            lightingBillsError = error
        )
    }

    private fun defaultFeeitemidFor(target: ElectricityTarget): String = when (target) {
        ElectricityTarget.AC -> "408"
        ElectricityTarget.LIGHTING -> "428"
    }

    private fun HomeUiState.cascadeFor(target: ElectricityTarget): FeeItemCascadeState = stateFor(target).cascade

    private fun HomeUiState.applyCascade(target: ElectricityTarget, cascade: FeeItemCascadeState): HomeUiState =
        when (target) {
            ElectricityTarget.AC -> copy(ac = ac.copy(cascade = cascade))
            ElectricityTarget.LIGHTING -> copy(lighting = lighting.copy(cascade = cascade))
        }

    private val ElectricityTarget.suffix: String
        get() = when (this) {
            ElectricityTarget.AC -> "空调"
            ElectricityTarget.LIGHTING -> "照明"
        }

    private fun List<FeeItemOption>.filterFor(target: ElectricityTarget): List<FeeItemOption> {
        val suffix = target.suffix
        val filtered = filter { it.name.endsWith(suffix) }
        return filtered.ifEmpty { this }
    }

    // ── 网费余额 ────────────────────────────────────────

    fun loadInternetBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(internetLoading = true, internetError = null) }
            withContext(Dispatchers.IO) {
                withYcardRelogin { ycardRepository.getInternetBalance() }.fold(
                    onSuccess = { data ->
                        _uiState.update {
                            it.copy(internetData = data, internetLoading = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(internetLoading = false, internetError = e.message ?: "网费查询失败")
                        }
                    }
                )
            }
        }
    }

    /** 连续 QR 加载失败次数，用于退避 */
    private var qrConsecutiveFailures = 0

    /**
     * 冷启动时用持久化的旧码先填充 UI(并标注新鲜度),避免服务器抖动时白屏。
     * 太旧的码(> [QR_CACHE_MAX_RESTORE_MS])不展示,直接走正常加载流程。
     */
    private fun restoreCachedQr() {
        if (adwmhCardRepository == null) return
        val (payload, serverText, fetchedAt) = sessionManager.getAdwmhQrCache()
        if (payload.isNullOrBlank() || fetchedAt <= 0L) return
        val ageMs = System.currentTimeMillis() - fetchedAt
        if (ageMs > QR_CACHE_MAX_RESTORE_MS) return
        _uiState.update { state ->
            if (state.qrCode != null) return@update state
            state.copy(
                qrCode = AdwmhQrCode(
                    payload = payload,
                    statusMsg = serverText,
                    fetchedAt = fetchedAt
                ),
                qrStale = ageMs > QR_STALE_THRESHOLD_MS,
                qrAgeSeconds = (ageMs / 1000).toInt()
            )
        }
    }

    /** 持久化最近一次成功的支付码,供下次冷启动兜底展示。 */
    private suspend fun persistQr(qr: AdwmhQrCode) {
        sessionManager.saveAdwmhQrCache(qr.payload, qr.statusMsg, qr.fetchedAt)
    }

    fun loadCampusQrCode() {
        val qrRepository = adwmhCardRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(qrLoading = true, qrError = null) }
            withContext(Dispatchers.IO) {
                // 先尝试直接加载（用已有 session）
                val qrResult = qrRepository.getQrCode()
                if (qrResult.isSuccess) {
                    qrConsecutiveFailures = 0
                    val qr = qrResult.getOrThrow()
                    persistQr(qr)
                    val balanceResult = qrRepository.getBalance()
                    _uiState.update { state ->
                        state.copy(
                            qrCode = qr,
                            qrBalance = balanceResult.getOrNull() ?: state.qrBalance,
                            qrLoading = false,
                            qrError = null,
                            qrStale = false,
                            qrAgeSeconds = 0
                        )
                    }
                    return@withContext
                }

                val errorMsg = qrResult.exceptionOrNull()?.message.orEmpty()
                val isTimeout = errorMsg.contains("超时") || errorMsg.contains("timeout")

                // 仅会话过期时自动重登录；超时/网络问题不触发重登录（避免加重速率限制）
                val isAuthError = errorMsg.contains("会话已过期") ||
                    errorMsg.contains("重新登录") ||
                    errorMsg.contains("请先登录") ||
                    errorMsg.contains("返回 HTML")

                if (isAuthError) {
                    val username = sessionManager.getUsername()
                    val password = sessionManager.getPassword()
                    if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                        val loginResult = qrRepository.autoLogin(
                            username, password,
                            concurrentRetry = false  // 速率限制下禁用并发重试
                        )
                        if (loginResult.isSuccess) {
                            qrConsecutiveFailures = 0
                            val retryQr = qrRepository.getQrCode()
                            val balanceResult = qrRepository.getBalance()
                            _uiState.update { state ->
                                retryQr.fold(
                                    onSuccess = { qr ->
                                        state.copy(
                                            qrCode = qr,
                                            qrBalance = balanceResult.getOrNull() ?: state.qrBalance,
                                            qrLoading = false,
                                            qrError = null,
                                            qrStale = false,
                                            qrAgeSeconds = 0
                                        )
                                    },
                                    onFailure = { e2 ->
                                        qrConsecutiveFailures++
                                        state.copy(
                                            qrLoading = false,
                                            qrError = e2.message ?: "QR 加载失败"
                                        )
                                    }
                                )
                            }
                            retryQr.getOrNull()?.let { persistQr(it) }
                            return@withContext
                        }
                    }
                }

                // 全部失败（超时或登录失败）
                qrConsecutiveFailures++
                _uiState.update {
                    it.copy(
                        qrLoading = false,
                        qrError = if (isTimeout) "支付码服务暂不可用" else errorMsg.ifBlank { "加载失败" }
                    )
                }
            }
        }
    }

    // ── 智慧安大自动登录（参考 AHUTong）──────────────────

    /** 后台自动登录智慧安大，登录成功后自动加载 QR 码。 */
    fun autoLoginAdwmh() {
        val qrRepository = adwmhCardRepository ?: return
        val username = sessionManager.getUsername()
        val password = sessionManager.getPassword()
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            _uiState.update { it.copy(qrError = "请先完成统一身份认证") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(qrLoading = true, qrError = null) }
            withContext(Dispatchers.IO) {
                qrRepository.autoLogin(username, password, sessionManager.getAdwmhConcurrentRetry()).fold(
                    onSuccess = {
                        // 登录成功 → 加载 QR 码
                        loadCampusQrCode()
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                qrLoading = false,
                                qrError = e.message ?: "智慧安大登录失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun hasAdwmhSession(): Boolean = adwmhCardRepository?.hasSession() ?: false

    /** 读取设置：是否在支付码界面自动调高亮度 */
    fun getQrBrightnessBoost(): Boolean = sessionManager.getQrBrightnessBoost()

    /** 读取设置：智慧安大登录是否启用并发重试 */
    fun getAdwmhConcurrentRetry(): Boolean = sessionManager.getAdwmhConcurrentRetry()

    fun setQrBrightnessBoost(enabled: Boolean) { viewModelScope.launch { sessionManager.setQrBrightnessBoost(enabled) } }
    fun setAdwmhConcurrentRetry(enabled: Boolean) { viewModelScope.launch { sessionManager.setAdwmhConcurrentRetry(enabled) } }

    /** @deprecated 保留兼容 — 手动导入 session 的旧入口。 */
    fun importAdwmhSession(sessionId: String) {
        val qrRepository = adwmhCardRepository ?: return
        viewModelScope.launch {
            qrRepository.importSessionId(sessionId)
            loadCampusQrCode()
        }
    }

    // ── 电费通用加载 ────────────────────────────────────

    fun loadInternetBills() {
        viewModelScope.launch {
            _uiState.update { it.copy(internetBillsLoading = true, internetBillsError = null) }
            withContext(Dispatchers.IO) {
                withYcardRelogin { ycardRepository.getInternetBills(row = 20) }.fold(
                    onSuccess = { response ->
                        _uiState.update {
                            it.copy(
                                internetBills = response.accountList,
                                internetBillsLoading = false,
                                internetBillsError = null
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                internetBillsLoading = false,
                                internetBillsError = e.message ?: "网费账单查询失败"
                            )
                        }
                    }
                )
            }
        }
    }

    private suspend fun <T> withYcardRelogin(request: suspend () -> Result<T>): Result<T> {
        val first = request()
        if (!first.isYcardAuthExpired()) return first

        val username = sessionManager.getUsername()
        val password = sessionManager.getPassword()
        if (username.isNullOrBlank() || password.isNullOrBlank()) return first

        Log.w("HomeVM", "ycard 请求认证过期，尝试重新登录后重试")
        casAuthRepository.ensureValidSession()
        val login = ycardRepository.login(username, password)
        if (login.isFailure) {
            return Result.failure(login.exceptionOrNull() ?: Exception("登录状态过期，请重新登录"))
        }
        return request()
    }

    /**
     * 类型化检测 ycard 认证过期。
     *
     * ycard HTTP 401/403 由 [YcardRepository] 抛 [YcardAuthExpiredException];
     * "未登录 ycard" 走未登录分支(此处不重试,直接返回首次结果)。
     */
    private fun Result<*>.isYcardAuthExpired(): Boolean =
        exceptionOrNull() is YcardAuthExpiredException

    fun onRefresh() {
        loadBalanceAndBills(forceBills = true)
        loadCampusQrCode()
    }

    // ─── 水电费充值 (2026-06-29 接入) ────────────────────────────────

    /**
     * 打开充值 sheet,根据 target 预填。
     * - 电费:用对应 ElectricityState 的 room 元数据
     * - 浴室:用 bathroomData
     *
     * 密码从学生信息 `ID_NUMBER` / `SFZH` 字段取后 6 位(默认查询密码 = 身份证后 6 位),
     * 取不到就留空让用户手输。
     */
    fun openDepositSheet(target: DepositTarget) {
        if (target === DepositTarget.None) return
        val info = studentInfoRepository?.readCachedStudentInfo()
        val idNumber = info?.firstValueOf("身份证号", "身份证件号", "ID_NUMBER", "SFZH")
        val prefilledPassword = idNumber
            ?.filter(Char::isDigit)
            ?.takeLast(6)
            ?.takeIf { it.length == 6 }
            ?: ""

        val sheet = when (target) {
            is DepositTarget.Electricity -> {
                val ac = _uiState.value.ac
                val lighting = _uiState.value.lighting
                val eState = if (target.feeitemid == "428") lighting else ac
                val isNew = target.feeitemid == "488"
                DepositSheetState(
                    visible = true,
                    target = target,
                    title = if (target.feeitemid == "428") "照明充值" else "空调充值",
                    subtitle = "${eState.config.campus.substringAfter("&", "未知校区")} " +
                        "${eState.config.building.substringAfter("&", "")} " +
                        eState.config.room.substringAfter("&", ""),
                    amount = _uiState.value.depositSheet.amount.ifBlank { "5" },
                    password = prefilledPassword,
                    passwordPrefilled = prefilledPassword.isNotEmpty(),
                    showAcOrLighting = isNew,
                    subTarget = if (target.feeitemid == "428") DepositSubTarget.Lighting
                                 else DepositSubTarget.Ac,
                )
            }
            is DepositTarget.Bathroom -> {
                DepositSheetState(
                    visible = true,
                    target = target,
                    title = "浴室充值",
                    subtitle = "项目:${target.bathroom.projectName.ifBlank { "浴室" }} " +
                        "手机:${_uiState.value.bathroomPhone}",
                    amount = _uiState.value.depositSheet.amount.ifBlank { "5" },
                    password = prefilledPassword,
                    passwordPrefilled = prefilledPassword.isNotEmpty(),
                    showAcOrLighting = false,
                )
            }
            is DepositTarget.Internet -> {
                DepositSheetState(
                    visible = true,
                    target = target,
                    title = "网费充值",
                    subtitle = "账号: ${target.internet.account} · " +
                        "余额: ${"%.2f".format(target.internet.balanceYuan)} 元",
                    amount = _uiState.value.depositSheet.amount.ifBlank { "5" },
                    password = prefilledPassword,
                    passwordPrefilled = prefilledPassword.isNotEmpty(),
                    showAcOrLighting = false,
                )
            }
            DepositTarget.None -> return
        }
        _uiState.update { it.copy(depositSheet = sheet) }
    }

    /** 关闭 sheet(取消 / 成功 / 失败) */
    fun closeDepositSheet() {
        _uiState.update { it.copy(depositSheet = it.depositSheet.copy(visible = false, error = null)) }
    }

    fun updateDepositAmount(v: String) {
        _uiState.update { it.copy(depositSheet = it.depositSheet.copy(amount = v, error = null)) }
    }

    fun updateDepositPassword(v: String) {
        _uiState.update { it.copy(
            depositSheet = it.depositSheet.copy(
                password = v.filter(Char::isDigit).take(6),
                passwordError = null,
                error = null,
            )
        ) }
    }

    fun updateDepositSubTarget(t: DepositSubTarget) {
        _uiState.update { it.copy(depositSheet = it.depositSheet.copy(subTarget = t)) }
    }

    /**
     * 提交充值:Step 1 下单 → Step 2+3 拉 passwordMap + 提交密文(真扣款)。
     *
     * @return 成功 → 关闭 sheet + 刷新余额
     */
    fun submitDeposit() {
        val sheet = _uiState.value.depositSheet
        if (!sheet.canConfirm) return

        _uiState.update { it.copy(
            depositSheet = sheet.copy(inProgress = true, error = null)
        ) }

        viewModelScope.launch {
            val target = sheet.target
            val amount = sheet.amount
            val password = sheet.password

            val result: Result<Unit> = when (target) {
                is DepositTarget.Electricity -> {
                    val ac = _uiState.value.ac
                    val lighting = _uiState.value.lighting
                    val eState = if (target.feeitemid == "428") lighting else ac
                    val eData = eState.data
                    if (eData == null) {
                        Result.failure(IllegalStateException("请先选择房间再充值"))
                    } else if (eData.aid.isBlank() || eData.account.isBlank()) {
                        Result.failure(IllegalStateException("未取到账户信息,请刷新余额后重试"))
                    } else {
                        // 缴费实际 feeitemid:用户选「充空调」仍传 488 (沿用 AHUTong)
                        val payFeeitemid = if (target.feeitemid == "488") "488" else target.feeitemid
                        // third_party 以服务器回显字段为准 (对齐 AHUTong),仅在服务器没回填时回退 config。
                        // 老区 config.campus 为空,新区为 "code&name"。
                        val cfgAreaCode = eState.config.campus.substringBefore("&", "")
                        val cfgAreaName = eState.config.campus.substringAfter("&", "")
                        val roomData = ElectricityBalanceData(
                            area = eData.area.ifBlank { cfgAreaCode },
                            buildingName = eData.buildingName,
                            areaName = eData.areaName.ifBlank { cfgAreaName },
                            floorName = eData.floorName,
                            floor = eData.floor.ifBlank { eState.config.floor },
                            aid = eData.aid,
                            account = eData.account,
                            building = eData.building.ifBlank { eState.config.building },
                            room = eData.room.ifBlank { eState.config.room },
                            roomName = eData.roomName,
                        )
                        val orderRes = ycardPayRepository.createElectricityOrder(
                            amount = amount,
                            feeitemid = payFeeitemid,
                            room = roomData,
                        )
                        if (orderRes.isFailure) {
                            Result.failure(orderRes.exceptionOrNull()!!)
                        } else {
                            ycardPayRepository.payWithAccount(orderRes.getOrThrow(), password)
                        }
                    }
                }
                is DepositTarget.Bathroom -> {
                    val orderRes = ycardPayRepository.createBathroomOrder(
                        amount = amount,
                        bathroom = target.bathroom,
                    )
                    if (orderRes.isFailure) {
                        Result.failure(orderRes.exceptionOrNull()!!)
                    } else {
                        ycardPayRepository.payBathroomWithAccount(orderRes.getOrThrow(), password)
                    }
                }
                is DepositTarget.Internet -> {
                    val orderRes = ycardPayRepository.createInternetOrder(
                        amount = amount,
                        internet = target.internet,
                    )
                    if (orderRes.isFailure) {
                        Result.failure(orderRes.exceptionOrNull()!!)
                    } else {
                        ycardPayRepository.payWithAccount(orderRes.getOrThrow(), password)
                    }
                }
                DepositTarget.None -> Result.failure(IllegalStateException("未知充值类型"))
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(
                        depositSheet = it.depositSheet.copy(visible = false, inProgress = false, error = null)
                    ) }
                    // 刷新余额 / 账单
                    when (target) {
                        is DepositTarget.Electricity -> {
                            if (target.feeitemid == "428") loadElectricityBalance(ElectricityTarget.LIGHTING)
                            else loadElectricityBalance(ElectricityTarget.AC)
                        }
                        is DepositTarget.Bathroom -> loadBathroomBalance()
                        is DepositTarget.Internet -> loadInternetBalance()
                        DepositTarget.None -> Unit
                    }
                },
                onFailure = { e ->
                    val msg = e.message ?: "充值失败"
                    val isPasswordError = msg.contains("密码", ignoreCase = true) ||
                        msg.contains("password", ignoreCase = true) ||
                        msg.contains("0001")
                    _uiState.update { it.copy(
                        depositSheet = it.depositSheet.copy(
                            inProgress = false,
                            error = if (isPasswordError) "密码错误,请重新输入" else msg,
                            password = if (isPasswordError) "" else it.depositSheet.password,
                            passwordError = if (isPasswordError) "密码错误" else null,
                        )
                    ) }
                },
            )
        }
    }

    private fun startQrAutoRefresh() {
        if (adwmhCardRepository == null || qrRefreshJob?.isActive == true) return
        qrRefreshJob = viewModelScope.launch {
            val baseInterval = (QR_REFRESH_INTERVAL_MS / 1000).toInt()
            while (true) {
                loadCampusQrCode()
                // 指数退避：连续失败越多，等待越久（最多 4 分钟）
                val backoffMultiplier = when {
                    qrConsecutiveFailures >= 5 -> 6   // 5+ 次失败 → 4.5 分钟
                    qrConsecutiveFailures >= 3 -> 3   // 3-4 次失败 → 2.25 分钟
                    qrConsecutiveFailures >= 1 -> 2   // 1-2 次失败 → 1.5 分钟
                    else -> 1                         // 正常 → 45 秒
                }
                val totalSeconds = baseInterval * backoffMultiplier
                for (remaining in totalSeconds downTo 0) {
                    _uiState.update { state ->
                        // 按当前展示码的获取时间实时计算新鲜度
                        val fetchedAt = state.qrCode?.fetchedAt ?: 0L
                        if (fetchedAt <= 0L) {
                            state.copy(qrCountdownSeconds = remaining)
                        } else {
                            val ageMs = System.currentTimeMillis() - fetchedAt
                            state.copy(
                                qrCountdownSeconds = remaining,
                                qrAgeSeconds = (ageMs / 1000).toInt(),
                                qrStale = ageMs > QR_STALE_THRESHOLD_MS
                            )
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    private companion object {
        const val QR_REFRESH_INTERVAL_MS = 45_000L
        /** 展示码超过此时长视为可能已失效,UI 弹出醒目提示。 */
        const val QR_STALE_THRESHOLD_MS = 60_000L
        /** 冷启动时,缓存码超过此时长则不再展示(太旧已无意义)。 */
        const val QR_CACHE_MAX_RESTORE_MS = 10 * 60_000L
    }
}

// ── UI State ──────────────────────────────────────────────

data class HomeUiState(
    val balance: Double = 0.0,
    val timestamp: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // 账单
    val bills: List<BillRecord> = emptyList(),
    val billsLoading: Boolean = false,
    val billsError: String? = null,
    // 浴室余额
    val bathroomPhone: String = "",
    val bathroomData: BathroomBalanceData? = null,
    val bathroomLoading: Boolean = false,
    val bathroomError: String? = null,
    // 电费 — 空调 (feeitemid 408/488)
    val ac: ElectricityState = ElectricityState(),
    val acBillRange: ElectricityBillRange = ElectricityBillRange.SEVEN_DAYS,
    val acBills: List<ElectricityDailyRecord> = emptyList(),
    val acBillsLoading: Boolean = false,
    val acBillsError: String? = null,
    // 电费 — 照明 (feeitemid 428)
    val lighting: ElectricityState = ElectricityState(),
    val lightingBillRange: ElectricityBillRange = ElectricityBillRange.SEVEN_DAYS,
    val lightingBills: List<ElectricityDailyRecord> = emptyList(),
    val lightingBillsLoading: Boolean = false,
    val lightingBillsError: String? = null,
    // 网费
    val internetData: InternetBalanceData? = null,
    val internetLoading: Boolean = false,
    val internetError: String? = null,
    val internetBills: List<InternetBillRecord> = emptyList(),
    val internetBillsLoading: Boolean = false,
    val internetBillsError: String? = null,
    val qrCode: AdwmhQrCode? = null,
    val qrBalance: Double? = null,
    val qrLoading: Boolean = false,
    val qrError: String? = null,
    val qrCountdownSeconds: Int = 0,
    /** 当前展示码是否可能已失效(获取时间超过阈值)。 */
    val qrStale: Boolean = false,
    /** 当前展示码的获取距今秒数,用于"X 秒前"文案。 */
    val qrAgeSeconds: Int = 0,
    // 智慧安大登录态
    val adwmhCaptchaBytes: ByteArray? = null,
    val adwmhCaptchaLoading: Boolean = false,
    val adwmhCaptchaError: String? = null,
    val adwmhLoginLoading: Boolean = false,
    val adwmhLoginError: String? = null,
    val adwmhLoginInfo: AdwmhLoginInfo? = null,
    // 水电费充值 sheet (2026-06-29 接入)
    val depositSheet: DepositSheetState = DepositSheetState(),
)

enum class ElectricityBillRange(val label: String, val loadingText: String, val emptyText: String) {
    SEVEN_DAYS("近 7 天", "正在加载近 7 天明细...", "近 7 天暂无明细"),
    LAST_MONTH("近一个月", "正在分周加载近一个月明细...", "近一个月暂无明细")
}

data class ElectricityState(
    val data: ElectricityUiData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val config: ElectricityRoomConfig = ElectricityRoomConfig(),
    /** 级联下拉状态 (楼栋→楼层→房间)。打开"设置房间"对话框时由 UI 触发加载。 */
    val cascade: FeeItemCascadeState = FeeItemCascadeState()
)

/** 电费房间级联元数据状态。 */
data class FeeItemCascadeState(
    val buildings: List<FeeItemOption> = emptyList(),
    val floors: List<FeeItemOption> = emptyList(),
    val rooms: List<FeeItemOption> = emptyList(),
    val selectedBuilding: FeeItemOption? = null,
    val selectedFloor: FeeItemOption? = null,
    val selectedRoom: FeeItemOption? = null,
    val selectedFeeitemid: String? = null,
    /** 新区 488 building value 前缀 → campus full value 映射。如 "ul001002002" → "ul001002002&磬苑校区" */
    val campusMap: Map<String, String> = emptyMap(),
    val loadingBuildings: Boolean = false,
    val loadingFloors: Boolean = false,
    val loadingRooms: Boolean = false,
    val buildingsError: String? = null,
    val floorsError: String? = null,
    val roomsError: String? = null,
    val dormHint: DormHint? = null
)

/** 标识电费类型 (空调 / 照明)。 */
enum class ElectricityTarget { AC, LIGHTING }
