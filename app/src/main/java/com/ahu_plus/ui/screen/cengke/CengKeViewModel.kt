package com.ahu_plus.ui.screen.cengke

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jwapp.CengCourse
import com.ahu_plus.data.model.jwapp.JwAppAccount
import com.ahu_plus.data.model.jwapp.JwAppBuilding
import com.ahu_plus.data.model.jwapp.JwAppCampus
import com.ahu_plus.data.model.jwapp.JwAppRoomType
import com.ahu_plus.data.model.jwapp.TimeSlot
import com.ahu_plus.data.repository.CengKeParser
import com.ahu_plus.data.repository.CengKeRepository
import com.ahu_plus.data.repository.JwAppAuthRepository
import com.ahu_plus.data.repository.JwAppAuthRequiredException
import com.ahu_plus.data.repository.JwAppLoginResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 蹭课 ViewModel。复用 [JwAppAuthRepository](与教室课表共享同一 jwapp 会话)。
 *
 * 池模型:改校区/日期/楼/教室类型使候选池失效([poolKey] 变化),下次抽卡重新拉;
 * 改学院/时段/"换一个"是纯内存重筛,不重复请求。随机与过滤逻辑在 [CengKeParser]。
 */
class CengKeViewModel(
    private val authRepository: JwAppAuthRepository,
    private val repository: CengKeRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CengKeUiState(
            loggedIn = authRepository.isLoggedIn(),
            username = authRepository.savedUsername() ?: sessionManager.getUsername().orEmpty(),
            password = authRepository.savedPassword().orEmpty(),
        )
    )
    val uiState: StateFlow<CengKeUiState> = _uiState.asStateFlow()

    private var buildingJob: Job? = null
    private var buildingGeneration = 0L
    private var pickJob: Job? = null
    private var pickGeneration = 0L

    /** 已加载的候选池 + 其对应的 key(campus|date|buildings|roomTypes)。 */
    private var pool: List<CengCourse> = emptyList()
    private var poolKey: String? = null

    fun activate() {
        if (_uiState.value.activated) return
        _uiState.value = _uiState.value.copy(activated = true)
        if (authRepository.isLoggedIn()) loadMetadata()
    }

    // ── 登录(复用 jwapp 会话)────────────────────────────────
    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, loginError = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, loginError = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(loginError = "请输入教务账号和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(loginLoading = true, loginError = null)
            authRepository.login(state.username.trim(), state.password).fold(
                onSuccess = { result ->
                    when (result) {
                        JwAppLoginResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                loggedIn = true,
                                loginLoading = false,
                                accountCid = null,
                                accountChoices = emptyList(),
                            )
                            loadMetadata()
                        }
                        is JwAppLoginResult.ChooseAccount -> {
                            _uiState.value = _uiState.value.copy(
                                loginLoading = false,
                                accountCid = result.cid,
                                accountChoices = result.accounts,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loginLoading = false,
                        loginError = error.message ?: "教务平台登录失败",
                    )
                },
            )
        }
    }

    fun chooseAccount(account: JwAppAccount) {
        val state = _uiState.value
        val accountId = account.id ?: return
        val cid = state.accountCid ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loginLoading = true, loginError = null)
            authRepository.chooseAccount(
                accountId = accountId,
                cid = cid,
                username = state.username.trim(),
                password = state.password,
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        loggedIn = true,
                        loginLoading = false,
                        accountCid = null,
                        accountChoices = emptyList(),
                    )
                    loadMetadata()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loginLoading = false,
                        loginError = error.message ?: "账号选择失败",
                    )
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearSession()
            resetPool()
            _uiState.value = CengKeUiState(activated = true, username = _uiState.value.username)
        }
    }

    // ── 元数据:校区 + 教室类型,默认选第一个校区并拉楼 ──────────
    private fun loadMetadata() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(metaLoading = true, error = null, loggedIn = true)
            try {
                val campuses = repository.getCampuses().getOrThrow().filter { it.enabled }
                val roomTypes = repository.getRoomTypes().getOrThrow().filter { it.enabled }
                val defaultCampus = campuses.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    metaLoading = false,
                    campuses = campuses,
                    roomTypes = roomTypes,
                    selectedCampusId = defaultCampus?.id,
                )
                defaultCampus?.let { loadBuildings(it.id) }
            } catch (error: Throwable) {
                handleError(error) { msg ->
                    _uiState.value = _uiState.value.copy(metaLoading = false, error = msg)
                }
            }
        }
    }

    private fun loadBuildings(campusId: Int) {
        val generation = ++buildingGeneration
        buildingJob?.cancel()
        buildingJob = viewModelScope.launch {
            repository.getBuildings(campusId).fold(
                onSuccess = { buildings ->
                    if (generation == buildingGeneration && _uiState.value.selectedCampusId == campusId) {
                        _uiState.value = _uiState.value.copy(buildings = buildings.filter { it.enabled })
                    }
                },
                onFailure = { error ->
                    if (generation != buildingGeneration || _uiState.value.selectedCampusId != campusId) return@fold
                    handleError(error) { msg -> _uiState.value = _uiState.value.copy(error = msg) }
                },
            )
        }
    }

    // ── 筛选(改这些会使候选池失效)──────────────────────────
    fun selectCampus(campusId: Int) {
        if (campusId == _uiState.value.selectedCampusId) return
        resetPool()
        _uiState.value = _uiState.value.copy(
            selectedCampusId = campusId,
            buildings = emptyList(),
            selectedBuildingIds = emptySet(),
            recommended = null,
            colleges = emptyList(),
            selectedColleges = emptySet(),
        )
        loadBuildings(campusId)
    }

    fun selectDate(date: LocalDate) {
        if (date.isBefore(DebugClock.todayDate()) || date == _uiState.value.selectedDate) return
        resetPool()
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            recommended = null,
            colleges = emptyList(),
            selectedColleges = emptySet(),
        )
    }

    fun toggleBuilding(buildingId: Int) {
        resetPool()
        val current = _uiState.value.selectedBuildingIds
        val updated = if (buildingId in current) current - buildingId else current + buildingId
        _uiState.value = _uiState.value.copy(
            selectedBuildingIds = updated,
            recommended = null,
            colleges = emptyList(),
            selectedColleges = emptySet(),
        )
    }

    fun toggleRoomType(roomTypeId: Int) {
        resetPool()
        val current = _uiState.value.selectedRoomTypeIds
        val updated = if (roomTypeId in current) current - roomTypeId else current + roomTypeId
        _uiState.value = _uiState.value.copy(
            selectedRoomTypeIds = updated,
            recommended = null,
            colleges = emptyList(),
            selectedColleges = emptySet(),
        )
    }

    // ── 客户端二次过滤(纯内存重筛,不重新请求)──────────────
    fun toggleTimeSlot(slot: TimeSlot) {
        val current = _uiState.value.selectedSlots
        val updated = if (slot in current) current - slot else current + slot
        _uiState.value = _uiState.value.copy(selectedSlots = updated)
        reshuffleFromPool()
    }

    fun toggleCollege(college: String) {
        val current = _uiState.value.selectedColleges
        val updated = if (college in current) current - college else current + college
        _uiState.value = _uiState.value.copy(selectedColleges = updated)
        reshuffleFromPool()
    }

    // ── 抽卡 + 换一个 ─────────────────────────────────────────
    /** 主按钮:池新鲜就直接抽,过滤条件变了就重新拉再抽。 */
    fun pickCourse() {
        val state = _uiState.value
        val campusId = state.selectedCampusId ?: run {
            _uiState.value = state.copy(error = "请先选择校区")
            return
        }
        val desiredKey = computePoolKey(state)
        if (poolKey == desiredKey && pool.isNotEmpty()) {
            // 池仍有效,直接重筛 + 换一个
            pickFromPool(reshuffleOnly = false)
            return
        }
        val generation = ++pickGeneration
        pickJob?.cancel()
        pickJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(picking = true, error = null, recommended = null)
            val buildingNames = state.buildings.associate { it.id to it.nameZh }
            repository.fetchLessons(
                date = state.selectedDate.format(DATE_FMT),
                campusId = campusId,
                buildingIds = state.selectedBuildingIds.toList(),
                roomTypeIds = state.selectedRoomTypeIds.toList(),
                buildingNames = buildingNames,
            ).fold(
                onSuccess = { courses ->
                    if (generation != pickGeneration) return@fold
                    pool = courses
                    poolKey = desiredKey
                    val colleges = CengKeParser.distinctColleges(courses)
                    // 拉新池后清掉不在新池里的旧学院选择
                    val validColleges = _uiState.value.selectedColleges.intersect(colleges.toSet())
                    _uiState.value = _uiState.value.copy(
                        picking = false,
                        colleges = colleges,
                        selectedColleges = validColleges,
                        poolSize = courses.size,
                    )
                    pickFromPool(reshuffleOnly = false)
                },
                onFailure = { error ->
                    if (generation != pickGeneration) return@fold
                    handleError(error) { msg ->
                        _uiState.value = _uiState.value.copy(picking = false, error = msg)
                    }
                },
            )
        }
    }

    /** "换一个":池已在手,从当前过滤结果里换一节(尽量不重复)。 */
    fun reshuffle() = pickFromPool(reshuffleOnly = true)

    private fun pickFromPool(reshuffleOnly: Boolean) {
        val state = _uiState.value
        val filtered = CengKeParser.filter(
            pool = pool,
            slots = state.selectedSlots,
            colleges = state.selectedColleges,
        )
        val next = CengKeParser.pickRandom(
            filtered = filtered,
            exclude = if (reshuffleOnly) state.recommended else null,
        )
        _uiState.value = _uiState.value.copy(
            recommended = next,
            filteredSize = filtered.size,
            noMatch = next == null && pool.isNotEmpty(),
        )
    }

    /** 学院/时段变化后,若已有池且已推荐,则就地重筛换卡。 */
    private fun reshuffleFromPool() {
        if (pool.isEmpty() || _uiState.value.recommended == null) {
            // 未抽卡前只更新可选池计数,不弹卡
            if (pool.isNotEmpty()) {
                val filtered = CengKeParser.filter(pool, _uiState.value.selectedSlots, _uiState.value.selectedColleges)
                _uiState.value = _uiState.value.copy(filteredSize = filtered.size)
            }
            return
        }
        pickFromPool(reshuffleOnly = false)
    }

    private fun resetPool() {
        pool = emptyList()
        poolKey = null
    }

    private fun computePoolKey(state: CengKeUiState): String = listOf(
        state.selectedCampusId,
        state.selectedDate.format(DATE_FMT),
        state.selectedBuildingIds.sorted().joinToString(","),
        state.selectedRoomTypeIds.sorted().joinToString(","),
    ).joinToString("|")

    private fun handleError(error: Throwable, update: (String) -> Unit) {
        if (error is JwAppAuthRequiredException) {
            viewModelScope.launch { authRepository.clearSession() }
            resetPool()
            _uiState.value = _uiState.value.copy(loggedIn = false, loginError = error.message)
        } else {
            update(error.message ?: "加载失败")
        }
    }

    companion object {
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

data class CengKeUiState(
    val activated: Boolean = false,
    // 登录
    val loggedIn: Boolean = false,
    val username: String = "",
    val password: String = "",
    val loginLoading: Boolean = false,
    val loginError: String? = null,
    val accountCid: String? = null,
    val accountChoices: List<JwAppAccount> = emptyList(),
    // 元数据
    val metaLoading: Boolean = false,
    val campuses: List<JwAppCampus> = emptyList(),
    val buildings: List<JwAppBuilding> = emptyList(),
    val roomTypes: List<JwAppRoomType> = emptyList(),
    // 筛选(选池维度)
    val selectedCampusId: Int? = null,
    val selectedDate: LocalDate = DebugClock.todayDate(),
    val selectedBuildingIds: Set<Int> = emptySet(),
    val selectedRoomTypeIds: Set<Int> = emptySet(),
    // 客户端二次过滤维度
    val selectedSlots: Set<TimeSlot> = emptySet(),
    val colleges: List<String> = emptyList(),
    val selectedColleges: Set<String> = emptySet(),
    // 抽卡
    val picking: Boolean = false,
    val recommended: CengCourse? = null,
    val poolSize: Int = 0,
    val filteredSize: Int = 0,
    val noMatch: Boolean = false,
    val error: String? = null,
) {
    val hasCampus: Boolean get() = selectedCampusId != null
}

