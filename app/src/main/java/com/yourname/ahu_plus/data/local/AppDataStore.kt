package com.yourname.ahu_plus.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局共享的 DataStore 委托。
 *
 * 从 SessionManager 抽离而来——原本 SessionManager 顶层的 `Context.dataStore`
 * 是 private 的,只能它自己用。现在提升到 internal,允许同包下的其他类
 * (例如 CourseNoteRepository) 复用同一个 Preferences 文件。
 *
 * 注意:这是进程级单例,同一进程对一个 name 只能有一个委托实例。
 */
internal val Context.ahuPlusDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "ahu_plus_prefs")

/**
 * 全局共享的 DataStore 访问层。
 *
 * 职责:
 *  - 暴露原始 DataStore 给 SessionManager 沿用旧 key (JSESSIONID / JW SESSION / 凭据 / 主题 等)
 *  - 提供课程备注存取 (key 形如 note_${lessonId})
 */
class AppDataStore(context: Context) {

    private val ds: DataStore<Preferences> = context.applicationContext.ahuPlusDataStore

    /** 暴露原始 DataStore,供 SessionManager 沿用其原有 key 体系 */
    val dataStore: DataStore<Preferences> get() = ds

    // ── 课程备注 ─────────────────────────────────────

    /** 观察某门课的备注 (默认空字符串) */
    fun noteFlow(lessonId: Long): Flow<String> = ds.data.map { prefs ->
        prefs[noteKey(lessonId)] ?: ""
    }

    /** 保存某门课的备注 */
    suspend fun saveNote(lessonId: Long, text: String) {
        ds.edit { it[noteKey(lessonId)] = text }
    }

    /** 清除某门课的备注 */
    suspend fun clearNote(lessonId: Long) {
        ds.edit { it.remove(noteKey(lessonId)) }
    }

    private fun noteKey(lessonId: Long): Preferences.Key<String> =
        stringPreferencesKey("note_${lessonId}")
}
