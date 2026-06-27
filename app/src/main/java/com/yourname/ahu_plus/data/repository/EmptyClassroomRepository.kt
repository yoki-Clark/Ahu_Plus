package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.AhuUnitTimes
import com.yourname.ahu_plus.data.model.jw.FreeRoom
import com.yourname.ahu_plus.data.model.jw.FreeRoomRequest
import com.yourname.ahu_plus.data.model.jw.FreeRoomResponse
import com.yourname.ahu_plus.data.model.jw.DateTimeSegmentCmd
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

/**
 * 空教室查询仓库。
 *
 * 端点：`POST /student/ws/room-borrow/free-list`
 * 鉴权：复用 [JwAuthRepository.jwCookieJar] 的 JW SESSION cookie。
 *
 * 核心算法 —— 逐节并行 + 段折叠：
 * 1. 确定起始节次 (今天=currentUnit，未来日期=null → 全天 1-13)
 * 2. 并行发起 N 次 API 调用，每次仅查询单个节次 u
 * 3. 每间教室在哪些节次空闲 = 它在哪些调用中出现
 * 4. 用 [AhuUnitTimes.collapseToSegments] 把分散的空闲节次折叠成连续段
 * 5. 结果按空闲节次总数降序、教室名升序排列
 *
 * 设计动机：用户期望「当前节之后任何空闲段都计入」，
 * 例如 5-6 空闲、7 占用、8-9 空闲、10 占用、11 空闲，应显示「空闲 5 节 (3 段)」。
 */
class EmptyClassroomRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val gson = GsonProvider.instance
    private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        trustAll = true,  // jw.ahu.edu.cn 自签名证书
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    /**
     * 查询在指定 [units] 全部空闲的教室。
     */
    suspend fun getFreeRooms(
        buildingId: String,
        campusId: String,
        units: List<Int>,
        date: LocalDate = LocalDate.now()
    ): Result<List<FreeRoom>> = withContext(Dispatchers.IO) {
        try {
            val dateStr = date.toString()
            val requestBody = FreeRoomRequest(
                buildingId = buildingId,
                campusId = campusId,
                dateTimeSegmentCmd = DateTimeSegmentCmd(
                    startDateTime = dateStr,
                    endDateTime = dateStr,
                    units = units.map { it.toString() }
                )
            )
            val jsonBody = gson.toJson(requestBody)
            Log.d(TAG, "free-list request: campus=$campusId building=$buildingId units=$units")

            val request = Request.Builder()
                .url(FREE_LIST_URL)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$JW_BASE/student/for-std/room-borrow")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 302) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("空教室查询失败: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    throw Exception("空教室查询返回空响应")
                }
                if (body[0] != '{') {
                    throw Exception("空教室查询返回非 JSON: ${body.take(200)}")
                }
                val parsed = gson.fromJson(body, FreeRoomResponse::class.java)
                val rooms = parsed.roomList
                Log.i(TAG, "free-list: ${rooms.size} rooms (campus=$campusId building=$buildingId units=$units)")
                Result.success(rooms)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFreeRooms 失败 (campus=$campusId building=$buildingId units=$units)", e)
            Result.failure(e)
        }
    }

    /**
     * 逐节并行查询 + 多段空闲统计。
     *
     * 对 [currentUnit] (若非 null) 起的所有节次，每节单独调用一次 API，
     * 汇总每间教室的「全部空闲节次」集合，再折叠为连续段。
     *
     * @param currentUnit 今天查询的起始节次 (从当前时刻推算)；传 null 表示全天 1-13 节 (用于未来日期)。
     * @param date 查询日期 (默认今天)
     * @return 按空闲节次总数降序、教室名升序排列的 [FreeRoomResult] 列表
     */
    suspend fun getFreeRoomsWithDuration(
        buildingId: String,
        campusId: String,
        currentUnit: Int?,
        date: LocalDate = LocalDate.now()
    ): Result<List<FreeRoomResult>> = withContext(Dispatchers.IO) {
        try {
            // 起始节次: currentUnit 非空则从它起；为空 (未来日期) 则全天 1..13
            val startUnit = currentUnit ?: 1
            val periodList = AhuUnitTimes.getRemainingUnits(startUnit)
            if (periodList.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 并行发起 N 个单节次调用，semaphore 限流避免服务端限流
            val semaphore = Semaphore(3)
            val perPeriod = coroutineScope {
                periodList.map { unit ->
                    async {
                        semaphore.withPermit {
                            unit to getFreeRooms(buildingId, campusId, listOf(unit), date)
                        }
                    }
                }.awaitAll()
            }

            // roomId -> 已确认空闲的节次集合 (可能跨多个非连续段)
            val roomToFreeUnits = mutableMapOf<Int, MutableSet<Int>>()
            // roomId -> 去重后的 FreeRoom 对象
            val roomMap = mutableMapOf<Int, FreeRoom>()

            for ((unit, result) in perPeriod) {
                result.getOrNull()?.forEach { room ->
                    roomMap.putIfAbsent(room.id, room)
                    roomToFreeUnits.getOrPut(room.id) { mutableSetOf() }.add(unit)
                }
            }

            val results = roomMap.values.map { room ->
                val freeUnits = roomToFreeUnits[room.id]?.sorted() ?: emptyList()
                FreeRoomResult(
                    room = room,
                    freeUnitsCount = freeUnits.size,
                    freeUnitNumbers = freeUnits,
                    freeSegments = AhuUnitTimes.collapseToSegments(freeUnits),
                    freeTimeRange = AhuUnitTimes.formatSegmentedRange(freeUnits)
                )
            }.sortedWith(
                compareByDescending<FreeRoomResult> { it.freeUnitsCount }
                    .thenBy { it.room.nameZh }
            )

            Log.i(TAG, "getFreeRoomsWithDuration: ${results.size} rooms (building=$buildingId, " +
                "currentUnit=$currentUnit, date=$date, periods=${periodList.size})")
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "getFreeRoomsWithDuration 失败", e)
            Result.failure(e)
        }
    }

    /**
     * 拉取指定教学楼的全部房间列表(2026-06-27:用于楼层 chip 全量展示)。
     *
     * 与 free-list 的区别:这里返的是该楼所有 enabled 房间(含 1-13 节全部占用 / 实验 / 行政用),
     * 不带时间过滤。楼层 chip 应当展示全部楼层,而不是只看空闲房间所在楼层。
     *
     * 失败返回 `Result.failure`,调用方应静默回退到 free-list 推导(老行为)。
     */
    suspend fun getBuildingRooms(buildingId: String): Result<List<FreeRoom>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GET_ROOMS_URL?buildingId=$buildingId&hasDataPermission=false&hasUsableDepartPermission=false")
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$JW_BASE/student/for-std/room-borrow")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 302) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("get-rooms 失败: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    throw Exception("get-rooms 返回空响应")
                }
                // 顶层是 JSON 数组(不是包装对象)
                val rooms = gson.fromJson(body, Array<FreeRoom>::class.java).toList()
                Log.i(TAG, "get-rooms: ${rooms.size} rooms (building=$buildingId)")
                Result.success(rooms)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getBuildingRooms 失败 (building=$buildingId): ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "EmptyClassroomRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val FREE_LIST_URL = "$JW_BASE/student/ws/room-borrow/free-list"
        // 2026-06-27: 用于拉取该楼全部房间(含实验/非实验),让楼层 chip 显示全量。
        private const val GET_ROOMS_URL = "$JW_BASE/student/ws/room/get-rooms"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}

/**
 * 单间教室的空闲查询结果（UI 层使用）。
 *
 * - [freeUnitNumbers] 是全部空闲节次的有序集合（语义：当前节之后任意空闲段都计入）
 * - [freeSegments] 是 [freeUnitNumbers] 折叠后的连续区间列表，供 [com.yourname.ahu_plus.ui.screen.emptyclassroom.FreeTimeBar] 多段渲染使用
 * - [freeTimeRange] 是格式化好的展示文本；单段附时间范围，多段为「第 X-Y 节, ...」逗号分隔
 */
data class FreeRoomResult(
    val room: FreeRoom,
    val freeUnitsCount: Int,
    val freeUnitNumbers: List<Int>,
    val freeSegments: List<IntRange> = emptyList(),
    val freeTimeRange: String
)
