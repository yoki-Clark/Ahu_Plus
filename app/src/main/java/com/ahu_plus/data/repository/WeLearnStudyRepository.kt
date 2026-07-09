package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.model.WeLearnStudyUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * WeLearn 刷题引擎 (2026-06-28 重构)
 *
 * 流程:遍历 CourseTree 每个单元 → 拉章节列表 → 跳过已完成/未开放 →
 * 对剩余章节:
 *   1. 并发拉 scoAddr + 真答案(centercourseware.sflep.com CDN,公开,无认证)
 *   2. 构造 cmi with real interactions(填空/选择/混合)
 *   3. POST 两次 SCORM 包(setscoinfo / savescoinfo160928)
 * 进度通过 [studyState] StateFlow 暴露给 UI。
 *
 * 不做"刷时长"心跳(keepsco_with_getticket_with_updatecmitime),那是阶段 3 事情。
 *
 * 协议照搬 jhl337/Auto_WeLearn + welearn_probe_real_submit.py (2026-06-28 实测确认)。
 *
 * 重要(2026-06-28 改造):
 *   - 不再是"假完成度"(fake cmi 假装做了 sco),而是"真刷题"
 *   - 拉 CDN 答案 → 构造 cmi.interactions 数组(SCORM 2004 标准)
 *   - 填空题:interactions[].type=fill-in, student_response=CDN 答案
 *   - 选择题:interactions[].type=choice, student_response=CDN 答案(如 "a" / "2,4")
 *   - 拉不到答案(录音题/无数据):fallback 旧"假完成度"提交
 *   - 进度正常,服务端会真把 iscomplete 改成"已完成",crate=100
 */
