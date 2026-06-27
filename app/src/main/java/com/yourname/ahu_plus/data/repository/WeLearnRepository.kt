package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.model.WeLearnCourse
import com.yourname.ahu_plus.data.model.WeLearnSco
import com.yourname.ahu_plus.data.model.WeLearnUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                .url("${WeLearnAuthRepository.BASE_URL}/ajax/authCourse.aspx?action=gmc")
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
                val req = Request.Builder()
                    .url(
                        "${WeLearnAuthRepository.BASE_URL}/ajax/StudyStat.aspx" +
                            "?action=scoLeaves&cid=$cid&uid=$uid&unitidx=$unitIdx&classid=$classid"
                    )
                    .header("User-Agent", UA)
                    .header("Referer", "${WeLearnAuthRepository.BASE_URL}/2019/student/course_info.aspx?cid=$cid")
                    .build()
                authRepo.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("scoLeaves HTTP ${resp.code}")
                    val arr = JsonParser.parseString(resp.body?.string().orEmpty())
                        .asJsonObject.getAsJsonArray("info") ?: return@use emptyList<WeLearnSco>()
                    arr.map { it.asJsonObject }.map { o ->
                        WeLearnSco(
                            id = o.get("id")?.asString.orEmpty(),
                            location = o.get("location")?.asString.orEmpty(),
                            isComplete = "未" !in (o.get("iscomplete")?.asString.orEmpty()),
                            isVisible = o.get("isvisible")?.asString == "true",
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
}