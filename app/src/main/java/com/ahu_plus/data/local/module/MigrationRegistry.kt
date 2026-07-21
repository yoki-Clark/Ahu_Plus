package com.ahu_plus.data.local.module

/**
 * 迁移注册表：版本化数据迁移。
 *
 * ponytail: 幂等迁移，支持中断恢复，避免重复执行。
 */
interface MigrationRegistry {

    /**
     * 当前数据版本。每次发布修改数据结构时递增。
     */
    companion object {
        const val CURRENT_VERSION = 1  // 初始版本，M2 实施后为 2
    }

    /**
     * 执行所有待执行迁移（从上次版本到当前版本）。
     *
     * @return Result.success(执行的迁移数) 或 Result.failure(exception)
     */
    suspend fun runPendingMigrations(): Result<Int>

    /**
     * 获取当前已迁移到的版本号。
     */
    suspend fun getCurrentMigratedVersion(): Int

    /**
     * 手动标记迁移完成（测试/恢复用）。
     */
    suspend fun markMigrationComplete(version: Int)

    /**
     * 迁移定义。
     */
    interface Migration {
        val fromVersion: Int
        val toVersion: Int
        val description: String

        /**
         * 执行迁移。必须幂等（可重复执行）。
         *
         * @return Result.success(Unit) 或 Result.failure(exception)
         */
        suspend fun migrate(): Result<Unit>

        /**
         * 回滚迁移（可选，用于测试）。
         */
        suspend fun rollback(): Result<Unit> = Result.success(Unit)
    }

    /**
     * 注册的迁移列表（按版本顺序）。
     */
    fun getMigrations(): List<Migration>
}

/**
 * 内置迁移清单。
 */
object BuiltInMigrations {

    /**
     * M2 迁移：SessionManager 150+ key → 5 个 Module。
     */
    class M2Migration(
        private val accountStateModule: AccountStateModule,
        private val settingsModule: SettingsModule,
        private val cacheModule: CacheModule,
        // SessionManager 用于读取旧 key
        private val legacyDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    ) : MigrationRegistry.Migration {

        override val fromVersion = 1
        override val toVersion = 2
        override val description = "Migrate SessionManager to modular storage"

        override suspend fun migrate(): Result<Unit> {
            return try {
                // ponytail: 只读旧 key，写新 Module，不删旧 key（永久兼容）
                migrateCasCredentials()
                migrateThirdPartyCredentials()
                migrateSettings()
                migrateCaches()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private suspend fun migrateCasCredentials() {
            // 从 legacyDataStore 读取 username/password/sessions
            // 写入 AccountStateModule
            // 不删除旧 key
        }

        private suspend fun migrateThirdPartyCredentials() {
            // 同上，迁移第三方凭据
        }

        private suspend fun migrateSettings() {
            // 迁移 20+ 设置 key 到 SettingsModule
        }

        private suspend fun migrateCaches() {
            // 迁移业务缓存到 CacheModule，增加 generation 和 schema 字段
        }

        override suspend fun rollback(): Result<Unit> {
            // M2 迁移不支持回滚（只增加新 key，不删旧 key）
            return Result.success(Unit)
        }
    }

    /**
     * 示例：未来的 schema 升级迁移。
     */
    class CacheSchemaV2Migration(
        private val cacheModule: CacheModule
    ) : MigrationRegistry.Migration {

        override val fromVersion = 2
        override val toVersion = 3
        override val description = "Upgrade cache schema to v2"

        override suspend fun migrate(): Result<Unit> {
            // 读取所有缓存，转换格式，写回
            return Result.success(Unit)
        }
    }
}
