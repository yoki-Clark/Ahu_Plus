package com.yourname.ahu_plus.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.CacheSizeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地缓存清理仓库。
 *
 * **设计原则**:不动登录态、不动用户设置、不动超星凭据。
 * 只清理 DataStore 里的"业务数据 JSON + 应用运行痕迹"以及 download 目录里的 APK 文件。
 *
 * 分组 ID 与 [CacheCleanupScreen] 中展示的 8 类一一对应。
 */
class CacheCleanupRepository(
    private val appDataStore: AppDataStore,
    private val appContext: Context
) {
    /**
     * 分组 → DataStore 字符串 key 列表。
     * 计算大小时:把这些 key 的 stringValue UTF-8 字节数累加;清理时:把它们从 DataStore 移除。
     */
    private val groupKeys: Map<String, List<Preferences.Key<String>>> = mapOf(
        // 教务:课表 / 成绩 / GPA / 考试 / 培养方案 / 空教室
        GROUP_ACADEMIC to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.SCHEDULE_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.GRADES_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.GPA_METADATA_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.EXAMS_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.TRAINING_PLAN_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.EMPTY_CLASSROOM_JSON_KEY,
        ),
        // 学生信息(一张表)
        GROUP_STUDENT_INFO to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.STUDENT_INFO_KEY,
        ),
        // 财务 / 考勤 / 一卡通账单
        GROUP_FINANCE_ATTENDANCE to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.FINANCE_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.ATTENDANCE_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.KQCARD_ATTENDANCE_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.BILLS_JSON_KEY,
        ),
        // 校园服务:评教 / 作业 / 课表备注 / 自定义任务 / 浴室电话 / 电费配置 / 支付码缓存
        GROUP_CAMPUS_SERVICES to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.ASSESSMENT_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.RECORD_INDEX_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.HOMEWORK_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.USER_TASKS_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.USER_SCHEDULE_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.ADWMH_QR_PAYLOAD_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.ADWMH_QR_SERVER_TEXT_KEY,
        ),
        // 超星:业务缓存(登录态/凭据在 AUTH_DATA,不进 ALL_CLEARABLE_KEYS,不被此清理触及)
        GROUP_CHAOXING to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.CX_COURSES_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_COURSES_PROGRESS_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_HOMEWORK_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_HOMEWORK_DETAIL_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_MESSAGES_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_TASK_LOG_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.CX_TIKU_CACHE_KEY,
        ),
        // 公开数据:排考预测 / 天气 / 开发者公告
        GROUP_PUBLIC_DATA to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.EXAM_PREDICTIONS_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.WEATHER_JSON_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.ANNOUNCEMENTS_JSON_KEY,
        ),
        // 应用记录:最近使用应用 + 已忽略公告 id
        GROUP_APP_RECORDS to listOf(
            com.yourname.ahu_plus.data.local.SessionManager.RECENT_APPS_KEY,
            com.yourname.ahu_plus.data.local.SessionManager.DISMISSED_ANNOUNCEMENT_IDS_KEY,
        ),
        // download 文件由文件系统独立处理,这里只占位
        GROUP_DOWNLOAD to emptyList(),
    )

    /** 计算 8 个分组的字节大小(不含 download 分组,后者按 APK 文件计)。 */
    suspend fun calculate(): CacheSizeInfo = withContext(Dispatchers.IO) {
        val prefs = appDataStore.dataStore.data.first()
        val sizes = mutableMapOf<String, Long>()
        for ((groupId, keys) in groupKeys) {
            if (groupId == GROUP_DOWNLOAD) continue
            var sum = 0L
            for (k in keys) {
                sum += prefs[k]?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
            }
            sizes[groupId] = sum
        }
        CacheSizeInfo(sizes)
    }

    /** download 目录里 APK 文件总大小和数量。 */
    fun downloadApkSize(): Pair<Long, Int> {
        var totalSize = 0L
        var count = 0
        for (root in apkRoots()) {
            root.listFiles()?.asSequence()
                ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                ?.forEach {
                    totalSize += it.length()
                    count++
                }
        }
        return totalSize to count
    }

    /** 清理指定分组。download 分组走文件系统;其余走 DataStore。 */
    suspend fun clearGroups(groupIds: List<String>) = withContext(Dispatchers.IO) {
        val distinct = groupIds.toSet()
        if (distinct.isEmpty()) return@withContext

        if (GROUP_DOWNLOAD in distinct) {
            deleteDownloadApks()
        }

        val keysToRemove = distinct
            .filter { it != GROUP_DOWNLOAD }
            .flatMap { groupKeys[it].orEmpty() }

        if (keysToRemove.isNotEmpty()) {
            appDataStore.dataStore.edit { prefs ->
                keysToRemove.forEach { prefs.remove(it) }
            }
        }
    }

    private fun apkRoots(): List<File> = buildList {
        add(File(appContext.filesDir, "download"))
        appContext.getExternalFilesDir(null)?.let { add(File(it, "download")) }
    }

    private fun deleteDownloadApks() {
        for (root in apkRoots()) {
            root.listFiles()?.asSequence()
                ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                ?.forEach { it.delete() }
        }
    }

    companion object {
        const val GROUP_ACADEMIC = "academic"
        const val GROUP_STUDENT_INFO = "student_info"
        const val GROUP_FINANCE_ATTENDANCE = "finance_attendance"
        const val GROUP_CAMPUS_SERVICES = "campus_services"
        const val GROUP_CHAOXING = "chaoxing"
        const val GROUP_PUBLIC_DATA = "public_data"
        const val GROUP_APP_RECORDS = "app_records"
        const val GROUP_DOWNLOAD = "download"
    }
}