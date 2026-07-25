package com.ahu_plus.data.local.module

import java.io.File

/**
 * 用户资产模块：备份导出/导入。
 *
 * ponytail: 只导出用户可转移资产，严格排除敏感凭据。
 */
interface UserAssetModule {

    /**
     * 用户可导出资产清单。
     */
    data class UserAssets(
        // ── 设置 ──────────────────────────────────────
        val themeSettings: SettingsModule.ThemeSettings? = null,
        val scheduleDisplaySettings: SettingsModule.ScheduleDisplaySettings? = null,
        val courseReminderSettings: SettingsModule.CourseReminderSettings? = null,
        val agendaSettings: SettingsModule.AgendaSettings? = null,
        val homeAppSettings: SettingsModule.HomeAppSettings? = null,
        val evaluationSettings: SettingsModule.EvaluationSettings? = null,
        val onboardingFlags: SettingsModule.OnboardingFlags? = null,

        // ── 非敏感缓存（可选）──────────────────────────
        val favoriteApps: List<String>? = null,  // 收藏的应用
        val recentApps: List<String>? = null,    // 最近使用应用
        val evaluationComments: List<String>? = null,  // 评教常用评语

        // ── 元数据 ──────────────────────────────────
        val exportedAt: Long = System.currentTimeMillis(),
        val appVersion: String = "",
        val backupVersion: Int = BACKUP_VERSION
    )

    companion object {
        /**
         * 备份格式版本。修改 UserAssets 结构时递增。
         */
        const val BACKUP_VERSION = 1

        /**
         * 备份文件扩展名。
         */
        const val BACKUP_EXTENSION = ".ahuplus-backup"
    }

    /**
     * 导出用户资产到 ZIP 文件。
     *
     * @param outputFile 输出文件路径（由调用方选择，通常在 Downloads）
     * @return Result.success(file) 或 Result.failure(exception)
     */
    suspend fun exportAssets(outputFile: File): Result<File>

    /**
     * 从备份文件导入用户资产。
     *
     * @param inputFile 备份文件路径
     * @param conflictStrategy 冲突策略：MERGE（合并，新优先）或 REPLACE（完全替换）
     * @return Result.success(导入的资产数量) 或 Result.failure(exception)
     */
    suspend fun importAssets(
        inputFile: File,
        conflictStrategy: ConflictStrategy = ConflictStrategy.MERGE
    ): Result<Int>

    enum class ConflictStrategy {
        /**
         * 合并：导入的设置与现有设置合并，导入优先。
         */
        MERGE,

        /**
         * 替换：导入的设置完全覆盖现有设置（清空后再导入）。
         */
        REPLACE
    }

    /**
     * 验证备份文件是否有效。
     *
     * @return Result.success(备份元数据) 或 Result.failure(exception)
     */
    suspend fun validateBackup(inputFile: File): Result<BackupMetadata>

    data class BackupMetadata(
        val backupVersion: Int,
        val appVersion: String,
        val exportedAt: Long,
        val assetCount: Int  // 包含的资产项数量
    )

    /**
     * 检查备份文件是否包含敏感信息（安全审计用）。
     *
     * ponytail: 备份中不应有密码、token、session，此方法用于自动化测试验证。
     */
    suspend fun auditBackupSecurity(inputFile: File): Result<SecurityAuditReport>

    data class SecurityAuditReport(
        val hasSensitiveData: Boolean,
        val sensitiveKeys: List<String> = emptyList(),
        val message: String = ""
    )
}
