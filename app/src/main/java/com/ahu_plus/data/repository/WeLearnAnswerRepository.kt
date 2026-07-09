package com.ahu_plus.data.repository

import android.util.Log
import com.ahu_plus.data.model.WeLearnSco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

/**
 * WeLearn 答题仓库 (2026-06-28)
 *
 * 数据源:`centercourseware.sflep.com` Aliyun OSS CDN,公开,无认证/无 SMS/无激活门。
 * 协议参考 welearn_probe_answer.py + welearn_probe_real_submit.py 实测 (2026-06-28)。
 *
 * 端到端:
 *   1. POST /Ajax/SCO.aspx?action=scoAddr&cid=&scoid= → {addr: "//centercourseware.sflep.com/.../index.html#/1/1-1-3|..."}
 *   2. 解析 hash 路由(1/1-1-3)→ data file: https://centercourseware.sflep.com/{COURSE}/data/1/1-1-3.html
 *   3. GET HTML,Jsoup 解析 <et-blank> + <et-choice key="..."> 拿答案
 *
 * 返回结构化 [ScoAnswers],供 WeLearnStudyRepository 构造 cmi.interactions。
 */
class WeLearnAnswerRepository(
    private val authRepo: WeLearnAuthRepository,
) {
    companion object {
        private const val TAG = "WeLearnAns"
        private const val CW_BASE = "https://centercourseware.sflep.com"
    }

    /**
     * 已知 course path(从 StudyRepository.tree.units[].scoAddr 拿)和 sco 列表时,
     * 一次性批量拉所有 sco 的答案。失败/无数据返回空。
     *
     * @param answersMap 累积容器,key=scoid,value=答案
     */
    suspend fun fetchAnswersForCourse(
        scoAddrs: List<Pair<String /*scoid*/, String /*addr*/>>,
    ): Map<String, ScoAnswers> = withContext(Dispatchers.IO) {
        val out = mutableMapOf<String, ScoAnswers>()
        for ((scoid, addr) in scoAddrs) {
            val answers = fetchAnswersForSco(addr)
            if (answers != null) out[scoid] = answers
        }
        out
    }

    /**
     * 拉单个 sco 的答案。
     * @param addr scoAddr 接口返回的 addr 字段(形如 `//centercourseware.sflep.com/.../index.html#/1/1-1-3|Unit 1 ...`)
     * @return 解析失败/无答案时返回 null
     */
    suspend fun fetchAnswersForSco(addr: String): ScoAnswers? = withContext(Dispatchers.IO) {
        try {
            val hashPart = extractHashFromAddr(addr) ?: return@withContext null
            val coursePath = extractCoursePathFromAddr(addr) ?: return@withContext null
            val url = "$CW_BASE/${java.net.URLEncoder.encode(coursePath, "UTF-8").replace("+", "%20")}/data/$hashPart.html"
            val req = Request.Builder().url(url).build()
            val resp = authRepo.client.newCall(req).execute()
            val html: String? = resp.use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
            if (html == null) return@withContext null
            parseAnswers(html)
        } catch (e: Exception) {
            Log.w(TAG, "fetchAnswersForSco 失败: ${e.message}")
            null
        }
    }

    /** 从 addr 抠出 hash 部分(如 "1/1-1-3") */
    internal fun extractHashFromAddr(addr: String): String? {
        val hashIdx = addr.indexOf("#/")
        if (hashIdx < 0) return null
        val raw = addr.substring(hashIdx + 2)
        // 去掉 | 后面的 location 描述
        return raw.substringBefore("|")
    }

    /** 从 addr 抠出 course path(如 "New College English Viewing Listening Speaking 3") */
    internal fun extractCoursePathFromAddr(addr: String): String? {
        // 形如 //centercourseware.sflep.com/{COURSE_PATH}/index.html#/...
        val noProto = if (addr.startsWith("//")) "https:$addr" else addr
        val parts = noProto.split("/")
        // parts[0]=https:  parts[1]=  parts[2]=centercourseware.sflep.com  parts[3]={COURSE}
        return if (parts.size > 3) parts[3] else null
    }

    /** 解析 et-blank + et-choice HTML → ScoAnswers */
    internal fun parseAnswers(html: String): ScoAnswers? {
        val doc = Jsoup.parse(html)
        val blanks = doc.select("et-blank").mapNotNull { it.text().takeIf { t -> t.isNotBlank() } }
        val choices = doc.select("et-choice").mapNotNull {
            val key = it.attr("key")
            if (key.isBlank()) null else key
        }
        if (blanks.isEmpty() && choices.isEmpty()) return null
        return ScoAnswers(blanks = blanks, choices = choices)
    }
}

/** sco 的答案结构(填空 + 选择) */
data class ScoAnswers(
    val blanks: List<String>,   // 填空答案(按页面顺序)
    val choices: List<String>,  // 选择答案(按页面顺序,如 "a" / "2,4" / "b")
) {
    fun isEmpty() = blanks.isEmpty() && choices.isEmpty()
    fun total() = blanks.size + choices.size
}
