package com.ahu_plus.ui.screen.emptyclassroom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.local.DataRefreshPolicy
import com.ahu_plus.data.local.DataSnapshotStatus
import com.ahu_plus.data.local.EmptyClassroomPreset
import com.google.gson.reflect.TypeToken
import com.ahu_plus.data.model.AhuUnitTimes
import com.ahu_plus.data.model.BuildingInfo
import com.ahu_plus.data.model.CampusBuildingData
import com.ahu_plus.data.model.CampusInfo
import com.ahu_plus.data.repository.EmptyClassroomRepository
import com.ahu_plus.data.repository.FreeRoomResult
import com.ahu_plus.data.repository.JwAuthException
import com.ahu_plus.data.repository.JwAuthRepository
import com.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class EmptyClassroomViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val emptyClassroomRepository: EmptyClassroomRepository,
    private val sessionManager: SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmptyClassroomUiState())
    val uiState: StateFlow<EmptyClassroomUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance
    private var queryGeneration = 0L
    private var queryJob: Job? = null

    private fun beginQuery(): Long {
        queryJob?.cancel()
        return ++queryGeneration
    }

    private fun isCurrentQuery(
        requestId: Long,
        buildingId: String,
        campusId: String,
        date: LocalDate,
    ): Boolean {
        val state = _uiState.value
        return requestId == queryGeneration &&
            state.selectedBuildingId == buildingId &&
            state.selectedCampusId == campusId &&
            state.selectedDate == date
    }

    /**
     * 2026-06-27: buildingId → 该楼全量楼层集合缓存。
     * 楼层信息跟日期无关,但跟 buildingId 强相关,ViewModel 内存缓存够用。
     * 来源:`/student/ws/room/get-rooms` 返回该楼全部房间(不管今日是否空闲)。
     * 失败时不写入,fallback 到 free-list 推导的楼层(老行为)。
     */
    private val buildingRoomsFloors = mutableMapOf<String, List<Int>>()

    /**
     * 楼层 chip 数据源。优先级:缓存的全楼楼层 > 当前空闲房间所在楼层(fallback)。
     * ponytail: 单次排序去重,both 分支都做了,无须再 dedup。
     */
    private fun computeAllFloors(buildingId: String?, rooms: List<FreeRoomResult>): List<Int> {
        val cached = buildingId?.let { buildingRoomsFloors[it] }
        if (cached != null) return cached
        return rooms.mapNotNull { it.room.floor }.distinct().sorted()
    }

    init {
        _uiState.update { it.copy(presets = sessionManager?.getEmptyClassroomPresets().orEmpty()) }
        sessionManager?.getBuildingFloorsJson()?.let { raw ->
            runCatching {
                val type = object : TypeToken<Map<String, List<Int>>>() {}.type
                val cached: Map<String, List<Int>> = gson.fromJson(raw, type)
                buildingRoomsFloors.putAll(cached)
            }
        }
        val requestId = beginQuery()
        queryJob = viewModelScope.launch {
            val restored = restoreFromCache(requestId)
            if (!restored && requestId == queryGeneration) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUnit = currentUnitForToday()
                    )
                }
            }
        }
    }

    /**
     * 今天=按当前时间计算节次；其他日期=null(全天)。
     * 把这个分支集中在一处避免 ViewModel 多处重复。
     */
    private fun currentUnitForToday(date: LocalDate = DebugClock.todayDate()): Int? {
        return if (date == DebugClock.todayDate()) {
            AhuUnitTimes.getCurrentUnit(DebugClock.nowTime())
        } else null
    }

    fun selectCampus(campusId: String) {
        beginQuery()
        val buildings = CampusBuildingData.getBuildings(campusId)
        _uiState.update {
            it.copy(
                isLoading = false,
                selectedCampusId = campusId,
                selectedBuildingId = null,
                selectedFloor = null,
                buildings = buildings,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList(),
                error = null,
                needsLogin = false,
            )
        }
    }

    fun selectBuilding(buildingId: String) {
        val requestId = beginQuery()
        _uiState.update {
            it.copy(
                selectedBuildingId = buildingId,
                selectedFloor = null,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = computeAllFloors(buildingId, emptyList())
            )
        }
        val state = _uiState.value
        val campusId = state.selectedCampusId ?: return
        val date = state.selectedDate
        queryJob = viewModelScope.launch {
            loadData(
                isRefresh = true,
                buildingId = buildingId,
                campusId = campusId,
                date = date,
                requestId = requestId,
            )
        }
        // 2026-06-27: fire-and-forget 拉全楼房间,补全楼层 chip。
        // 不阻塞 loadData;失败时静默回退,fallback 已隐含在 computeAllFloors 优先级里。
        val floorsFresh = sessionManager?.let {
            !DataRefreshPolicy.isStale(
                it.getBuildingFloorsUpdatedAt(), 24L * 60 * 60 * 1000
            )
        } == true
        if (!floorsFresh || buildingId !in buildingRoomsFloors) {
            viewModelScope.launch { loadBuildingFloors(buildingId) }
        }
    }

    /**
     * 2026-06-27: 拉 buildingId 全部房间并缓存楼层,完成后重发 availableFloors。
     * ponytail: 单次 GET + 一次 state update,失败时不写入缓存、不重试。
     */
    private suspend fun loadBuildingFloors(buildingId: String) {
        val result = emptyClassroomRepository.getBuildingRooms(buildingId)
        result.fold(
            onSuccess = { rooms ->
                val floors = rooms.mapNotNull { it.floor }.distinct().sorted()
                if (floors.isNotEmpty()) {
                    buildingRoomsFloors[buildingId] = floors
                    sessionManager?.saveBuildingFloorsJson(gson.toJson(buildingRoomsFloors))
                    // 仅当用户仍停留在同一 buildingId 时刷新 chip(防止切走后覆盖)
                    if (_uiState.value.selectedBuildingId == buildingId) {
                        _uiState.update { it.copy(availableFloors = floors) }
                    }
                }
            },
            onFailure = { /* warn 已由 Repository 打,这里静默 */ }
        )
    }

    /**
     * 选择查询日期(今天/明天/+30 天内)。过去日期拒绝。
     * 选中后立即触发一次查询(若已选教学楼)。
     */
    fun selectDate(date: LocalDate) {
        if (date.isBefore(DebugClock.todayDate())) return
        if (date == _uiState.value.selectedDate) return
        val requestId = beginQuery()
        _uiState.update {
            it.copy(
                selectedDate = date,
                isSelectedDateToday = date == DebugClock.todayDate(),
                currentUnit = if (date == DebugClock.todayDate()) currentUnitForToday(date) else null,
                // 切换日期时清空旧结果,避免误用
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList(),
                error = null
            )
        }
        val state = _uiState.value
        val buildingId = state.selectedBuildingId
        val campusId = state.selectedCampusId
        if (buildingId != null && campusId != null) {
            queryJob = viewModelScope.launch {
                loadData(
                    isRefresh = false,
                    buildingId = buildingId,
                    campusId = campusId,
                    date = date,
                    requestId = requestId,
                )
            }
        }
    }

    fun selectFloor(floor: Int?) {
        _uiState.update { state ->
            val sortedAll = applyRoomSort(state.rooms, state.continuousFree)
            val filtered = if (floor == null) sortedAll
            else sortedAll.filter { it.room.floor == floor }
            state.copy(selectedFloor = floor, filteredRooms = filtered)
        }
    }

    /**
     * 2026-06-25: 切换"连续空闲"排序。
     *
     * 设计选择而非 bug:开启后,以"当前时间往后第一段连续空闲的节数"为主排序键。
     * 即从当前节起连续空闲,遇到第一节有课就截断 —— 后面的空闲段不计入。
     * 这是用户更关心的"现在进去最多能连坐几节",而非全天累计空闲数。
     *
     * 进入页面默认 false,需用户手动开启才会重排序。
     */
    fun setContinuousFree(value: Boolean) {
        _uiState.update { state ->
            state.copy(
                continuousFree = value,
                filteredRooms = applyRoomSort(state.rooms, value)
                    .let { all ->
                        val selectedFloor = state.selectedFloor
                        if (selectedFloor == null) all
                        else all.filter { it.room.floor == selectedFloor }
                    }
            )
        }
    }

    fun saveCurrentPreset() {
        val sm = sessionManager ?: return
        val state = _uiState.value
        val campusId = state.selectedCampusId ?: return
        val buildingId = state.selectedBuildingId ?: return
        val buildingName = state.buildings.firstOrNull { it.id == buildingId }?.nameZh ?: "教学楼"
        val dayOffset = ChronoUnit.DAYS.between(DebugClock.todayDate(), state.selectedDate)
            .toInt()
            .coerceIn(0, 30)
        val id = listOf(
            campusId,
            buildingId,
            state.selectedFloor?.toString().orEmpty(),
            dayOffset.toString(),
            state.continuousFree.toString(),
        ).joinToString("|")
        val dateLabel = when (dayOffset) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> "+${dayOffset}天"
        }
        val title = buildString {
            append(buildingName)
            state.selectedFloor?.let { append(" ${it}F") }
            append(" · ")
            append(dateLabel)
            if (state.continuousFree) append(" · 连续")
        }
        val preset = EmptyClassroomPreset(
            id = id,
            title = title,
            campusId = campusId,
            buildingId = buildingId,
            floor = state.selectedFloor,
            dayOffset = dayOffset,
            continuousFree = state.continuousFree,
        )
        val updated = (listOf(preset) + state.presets.filterNot { it.id == id }).take(6)
        _uiState.update { it.copy(presets = updated) }
        viewModelScope.launch { sm.saveEmptyClassroomPresets(updated) }
    }

    fun applyPreset(preset: EmptyClassroomPreset) {
        val date = DebugClock.todayDate().plusDays(preset.dayOffset.coerceIn(0, 30).toLong())
        selectCampus(preset.campusId)
        _uiState.update {
            it.copy(
                selectedDate = date,
                isSelectedDateToday = preset.dayOffset == 0,
                currentUnit = currentUnitForToday(date),
                continuousFree = preset.continuousFree,
            )
        }
        selectBuilding(preset.buildingId)
        selectFloor(preset.floor)
    }

    fun deletePreset(presetId: String) {
        val sm = sessionManager ?: return
        val updated = _uiState.value.presets.filterNot { it.id == presetId }
        _uiState.update { it.copy(presets = updated) }
        viewModelScope.launch { sm.saveEmptyClassroomPresets(updated) }
    }

    /**
     * 应用排序规则。
     * - total(默认): 按全天总空闲节数降序。
     * - continuous: 按"当前往后第一段连续空闲"降序(见 [continuousFreeCount]),次按总空闲数,再按名。
     */
    private fun applyRoomSort(rooms: List<FreeRoomResult>, continuous: Boolean): List<FreeRoomResult> {
        return if (continuous) {
            rooms.sortedWith(
                compareByDescending<FreeRoomResult> { continuousFreeCount(it) }
                    .thenByDescending { it.freeUnitsCount }
                    .thenBy { it.room.nameZh }
            )
        } else {
            rooms.sortedWith(
                compareByDescending<FreeRoomResult> { it.freeUnitsCount }
                    .thenBy { it.room.nameZh }
            )
        }
    }

    fun onRefresh() {
        val state = _uiState.value
        val buildingId = state.selectedBuildingId ?: return
        val campusId = state.selectedCampusId ?: return
        val date = state.selectedDate
        val requestId = beginQuery()
        queryJob = viewModelScope.launch {
            loadData(
                isRefresh = false,
                buildingId = buildingId,
                campusId = campusId,
                date = date,
                requestId = requestId,
            )
        }
    }

    private suspend fun restoreFromCache(requestId: Long): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getEmptyClassroomJson() ?: return false
        val key = sm.getEmptyClassroomKey() ?: return false
        val updatedAt = sm.getEmptyClassroomUpdatedAt()
        if (DebugClock.nowMillis() - updatedAt > 5 * 60 * 1000) return false

        // 缓存键格式: "<date>|<buildingId>|<campusId>"
        val parts = key.split("|")
        if (parts.size < 3) return false
        val cachedDate = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return false
        val buildingId = parts[1]
        val campusId = parts[2]

        return try {
            val rooms = withContext(Dispatchers.IO) {
                gson.fromJson(json, Array<FreeRoomResult>::class.java).toList()
            }
            if (requestId != queryGeneration) {
                true
            } else {
                val campus = CampusBuildingData.campuses.find { it.id == campusId }
                val floors = computeAllFloors(buildingId, rooms)
                val isToday = cachedDate == DebugClock.todayDate()
                _uiState.update { state ->
                    if (requestId != queryGeneration) return@update state
                    state.copy(
                        isLoading = false,
                        selectedCampusId = campusId,
                        selectedBuildingId = buildingId,
                        buildings = campus?.buildings ?: emptyList(),
                        rooms = rooms,
                        filteredRooms = applyRoomSort(rooms, _uiState.value.continuousFree),
                        availableFloors = floors,
                        selectedDate = cachedDate,
                        isSelectedDateToday = isToday,
                        currentUnit = if (isToday) currentUnitForToday(cachedDate) else null,
                        error = null,
                        needsLogin = false,
                        dataStatus = DataSnapshotStatus.cache(updatedAt),
                    )
                }
                true
            }
        } catch (_: Exception) { false }
    }

    private suspend fun loadData(
        isRefresh: Boolean,
        buildingId: String,
        campusId: String,
        date: LocalDate,
        requestId: Long,
    ) {
        if (!isCurrentQuery(requestId, buildingId, campusId, date)) return
        val isToday = date == DebugClock.todayDate()
        val currentUnit = currentUnitForToday(date)

        // 今天且当前节次为 null (= 当日课程已结束或尚未开始第一波)
        if (isToday && currentUnit == null) {
            _uiState.update { state ->
                if (isCurrentQuery(requestId, buildingId, campusId, date)) {
                    state.copy(isLoading = false, rooms = emptyList(), filteredRooms = emptyList())
                } else {
                    state
                }
            }
            return
        }

        val wasLoaded = _uiState.value.rooms.isNotEmpty()
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null, currentUnit = currentUnit) }
        } else {
            _uiState.update { it.copy(isRefreshing = true, currentUnit = currentUnit) }
        }

        try {
            val result = withContext(Dispatchers.IO) {
                jwAuthRepository.executeWithSessionRetry {
                    emptyClassroomRepository.getFreeRoomsWithDuration(
                        buildingId = buildingId,
                        campusId = campusId,
                        currentUnit = currentUnit,
                        date = date,
                    )
                }
            }
            result.fold(
                onSuccess = { rooms ->
                    if (!isCurrentQuery(requestId, buildingId, campusId, date)) return@fold
                    val sm = sessionManager
                    if (sm != null) {
                        try {
                            val cacheKey = "${date}|$buildingId|$campusId"
                            sm.saveEmptyClassroomJson(gson.toJson(rooms), cacheKey)
                        } catch (_: Exception) { Log.w(TAG, "Failed to cache empty classroom JSON") }
                    }
                    if (!isCurrentQuery(requestId, buildingId, campusId, date)) return@fold
                    val floors = computeAllFloors(buildingId, rooms)
                    val state = _uiState.value
                    val sortedAll = applyRoomSort(rooms, state.continuousFree)
                    val selectedFloor = state.selectedFloor
                    val filtered = if (selectedFloor != null)
                        sortedAll.filter { it.room.floor == selectedFloor } else sortedAll
                    _uiState.update { current ->
                        if (!isCurrentQuery(requestId, buildingId, campusId, date)) return@update current
                        current.copy(
                            isLoading = false,
                            rooms = rooms,
                            filteredRooms = filtered,
                            availableFloors = floors,
                            error = null,
                            needsLogin = false,
                            isRefreshing = false,
                            dataStatus = DataSnapshotStatus.network(),
                        )
                    }
                },
                onFailure = { e ->
                    // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次,
                    // 避免直接走到 needsLogin → onReauth 跳转登录的路径。
                    _uiState.update { state ->
                        if (!isCurrentQuery(requestId, buildingId, campusId, date)) return@update state
                        state.copy(
                            isLoading = false,
                            error = if (!wasLoaded) (e.message ?: "空教室查询失败") else state.error,
                            needsLogin = !wasLoaded &&
                                (e is SessionExpiredException || e is JwAuthException),
                            isRefreshing = false,
                            dataStatus = if (wasLoaded) {
                                state.dataStatus?.withFailedRefresh()
                            } else state.dataStatus,
                        )
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update { state ->
                if (!isCurrentQuery(requestId, buildingId, campusId, date)) return@update state
                state.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else state.error,
                    needsLogin = !wasLoaded &&
                        (e is SessionExpiredException || e is JwAuthException),
                    isRefreshing = false,
                    dataStatus = if (wasLoaded) {
                        state.dataStatus?.withFailedRefresh()
                    } else state.dataStatus,
                )
            }
        }
    }

    /**
     * 2026-06-23: SessionExpiredException 后,先尝试后台静默重连 + 重试一次,
     * 避免走 needsLogin → onReauth 的跳转路径。返回 null 表示重连失败(让原 onFailure 继续)。
     *
     * 重连策略:清掉 JW cookie 后调用 `jwAuthRepository.authenticate()`,
     * 它在没有 saved session 时会走 `trySimplifiedSso` 用 CAS 的 CASTGC 换新 SESSION;
     * CASTGC 也过期时会自动 fallback 到 `performFullLogin`(使用本地保存的账号密码)。
     */
    /** 应用房间查询结果到 UI state(重连+重试成功后调用)。 */
    private suspend fun handleRoomsResult(
        rooms: List<FreeRoomResult>,
        buildingId: String,
        campusId: String,
        date: LocalDate
    ) {
        val sm = sessionManager
        if (sm != null) {
            try {
                val cacheKey = "${date}|$buildingId|$campusId"
                sm.saveEmptyClassroomJson(gson.toJson(rooms), cacheKey)
            } catch (_: Exception) { Log.w(TAG, "Failed to cache empty classroom JSON") }
        }
        val floors = computeAllFloors(buildingId, rooms)
        val sortedAll = applyRoomSort(rooms, _uiState.value.continuousFree)
        val selectedFloor = _uiState.value.selectedFloor
        val filtered = if (selectedFloor != null) sortedAll.filter { it.room.floor == selectedFloor } else sortedAll
        _uiState.update {
            it.copy(
                isLoading = false,
                rooms = rooms,
                filteredRooms = filtered,
                availableFloors = floors,
                error = null,
                needsLogin = false,
                isRefreshing = false,
                dataStatus = DataSnapshotStatus.network(),
            )
        }
    }

    private companion object {
        private const val TAG = "EmptyClassroomVM"
    }
}

