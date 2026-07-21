package com.ahu_plus.data.local.module

import android.content.Context
import com.ahu_plus.BuildConfig
import com.ahu_plus.data.diagnostic.SafeLog as Log
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * UserAssetModule 实现。
 *
 * ponytail: ZIP 容器，单 JSON 文件，敏感信息扫描。
 */
class UserAssetModuleImpl(
    private val context: Context,
    private val settingsModule: SettingsModule,
    private val gson: Gson
) : UserAssetModule {

    companion object {
        private const val TAG = "UserAssetModule"
        private const val ASSETS_JSON = "assets.json"

        // 敏感关键词列表
        private val SENSITIVE_KEYWORDS = listOf(
            "password", "token", "jwt", "session", "cookie", "secret",
            "bearer", "authorization", "credential", "jsessionid", "castgc"
        )
    }

    override suspend fun exportAssets(outputFile: File): Result<File> = runCatching {
        Log.i(TAG, "Exporting assets to: ${outputFile.absolutePath}")

        val assets = UserAssetModule.UserAssets(
            themeSettings = settingsModule.getThemeSettings(),
            scheduleDisplaySettings = settingsModule.getScheduleDisplaySettings(),
            courseReminderSettings = settingsModule.getCourseReminderSettings(),
            agendaSettings = settingsModule.getAgendaSettings(),
            homeAppSettings = settingsModule.getHomeAppSettings(),
            evaluationSettings = settingsModule.getEvaluationSettings(),
            onboardingFlags = settingsModule.getOnboardingFlags(),
            favoriteApps = settingsModule.getHomeAppSettings().favoriteIds,
            recentApps = settingsModule.getHomeAppSettings().recentApps,
            evaluationComments = settingsModule.getEvaluationSettings().commentOptions,
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            backupVersion = UserAssetModule.BACKUP_VERSION
        )

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(ASSETS_JSON))
            val json = gson.toJson(assets)
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        Log.i(TAG, "Export successful, size: ${outputFile.length()} bytes")
        outputFile
    }

    override suspend fun importAssets(
        inputFile: File,
        conflictStrategy: UserAssetModule.ConflictStrategy
    ): Result<Int> = runCatching {
        Log.i(TAG, "Importing assets from: ${inputFile.absolutePath}, strategy: $conflictStrategy")

        val json = ZipFile(inputFile).use { zip ->
            val entry = zip.getEntry(ASSETS_JSON)
                ?: throw IllegalArgumentException("Invalid backup: missing $ASSETS_JSON")
            zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        }

        val assets = gson.fromJson(json, UserAssetModule.UserAssets::class.java)
            ?: throw IllegalArgumentException("Invalid backup: malformed JSON")

        if (assets.backupVersion > UserAssetModule.BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version ${assets.backupVersion} is newer than supported ${UserAssetModule.BACKUP_VERSION}")
        }

        var importedCount = 0

        when (conflictStrategy) {
            UserAssetModule.ConflictStrategy.REPLACE -> {
                // 清空现有设置（除引导标记外）
                settingsModule.saveThemeSettings(assets.themeSettings ?: SettingsModule.ThemeSettings())
                importedCount++
            }
            UserAssetModule.ConflictStrategy.MERGE -> {
                // 合并，导入优先
                if (assets.themeSettings != null) {
                    settingsModule.saveThemeSettings(assets.themeSettings)
                    importedCount++
                }
            }
        }

        // 其他设置（总是导入）
        assets.scheduleDisplaySettings?.let {
            settingsModule.saveScheduleDisplaySettings(it)
            importedCount++
        }
        assets.courseReminderSettings?.let {
            settingsModule.saveCourseReminderSettings(it)
            importedCount++
        }
        assets.agendaSettings?.let {
            settingsModule.saveAgendaSettings(it)
            importedCount++
        }
        assets.homeAppSettings?.let {
            settingsModule.saveHomeAppSettings(it)
            importedCount++
        }
        assets.evaluationSettings?.let {
            settingsModule.saveEvaluationSettings(it)
            importedCount++
        }

        Log.i(TAG, "Import successful, imported $importedCount items")
        importedCount
    }

    override suspend fun validateBackup(inputFile: File): Result<UserAssetModule.BackupMetadata> = runCatching {
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Backup file not found")
        }

        val json = ZipFile(inputFile).use { zip ->
            val entry = zip.getEntry(ASSETS_JSON)
                ?: throw IllegalArgumentException("Invalid backup: missing $ASSETS_JSON")
            zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        }

        val assets = gson.fromJson(json, UserAssetModule.UserAssets::class.java)
            ?: throw IllegalArgumentException("Invalid backup: malformed JSON")

        var assetCount = 0
        if (assets.themeSettings != null) assetCount++
        if (assets.scheduleDisplaySettings != null) assetCount++
        if (assets.courseReminderSettings != null) assetCount++
        if (assets.agendaSettings != null) assetCount++
        if (assets.homeAppSettings != null) assetCount++
        if (assets.evaluationSettings != null) assetCount++
        if (assets.onboardingFlags != null) assetCount++

        UserAssetModule.BackupMetadata(
            backupVersion = assets.backupVersion,
            appVersion = assets.appVersion,
            exportedAt = assets.exportedAt,
            assetCount = assetCount
        )
    }

    override suspend fun auditBackupSecurity(inputFile: File): Result<UserAssetModule.SecurityAuditReport> = runCatching {
        val json = ZipFile(inputFile).use { zip ->
            val entry = zip.getEntry(ASSETS_JSON)
                ?: throw IllegalArgumentException("Invalid backup: missing $ASSETS_JSON")
            zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        }

        val lowerJson = json.lowercase()
        val foundSensitiveKeys = SENSITIVE_KEYWORDS.filter { keyword ->
            lowerJson.contains(keyword)
        }

        if (foundSensitiveKeys.isNotEmpty()) {
            UserAssetModule.SecurityAuditReport(
                hasSensitiveData = true,
                sensitiveKeys = foundSensitiveKeys,
                message = "Backup contains sensitive keywords: ${foundSensitiveKeys.joinToString()}"
            )
        } else {
            UserAssetModule.SecurityAuditReport(
                hasSensitiveData = false,
                message = "No sensitive data detected"
            )
        }
    }
}