class WeLearnStudyRepository(
    private val authRepo: WeLearnAuthRepository,
    private val queryRepo: WeLearnRepository,
    private val answerRepo: WeLearnAnswerRepository,  // 2026-06-28 新增
) {
    companion object {
        private const val TAG = "WeLearnStudy"
        private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

        /**
         * 假 cmi 模板(fallback 用,无真答案时假装做了 sco)。
         * 与 jhl337/Auto_WeLearn 完全相同(2026-06-28 验证协议仍可用)。
         */
        private fun cmiDataFallback(accuracy: Int) = """
            {"cmi":{"completion_status":"completed","interactions":[],"launch_data":"",
            "progress_measure":"1","score":{"scaled":"$accuracy","raw":"100"},
            "session_time":"0","success_status":"unknown","total_time":"0","mode":"normal"},
            "adl":{"data":[]},"cci":{"data":[],"service":{"dictionary":{"headword":"","short_cuts":""},
            "new_words":[],"notes":[],"writing_marking":[],"record":{"files":[]},
            "play":{"offline_media_id":"9999"}},"retry_count":"0","submit_time":""}}
        """.trimIndent().replace(Regex("\\s+"), "")

        /**
         * 构造带真答案的 cmi JSON(SCORM 2004 interactions 数组)。
         * 结构必须与 [welearn_probe_real_submit.py] build_cmi 一致:
         * 顶层 {cmi:{...}, adl:{data:[]}, cci:{...}},adl/cci 是 cmi 的兄弟节点,不是子节点。
         * 漏掉 cmi 包装 → 服务端 cmi.completion_status 找不到,setscoinfo 返回 ret=0 但 sco 状态不变
         * (2026-06-28 实测「刷成功但不涨进度」就是这个原因)。
         */
        internal fun cmiDataWithAnswers(answers: ScoAnswers, score: Int): String {
            val cmi = JSONObject()
            cmi.put("completion_status", "completed")
            cmi.put("progress_measure", "1")
            cmi.put("score", JSONObject().apply {
                put("scaled", score.toString())
                put("raw", "100")
                put("min", "0")
                put("max", "100")
            })
            cmi.put("session_time", "0")
            cmi.put("success_status", "unknown")
            cmi.put("total_time", "0")
            cmi.put("mode", "normal")
            cmi.put("exit", "suspend")
            cmi.put("launch_data", "")

            val interactions = JSONArray()
            var idx = 0
            // 填空题
            for (ans in answers.blanks) {
                idx++
                val isOpen = ans.contains("Answers will vary")
                val interaction = JSONObject()
                interaction.put("id", "q$idx")
                interaction.put("type", "fill-in")
                interaction.put("weight", "1")
                interaction.put("time", "00:00:00")
                interaction.put("timestamp", "2026-06-28T00:00:00")
                interaction.put("student_response", ans)
                interaction.put("result", if (isOpen) "neutral" else "correct")
                if (!isOpen) {
                    val cr = JSONArray()
                    val pattern = JSONObject().put("pattern", ans)
                    cr.put(pattern)
                    interaction.put("correct_responses", cr)
                } else {
                    interaction.put("correct_responses", JSONArray())
                }
                interactions.put(interaction)
            }
            // 选择题
            for (key in answers.choices) {
                idx++
                val interaction = JSONObject()
                interaction.put("id", "q$idx")
                interaction.put("type", "choice")
                interaction.put("weight", "1")
                interaction.put("time", "00:00:00")
                interaction.put("timestamp", "2026-06-28T00:00:00")
                interaction.put("student_response", key)
                interaction.put("result", "correct")
                val cr = JSONArray()
                cr.put(JSONObject().put("pattern", key))
                interaction.put("correct_responses", cr)
                interactions.put(interaction)
            }
            cmi.put("interactions", interactions)

            // 顶层包 {cmi, adl, cci} 三个并列 key(参考 jhl337 模板 + welearn_probe_real_submit.py build_cmi)
            val root = JSONObject()
            root.put("cmi", cmi)
            root.put("adl", JSONObject().put("data", JSONArray()))
            val cci = JSONObject()
            cci.put("data", JSONArray())
            val service = JSONObject()
            val dict = JSONObject().put("headword", "").put("short_cuts", "")
            service.put("dictionary", dict)
            service.put("new_words", JSONArray())
            service.put("notes", JSONArray())
            service.put("writing_marking", JSONArray())
            service.put("record", JSONObject().put("files", JSONArray()))
            service.put("play", JSONObject().put("offline_media_id", "9999"))
            cci.put("service", service)
            cci.put("retry_count", "0")
            cci.put("submit_time", "")
            root.put("cci", cci)

            return root.toString()
        }
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

    // ── 刷时长心跳(2026-06-28) ──────────────────────────────
    /**
     * 对单个 sco 跑 N 秒心跳(30s 节奏 keepsco,savescoinfo 触发 learntime 持久化)。
     *
     * 协议(2026-06-28 本地实测验证):
     *   1. POST startsco160928 → 开 SCORM 会话
     *   2. 每秒 sleep,每 30s 一次 keepsco_with_getticket_with_updatecmitime(session_time/total_time 必填)
     *      失败时 retry 2 次(每次前重启 session 抗 stale);最终失败仅记日志(主流程继续)
     *   3. POST savescoinfo160928 → 关 SCORM 会话,服务端把累积的 session_time 写入 scoLeaves.learntime
     *
     * 节奏从 60s 改 30s(2026-06-29):手机 carrier NAT 经常在 60s 闲置后 RST 连接,导致首条 keep 失败
     * 失败处理:任何一步失败仅记日志(主流程继续);整个函数不抛异常。
     * 取消:协程 scope 共享 studyJob,shouldStop 期间直接 break。
     */
    suspend fun runHeartbeatForSco(
        cid: String, uid: String, classid: String, scoid: String,
        durationSec: Int,
        onTick: (Int) -> Unit,
    ) {
        if (durationSec <= 0) return
        if (shouldStop) return
        addLog("[心跳] $scoid 起步 ${durationSec}s (节奏 30s)")
        _studyState.value = _studyState.value.copy(
            currentScoHeartbeatSec = durationSec,
            heartbeatKeepFails = 0,
        )

        if (!queryRepo.heartbeatStart(cid, uid, scoid, classid)) {
            addLog("[心跳] $scoid startsco 失败,跳过")
            _studyState.value = _studyState.value.copy(elapsedSec = 0, currentScoHeartbeatSec = 0, heartbeatKeepFails = 0)
            return
        }

        // 2026-06-29:keep 失败时尝试 retry(2 次),retry 前先重启 SCORM session(防 stale session)
        // 手机端 carrier NAT 经常在 60s 闲置后 RST 连接,把节奏改 30s 大幅降低概率
        var keepFails = 0
        for (elapsed in 1..durationSec) {
            if (shouldStop) {
                addLog("[心跳] $scoid 用户停止 @ ${elapsed - 1}s")
                break
            }
            delay(1000)
            // 节奏 30s 一次(原 60s);最后一秒补一发保证 savescoinfo 之前服务端有最新数据
            if (elapsed % 30 == 0 || elapsed == durationSec) {
                var ok = queryRepo.heartbeatKeep(cid, uid, scoid, elapsed, elapsed)
                var retries = 0
                while (!ok && retries < 2 && !shouldStop) {
                    retries++
                    addLog("[心跳] $scoid t=${elapsed}s 失败,retry $retries/2 (等 3s + 重启 session)")
                    delay(3000)
                    queryRepo.heartbeatStart(cid, uid, scoid, classid)
                    ok = queryRepo.heartbeatKeep(cid, uid, scoid, elapsed, elapsed)
                }
                if (!ok) {
                    keepFails++
                    addLog("[心跳] $scoid t=${elapsed}s 最终失败 (本节累计 ${keepFails} 次)")
                    _studyState.value = _studyState.value.copy(heartbeatKeepFails = keepFails)
                }
            }
            onTick(elapsed)
        }

        queryRepo.heartbeatSave(cid, uid, scoid)
        _studyState.value = _studyState.value.copy(elapsedSec = 0, currentScoHeartbeatSec = 0, heartbeatKeepFails = 0)
        addLog("[心跳] $scoid 完成 (keep 失败 $keepFails 次)")
    }

    /**
     * 刷完一门课的全部未完成章节(2026-06-28 v2:按需拉答案,无答案直接跳过)。
     *
     * 流程(每个 sco 按需处理):
     *   1. scoAddr 拿 CDN URL
     *   2. CDN 拉 et-blank/et-choice 答案
     *   3. 拿不到答案 → skip(不浪费提交,也不做假完成)
     *   4. 拿得到答案 → 构造 cmi.interactions 提交(带 retry)
     *   5. 2026-06-28:如果 [heartbeatEnabled],提交完再跑 [heartbeatSecondsPerSco] 心跳(2026-06-29 改 30s 节奏 keepsco)
     *
     * 优势:
     *   - 不会对无答案的 sco(录音题/无数据)做无效提交
     *   - 不需要预拉所有 sco 的答案,启动延迟低
     *   - 中途异常不会浪费前面已拉的数据
     */
    suspend fun studyCourse(
        tree: WeLearnRepository.CourseTree,
        accuracyRange: IntRange = 100..100,
        delayMs: Long = 300,
        fullMode: Boolean = false,  // 2026-06-28:全量模式=true 时已完成的 sco 也重新提交
        unitIndices: IntArray? = null,  // 2026-06-28:选择性刷 — null=全部,IntArray=只刷选中单元
        heartbeatEnabled: Boolean = false,  // 2026-06-28:是否每节刷时长
        heartbeatSecondsPerSco: Int = 180,   // 2026-06-28:每节刷时长(秒),UI 配分钟数 × 60
    ) = withContext(Dispatchers.IO) {
        if (_studyState.value.isRunning) {
            Log.w(TAG, "已有任务在跑, 忽略新启动")
            return@withContext
        }
        studyJob = coroutineContext[Job]
        shouldStop = false
        _studyState.value = WeLearnStudyUiState(isRunning = true, accuracy = accuracyRange.first)

        try {
            // 2026-06-28:选择性刷 — 过滤单元
            val selectedSet = unitIndices?.toSet()
            val filteredUnits = tree.units.filterIndexed { idx, _ -> selectedSet == null || idx in selectedSet }
            if (selectedSet != null) {
                addLog("选择性刷:只刷 ${selectedSet.size} 个单元(总 ${tree.units.size} 单元)")
            }

            // 第一遍:扫所有单元,统计总章节数 + 拿 scoLeaves
            // 2026-06-29 修 bug:getScoLeaves 要的是原始 unitIdx(API 端 0-based 在 courseunits 列表的位置),
            // 不是 filteredUnits.withIndex() 的过滤后索引。否则「选择性刷」只剩 1 个单元时 idx=0,
            // 会拉回 Unit 1 的 sco(用户日志里看到「Unit 1 Free Therapy」被刷而不是选中的 Unit 8)
            val allScos = mutableListOf<Pair<Int, com.ahu_plus.data.model.WeLearnSco>>()
            for (unit in filteredUnits) {
                if (!unit.visible) {
                    addLog("跳过未开放单元: ${unit.unitName} / ${unit.name}")
                    continue
                }
                val scoRes = queryRepo.getScoLeaves(tree.cid, tree.uid, tree.classid, unit.unitIdx)
                val scos = scoRes.getOrElse {
                    addLog("✗ 拉章节失败 ${unit.name}: ${it.message}")
                    continue
                }
                allScos += scos.map { unit.unitIdx to it }
            }

            val pending = allScos.count { (_, sco) -> !sco.isSkippable }
            _studyState.value = _studyState.value.copy(totalCount = allScos.size, pendingCount = pending)
            addLog("总章节 ${allScos.size}, 待处理(非跳过) $pending")

            // 第二遍:按需处理 — 先判断再拉
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

                // 2026-06-28 详细日志:让用户看清每个 sco 的状态判断
                addLog("[判断] name=${sco.name.take(30)}  status=${sco.status}  isSkippable=${sco.isSkippable}  fullMode=$fullMode")
                if (sco.isSkippable && !fullMode) {
                    // 增量模式:已完成的 sco 直接跳过
                    _studyState.value = _studyState.value.copy(skippedCount = _studyState.value.skippedCount + 1)
                    addLog("[跳过] ${sco.location} (已完成)")
                    continue
                } else if (sco.status == com.ahu_plus.data.model.WeLearnScoStatus.LOCKED) {
                    // 全量模式下未开放的也跳过(server 拒)
                    _studyState.value = _studyState.value.copy(skippedCount = _studyState.value.skippedCount + 1)
                    addLog("[跳过] ${sco.location} (未开放)")
                    continue
                } else if (fullMode && sco.isSkippable) {
                    addLog("[全量重刷] ${sco.location}")
                }

                // 1. 拿 scoAddr(决定能不能进 CDN)
                val addrRes = runCatching { queryRepo.getScoAddr(tree.cid, sco.id) }.getOrNull()
                val addr = addrRes?.getOrNull()
                if (addr.isNullOrBlank()) {
                    addLog("[跳过] ${sco.location} (scoAddr 拿不到: ${addrRes?.exceptionOrNull()?.message ?: "空 addr"})")
                    _studyState.value = _studyState.value.copy(skippedCount = _studyState.value.skippedCount + 1)
                    continue
                }

                // 2. 拿 CDN 答案(拿不到就跳过,不浪费提交)
                val realAnswers = runCatching { answerRepo.fetchAnswersForSco(addr) }.getOrNull()
                if (realAnswers == null || realAnswers.isEmpty()) {
                    addLog("[跳过] ${sco.location} (CDN 无答案 — 录音题/特殊类型)")
                    _studyState.value = _studyState.value.copy(skippedCount = _studyState.value.skippedCount + 1)
                    continue
                }

                // 3. 拿到答案,提交(带 retry)
                _studyState.value = _studyState.value.copy(answersFetched = _studyState.value.answersFetched + 1)
                val accuracy = if (accuracyRange.first == accuracyRange.last) accuracyRange.first
                else Random.nextInt(accuracyRange.first, accuracyRange.last + 1)
                val (way1, way2, lastBody) = submitScoWithRetry(
                    cid = tree.cid, uid = tree.uid, classid = tree.classid,
                    scoid = sco.id, accuracy = accuracy, answers = realAnswers,
                )
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
                addLog("[$tag] [真答${realAnswers.total()}] ${sco.location} (way1=$way1 way2=$way2)")
                if (!way1 || !way2) {
                    // 失败诊断:打印服务器响应前 200 字符,方便排查 cmi 格式问题
                    addLog("    诊断: ${lastBody.take(200)}")
                }

                // 2026-06-28:刷时长心跳(在刷完成度成功后跑,失败不影响主流程)
                if (heartbeatEnabled && heartbeatSecondsPerSco > 0) {
                    runHeartbeatForSco(tree.cid, tree.uid, tree.classid, sco.id, heartbeatSecondsPerSco) { elapsed ->
                        _studyState.value = _studyState.value.copy(elapsedSec = elapsed)
                    }
                }

                // 限速控制:每个 sco 间隔最少 1.5s,每 10 个长暂停 8s
                val safeDelay = maxOf(delayMs, 1500L)
                delay(safeDelay)
                val processed = _studyState.value.completedCount + _studyState.value.partialCount + _studyState.value.failedCount
                if (processed > 0 && processed % 10 == 0) {
                    addLog("⏸ 长暂停 8s(避免 SFLEP 限速)...")
                    delay(8000)
                }
            }

            authRepo.flushCookies()
            _studyState.value = _studyState.value.copy(isRunning = false, currentUnitName = "", currentScoLocation = "")
            addLog("完成: ✓${_studyState.value.completedCount} △${_studyState.value.partialCount} ✗${_studyState.value.failedCount} 跳过${_studyState.value.skippedCount} 真答${_studyState.value.answersFetched}")
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
     * 单章节 SCORM 提交(2026-06-28 改造:支持真答案)。
     * 返回 (way1成功, way2成功)。
     *
     * @param answers 真答案(可选)。非空时构造 cmi.interactions;为空时 fallback 假完成度。
     */
    internal fun submitSco(
        cid: String, uid: String, classid: String, scoid: String, accuracy: Int,
        answers: ScoAnswers? = null,
    ): Pair<Boolean, Boolean> {
        val ajaxUrl = "${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx"
        val referer = "${WeLearnAuthRepository.BASE_URL}/Student/StudyCourse.aspx?cid=$cid&classid=$classid&sco=$scoid"

        // 构造 cmi:有真答案走 cmiDataWithAnswers,否则 fallback
        val cmi = if (answers != null && answers.total() > 0) {
            cmiDataWithAnswers(answers, accuracy)
        } else {
            cmiDataFallback(accuracy)
        }

        // way 1: setscoinfo (塞完整 cmi) — 2026-06-28 加诊断:返回响应体便于失败排查
        val (way1, w1Body) = runCatching {
            val setBody = FormBody.Builder()
                .add("action", "setscoinfo")
                .add("cid", cid).add("scoid", scoid).add("uid", uid)
                .add("data", cmi)
                .add("isend", "False")
                .build()
            authRepo.client.newCall(
                Request.Builder().url(ajaxUrl)
                    .header("Referer", referer).header("User-Agent", UA)
                    .post(setBody).build()
            ).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                (resp.isSuccessful && isRetZero(body)) to body
            }
        }.getOrDefault(false to "")

        // way 2: savescoinfo160928 (progress=100, status=completed)
        val (way2, w2Body) = runCatching {
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
                val body = resp.body?.string().orEmpty()
                (resp.isSuccessful && isRetZero(body)) to body
            }
        }.getOrDefault(false to "")

        // 拼装诊断信息
        val diag = if (!way1 || !way2) {
            "w1=$way1[${w1Body.take(80)}] w2=$way2[${w2Body.take(80)}]"
        } else ""

        // 出于 API 兼容,还是返回 Pair<Boolean, Boolean>
        // 诊断日志在 submitScoWithRetry 里读不到,改成 Triple 引用?
        // 简单做法:用 ThreadLocal? 算了,直接在 studyCourse 里调 raw 逻辑
        return way1 to way2
    }

    /**
     * 带 retry + 诊断的提交(2026-06-28 新增)。
     * 返回 (way1, way2, 最后一次响应的诊断信息)
     */
    private suspend fun submitScoWithRetry(
        cid: String, uid: String, classid: String, scoid: String, accuracy: Int, answers: ScoAnswers?,
    ): Triple<Boolean, Boolean, String> {
        var lastDiag = ""
        for (attempt in 0..2) {
            if (shouldStop) break
            val (way1, way2, diag) = submitScoRaw(cid, uid, classid, scoid, accuracy, answers)
            lastDiag = diag
            if (way1 && way2) return Triple(true, true, diag)
            if (attempt < 2) {
                val backoff = if (attempt == 0) 3000L else 6000L
                addLog("    [retry ${attempt+1}] 等 ${backoff/1000}s")
                delay(backoff)
            }
        }
        return Triple(false, false, lastDiag)
    }

    /** 单次提交(无 retry),返回 (way1, way2, 诊断)。诊断包含 way1/way2 HTTP 状态 + 响应体前 80 字符 */
    private fun submitScoRaw(
        cid: String, uid: String, classid: String, scoid: String, accuracy: Int, answers: ScoAnswers?,
    ): Triple<Boolean, Boolean, String> {
        val ajaxUrl = "${WeLearnAuthRepository.BASE_URL}/Ajax/SCO.aspx"
        val referer = "${WeLearnAuthRepository.BASE_URL}/Student/StudyCourse.aspx?cid=$cid&classid=$classid&sco=$scoid"
        val cmi = if (answers != null && answers.total() > 0) {
            cmiDataWithAnswers(answers, accuracy)
        } else {
            cmiDataFallback(accuracy)
        }

        // 0. startsco160928 (2026-06-28 修复 ret:3002 bug)
        // 服务端需要先注册 SCORM session,后续 setscoinfo/savescoinfo160928 才能找到对象。
        // 漏掉这一步 → 服务端返回 ret:3002 "对象不存在!"
        // 参考 jhl337/Auto_WeLearn + welearn-helper 都是 3 步:startsco → setscoinfo → savescoinfo
        val (startscoOk, startStatus, startBody) = runCatching {
            val startBody = FormBody.Builder()
                .add("action", "startsco160928")
                .add("uid", uid).add("cid", cid).add("scoid", scoid)
                .add("classid", classid).add("tid", "-1")
                .build()
            authRepo.client.newCall(
                Request.Builder().url(ajaxUrl)
                    .header("Referer", referer).header("User-Agent", UA)
                    .post(startBody).build()
            ).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Triple(isRetZero(body), "HTTP ${resp.code}", body)
            }
        }.getOrDefault(Triple(false, "EXC", ""))

        if (!startscoOk) {
            val diag = "startsco失败 $startStatus [${startBody.take(120)}]"
            return Triple(false, false, diag)
        }

        val (way1, w1Status, w1Body) = runCatching {
            val setBody = FormBody.Builder()
                .add("action", "setscoinfo")
                .add("cid", cid).add("scoid", scoid).add("uid", uid)
                .add("data", cmi)
                .add("isend", "False")
                .build()
            authRepo.client.newCall(
                Request.Builder().url(ajaxUrl)
                    .header("Referer", referer).header("User-Agent", UA)
                    .post(setBody).build()
            ).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                Triple(resp.isSuccessful && isRetZero(body), "HTTP ${resp.code}", body)
            }
        }.getOrDefault(Triple(false, "EXC", ""))

        // 2026-06-28:成功提交时打 setscoinfo 的 lasttime 字段(服务端记录的"最后提交时间"),便于排查是否真生效
        if (way1) {
            val lastTime = runCatching { org.json.JSONObject(w1Body).optString("lasttime", "") }.getOrDefault("")
            Log.d(TAG, "setscoinfo OK: ${w1Body.take(200)} lasttime=$lastTime")
        }

        val (way2, w2Status, w2Body) = runCatching {
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
                val body = resp.body?.string().orEmpty()
                Triple(resp.isSuccessful && isRetZero(body), "HTTP ${resp.code}", body)
            }
        }.getOrDefault(Triple(false, "EXC", ""))

        val diag = "startsco=ok | w1=$way1 $w1Status [${w1Body.take(60)}] | w2=$way2 $w2Status [${w2Body.take(60)}]"
        return Triple(way1, way2, diag)
    }

    private fun isRetZero(body: String): Boolean = runCatching {
        org.json.JSONObject(body).optInt("ret", -1) == 0
    }.getOrDefault(false)

    private fun addLog(msg: String) {
        Log.d(TAG, msg)
        _studyState.value = _studyState.value.copy(
            logs = (_studyState.value.logs + msg).takeLast(100)
        )
    }
}
