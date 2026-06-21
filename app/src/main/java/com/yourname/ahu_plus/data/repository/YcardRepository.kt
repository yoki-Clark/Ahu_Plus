package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.BathroomBalanceData
import com.yourname.ahu_plus.data.model.BathroomBalanceResponse
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.BillResponse
import com.yourname.ahu_plus.data.model.ElectricityBalanceResponse
import com.yourname.ahu_plus.data.model.ElectricityBillResponse
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.data.model.ElectricityUiData
import com.yourname.ahu_plus.data.model.FeeItemOption
import com.yourname.ahu_plus.data.model.FeeItemSelectResponse
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBalanceResponse
import com.yourname.ahu_plus.data.model.InternetBillResponse
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import com.yourname.ahu_plus.util.DES
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * 一卡通账单仓库 (ycard.ahu.edu.cn)。
 *
 * 认证链路:
 *   1. 优先复用 CasAuthRepository 已有的 CASTGC(简易 SSO),大幅缩短登录耗时
 *   2. 若 CASTGC 缺失或已过期,回退到完整 CAS 登录(DES 加密 + 6 步流程)
 *   3. 跟随重定向 → 最终 URL 含 synjones-auth JWT
 *   4. 用 JWT + SESSION cookie 调账单 API
 */
class YcardRepository(
    private val casAuthRepository: CasAuthRepository? = null
) {
    companion object {
        private const val TAG = "YcardRepo"
        private const val YCARD_BASE = "https://ycard.ahu.edu.cn"
        private val YCARD_CAS_ENTRY = "$YCARD_BASE/berserker-auth/cas/login/neusoftCas" +
            "?targetUrl=https%3A%2F%2Fycard.ahu.edu.cn%2Fberserker-base%2Fredirect%3FappId%3D16%26type%3Dapp"
        private const val CAS_BASE = "https://one.ahu.edu.cn/cas"
        private val SERVICE = YCARD_CAS_ENTRY
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
        private const val CAS_HOST = "one.ahu.edu.cn"
        private const val YCARD_HOST = "ycard.ahu.edu.cn"
    }

    private val gson = GsonProvider.instance

    // ── Cookie 存储 ──────────────────────────────────
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    // ── OkHttp 客户端 ──────────────────────────────────
    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = cookieJar,
        followRedirects = false,
        disableGzip = true,
        connectTimeoutSec = 30,
        readTimeoutSec = 30
    )

    // 第二个客户端用于跟随重定向提取 JWT
    private val redirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .build()

    // 缓存的 JWT
    @Volatile
    private var cachedJwt: String? = null

    /** 清除内存 cookie 和 JWT(退出登录时调用) */
    fun clearCookies() {
        cookieStore.clear()
        cachedJwt = null
    }

    // ══════════════════════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════════════════════

    /**
     * 登录 ycard。
     * 优先复用 CasAuthRepository 的 CASTGC,失败时回退到完整 CAS 登录。
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "开始 ycard 认证...")
            cookieStore.clear()
            cachedJwt = null

            // 策略 1: 复用 CASTGC 走简易 SSO
            val castgc = casAuthRepository?.cookieStore?.get(CAS_HOST)
                ?.find { it.name == "CASTGC" }?.value
            if (castgc != null) {
                try {
                    Log.i(TAG, "尝试复用 CAS 登录态走简易 SSO")
                    loginWithCastgc(castgc)
                    Log.d(TAG, "ycard 简易 SSO 成功")
                    return Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "简易 SSO 失败，回退到完整登录: ${e.message}")
                    cookieStore.clear()
                    cachedJwt = null
                }
            }

            // 策略 2: 完整 CAS 登录(走 6 步流程)
            performFullLogin(username, password)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "ycard 认证失败", e)
            Result.failure(e)
        }
    }

    /**
     * 查询一卡通账单。
     */
    suspend fun getBills(current: Int = 1, size: Int = 20): Result<BillResponse> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val url = "$YCARD_BASE/berserker-search/search/personal/turnover" +
                "?size=$size&current=$current&synAccessSource=h5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("synAccessSource", "h5")
                .header("Accept", "*/*")
                .header("Referer", "$YCARD_BASE/campus-card/billing/list?appId=16&type=app")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "账单 API HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("账单查询失败 HTTP $code"))
                }

                val billResp = gson.fromJson(body, BillResponse::class.java)
                Result.success(billResp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "账单查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询全量一卡通账单（遍历所有分页）。
     *
     * @param pageSize 每页条数，默认 50
     * @return 合并后的全部账单记录列表
     */
    suspend fun getAllBills(pageSize: Int = 50): Result<List<BillRecord>> {
        return try {
            // 第一页
            val firstPage = getBills(current = 1, size = pageSize).getOrThrow()
            val data = firstPage.data ?: return Result.success(emptyList<BillRecord>())
            val allRecords = data.records.toMutableList()
            val totalPages = data.pages

            // 遍历剩余页
            for (page in 2..totalPages) {
                Log.d(TAG, "账单分页: $page/$totalPages")
                val pageResult = getBills(current = page, size = pageSize).getOrThrow()
                pageResult.data?.records?.let { allRecords.addAll(it) }
            }

            Log.i(TAG, "账单全量加载完成: ${allRecords.size} 条, $totalPages 页")
            Result.success(allRecords)
        } catch (e: Exception) {
            Log.e(TAG, "全量账单查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询浴室余额 (ycard.ahu.edu.cn/charge/feeitem/getThirdData)。
     *
     * 复用 ycard JWT 认证,通过手机号查询对应的浴室水控账户余额。
     *
     * @param phone 绑定的手机号(11 位)
     * @return [BathroomBalanceData] 包含现金余额/赠送余额/项目名称等字段
     */
    suspend fun getBathroomBalance(phone: String): Result<BathroomBalanceData> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val formBody = FormBody.Builder()
                .add("feeitemid", "430")
                .add("type", "IEC")
                .add("level", "1")
                .add("telPhone", phone)
                .build()

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/feeitem/getThirdData")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", YCARD_BASE)
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "浴室余额 API HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("浴室余额查询失败 HTTP $code"))
                }

                val resp = gson.fromJson(body, BathroomBalanceResponse::class.java)
                val data = resp.map?.data
                    ?: return Result.failure(Exception("浴室余额数据为空: ${body.take(200)}"))

                if (resp.code != 200) {
                    return Result.failure(Exception(resp.msg.ifBlank { "浴室余额查询失败" }))
                }

                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "浴室余额查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询电费余额 (空调/照明等),与浴室共用同一端点但参数不同。
     *
     * @param feeitemid 费用项 ID (408=空调, 428=照明, 488=新区)
     * @param building  楼栋 (格式 "code&name",如 "57&榴园二号楼空调")
     * @param floor     楼层 (格式 "code&name",如 "228&五层")
     * @param room      房间 (格式 "code&name",如 "2514&2514")
     * @param level     层级,老区 "3",新区 "4"
     * @param campus    校区 (格式 "code&name",如 "ul001002002&磬苑校区"),新区必填
     */
    suspend fun getElectricityBalance(
        feeitemid: String,
        building: String,
        floor: String,
        room: String,
        level: String = "3",
        campus: String? = null
    ): Result<ElectricityUiData> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val formBuilder = FormBody.Builder()
                .add("feeitemid", feeitemid)
                .add("type", "IEC")
                .add("level", level)
                .add("building", building)
                .add("floor", floor)
                .add("room", room)
            if (!campus.isNullOrBlank()) formBuilder.add("campus", campus)
            val formBody = formBuilder.build()

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/feeitem/getThirdData")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", YCARD_BASE)
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .post(formBody)
                .build()

            Log.d(TAG, "电费 API 请求: feeitemid=$feeitemid building=$building floor=$floor room=$room level=$level")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "电费 API feeitemid=$feeitemid HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("电费查询失败 HTTP $code"))
                }

                val resp = gson.fromJson(body, ElectricityBalanceResponse::class.java)
                val map = resp.map
                    ?: return Result.failure(Exception("电费数据为空: ${body.take(200)}"))

                if (resp.code != 200) {
                    return Result.failure(Exception(resp.msg.ifBlank { "电费查询失败" }))
                }

                val uiData = map.toUiData()
                Result.success(uiData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "电费查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询网费余额 (feeitemid=431, level=0,无需额外参数,账号从 JWT 中自动识别)。
     */
    suspend fun getInternetBalance(): Result<InternetBalanceData> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val formBody = FormBody.Builder()
                .add("feeitemid", "431")
                .add("type", "IEC")
                .add("level", "0")
                .build()

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/feeitem/getThirdData")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", YCARD_BASE)
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "网费余额 API HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("网费查询失败 HTTP $code"))
                }

                val resp = gson.fromJson(body, InternetBalanceResponse::class.java)
                val data = resp.map?.data
                    ?: return Result.failure(Exception("网费数据为空: ${body.take(200)}"))

                if (resp.code != 200) {
                    return Result.failure(Exception(resp.msg.ifBlank { "网费查询失败" }))
                }

                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "网费查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询网费充值账单。
     *
     * @param page 页码(从 0 开始)
     * @param row  每页条数,默认 10
     */
    suspend fun getInternetBills(page: Int = 0, row: Int = 20): Result<InternetBillResponse> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/turnover/app_account?feeitemid=431&app_row=$row&app_page=$page")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "网费账单 API HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("网费账单查询失败 HTTP $code"))
                }

                val billResp = gson.fromJson(body, InternetBillResponse::class.java)
                Result.success(billResp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "网费账单查询错误", e)
            Result.failure(e)
        }
    }

    /**
     * 查询电费日用明细 (空调/照明)。
     *
     * @param feeitemid 费用项 ID (408=空调, 428=照明, 488=新区)
     * @param building  楼栋 (code&name 格式)
     * @param floor     楼层 (code&name 格式)
     * @param room      房间 (code&name 格式)
     * @param startDate 开始日期 (yyyy-MM-dd)
     * @param endDate   结束日期 (yyyy-MM-dd)
     * @param campus    校区 (code&name 格式),新区必填
     */
    suspend fun getElectricityBills(
        feeitemid: String,
        building: String,
        floor: String,
        room: String,
        startDate: String,
        endDate: String,
        campus: String? = null
    ): Result<List<ElectricityDailyRecord>> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            // URL 编码参数中的 & 符号
            val encodedBuilding = java.net.URLEncoder.encode(building, "UTF-8")
            val encodedFloor = java.net.URLEncoder.encode(floor, "UTF-8")
            val encodedRoom = java.net.URLEncoder.encode(room, "UTF-8")

            val campusParam = if (!campus.isNullOrBlank()) {
                "&campus=${java.net.URLEncoder.encode(campus, "UTF-8")}"
            } else ""

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/feeitem/getRechargeRecord" +
                    "?feeitemid=$feeitemid&startdate=$startDate&enddate=$endDate" +
                    campusParam +
                    "&building=$encodedBuilding&floor=$encodedFloor&room=$encodedRoom")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()

            Log.d(TAG, "电费账单请求: feeitemid=$feeitemid building=$building floor=$floor room=$room $startDate~$endDate")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "电费账单 API feeitemid=$feeitemid HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("电费账单查询失败 HTTP $code"))
                }

                val billResp = gson.fromJson(body, ElectricityBillResponse::class.java)
                Result.success(billResp.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "电费账单查询错误", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════
    // 电费房间级联元数据 (楼栋 → 楼层 → 房间)
    // 用于替代 HomeViewModel 里硬编码的码表,支持全部园区。
    // ══════════════════════════════════════════════════════

    /**
     * 拉取指定 feeitemid 下的校区列表 (仅 feeitemid=488 新区使用,level=0 返回校区)。
     *
     * 老区 feeitemid (408/428) 没有校区概念,此方法调用会得到空 data。
     */
    suspend fun getFeeItemCampuses(feeitemid: String): Result<List<FeeItemOption>> {
        return queryFeeItemSelect(
            feeitemid = feeitemid,
            level = "0",
            campus = null,
            building = null,
            floor = null,
            filterSuffix = null
        )
    }

    /**
     * 拉取指定 feeitemid 下的楼栋列表。
     *
     * 注意:feeitemid=408(空调) 的响应会混入"照明"后缀的楼栋
     * (电表合一或 feeitemid 错配),这里按 name 后缀过滤,只保留与 feeitemid 对应的项:
     * - 408 → name.endsWith("空调")
     * - 428 → name.endsWith("照明")
     * - 488 → 不过滤(空调和照明合并在同一 feeitemid)
     *
     * @param campus 可选,新区 feeitemid=488 时必填,老区 408/428 不传。
     */
    suspend fun getFeeItemBuildings(
        feeitemid: String,
        campus: String? = null
    ): Result<List<FeeItemOption>> {
        return queryFeeItemSelect(
            feeitemid = feeitemid,
            level = if (campus.isNullOrBlank()) "0" else "1",
            campus = campus,
            building = null,
            floor = null,
            filterSuffix = feeitemSuffix(feeitemid)
        )
    }

    /**
     * 拉取指定楼栋的楼层列表。
     */
    suspend fun getFeeItemFloors(
        feeitemid: String,
        campus: String?,
        building: String
    ): Result<List<FeeItemOption>> {
        return queryFeeItemSelect(
            feeitemid = feeitemid,
            level = if (campus.isNullOrBlank()) "1" else "2",
            campus = campus,
            building = building,
            floor = null,
            filterSuffix = null
        )
    }

    /**
     * 拉取指定楼栋+楼层的房间列表。
     */
    suspend fun getFeeItemRooms(
        feeitemid: String,
        campus: String?,
        building: String,
        floor: String
    ): Result<List<FeeItemOption>> {
        return queryFeeItemSelect(
            feeitemid = feeitemid,
            level = if (campus.isNullOrBlank()) "2" else "3",
            campus = campus,
            building = building,
            floor = floor,
            filterSuffix = null
        )
    }

    private fun feeitemSuffix(feeitemid: String): String = when (feeitemid) {
        "408" -> "空调"
        "428" -> "照明"
        "488" -> ""  // 新区 feeitemid=488 把空调+照明合并,不过滤
        else -> ""
    }

    /**
     * 通用 select 接口调用,返回 feeitemid/level 对应的元数据列表。
     *
     * @param campus  可选,新区 feeitemid=488 时必填,老区不传。
     * @param building 可选,level>=1 时必填。
     * @param floor    可选,level>=2 时必填。
     * @param filterSuffix 可选,只保留 name.endsWith(filterSuffix) 的项
     *                    (用于楼栋列表过滤 feeitemid=408 混入的"照明"楼栋)
     */
    private suspend fun queryFeeItemSelect(
        feeitemid: String,
        level: String,
        campus: String?,
        building: String?,
        floor: String?,
        filterSuffix: String?
    ): Result<List<FeeItemOption>> {
        return try {
            val jwt = cachedJwt ?: return Result.failure(Exception("未登录 ycard"))

            val formBuilder = FormBody.Builder()
                .add("feeitemid", feeitemid)
                .add("type", "select")
                .add("level", level)
            if (!campus.isNullOrBlank()) formBuilder.add("campus", campus)
            if (!building.isNullOrBlank()) formBuilder.add("building", building)
            if (!floor.isNullOrBlank()) formBuilder.add("floor", floor)

            val request = Request.Builder()
                .url("$YCARD_BASE/charge/feeitem/getThirdData")
                .header("User-Agent", UA)
                .header("synjones-auth", "bearer $jwt")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", YCARD_BASE)
                .header("Referer", "$YCARD_BASE/charge-app/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val code = response.code
                Log.d(TAG, "feeitem select feeitemid=$feeitemid level=$level HTTP $code")

                if (code != 200) {
                    return Result.failure(Exception("元数据查询失败 HTTP $code"))
                }

                val resp = gson.fromJson(body, FeeItemSelectResponse::class.java)
                if (resp.code != 200) {
                    return Result.failure(Exception(resp.msg.ifBlank { "元数据查询失败" }))
                }

                val raw = resp.map?.data ?: emptyList()
                val filtered = if (!filterSuffix.isNullOrBlank()) {
                    raw.filter { it.name.endsWith(filterSuffix) }
                } else {
                    raw
                }
                Result.success(filtered)
            }
        } catch (e: Exception) {
            Log.e(TAG, "feeitem select 查询错误", e)
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════
    // 策略 1: 复用 CASTGC 简易 SSO
    // ══════════════════════════════════════════════════════

    /** 复用 CAS cookie,直接请求 ycard CAS 入口,服务器识别 CASTGC 后会签发 ycard JWT */
    private suspend fun loginWithCastgc(castgc: String) {
        // Step 1: GET ycard CAS 入口(带 CASTGC)
        val r1 = client.newCall(Request.Builder()
            .url(YCARD_CAS_ENTRY)
            .header("User-Agent", UA)
            .header("Cookie", "CASTGC=$castgc")
            .build()).execute().use { response ->
            if (response.code == 200) {
                // 没有重定向,可能 CASTGC 已被消费或服务器特殊处理
                throw Exception("CASTGC 未触发重定向")
            }
            response.header("Location") ?: throw Exception("ycard 未重定向")
        }

        // Step 2: 跟随重定向链(进入 ycard 主页,激活 session)
        val finalUrl = redirectClient.newCall(Request.Builder()
            .url(r1)
            .header("User-Agent", UA)
            .build()).execute().use { response ->
            response.request.url.toString()
        }

        // Step 3: 从 URL 中提取 JWT
        val jwt = Regex("""synjones-auth=([^&"]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: throw Exception("未获取到 synjones-auth JWT (final URL: ${finalUrl.take(200)})")

        cachedJwt = jwt
        Log.i(TAG, "ycard 简易 SSO 成功")
    }

    // ══════════════════════════════════════════════════════
    // 策略 2: 完整 CAS 登录回退
    // ══════════════════════════════════════════════════════

    private suspend fun performFullLogin(username: String, password: String) {
        // Step 1: GET ycard CAS 入口 → 302 → CAS login URL
        val casUrl = client.newCall(Request.Builder().url(YCARD_CAS_ENTRY)
            .header("User-Agent", UA).build()).execute().use { response ->
            response.header("Location")
                ?: throw Exception("ycard 未重定向到 CAS")
        }
        Log.d(TAG, "ycard CAS URL resolved")

        // Step 2: GET CAS login page → lt + execution
        val (lt, execution) = client.newCall(Request.Builder().url(casUrl)
            .header("User-Agent", UA).build()).execute().use { response ->
            val html = response.body?.string() ?: ""
            val lt = Regex("""name="lt"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw Exception("未找到 lt")
            val execution = Regex("""name="execution"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: throw Exception("未找到 execution")
            Pair(lt, execution)
        }

        // Step 3: DES 加密
        val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")

        // Step 4: device 预验证
        client.newCall(Request.Builder().url("$CAS_BASE/device")
            .header("User-Agent", UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(FormBody.Builder()
                .add("ul", username.length.toString())
                .add("pl", password.length.toString())
                .add("rsa", encrypted)
                .add("method", "login").build()).build()
        ).execute().use { response ->
            response.body?.close()
        }

        // Step 5: 提交 CAS 表单(跟随重定向以获取 JWT)
        val finalUrl = redirectClient.newCall(Request.Builder().url(casUrl)
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(FormBody.Builder()
                .add("rsa", encrypted)
                .add("ul", username.length.toString())
                .add("pl", password.length.toString())
                .add("lt", lt)
                .add("execution", execution)
                .add("_eventId", "submit").build()).build()
        ).execute().use { response ->
            response.request.url.toString()
        }
        Log.d(TAG, "ycard OAuth redirect resolved")

        // Step 6: 从 URL 中提取 JWT
        val jwt = Regex("""synjones-auth=([^&"]+)""").find(finalUrl)?.groupValues?.get(1)
            ?: throw Exception("未获取到 synjones-auth JWT")

        cachedJwt = jwt
        Log.i(TAG, "ycard 完整登录成功")
    }
}
