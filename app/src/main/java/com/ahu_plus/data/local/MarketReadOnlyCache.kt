package com.ahu_plus.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.model.MarketReadOnlyCacheEntry
import com.ahu_plus.data.model.MarketTopic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 集市只读流的本地累积缓存。
 *
 * 这里把服务器索引分页拉到的帖按 `topic_id` 并入本地缓存，UI 始终展示本地
 * 可回放内容并按发布时间倒序。
 *
 * 纯 JVM 可测:只依赖 [AppDataStore] 与标准库,不触及 Android UI。
 */
class MarketReadOnlyCache(
    private val appDataStore: AppDataStore,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val gson = GsonProvider.instance
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @Volatile private var entries: List<MarketReadOnlyCacheEntry> = emptyList()
    @Volatile private var loaded = false
    private val mutex = Mutex()

    /** 首次访问时从 DataStore 读取;已加载则直接返回内存快照。 */
    suspend fun load(): List<MarketReadOnlyCacheEntry> {
        if (loaded) return entries
        return mutex.withLock {
            if (loaded) return@withLock entries
            val json = appDataStore.dataStore.data.first()[KEY] ?: ""
            entries = parse(json).also { loaded = true }
            entries
        }
    }

    /** 同步快照(未加载时返回空,调用方应先 [load])。 */
    fun snapshot(): List<MarketReadOnlyCacheEntry> = entries

    /**
     * 把新拉到的 `(topic, label)` 并入缓存:同 id 覆盖(拿到更新的互动数),
     * 按发布时间倒序,超过 [MAX_ENTRIES] 裁剪最旧,超过 [TTL_DAYS] 的过期剔除。
     * 持久化后返回最新全集。
     */
    suspend fun merge(
        fresh: List<Pair<MarketTopic, String>>,
    ): List<MarketReadOnlyCacheEntry> = mutex.withLock {
        val kept = mergeEntries(entries, fresh, nowProvider())
        entries = kept
        persist()
        kept
    }

    suspend fun clear() = mutex.withLock {
        entries = emptyList()
        appDataStore.dataStore.edit { it.remove(KEY) }
    }

    private suspend fun persist() {
        val json = gson.toJson(entries)
        appDataStore.dataStore.edit { it[KEY] = json }
    }

    private fun parse(json: String): List<MarketReadOnlyCacheEntry> {
        if (json.isBlank()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<MarketReadOnlyCacheEntry>>() {}.type
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson<List<MarketReadOnlyCacheEntry>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 解析 `createTime`("yyyy-MM-dd HH:mm:ss")为毫秒;失败返回 0L(视为最旧,不保留)。 */
    internal fun parseCreateTimeMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching { timeFormat.parse(raw)?.time ?: 0L }.getOrDefault(0L)
    }

    companion object {
        private val KEY = stringPreferencesKey("market_readonly_cache_v2")
        const val MAX_ENTRIES = 500
        const val TTL_DAYS = 30L
        private val TTL_MS = TimeUnit.DAYS.toMillis(TTL_DAYS)

        /**
         * 纯函数:把 [existing] 与 [fresh] 按 `topic_id` 合并去重(同 id 覆盖,
         * 保留已缓存标签),剔除超过 30 天的旧帖和无效时间戳的帖子,按发布时间倒序,
         * 裁剪到 [MAX_ENTRIES]。不触碰存储,便于纯 JVM 测试。
         */
        fun mergeEntries(
            existing: List<MarketReadOnlyCacheEntry>,
            fresh: List<Pair<MarketTopic, String>>,
            nowMs: Long,
        ): List<MarketReadOnlyCacheEntry> {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            fun ms(raw: String): Long {
                if (raw.isBlank()) return 0L
                return runCatching { parser.parse(raw)?.time ?: 0L }.getOrDefault(0L)
            }
            val byId = LinkedHashMap<Long, MarketReadOnlyCacheEntry>()
            existing.forEach { byId[it.topic.id] = it }

            // Filter fresh topics for basic integrity: valid ID, valid timestamp
            val validFresh = fresh.filter { (topic, _) ->
                val createMs = ms(topic.createTime)
                topic.id > 0L &&
                createMs > 0L &&
                createMs <= nowMs + 86400_000L  // Not more than 1 day in future
            }

            validFresh.forEach { (topic, label) ->
                val cached = byId[topic.id]
                val resolvedLabel = label.ifBlank { cached?.label ?: "" }
                byId[topic.id] = MarketReadOnlyCacheEntry(topic = topic, label = resolvedLabel)
            }
            val cutoff = nowMs - TTL_MS
            return byId.values
                .filter { ms(it.topic.createTime) >= cutoff }
                .sortedByDescending { ms(it.topic.createTime) }
                .take(MAX_ENTRIES)
        }
    }
}
