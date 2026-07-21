package com.ahu_plus.data.local.module

import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.AppDataStore
import com.ahu_plus.data.local.EncryptedCredentialStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

/**
 * CacheModule 实现。
 *
 * ponytail: generation 检查防跨账号污染，schema 版本化支持迁移。
 * 新写入包含 generation + schema，读取兼容旧 key。
 */
class CacheModuleImpl(
    private val appDataStore: AppDataStore,
    private val credentialStore: EncryptedCredentialStore,
    private val accountStateModule: AccountStateModule
) : CacheModule {

    private val gson = GsonProvider.instance

    companion object {
        private const val TAG = "CacheModule"

        // 新 key（包含 generation + schema）
        private val SCHEDULE_CACHE_KEY = stringPreferencesKey("cache_schedule_json")
        private val GRADES_CACHE_KEY = stringPreferencesKey("cache_grades_json")
        private val EXAMS_CACHE_KEY = stringPreferencesKey("cache_exams_json")
        private val STUDENT_INFO_CACHE_KEY = stringPreferencesKey("cache_student_info_json")
        private val BILLS_CACHE_KEY = stringPreferencesKey("cache_bills_json")
        private val QR_CODE_CACHE_KEY = stringPreferencesKey("cache_qr_code_json")
        private val EMPTY_CLASSROOMS_CACHE_KEY = stringPreferencesKey("cache_empty_classrooms_json")
        private val BUILDING_FLOORS_CACHE_KEY = stringPreferencesKey("cache_building_floors_json")
        private val WELEARN_COURSES_CACHE_KEY = stringPreferencesKey("cache_welearn_courses_json")
        private val CHAOXING_COURSES_CACHE_KEY = stringPreferencesKey("cache_chaoxing_courses_json")
        private val WEATHER_CACHE_KEY = stringPreferencesKey("cache_weather_json")
        private val ANNOUNCEMENTS_CACHE_KEY = stringPreferencesKey("cache_announcements_json")
        private val EXAM_PREDICTIONS_CACHE_KEY = stringPreferencesKey("cache_exam_predictions_json")
        private val EVALUATION_JWT_CACHE_KEY = stringPreferencesKey("cache_evaluation_jwt_json")

        // 旧 key（兼容读取）
        private val OLD_SCHEDULE_JSON_KEY = stringPreferencesKey("schedule_json")
        private val OLD_SCHEDULE_UPDATED_AT_KEY = longPreferencesKey("schedule_updated_at")
        private val OLD_GRADES_JSON_KEY = stringPreferencesKey("grades_json")
        private val OLD_GPA_METADATA_JSON_KEY = stringPreferencesKey("gpa_metadata_json")
        private val OLD_GRADES_UPDATED_AT_KEY = longPreferencesKey("grades_updated_at")
        private val OLD_STUDENT_INFO_KEY = stringPreferencesKey("student_info")
        private val OLD_STUDENT_INFO_UPDATED_AT_KEY = longPreferencesKey("student_info_updated_at")
        private val OLD_BILLS_JSON_KEY = stringPreferencesKey("bills_json")
        private val OLD_BILLS_UPDATED_AT_KEY = longPreferencesKey("bills_updated_at")
        private val OLD_ADWMH_QR_SERVER_TEXT_KEY = stringPreferencesKey("adwmh_qr_server_text")
        private val OLD_ADWMH_QR_FETCHED_AT_KEY = longPreferencesKey("adwmh_qr_fetched_at")
        private val OLD_WELEARN_COURSES_JSON_KEY = stringPreferencesKey("welearn_courses_json")
        private val OLD_WELEARN_COURSES_UPDATED_AT_KEY = longPreferencesKey("welearn_courses_updated_at")
        private val OLD_WEATHER_JSON_KEY = stringPreferencesKey("weather_json")
        private val OLD_ANNOUNCEMENTS_JSON_KEY = stringPreferencesKey("announcements_json")
        private val OLD_EXAM_PREDICTIONS_JSON_KEY = stringPreferencesKey("exam_predictions_json")
        private val OLD_EVALUATION_JWT_KEY = stringPreferencesKey("evaluation_jwt")
    }

    // ── 课表缓存 ──────────────────────────────────────
    override suspend fun getScheduleCache(): CacheModule.ScheduleCache? {
        val prefs = appDataStore.dataStore.data.first()

        // 优先读新 key（包含 generation + schema）
        prefs[SCHEDULE_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.ScheduleCache::class.java)
        }

        // 兼容读旧 key（无 generation，可能跨账号污染，但仍返回）
        val oldJson = prefs[OLD_SCHEDULE_JSON_KEY] ?: return null
        val oldUpdatedAt = prefs[OLD_SCHEDULE_UPDATED_AT_KEY] ?: 0L
        return CacheModule.ScheduleCache(
            json = oldJson,
            updatedAt = oldUpdatedAt,
            generation = 0L,  // 旧数据无 generation
            schemaVersion = 1
        )
    }

    override suspend fun saveScheduleCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale schedule cache write (generation mismatch)")
            return
        }

        val cache = CacheModule.ScheduleCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[SCHEDULE_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearScheduleCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(SCHEDULE_CACHE_KEY)
        }
    }

    // ── 成绩缓存 ──────────────────────────────────────
    override suspend fun getGradesCache(): CacheModule.GradesCache? {
        val prefs = appDataStore.dataStore.data.first()

        prefs[GRADES_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.GradesCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_GRADES_JSON_KEY] ?: return null
        val oldGpaMetadata = prefs[OLD_GPA_METADATA_JSON_KEY]
        val oldUpdatedAt = prefs[OLD_GRADES_UPDATED_AT_KEY] ?: 0L
        return CacheModule.GradesCache(
            json = oldJson,
            gpaMetadataJson = oldGpaMetadata,
            updatedAt = oldUpdatedAt,
            generation = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveGradesCache(json: String, gpaMetadataJson: String?, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale grades cache write (generation mismatch)")
            return
        }

        val cache = CacheModule.GradesCache(
            json = json,
            gpaMetadataJson = gpaMetadataJson,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[GRADES_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearGradesCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(GRADES_CACHE_KEY)
        }
    }

    // ── 考试缓存 ──────────────────────────────────────
    override suspend fun getExamsCache(): CacheModule.ExamsCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[EXAMS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.ExamsCache::class.java)
        }
        return null
    }

    override suspend fun saveExamsCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale exams cache write")
            return
        }

        val cache = CacheModule.ExamsCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[EXAMS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearExamsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(EXAMS_CACHE_KEY)
        }
    }

    // ── 学生信息缓存 ──────────────────────────────────
    override suspend fun getStudentInfoCache(): CacheModule.StudentInfoCache? {
        val prefs = appDataStore.dataStore.data.first()

        prefs[STUDENT_INFO_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.StudentInfoCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_STUDENT_INFO_KEY] ?: return null
        val oldUpdatedAt = prefs[OLD_STUDENT_INFO_UPDATED_AT_KEY] ?: 0L
        return CacheModule.StudentInfoCache(
            json = oldJson,
            updatedAt = oldUpdatedAt,
            generation = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveStudentInfoCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale student info cache write")
            return
        }

        val cache = CacheModule.StudentInfoCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[STUDENT_INFO_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearStudentInfoCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(STUDENT_INFO_CACHE_KEY)
        }
    }

    // ── 一卡通账单缓存 ──────────────────────────────────
    override suspend fun getBillsCache(): CacheModule.BillsCache? {
        val prefs = appDataStore.dataStore.data.first()

        prefs[BILLS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.BillsCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_BILLS_JSON_KEY] ?: return null
        val oldUpdatedAt = prefs[OLD_BILLS_UPDATED_AT_KEY] ?: 0L
        return CacheModule.BillsCache(
            json = oldJson,
            updatedAt = oldUpdatedAt,
            generation = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveBillsCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale bills cache write")
            return
        }

        val cache = CacheModule.BillsCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[BILLS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearBillsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(BILLS_CACHE_KEY)
        }
    }

    // ── 支付码缓存 ──────────────────────────────────────
    override suspend fun getQrCodeCache(): CacheModule.QrCodeCache? {
        val prefs = appDataStore.dataStore.data.first()

        prefs[QR_CODE_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.QrCodeCache::class.java)
        }

        // 兼容读旧 key（payload 已迁移到加密存储）
        val oldPayload = credentialStore.getString(EncryptedCredentialStore.ADWMH_QR_PAYLOAD) ?: return null
        val oldServerText = prefs[OLD_ADWMH_QR_SERVER_TEXT_KEY] ?: ""
        val oldFetchedAt = prefs[OLD_ADWMH_QR_FETCHED_AT_KEY] ?: 0L
        return CacheModule.QrCodeCache(
            payload = oldPayload,
            serverText = oldServerText,
            fetchedAt = oldFetchedAt,
            generation = 0L
        )
    }

    override suspend fun saveQrCodeCache(payload: String, serverText: String, fetchedAt: Long, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale QR code cache write")
            return
        }

        // payload 存加密存储
        credentialStore.putString(EncryptedCredentialStore.ADWMH_QR_PAYLOAD, payload)

        // 其他元数据存 CacheModule
        val cache = CacheModule.QrCodeCache(
            payload = payload,  // 引用，实际在加密存储
            serverText = serverText,
            fetchedAt = fetchedAt,
            generation = generation
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[QR_CODE_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearQrCodeCache() {
        credentialStore.remove(EncryptedCredentialStore.ADWMH_QR_PAYLOAD)
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(QR_CODE_CACHE_KEY)
        }
    }

    // ── 空教室/楼层/天气/公告等（简化实现，完整版需补充旧 key 兼容）──

    override suspend fun getEmptyClassroomsCache(): CacheModule.EmptyClassroomsCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[EMPTY_CLASSROOMS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.EmptyClassroomsCache::class.java)
        }
        return null
    }

    override suspend fun saveEmptyClassroomsCache(json: String) {
        val cache = CacheModule.EmptyClassroomsCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            schemaVersion = CacheModule.SCHEMA_VERSION
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[EMPTY_CLASSROOMS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearEmptyClassroomsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(EMPTY_CLASSROOMS_CACHE_KEY)
        }
    }

    override suspend fun getBuildingFloorsCache(): CacheModule.BuildingFloorsCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[BUILDING_FLOORS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.BuildingFloorsCache::class.java)
        }
        return null
    }

    override suspend fun saveBuildingFloorsCache(json: String) {
        val cache = CacheModule.BuildingFloorsCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            schemaVersion = CacheModule.SCHEMA_VERSION
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[BUILDING_FLOORS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearBuildingFloorsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(BUILDING_FLOORS_CACHE_KEY)
        }
    }

    // ── WeLearn 课程缓存 ──────────────────────────────────
    override suspend fun getWeLearnCoursesCache(): CacheModule.WeLearnCoursesCache? {
        val prefs = appDataStore.dataStore.data.first()

        prefs[WELEARN_COURSES_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.WeLearnCoursesCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_WELEARN_COURSES_JSON_KEY] ?: return null
        val oldUpdatedAt = prefs[OLD_WELEARN_COURSES_UPDATED_AT_KEY] ?: 0L
        return CacheModule.WeLearnCoursesCache(
            json = oldJson,
            updatedAt = oldUpdatedAt,
            generation = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveWeLearnCoursesCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale WeLearn courses cache write")
            return
        }

        val cache = CacheModule.WeLearnCoursesCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[WELEARN_COURSES_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearWeLearnCoursesCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(WELEARN_COURSES_CACHE_KEY)
        }
    }

    // ── 超星学习通课程缓存 ──────────────────────────────────
    override suspend fun getChaoxingCoursesCache(): CacheModule.ChaoxingCoursesCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[CHAOXING_COURSES_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.ChaoxingCoursesCache::class.java)
        }
        return null
    }

    override suspend fun saveChaoxingCoursesCache(json: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale Chaoxing courses cache write")
            return
        }

        val cache = CacheModule.ChaoxingCoursesCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            generation = generation,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )

        appDataStore.dataStore.edit { prefs ->
            prefs[CHAOXING_COURSES_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearChaoxingCoursesCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(CHAOXING_COURSES_CACHE_KEY)
        }
    }

    // ── 天气缓存 ──────────────────────────────────────
    override suspend fun getWeatherCache(): CacheModule.WeatherCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[WEATHER_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.WeatherCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_WEATHER_JSON_KEY] ?: return null
        return CacheModule.WeatherCache(
            json = oldJson,
            updatedAt = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveWeatherCache(json: String) {
        val cache = CacheModule.WeatherCache(
            json = json,
            updatedAt = System.currentTimeMillis(),
            schemaVersion = CacheModule.SCHEMA_VERSION
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[WEATHER_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearWeatherCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(WEATHER_CACHE_KEY)
        }
    }

    // ── 公告缓存 ──────────────────────────────────────
    override suspend fun getAnnouncementsCache(): CacheModule.AnnouncementsCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[ANNOUNCEMENTS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.AnnouncementsCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_ANNOUNCEMENTS_JSON_KEY] ?: return null
        return CacheModule.AnnouncementsCache(
            json = oldJson,
            fetchedAt = 0L,
            schemaVersion = 1
        )
    }

    override suspend fun saveAnnouncementsCache(json: String) {
        val cache = CacheModule.AnnouncementsCache(
            json = json,
            fetchedAt = System.currentTimeMillis(),
            schemaVersion = CacheModule.SCHEMA_VERSION
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[ANNOUNCEMENTS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearAnnouncementsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(ANNOUNCEMENTS_CACHE_KEY)
        }
    }

    // ── 排考预测缓存 ──────────────────────────────────────
    override suspend fun getExamPredictionsCache(): CacheModule.ExamPredictionsCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[EXAM_PREDICTIONS_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.ExamPredictionsCache::class.java)
        }

        // 兼容读旧 key
        val oldJson = prefs[OLD_EXAM_PREDICTIONS_JSON_KEY] ?: return null
        return CacheModule.ExamPredictionsCache(
            json = oldJson,
            schemaVersion = 1
        )
    }

    override suspend fun saveExamPredictionsCache(json: String) {
        val cache = CacheModule.ExamPredictionsCache(
            json = json,
            schemaVersion = CacheModule.SCHEMA_VERSION
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[EXAM_PREDICTIONS_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearExamPredictionsCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(EXAM_PREDICTIONS_CACHE_KEY)
        }
    }

    // ── 评教 JWT 缓存 ──────────────────────────────────────
    override suspend fun getEvaluationJwtCache(): CacheModule.EvaluationJwtCache? {
        val prefs = appDataStore.dataStore.data.first()
        prefs[EVALUATION_JWT_CACHE_KEY]?.let { json ->
            return gson.fromJson(json, CacheModule.EvaluationJwtCache::class.java)
        }

        // 兼容读旧 key
        val oldJwt = prefs[OLD_EVALUATION_JWT_KEY] ?: return null
        return CacheModule.EvaluationJwtCache(
            jwt = oldJwt,
            generation = 0L
        )
    }

    override suspend fun saveEvaluationJwtCache(jwt: String, generation: Long) {
        if (!accountStateModule.isCurrentGeneration(generation)) {
            Log.w(TAG, "Ignoring stale evaluation JWT cache write")
            return
        }

        val cache = CacheModule.EvaluationJwtCache(
            jwt = jwt,
            generation = generation
        )
        appDataStore.dataStore.edit { prefs ->
            prefs[EVALUATION_JWT_CACHE_KEY] = gson.toJson(cache)
        }
    }

    override suspend fun clearEvaluationJwtCache() {
        appDataStore.dataStore.edit { prefs ->
            prefs.remove(EVALUATION_JWT_CACHE_KEY)
        }
    }

    // ── 批量清理 ──────────────────────────────────────
    override suspend fun clearAccountScopedCaches() {
        clearScheduleCache()
        clearGradesCache()
        clearExamsCache()
        clearStudentInfoCache()
        clearBillsCache()
        clearQrCodeCache()
        clearWeLearnCoursesCache()
        clearChaoxingCoursesCache()
        clearEvaluationJwtCache()
        Log.i(TAG, "Cleared all account-scoped caches")
    }

    override suspend fun clearAllCaches() {
        clearAccountScopedCaches()
        clearEmptyClassroomsCache()
        clearBuildingFloorsCache()
        clearWeatherCache()
        clearAnnouncementsCache()
        clearExamPredictionsCache()
        Log.i(TAG, "Cleared all caches")
    }
}
