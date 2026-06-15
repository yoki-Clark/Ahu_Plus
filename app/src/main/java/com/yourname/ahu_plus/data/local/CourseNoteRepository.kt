package com.yourname.ahu_plus.data.local

import kotlinx.coroutines.flow.Flow

/**
 * 课程备注仓库。
 *
 * 提供对课程备注的观察/保存/删除能力,底层使用 [AppDataStore] 持久化。
 */
class CourseNoteRepository(private val appDataStore: AppDataStore) {

    /** 观察指定课程的备注 (空字符串表示无备注) */
    fun observeNote(lessonId: Long): Flow<String> =
        appDataStore.noteFlow(lessonId)

    /** 保存备注。自动 trim 前后空白。 */
    suspend fun saveNote(lessonId: Long, text: String) {
        appDataStore.saveNote(lessonId, text.trim())
    }

    /** 清除指定课程的备注 */
    suspend fun clearNote(lessonId: Long) {
        appDataStore.clearNote(lessonId)
    }
}
