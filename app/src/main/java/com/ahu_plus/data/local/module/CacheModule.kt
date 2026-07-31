package com.ahu_plus.data.local.module

import kotlinx.coroutines.flow.Flow

/**
 * 缓存模块：管理业务数据缓存，按 domain 隔离，schema 版本化。
 *
 * ponytail: generation 检查防跨账号污染，schema 版本支持数据迁移。
 */
interface CacheModule {

    // ── Schema 版本 ──────────────────────────────────────
    /**
     * 当前缓存 schema 版本。修改缓存结构时递增。
     * 用于触发 MigrationRegistry 中的缓存迁移逻辑。
     */
    companion object {
        const val SCHEMA_VERSION = 1
    }

    // ── 课表缓存 ──────────────────────────────────────
    data class ScheduleCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getScheduleCache(): ScheduleCache?
    suspend fun saveScheduleCache(json: String, generation: Long)
    suspend fun clearScheduleCache()

    // ── 成绩缓存 ──────────────────────────────────────
    data class GradesCache(
        val json: String,
        val gpaMetadataJson: String?,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getGradesCache(): GradesCache?
    suspend fun saveGradesCache(json: String, gpaMetadataJson: String?, generation: Long)
    suspend fun clearGradesCache()

    // ── 考试缓存 ──────────────────────────────────────
    data class ExamsCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getExamsCache(): ExamsCache?
    suspend fun saveExamsCache(json: String, generation: Long)
    suspend fun clearExamsCache()

    // ── 学生信息缓存 ──────────────────────────────────
    data class StudentInfoCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getStudentInfoCache(): StudentInfoCache?
    suspend fun saveStudentInfoCache(json: String, generation: Long)
    suspend fun clearStudentInfoCache()

    // ── 一卡通账单缓存 ──────────────────────────────────
    data class BillsCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getBillsCache(): BillsCache?
    suspend fun saveBillsCache(json: String, generation: Long)
    suspend fun clearBillsCache()

    // ── 支付码缓存 ──────────────────────────────────────
    data class QrCodeCache(
        /** Only populated in memory. Persisted metadata keeps this empty. */
        // payload/serverText 必须可空:Gson 绕过 Kotlin 构造器(Unsafe)反序列化旧缓存 JSON,
        // 字段缺失/为 null 时运行时就是 null;声明非空会被 R8 按类型删掉空检查,release 下闪退。
        val payload: String?,
        val serverText: String?,
        val fetchedAt: Long,
        val generation: Long
    )

    suspend fun getQrCodeCache(): QrCodeCache?
    suspend fun saveQrCodeCache(payload: String, serverText: String, fetchedAt: Long, generation: Long)
    suspend fun clearQrCodeCache()

    // ── 空教室缓存 ──────────────────────────────────────
    data class EmptyClassroomsCache(
        val json: String,
        val updatedAt: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getEmptyClassroomsCache(): EmptyClassroomsCache?
    suspend fun saveEmptyClassroomsCache(json: String)
    suspend fun clearEmptyClassroomsCache()

    // ── 楼层信息缓存 ──────────────────────────────────────
    data class BuildingFloorsCache(
        val json: String,
        val updatedAt: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getBuildingFloorsCache(): BuildingFloorsCache?
    suspend fun saveBuildingFloorsCache(json: String)
    suspend fun clearBuildingFloorsCache()

    // ── WeLearn 课程缓存 ──────────────────────────────────
    data class WeLearnCoursesCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getWeLearnCoursesCache(): WeLearnCoursesCache?
    suspend fun saveWeLearnCoursesCache(json: String, generation: Long)
    suspend fun clearWeLearnCoursesCache()

    // ── 超星学习通课程缓存 ──────────────────────────────────
    data class ChaoxingCoursesCache(
        val json: String,
        val updatedAt: Long,
        val generation: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getChaoxingCoursesCache(): ChaoxingCoursesCache?
    suspend fun saveChaoxingCoursesCache(json: String, generation: Long)
    suspend fun clearChaoxingCoursesCache()

    // ── 天气缓存 ──────────────────────────────────────
    data class WeatherCache(
        val json: String,
        val updatedAt: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getWeatherCache(): WeatherCache?
    suspend fun saveWeatherCache(json: String)
    suspend fun clearWeatherCache()

    // ── 开发者公告缓存 ──────────────────────────────────
    data class AnnouncementsCache(
        val json: String,
        val fetchedAt: Long,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getAnnouncementsCache(): AnnouncementsCache?
    suspend fun saveAnnouncementsCache(json: String)
    suspend fun clearAnnouncementsCache()

    // ── 排考预测缓存 ──────────────────────────────────
    data class ExamPredictionsCache(
        val json: String,
        val schemaVersion: Int = SCHEMA_VERSION
    )

    suspend fun getExamPredictionsCache(): ExamPredictionsCache?
    suspend fun saveExamPredictionsCache(json: String)
    suspend fun clearExamPredictionsCache()

    // ── 评教 JWT 缓存 ──────────────────────────────────
    data class EvaluationJwtCache(
        val jwt: String,
        val generation: Long
    )

    suspend fun getEvaluationJwtCache(): EvaluationJwtCache?
    suspend fun saveEvaluationJwtCache(jwt: String, generation: Long)
    suspend fun clearEvaluationJwtCache()

    // ── 批量清理 ──────────────────────────────────────
    suspend fun clearAccountScopedCaches()
    suspend fun clearAllCaches()
}
