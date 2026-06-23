package com.yourname.ahu_plus.ui.screen.emptyclassroom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.AhuUnitTimes
import com.yourname.ahu_plus.data.model.BuildingInfo
import com.yourname.ahu_plus.data.model.CampusBuildingData
import com.yourname.ahu_plus.data.model.CampusInfo
import com.yourname.ahu_plus.data.repository.EmptyClassroomRepository
import com.yourname.ahu_plus.data.repository.FreeRoomResult
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class EmptyClassroomViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val emptyClassroomRepository: EmptyClassroomRepository,
    private val sessionManager: SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmptyClassroomUiState())
    val uiState: StateFlow<EmptyClassroomUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance

    init {
        viewModelScope.launch {
            val restored = restoreFromCache()
            if (!restored) {
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
    private fun currentUnitForToday(date: LocalDate = LocalDate.now()): Int? {
        return if (date == LocalDate.now()) {
            AhuUnitTimes.getCurrentUnit(LocalTime.now())
        } else null
    }

    fun selectCampus(campusId: String) {
        val buildings = CampusBuildingData.getBuildings(campusId)
        _uiState.update {
            it.copy(
                selectedCampusId = campusId,
                selectedBuildingId = null,
                selectedFloor = null,
                buildings = buildings,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList(),
                error = null
            )
        }
    }

    fun selectBuilding(buildingId: String) {
        _uiState.update {
            it.copy(
                selectedBuildingId = buildingId,
                selectedFloor = null,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList()
            )
        }
        viewModelScope.launch { loadData(isRefresh = false) }
    }

    /**
     * 选择查询日期(今天/明天/+30 天内)。过去日期拒绝。
     * 选中后立即触发一次查询(若已选教学楼)。
     */
    fun selectDate(date: LocalDate) {
        if (date.isBefore(LocalDate.now())) return
        if (date == _uiState.value.selectedDate) return
        _uiState.update {
            it.copy(
                selectedDate = date,
                isSelectedDateToday = date == LocalDate.now(),
                currentUnit = if (date == LocalDate.now()) currentUnitForToday(date) else null,
                // 切换日期时清空旧结果,避免误用
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList(),
                error = null
            )
        }
        if (_uiState.value.hasBuildingSelected) {
            viewModelScope.launch { loadData(isRefresh = false, date = date) }
        }
    }

    fun selectFloor(floor: Int?) {
        _uiState.update { state ->
            val filtered = if (floor == null) state.rooms
            else state.rooms.filter { it.room.floor == floor }
            state.copy(selectedFloor = floor, filteredRooms = filtered)
        }
    }

    fun onRefresh() {
        viewModelScope.launch { loadData(isRefresh = false) }
    }

    private suspend fun restoreFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getEmptyClassroomJson() ?: return false
        val key = sm.getEmptyClassroomKey() ?: return false
        val updatedAt = sm.getEmptyClassroomUpdatedAt()
        if (System.currentTimeMillis() - updatedAt > 5 * 60 * 1000) return false

        // 缓存键格式: "<date>|<buildingId>|<campusId>"
        val parts = key.split("|")
        if (parts.size < 3) return false
        val cachedDate = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return false
        val buildingId = parts[1]
        val campusId = parts[2]

        return try {
            withContext(Dispatchers.IO) {
                val rooms = gson.fromJson(json, Array<FreeRoomResult>::class.java).toList()
                val campus = CampusBuildingData.campuses.find { it.id == campusId }
                val floors = rooms.mapNotNull { it.room.floor }.distinct().sorted()
                val isToday = cachedDate == LocalDate.now()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedCampusId = campusId,
                        selectedBuildingId = buildingId,
                        buildings = campus?.buildings ?: emptyList(),
                        rooms = rooms,
                        filteredRooms = rooms,
                        availableFloors = floors,
                        selectedDate = cachedDate,
                        isSelectedDateToday = isToday,
                        currentUnit = if (isToday) currentUnitForToday(cachedDate) else null,
                        error = null,
                        needsLogin = false
                    )
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadData(isRefresh: Boolean, date: LocalDate = _uiState.value.selectedDate) {
        val buildingId = _uiState.value.selectedBuildingId ?: return
        val campusId = _uiState.value.selectedCampusId ?: return
        val isToday = date == LocalDate.now()
        val currentUnit = currentUnitForToday(date)

        // 今天且当前节次为 null (= 当日课程已结束或尚未开始第一波)
        if (isToday && currentUnit == null) {
            _uiState.update { it.copy(isLoading = false, rooms = emptyList(), filteredRooms = emptyList()) }
            return
        }

        val wasLoaded = _uiState.value.rooms.isNotEmpty()
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null, currentUnit = currentUnit) }
        } else {
            _uiState.update { it.copy(currentUnit = currentUnit) }
        }

        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败" else null
                        )
                    }
                    return@withContext
                }

                emptyClassroomRepository.getFreeRoomsWithDuration(
                    buildingId = buildingId,
                    campusId = campusId,
                    currentUnit = currentUnit,   // null = 全天(未来日期)
                    date = date
                ).fold(
                    onSuccess = { rooms ->
                        val sm = sessionManager
                        if (sm != null) {
                            try {
                                val cacheKey = "${date}|$buildingId|$campusId"
                                sm.saveEmptyClassroomJson(gson.toJson(rooms), cacheKey)
                            } catch (_: Exception) { Log.w(TAG, "Failed to cache empty classroom JSON") }
                        }
                        val floors = rooms.mapNotNull { it.room.floor }.distinct().sorted()
                        val selectedFloor = _uiState.value.selectedFloor
                        val filtered = if (selectedFloor != null)
                            rooms.filter { it.room.floor == selectedFloor } else rooms
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                rooms = rooms,
                                filteredRooms = filtered,
                                availableFloors = floors,
                                error = null,
                                needsLogin = false
                            )
                        }
                    },
                    onFailure = { e ->
                        // 2026-06-23: SessionExpiredException 时尝试后台静默重连 + 重试一次,
                        // 避免直接走到 needsLogin → onReauth 跳转登录的路径。
                        if (e is SessionExpiredException) {
                            val retryResult = retryAfterSilentReauth(buildingId, campusId, currentUnit, date)
                            if (retryResult != null) {
                                // 重连+重试成功,直接消费结果
                                handleRoomsResult(retryResult, buildingId, campusId, date)
                                return@fold
                            }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "空教室查询失败") else it.error,
                                needsLogin = !wasLoaded && e is SessionExpiredException
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    needsLogin = !wasLoaded && e is SessionExpiredException
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
    private suspend fun retryAfterSilentReauth(
        buildingId: String,
        campusId: String,
        currentUnit: Int?,
        date: LocalDate
    ): List<FreeRoomResult>? {
        return try {
            // 1. 清掉 JW 旧 cookie,强制 authenticate() 走 SSO(否则它会用 stale session 直接返回 success)
            jwAuthRepository.clearCookies()
            val authOk = jwAuthRepository.authenticate().isSuccess
            if (!authOk) {
                Log.w(TAG, "retryAfterSilentReauth: 静默重连失败,放弃重试")
                return null
            }
            // 2. 重试一次查询
            val retry = emptyClassroomRepository.getFreeRoomsWithDuration(
                buildingId = buildingId,
                campusId = campusId,
                currentUnit = currentUnit,
                date = date
            )
            retry.getOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "retryAfterSilentReauth 异常: ${e.message}")
            null
        }
    }

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
        val floors = rooms.mapNotNull { it.room.floor }.distinct().sorted()
        val selectedFloor = _uiState.value.selectedFloor
        val filtered = if (selectedFloor != null) rooms.filter { it.room.floor == selectedFloor } else rooms
        _uiState.update {
            it.copy(
                isLoading = false,
                rooms = rooms,
                filteredRooms = filtered,
                availableFloors = floors,
                error = null,
                needsLogin = false
            )
        }
    }

    private companion object {
        private const val TAG = "EmptyClassroomVM"
    }
}

data class EmptyClassroomUiState(
    val isLoading: Boolean = false,
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
    val selectedDate: LocalDate = LocalDate.now(),
    val isSelectedDateToday: Boolean = true
) {
    val availableCampuses: List<CampusInfo>
        get() = CampusBuildingData.campuses

    val hasBuildingSelected: Boolean
        get() = selectedCampusId != null && selectedBuildingId != null
}