data class EmptyClassroomUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCampusId: String? = null,
    val selectedBuildingId: String? = null,
    val selectedFloor: Int? = null,
    val buildings: List<BuildingInfo> = emptyList(),
    val rooms: List<FreeRoomResult> = emptyList(),
    val filteredRooms: List<FreeRoomResult> = emptyList(),
    val availableFloors: List<Int> = emptyList(),
    val currentUnit: Int? = null,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val selectedDate: LocalDate = DebugClock.todayDate(),
    val isSelectedDateToday: Boolean = true,
    /**
     * 2026-06-25: 是否按"连续空闲"重排。
     * - 默认 false(每个 ViewModel 实例创建时都是 false)。
     * - 开启后,filteredRooms 按"当前往后第一段连续空闲节数"([continuousFreeCount])降序排,
     *   次按总空闲节数降序,再次按教室名升序。
     *
     * **设计而非 bug** — 用户可能更看重"现在进去能连坐多少节",而非"今日累计空闲节数"。
     * 这是产品设计选择,不是 bug,后续维护请勿当成"排序异常"修复。
     */
    val continuousFree: Boolean = false,
    val dataStatus: DataSnapshotStatus? = null,
    val presets: List<EmptyClassroomPreset> = emptyList(),
) {
    val availableCampuses: List<CampusInfo>
        get() = CampusBuildingData.campuses

    val hasBuildingSelected: Boolean
        get() = selectedCampusId != null && selectedBuildingId != null
}

/**
 * "当前往后第一段连续空闲"的节数(2026-06-25)。
 *
 * [FreeRoomResult.freeUnitNumbers] 已是「从查询起始节(今天=currentUnit,未来=1)起的空闲节次集合」,
 * 升序。此函数取其第一段连续区间的长度:
 *   - freeUnitNumbers = [5,6,8,9,11] → 第一段 5..6 → 返回 2
 *   - freeUnitNumbers = [5,6,7,8]    → 第一段 5..8 → 返回 4
 *   - freeUnitNumbers 为空            → 返回 0
 *
 * 语义:从现在进去,遇到第一节有课就截断,只算能连坐的节数。
 */
fun continuousFreeCount(result: FreeRoomResult): Int {
    val units = result.freeUnitNumbers
    if (units.isEmpty()) return 0
    val sorted = units.sorted()
    var count = 1
    for (i in 1 until sorted.size) {
        if (sorted[i] == sorted[i - 1] + 1) count++ else break
    }
    return count
}
