package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.model.WeLearnCourse
import com.yourname.ahu_plus.data.model.WeLearnSco
import com.yourname.ahu_plus.data.model.WeLearnScoStatus
import com.yourname.ahu_plus.data.model.WeLearnUnit
import com.yourname.ahu_plus.data.model.WeLearnUnitScos
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
                val root = JsonParser.parseString(body).asJsonObject
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
                val arr = JsonParser.parseString(resp.body?.string().orEmpty())
                    .asJsonObject.getAsJsonArray("info") ?: return@use emptyList<WeLearnUnit>()
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
                    val arr = JsonParser.parseString(resp.body?.string().orEmpty())
                        .asJsonObject.getAsJsonArray("info") ?: return@use emptyList<WeLearnSco>()
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
                val obj = JsonParser.parseString(text).asJsonObject
                if (obj.get("ret")?.asInt != 0) throw IOException("scoAddr ret=${obj.get("ret")} msg=${obj.get("msg")}")
                obj.get("addr")?.asString ?: throw IOException("scoAddr no addr field")
            }
        }.onFailure { Log.w(TAG, "getScoAddr($scoid) 失败", it) }
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