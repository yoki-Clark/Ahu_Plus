package com.ahu_plus.ui.screen.roomcoursetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.jw.CourseDisplayItem
import com.ahu_plus.data.model.jwapp.JwAppAccount
import com.ahu_plus.data.model.jwapp.JwAppBuilding
import com.ahu_plus.data.model.jwapp.JwAppCampus
import com.ahu_plus.data.model.jwapp.JwAppLesson
import com.ahu_plus.data.model.jwapp.JwAppRoom
import com.ahu_plus.data.model.jwapp.JwAppRoomType
import com.ahu_plus.data.model.jwapp.JwAppSemester
import com.ahu_plus.data.model.jwapp.RoomCourseTableData
import com.ahu_plus.data.model.jwapp.RoomSearchFilter
import com.ahu_plus.data.repository.JwAppAuthRepository
import com.ahu_plus.data.repository.JwAppAuthRequiredException
import com.ahu_plus.data.repository.JwAppLoginResult
import com.ahu_plus.data.repository.RoomCourseTableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class RoomCourseTableUiState(
    val activated: Boolean = false,
    val loggedIn: Boolean = false,
    val username: String = "",
    val password: String = "",
    val loginLoading: Boolean = false,
    val loginError: String? = null,
    val accountCid: String? = null,
    val accountChoices: List<JwAppAccount> = emptyList(),
    val initialLoading: Boolean = false,
    val error: String? = null,
    val semesters: List<JwAppSemester> = emptyList(),
    val currentSemesterId: Int? = null,
    val selectedSemester: JwAppSemester? = null,
    val campuses: List<JwAppCampus> = emptyList(),
    val buildings: List<JwAppBuilding> = emptyList(),
    val roomTypes: List<JwAppRoomType> = emptyList(),
    val filter: RoomSearchFilter = RoomSearchFilter(),
    val rooms: List<JwAppRoom> = emptyList(),
    val totalRooms: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val roomsLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val selectedRoom: JwAppRoom? = null,
    val scheduleLoading: Boolean = false,
    val scheduleError: String? = null,
    val schedule: RoomCourseTableData? = null,
    val selectedWeek: Int = 1,
    val selectedCourse: CourseDisplayItem? = null,
)

