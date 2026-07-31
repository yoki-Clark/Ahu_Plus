package com.ahu_plus.data.local

/**
 * 首页快捷栏的展示模式。
 *
 * - [RECENT]:展示"最近使用"应用横滑轨(按使用时间倒序)。
 * - [FAVORITE]:展示"收藏应用"网格(用户显式收藏,可拖拽排序)。
 *
 * 默认 [FAVORITE]。持久化到 DataStore,退登(clearAuthData)保留,clearAll 清理。
 */
enum class HomeDockMode(val storageValue: String) {
    RECENT("recent"),
    FAVORITE("favorite");

    companion object {
        val DEFAULT: HomeDockMode = FAVORITE

        fun fromStorageValue(value: String?): HomeDockMode {
            return entries.firstOrNull { it.storageValue == value } ?: DEFAULT
        }
    }
}
