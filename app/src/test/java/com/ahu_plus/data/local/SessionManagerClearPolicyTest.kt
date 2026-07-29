package com.ahu_plus.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerClearPolicyTest {
    @Test
    fun `account logout preserves user assets`() {
        val userAssetKeys = setOf(
            SessionManager.USER_SCHEDULE_JSON_KEY,
            SessionManager.HOMEWORK_JSON_KEY,
            SessionManager.HOMEWORK_UPDATED_AT_KEY,
            SessionManager.USER_TASKS_JSON_KEY,
            SessionManager.USER_TASKS_UPDATED_AT_KEY,
        )

        assertTrue(SessionManager.AUTH_DATA_KEYS.intersect(userAssetKeys).isEmpty())
        assertTrue(SessionManager.ALL_CLEARABLE_KEYS.containsAll(userAssetKeys))
    }

    @Test
    fun `account memory cleanup excludes user assets while clear all includes them`() {
        val source = File("src/main/java/com/ahu_plus/data/local/SessionManager.kt").readText()
        val accountCleanup = source
            .substringAfter("private fun clearCachedAuthData()")
            .substringBefore("private fun clearCachedUserAssets()")
        val clearAll = source
            .substringAfter("suspend fun clearAll()")
            .substringBefore("private fun clearCachedAuthData()")

        assertFalse(accountCleanup.contains("cachedUserScheduleJson = null"))
        assertFalse(accountCleanup.contains("cachedHomeworkJson = null"))
        assertFalse(accountCleanup.contains("cachedUserTasksJson = null"))
        assertTrue(clearAll.contains("clearCachedUserAssets()"))
    }

    @Test
    fun `avatar mode is account-scoped and cleared on logout`() {
        // avatar_mode 与 avatar_url 一致:账号数据,退登清理(退登后头像相关数据全部清理)
        assertTrue(SessionManager.AUTH_DATA_KEYS.contains(SessionManager.AVATAR_MODE_KEY))

        val source = File("src/main/java/com/ahu_plus/data/local/SessionManager.kt").readText()
        val accountCleanup = source
            .substringAfter("private fun clearCachedAuthData()")
            .substringBefore("private fun clearCachedUserAssets()")
        assertTrue(
            "clearCachedAuthData 应将 cachedAvatarMode 重置为 DEFAULT",
            accountCleanup.contains("cachedAvatarMode = AvatarMode.DEFAULT")
        )
    }
}