class RoomCourseTableViewModel(
    private val authRepository: JwAppAuthRepository,
    private val repository: RoomCourseTableRepository,
    sessionManager: SessionManager,
) : ViewModel() {
    private var buildingJob: Job? = null
    private var buildingRequestGeneration = 0L
    private val _uiState = MutableStateFlow(
        RoomCourseTableUiState(
            loggedIn = authRepository.isLoggedIn(),
            username = authRepository.savedUsername() ?: sessionManager.getUsername().orEmpty(),
            password = authRepository.savedPassword().orEmpty(),
        )
    )
    val uiState: StateFlow<RoomCourseTableUiState> = _uiState.asStateFlow()

    fun activate() {
        if (_uiState.value.activated) return
        _uiState.value = _uiState.value.copy(activated = true)
        if (authRepository.isLoggedIn()) loadInitial()
    }

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
                            loadInitial()
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
                    loadInitial()
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
            _uiState.value = RoomCourseTableUiState(
                activated = true,
                username = _uiState.value.username,
            )
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(initialLoading = true, error = null, loggedIn = true)
            try {
                val current = repository.getCurrentSemester().getOrThrow()
                val semesters = repository.getSemesters().getOrThrow()
                val campuses = repository.getCampuses().getOrThrow().filter { it.enabled }
                val roomTypes = repository.getRoomTypes().getOrThrow().filter { it.enabled }
                val allSemesters = (listOf(current) + semesters)
                    .distinctBy { it.id }
                    .sortedByDescending { it.startDate }
                _uiState.value = _uiState.value.copy(
                    initialLoading = false,
                    semesters = allSemesters,
                    currentSemesterId = current.id,
                    selectedSemester = current,
                    campuses = campuses,
                    roomTypes = roomTypes,
                    filter = _uiState.value.filter.copy(semesterId = current.id),
                )
                refreshRooms()
            } catch (error: Throwable) {
                handleError(error) { message ->
                    _uiState.value = _uiState.value.copy(initialLoading = false, error = message)
                }
            }
        }
    }

    fun onSearchChange(value: String) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(name = value))
    }

    fun submitSearch() = refreshRooms()

    fun setCampus(campusId: Int?) {
        val generation = ++buildingRequestGeneration
        buildingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            filter = _uiState.value.filter.copy(campusId = campusId, buildingId = null),
            buildings = emptyList(),
        )
        if (campusId != null) {
            buildingJob = viewModelScope.launch {
                repository.getBuildings(campusId).fold(
                    onSuccess = { buildings ->
                        if (generation == buildingRequestGeneration && _uiState.value.filter.campusId == campusId) {
                            _uiState.value = _uiState.value.copy(buildings = buildings.filter { it.enabled })
                        }
                    },
                    onFailure = { error ->
                        if (generation != buildingRequestGeneration || _uiState.value.filter.campusId != campusId) return@fold
                        handleError(error) { message ->
                            _uiState.value = _uiState.value.copy(error = message)
                        }
                    },
                )
            }
        }
    }

    fun setBuilding(buildingId: Int?) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(buildingId = buildingId))
    }

    fun setRoomType(roomTypeId: Int?) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(roomTypeId = roomTypeId))
    }

    fun setFloor(value: String) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(floor = value.toIntOrNull()))
    }

    fun setSeatsLower(value: String) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(seatsLower = value.toIntOrNull()))
    }

    fun setSeatsUpper(value: String) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(seatsUpper = value.toIntOrNull()))
    }

    fun setOccupiedOnly(value: Boolean) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(occupied = if (value) true else null))
    }

    fun setIncludeVirtual(value: Boolean) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(includeVirtual = value))
    }

    fun applyFilters() = refreshRooms()

    fun resetFilters() {
        val semesterId = _uiState.value.selectedSemester?.id
        _uiState.value = _uiState.value.copy(
            filter = RoomSearchFilter(semesterId = semesterId),
            buildings = emptyList(),
        )
        refreshRooms()
    }

    fun refreshRooms() {
        val state = _uiState.value
        if (!state.loggedIn || state.roomsLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(roomsLoading = true, error = null)
            repository.searchRooms(_uiState.value.filter, page = 1).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        roomsLoading = false,
                        rooms = page.data,
                        totalRooms = page.page.totalRows,
                        currentPage = page.page.currentPage,
                        totalPages = page.page.totalPages,
                    )
                },
                onFailure = { error ->
                    handleError(error) { message ->
                        _uiState.value = _uiState.value.copy(roomsLoading = false, error = message)
                    }
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || state.roomsLoading || state.currentPage >= state.totalPages) return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true)
            repository.searchRooms(state.filter, page = state.currentPage + 1).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        rooms = (state.rooms + page.data).distinctBy { it.id },
                        currentPage = page.page.currentPage,
                        totalPages = page.page.totalPages,
                        totalRooms = page.page.totalRows,
                    )
                },
                onFailure = { error ->
                    handleError(error) { message ->
                        _uiState.value = _uiState.value.copy(loadingMore = false, error = message)
                    }
                },
            )
        }
    }

    fun openRoom(room: JwAppRoom) {
        _uiState.value = _uiState.value.copy(selectedRoom = room, selectedCourse = null)
        loadSchedule(room, _uiState.value.selectedSemester ?: return)
    }

    fun selectSemester(semester: JwAppSemester) {
        val room = _uiState.value.selectedRoom ?: return
        _uiState.value = _uiState.value.copy(
            selectedSemester = semester,
            filter = _uiState.value.filter.copy(semesterId = semester.id),
        )
        loadSchedule(room, semester)
    }

    private fun loadSchedule(room: JwAppRoom, semester: JwAppSemester) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                scheduleLoading = true,
                scheduleError = null,
                schedule = null,
            )
            repository.getRoomSchedule(room.id, semester).fold(
                onSuccess = { schedule ->
                    val firstWeek = schedule.weekIndices.minOrNull() ?: 1
                    val lastWeek = schedule.weekIndices.maxOrNull() ?: firstWeek
                    val selectedWeek = if (semester.id == _uiState.value.currentSemesterId) {
                        schedule.currentWeek.coerceIn(firstWeek, lastWeek)
                    } else {
                        firstWeek
                    }
                    _uiState.value = _uiState.value.copy(
                        scheduleLoading = false,
                        schedule = schedule,
                        selectedRoom = schedule.room,
                        selectedWeek = selectedWeek,
                    )
                },
                onFailure = { error ->
                    handleError(error) { message ->
                        _uiState.value = _uiState.value.copy(
                            scheduleLoading = false,
                            scheduleError = message,
                        )
                    }
                },
            )
        }
    }

    fun setSelectedWeek(week: Int) {
        val weeks = _uiState.value.schedule?.weekIndices.orEmpty()
        if (week in weeks) _uiState.value = _uiState.value.copy(selectedWeek = week)
    }

    fun selectCourse(item: CourseDisplayItem) {
        _uiState.value = _uiState.value.copy(selectedCourse = item)
    }

    fun dismissCourse() {
        _uiState.value = _uiState.value.copy(selectedCourse = null)
    }

    fun selectedLesson(): JwAppLesson? {
        val id = _uiState.value.selectedCourse?.lessonId ?: return null
        return _uiState.value.schedule?.lessons?.firstOrNull { it.id == id }
    }

    fun backToRooms() {
        _uiState.value = _uiState.value.copy(
            selectedRoom = null,
            schedule = null,
            scheduleError = null,
            selectedCourse = null,
        )
    }

    private fun handleError(error: Throwable, update: (String) -> Unit) {
        if (error is JwAppAuthRequiredException) {
            viewModelScope.launch { authRepository.clearSession() }
            _uiState.value = _uiState.value.copy(loggedIn = false, loginError = error.message)
        } else {
            update(error.message ?: "加载失败")
        }
    }
}
