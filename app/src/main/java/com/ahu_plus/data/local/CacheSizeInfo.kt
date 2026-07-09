package com.ahu_plus.data.local

/**
 * 缓存清理分组的字节大小聚合。
 *
 * - key 为 [CacheCleanupRepository] 中定义的 8 个业务分组 ID
 * - value 为该组下所有 DataStore 字符串值字节数之和(已 UTF-8 编码)
 *
 * 用 map 而非具名字段,新增分组无需改动这里。
 */
data class CacheSizeInfo(
    val sizes: Map<String, Long> = emptyMap()
) {
    fun getSize(groupId: String): Long = sizes[groupId] ?: 0L

    val total: Long get() = sizes.values.sum()
}