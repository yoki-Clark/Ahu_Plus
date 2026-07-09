package com.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ahu_plus.data.model.WeLearnCourse
import com.ahu_plus.data.model.WeLearnSco
import com.ahu_plus.data.model.WeLearnScoStatus
import com.ahu_plus.data.model.WeLearnUnit
import com.ahu_plus.data.model.WeLearnUnitScos
import com.ahu_plus.data.model.parseHmsToSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * WeLearn 课程查询门面。
 *
 * 复用 [WeLearnAuthRepository.client](含已登录 Cookie),负责:
 * - 课程列表 `authCourse.aspx?action=gmc`
 * - 课程单元 `StudyStat.aspx?action=courseunits`(同时抠 uid/classid)
 * - 章节列表 `StudyStat.aspx?action=scoLeaves`
 *
 * 协议已用 tools/welearn_probe.py 验证(2026-06-27),见阶段 0。
 */
class WeLearnRepository(
    private val authRepo: WeLearnAuthRepository,
) {
    companion object {
        private const val TAG = "WeLearn"
        private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
        /** 登录过期 sentinel 前缀,ViewModel 据此触发 needsLogin 与自动重登 */
        internal const val SESSION_EXPIRED_PREFIX = "session expired:"

        /**
         * SFLEP 未登录返回 HTML 登录页,而非 JSON。嗅探:body 以 `<` 开头或 gson 解析失败 → 抛带
         * [SESSION_EXPIRED_PREFIX] 前缀的 IOException,VM 端据此触发 needsLogin + 自动重登。
         * ponytail: 真 schema 异常也走 session-expired,误判可接受(失败透传即可,不影响其他场景)。
         */
        internal fun parseJsonOrSessionExpired(body: String, fromApi: String): JsonObject {
            if (body.trimStart().startsWith("<")) throw IOException("$SESSION_EXPIRED_PREFIX $fromApi")
            return runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw IOException("$SESSION_EXPIRED_PREFIX $fromApi (parse)") }
        }
    }

    // ── 课程列表 ────────────────────────────────────────────
    /** @return 成功:课程列表(可能为空)。失败:抛异常 */
    suspend fun getCourses(): Result<List<WeLearnCourse>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                // 2026-06-28 加 _t cache-buster,避免服务端缓存返回旧 course.per
                .url("${WeLearnAuthRepository.BASE_URL}/ajax/authCourse.aspx?action=gmc&_t=${System.currentTimeMillis()}")
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/2019/student/index.aspx")
                .build()
            authRepo.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("authCourse HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = parseJsonOrSessionExpired(body, "authCourse gmc")
                val arr = root.getAsJsonArray("clist") ?: return@runCatching emptyList()
                arr.map { it.asJsonObject }.map { o ->
                    WeLearnCourse(
                        cid = o.get("cid")?.asString.orEmpty(),
                        name = o.get("name")?.asString.orEmpty(),
                        per = o.get("per")?.asInt ?: 0,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "getCourses 失败", it) }
    }

    // ── 课程单元 ────────────────────────────────────────────
    /** 拉 uid + classid + 单元列表 */
    suspend fun getCourseTree(cid: String): Result<CourseTree> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. course_info.aspx 抠 uid / classid
            val infoReq = Request.Builder()
                .url("${WeLearnAuthRepository.BASE_URL}/student/course_info.aspx?cid=$cid")
                .header("User-Agent", UA)
                .build()
            val (uid, classid) = authRepo.client.newCall(infoReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("course_info HTTP ${resp.code}")
                val text = resp.body?.string().orEmpty()
                val uidMatch = Regex("\"uid\":\\s*(\\d+)").find(text)
                val classidMatch = Regex("\"classid\":\"(\\w+)\"").find(text)
                (uidMatch?.groupValues?.get(1) ?: throw IOException("course_info 抠不出 uid")) to
                    (classidMatch?.groupValues?.get(1) ?: throw IOException("course_info 抠不出 classid"))
            }

            // 2. courseunits 拉单元
            val unitsReq = Request.Builder()
                .url("${WeLearnAuthRepository.BASE_URL}/ajax/StudyStat.aspx?action=courseunits&cid=$cid&uid=$uid")
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/2019/student/course_info.aspx")
                .build()
            val units = authRepo.client.newCall(unitsReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("courseunits HTTP ${resp.code}")
                val arr = parseJsonOrSessionExpired(resp.body?.string().orEmpty(), "courseunits")
                    .getAsJsonArray("info") ?: return@use emptyList<WeLearnUnit>()
                arr.mapIndexed { idx, it ->
                    val o = it.asJsonObject
                    WeLearnUnit(
                        unitIdx = idx,
                        unitName = o.get("unitname")?.asString.orEmpty(),
                        name = o.get("name")?.asString.orEmpty(),
                        visible = o.get("visible")?.asString == "true",
                    )
                }
            }
            CourseTree(cid = cid, uid = uid, classid = classid, units = units)
        }.onFailure { Log.w(TAG, "getCourseTree($cid) 失败", it) }
    }

    // ── 章节列表 ────────────────────────────────────────────
    suspend fun getScoLeaves(cid: String, uid: String, classid: String, unitIdx: Int): Result<List<WeLearnSco>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 2026-06-28 加 _t cache-buster,避免服务端缓存返回旧 iscomplete
                val req = Request.Builder()
                    .url(
                        "${WeLearnAuthRepository.BASE_URL}/ajax/StudyStat.aspx" +
                            "?action=scoLeaves&cid=$cid&uid=$uid&unitidx=$unitIdx&classid=$classid" +
                            "&_t=${System.currentTimeMillis()}"
                    )
                    .header("User-Agent", UA)
                    .header("Referer", "${WeLearnAuthRepository.BASE_URL}/2019/student/course_info.aspx?cid=$cid")
                    .build()
                authRepo.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("scoLeaves HTTP ${resp.code}")
                    val arr = parseJsonOrSessionExpired(resp.body?.string().orEmpty(), "scoLeaves")
                        .getAsJsonArray("info") ?: return@use emptyList<WeLearnSco>()
                    arr.map { it.asJsonObject }.map { o ->
                        val isVisible = o.get("isvisible")?.asString == "true"
                        val isComplete = o.get("iscomplete")?.asString == "已完成"
                        val status = when {
                            !isVisible -> WeLearnScoStatus.LOCKED
                            isComplete -> WeLearnScoStatus.COMPLETED
                            else -> WeLearnScoStatus.TODO
                        }
                        WeLearnSco(
                            id = o.get("id")?.asString.orEmpty(),
                            name = o.get("name")?.asString.orEmpty(),
                            location = o.get("location")?.asString.orEmpty(),
                            status = status,
                            // 2026-06-28:加 learntime 字段(已学时长,"HH:MM:SS")+ completetime(最后完成时间)
                            learntimeSeconds = parseHmsToSeconds(o.get("learntime")?.asString),
                            completetime = o.get("completetime")?.asString?.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "getScoLeaves 失败", it) }
        }

    /** 课程树返回结构 */
    data class CourseTree(
        val cid: String,
        val uid: String,
        val classid: String,
        val units: List<WeLearnUnit>,
    )

    /**
     * 拉 scoAddr(2026-06-28 用于刷题,拿 CDN 答案 URL)。
     * 返回 Result<String> 内容是 addr 字段(形如 `//centercourseware.sflep.com/.../index.html#/1/1-1-3|...`)。
     */
    suspend fun getScoAddr(cid: String, scoid: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("action", "scoAddr")
                .add("cid", cid)
                .add("scoid", scoid)
                .add("nocache", System.currentTimeMillis().toString())
                .build()
            val req = Request.Builder()
                .url("${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx")
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/student/studycourse.aspx?cid=$cid&classid=0&sco=")
                .post(body)
                .build()
            authRepo.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("scoAddr HTTP ${resp.code}")
                val text = resp.body?.string().orEmpty()
                val obj = parseJsonOrSessionExpired(text, "scoAddr")
                if (obj.get("ret")?.asInt != 0) throw IOException("scoAddr ret=${obj.get("ret")} msg=${obj.get("msg")}")
                obj.get("addr")?.asString ?: throw IOException("scoAddr no addr field")
            }
        }.onFailure { Log.w(TAG, "getScoAddr($scoid) 失败", it) }
    }

    // ── 课程级统计(Hour/Minute 等) ──────────────────────────────
    /**
     * 拉课程级统计(Total/Finish/Hour/Minute/ExAvgRate),供课程详情页显示"已学习 X 时 Y 分"。
     * 端点实测:`GET /ajax/StudyStat.aspx?action=scogeneral&cid=X&stuid=Y`(uid 也接受)
     * 响应:`{"ret":0,"Totalcount":274,"Finishcount":202,"ExAvgRate":"94","Hour":2,"Minute":59}`
     * 注意:Hour/Minute 似乎只统计 iscomplete='已完成' 的 sco(本地验证 2026-06-28)
     */
    suspend fun getScoGeneral(cid: String, stuid: String): Result<ScoGeneralStats> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(
                    "${WeLearnAuthRepository.BASE_URL}/ajax/StudyStat.aspx" +
                        "?action=scogeneral&cid=$cid&stuid=$stuid" +
                        "&_t=${System.currentTimeMillis()}"
                )
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/2019/student/course_info.aspx?cid=$cid")
                .build()
            authRepo.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("scogeneral HTTP ${resp.code}")
                val o = parseJsonOrSessionExpired(resp.body?.string().orEmpty(), "scogeneral")
                val hour = o.get("Hour")?.asInt ?: 0
                val min = o.get("Minute")?.asInt ?: 0
                ScoGeneralStats(
                    totalCount = o.get("Totalcount")?.asInt ?: 0,
                    finishCount = o.get("Finishcount")?.asInt ?: 0,
                    avgRate = o.get("ExAvgRate")?.asInt ?: 0,
                    studiedSeconds = hour * 3600 + min * 60,
                )
            }
        }.onFailure { Log.w(TAG, "getScoGeneral($cid) 失败", it) }
    }

    /** 课程级统计(总章节/已完成/正确率/已学时长秒),给 CompletionCard 用 */
    data class ScoGeneralStats(
        val totalCount: Int,
        val finishCount: Int,
        val avgRate: Int,
        val studiedSeconds: Int,
    )

    // ── 心跳(刷时长)3 步 ───────────────────────────────────
    /**
     * 打开 SCORM 会话(刷时长流程的 step 1)。
     * 端点:`POST /Ajax/SCO.aspx?action=startsco160928` body: uid/cid/scoid/classid/tid=-1
     * Referer:`/student/StudyCourse.aspx`(小写 s,无 query)
     * 响应(ret=0):{"ret":0,"timelitsec":1800,"useSeconds":N,"enableview":"false","configs":{...}}
     *
     * 注:此步 2026-06-28 改造中已确认是 setscoinfo 前置必需,见 WeLearnStudyRepository。
     * 心跳模式 timeout 短(10s),失败仅记日志,不抛异常(刷课主流程应继续)。
     */
    suspend fun heartbeatStart(cid: String, uid: String, scoid: String, classid: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder()
                    .add("action", "startsco160928")
                    .add("uid", uid).add("cid", cid).add("scoid", scoid)
                    .add("classid", classid).add("tid", "-1")
                    .build()
                val req = Request.Builder()
                    .url("${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx")
                    .header("User-Agent", UA)
                    .header("Referer", "${WeLearnAuthRepository.BASE_URL}/student/StudyCourse.aspx")
                    .post(body).build()
                authRepo.client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val ok = resp.isSuccessful && runCatching {
                        org.json.JSONObject(text).optInt("ret", -1)
                    }.getOrDefault(-1) == 0
                    if (!ok) Log.w(TAG, "heartbeatStart $scoid ret!=0: ${text.take(120)}")
                    ok
                }
            }.getOrElse {
                Log.w(TAG, "heartbeatStart $scoid 异常: ${it.message}")
                false
            }
        }

    /**
     * 发送一次心跳(刷时长流程的 step 2,每 60s 一次)。
     * 端点:`POST /Ajax/SCO.aspx?action=keepsco_with_getticket_with_updatecmitime`
     * 必填字段:session_time, total_time(本地验证 2026-06-28:缺这俩 ret=2001"参数错误")
     * 响应(ret=0):{"ret":0,"seconds":N,"endcaltime":false,"ticket":""}
     */
    suspend fun heartbeatKeep(
        cid: String, uid: String, scoid: String,
        sessionTime: Int, totalTime: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("action", "keepsco_with_getticket_with_updatecmitime")
                .add("uid", uid).add("cid", cid).add("scoid", scoid)
                .add("session_time", sessionTime.toString())
                .add("total_time", totalTime.toString())
                .add("timelimitsec", "0").add("endcaltime", "false")
                .build()
            val req = Request.Builder()
                .url("${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx")
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/student/StudyCourse.aspx")
                .post(body).build()
            authRepo.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ret = runCatching { org.json.JSONObject(text).optInt("ret", -1) }.getOrDefault(-1)
                val ok = resp.isSuccessful && (ret == 0 || ret == 1)  // 0/1 都视为成功(jhl337 + welearn-helper 都接受)
                if (!ok) Log.w(TAG, "heartbeatKeep $scoid t=$sessionTime ret=$ret: ${text.take(120)}")
                ok
            }
        }.getOrElse {
            Log.w(TAG, "heartbeatKeep $scoid 异常: ${it.message}")
            false
        }
    }

    /**
     * 关闭 SCORM 会话(刷时长流程的 step 3,持久化 learntime)。
     * 端点:`POST /Ajax/SCO.aspx?action=savescoinfo160928`
     * ponytail:心跳场景下 crate=0(避免影响完成度统计),与 jhl337 一致。
     */
    suspend fun heartbeatSave(cid: String, uid: String, scoid: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("action", "savescoinfo160928")
                .add("cid", cid).add("scoid", scoid).add("uid", uid)
                .add("progress", "100")
                .add("crate", "0")           // ponytail:心跳模式 crate=0,避免误算完成度
                .add("status", "unknown")
                .add("cstatus", "completed")
                .add("trycount", "0")
                .build()
            val req = Request.Builder()
                .url("${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx")
                .header("User-Agent", UA)
                .header("Referer", "${WeLearnAuthRepository.BASE_URL}/student/StudyCourse.aspx")
                .post(body).build()
            authRepo.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ret = runCatching { org.json.JSONObject(text).optInt("ret", -1) }.getOrDefault(-1)
                val ok = resp.isSuccessful && ret == 0
                if (!ok) Log.w(TAG, "heartbeatSave $scoid ret=$ret: ${text.take(120)}")
                ok
            }
        }.getOrElse {
            Log.w(TAG, "heartbeatSave $scoid 异常: ${it.message}")
            false
        }
    }

    // ── 课程详情(单元+章节) ──────────────────────────────
    /**
     * 拉课程下全部单元的章节,用于课程详情页(WeLearnCourseDetailScreen)显示
     * 单元→章节的树形结构。N+1 调用(courseunits + N×scoLeaves),调用方按需触发。
     *
     * 某个单元的 scoLeaves 失败时该单元章节用空列表兜底,不中断整棵树。
     */
    suspend fun getCourseTreeWithScos(cid: String): Result<List<WeLearnUnitScos>> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = getCourseTree(cid).getOrThrow()
            tree.units.mapIndexed { idx, unit ->
                val scos = getScoLeaves(cid, tree.uid, tree.classid, idx).getOrElse {
                    Log.w(TAG, "getCourseTreeWithScos: 单元 $idx scoLeaves 失败, 用空列表兜底: ${it.message}")
                    emptyList()
                }
                WeLearnUnitScos(unit, scos)
            }
        }.onFailure { Log.w(TAG, "getCourseTreeWithScos($cid) 失败", it) }
    }
}