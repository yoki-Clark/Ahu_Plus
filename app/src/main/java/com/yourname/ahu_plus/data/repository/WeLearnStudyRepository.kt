package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonParser
import com.yourname.ahu_plus.data.model.WeLearnStudyUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * WeLearn 刷课引擎。
 *
 * 流程:遍历 CourseTree 的每个单元 → 拉章节列表 → 跳过已完成/未开放 →
 * 对剩余章节 POST 两次 SCORM 包(setscoinfo / savescoinfo160928)。
 * 进度通过 [studyState] StateFlow 暴露给 UI。
 *
 * 不做"刷时长"心跳(keepsco_with_getticket_with_updatecmitime),
 * 那是阶段 3 Service 组合的事情——本仓库只关心"完成度 100% + 正确率"。
 *
 * 协议照搬 Auto_WeLearn/Auto_WeLearn (2025-12 仍活跃) + WELearnToSleep (2021, 兼容旧 SCORM 端点)。
 */
class WeLearnStudyRepository(
    private val authRepo: WeLearnAuthRepository,
    private val queryRepo: WeLearnRepository,
) {
    companion object {
        private const val TAG = "WeLearnStudy"
        private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

        /** SCORM cmi 模板:completion_status=completed, mode=normal, success_status=unknown。 */
        private fun cmiData(accuracy: Int) = """
            {"cmi":{"completion_status":"completed","interactions":[],"launch_data":"",
            "progress_measure":"1","score":{"scaled":"$accuracy","raw":"100"},
            "session_time":"0","success_status":"unknown","total_time":"0","mode":"normal"},
            "adl":{"data":[]},"cci":{"data":[],"service":{"dictionary":{"headword":"","short_cuts":""},
            "new_words":[],"notes":[],"writing_marking":[],"record":{"files":[]},
            "play":{"offline_media_id":"9999"}},"retry_count":"0","submit_time":""}}
        """.trimIndent().replace(Regex("\\s+"), "")
    }

    private val _studyState = MutableStateFlow(WeLearnStudyUiState())
    val studyState: StateFlow<WeLearnStudyUiState> = _studyState.asStateFlow()

    @Volatile
    private var shouldStop = false

    @Volatile
    private var studyJob: Job? = null

    fun stop() {
        shouldStop = true
        studyJob?.cancel()
    }

    /**
     * 刷完一门课的全部未完成章节。
     *
     * @param tree 来自 [WeLearnRepository.getCourseTree]
     * @param accuracyRange 固定或区间随机,parseAccuracy 解析
     * @param delayMs 每次提交后休眠毫秒,避免对 SFLEP 压力过大(默认 300ms)
     */
    suspend fun studyCourse(
        tree: WeLearnRepository.CourseTree,
        accuracyRange: IntRange = 100..100,
        delayMs: Long = 300,
    ) = withContext(Dispatchers.IO) {
        if (_studyState.value.isRunning) {
            Log.w(TAG, "已有任务在跑, 忽略新启动")
            return@withContext
        }
        studyJob = coroutineContext[Job]
        shouldStop = false
        _studyState.value = WeLearnStudyUiState(isRunning = true, accuracy = accuracyRange.first)

        try {
            // 第一遍:统计总章节数,UI 显示进度条
            val allScos = mutableListOf<Pair<Int, com.yourname.ahu_plus.data.model.WeLearnSco>>()
            for ((idx, unit) in tree.units.withIndex()) {
                if (!unit.visible) {
                    addLog("跳过未开放单元: ${unit.unitName} / ${unit.name}")
                    continue
                }
                val scoRes = queryRepo.getScoLeaves(tree.cid, tree.uid, tree.classid, idx)
                val scos = scoRes.getOrElse {
                    addLog("✗ 拉章节失败 ${unit.name}: ${it.message}")
                    continue
                }
                allScos += scos.map { idx to it }
            }

            val pending = allScos.count { (_, sco) -> !sco.isSkippable }
            _studyState.value = _studyState.value.copy(totalCount = allScos.size, pendingCount = pending)
            addLog("总章节 ${allScos.size}, 待刷 $pending")

            // 第二遍:刷
            for ((unitIdx, sco) in allScos) {
                if (shouldStop) {
                    addLog("⚠ 用户停止")
                    break
                }
                val unit = tree.units.getOrNull(unitIdx)
                _studyState.value = _studyState.value.copy(
                    currentUnitName = "${unit?.unitName.orEmpty()} / ${unit?.name.orEmpty()}",
                    currentScoLocation = sco.location,
                )

                if (sco.isSkippable) {
                    _studyState.value = _studyState.value.copy(skippedCount = _studyState.value.skippedCount + 1)
                    addLog("[跳过] ${sco.location}")
                    continue
                }

                val accuracy = if (accuracyRange.first == accuracyRange.last) accuracyRange.first
                else Random.nextInt(accuracyRange.first, accuracyRange.last + 1)
                val (way1, way2) = submitSco(tree.cid, tree.uid, tree.classid, sco.id, accuracy)
                val newState = _studyState.value.let {
                    when {
                        way1 && way2 -> it.copy(completedCount = it.completedCount + 1)
                        way1 || way2 -> it.copy(partialCount = it.partialCount + 1)
                        else -> it.copy(failedCount = it.failedCount + 1)
                    }
                }
                _studyState.value = newState
                val tag = when {
                    way1 && way2 -> "✓"
                    way1 || way2 -> "△"
                    else -> "✗"
                }
                addLog("[$tag] 正确率 $accuracy% ${sco.location} (way1=$way1 way2=$way2)")

                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            }

            authRepo.flushCookies()
            _studyState.value = _studyState.value.copy(isRunning = false, currentUnitName = "", currentScoLocation = "")
            addLog("完成: ✓${_studyState.value.completedCount} △${_studyState.value.partialCount} ✗${_studyState.value.failedCount} 跳过${_studyState.value.skippedCount}")
        } catch (e: CancellationException) {
            addLog("⚠ 协程取消")
            _studyState.value = _studyState.value.copy(isRunning = false)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "studyCourse 异常", e)
            _studyState.value = _studyState.value.copy(isRunning = false, error = e.message)
            addLog("✗ 异常: ${e.message}")
        }
    }

    /**
     * 单章节 SCORM 提交。返回 (way1成功, way2成功)。
     * 与 welearn_probe.py 中的 submit_sco 完全对应,便于 Python 探测 ↔ Kotlin 实现对照验证。
     */
    internal fun submitSco(cid: String, uid: String, classid: String, scoid: String, accuracy: Int): Pair<Boolean, Boolean> {
        val ajaxUrl = "${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx"
        val referer = "${WeLearnAuthRepository.BASE_URL}/Student/StudyCourse.aspx?cid=$cid&classid=$classid&sco=$scoid"

        // way 1: setscoinfo (塞完整 SCORM cmi)
        val way1 = runCatching {
            val setBody = FormBody.Builder()
                .add("action", "setscoinfo")
                .add("cid", cid).add("scoid", scoid).add("uid", uid)
                .add("data", cmiData(accuracy))
                .add("isend", "False")
                .build()
            authRepo.client.newCall(
                Request.Builder().url(ajaxUrl)
                    .header("Referer", referer).header("User-Agent", UA)
                    .post(setBody).build()
            ).execute().use { resp ->
                resp.isSuccessful && isRetZero(resp.body?.string().orEmpty())
            }
        }.getOrDefault(false)

        // way 2: savescoinfo160928 (progress=100, status=completed)
        val way2 = runCatching {
            val saveBody = FormBody.Builder()
                .add("action", "savescoinfo160928")
                .add("cid", cid).add("scoid", scoid).add("uid", uid)
                .add("progress", "100")
                .add("crate", accuracy.toString())
                .add("status", "unknown")
                .add("cstatus", "completed")
                .add("trycount", "0")
                .build()
            authRepo.client.newCall(
                Request.Builder().url(ajaxUrl)
                    .header("Referer", referer).header("User-Agent", UA)
                    .post(saveBody).build()
            ).execute().use { resp ->
                resp.isSuccessful && isRetZero(resp.body?.string().orEmpty())
            }
        }.getOrDefault(false)

        return way1 to way2
    }

    private fun isRetZero(body: String): Boolean = runCatching {
        JsonParser.parseString(body).asJsonObject.get("ret")?.asInt == 0
    }.getOrDefault(false)

    private fun addLog(msg: String) {
        Log.d(TAG, msg)
        _studyState.value = _studyState.value.copy(
            logs = (_studyState.value.logs + msg).takeLast(100)
        )
    }
}