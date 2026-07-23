package com.ahu_plus.data.home

import com.ahu_plus.data.GsonProvider
import com.google.gson.JsonParser

/**
 * 应用页(AppHubScreen)排版配置。
 *
 * 设计动机:应用页原本写死 2 列横向卡片 + 按分类分组。本配置让用户自定义
 * 列数 / 卡片样式 / 密度 / 分组 / 排序 / 可见性 / 自定义顺序,统一由
 * [AppRegistry.arrange] 消费,应用页渲染与设置页实时预览共用同一套逻辑。
 *
 * 持久化:JSON 序列化后存 [com.ahu_plus.data.local.SessionManager] 的
 * `app_hub_layout_json` key(退登保留,只在 clearAll 重置)。Gson 反序列化枚举
 * 用名字匹配,未知值回退默认——因此不能随意重命名枚举常量。
 *
 * 所有默认值 == 改动前的应用页现状,保证老用户视觉零变化。
 */
data class AppHubLayoutConfig(
    val columns: AppHubColumns = AppHubColumns.TWO,
    val cardStyle: AppHubCardStyle = AppHubCardStyle.HORIZONTAL,
    val density: AppHubDensity = AppHubDensity.COMFORTABLE,
    val groupMode: AppHubGroupMode = AppHubGroupMode.BY_CATEGORY,
    val sortMode: AppHubSortMode = AppHubSortMode.DEFAULT,
    /** 分组标题是否显示(仅 BY_CATEGORY 生效)。 */
    val showSectionHeaders: Boolean = true,
    /** 顶栏搜索按钮/输入框是否显示。 */
    val showSearchBar: Boolean = true,
    /**
     * 是否在应用页显示「第三方服务」区(集市/学习通/WeLearn)。
     * 与各服务自身的启用开关做 AND —— 关掉这里只是从应用页隐藏,不影响底栏固定与服务本身。
     */
    val showThirdPartyServices: Boolean = true,
    /**
     * 磁贴是否绘制应用图标。关掉后磁贴只留文字标题(纯文字列表风)。
     * 后加字段——反序列化务必走 [fromStoredJson],否则老 JSON 缺此键会被 Gson 填成 false。
     */
    val showIcons: Boolean = true,
    /** [AppHubSortMode.CUSTOM] 时的应用顺序(app key,不含第三方服务)。 */
    val customOrder: List<String> = emptyList(),
    /** 用户隐藏的应用 key 集合;隐藏项不在应用页渲染,但最近使用/收藏仍可追踪。 */
    val hiddenKeys: Set<String> = emptySet(),
) {
    /**
     * 归一化:剔除已失效的 app key、施加样式间的硬约束,得到可安全渲染的配置。
     *
     * @param validKeys 当前 [AppRegistry] 里真实存在的 app key 全集。
     */
    fun normalize(validKeys: Set<String>): AppHubLayoutConfig {
        val cleanHidden = hiddenKeys.intersect(validKeys)
        // customOrder 去重 + 去失效 key,再补上未列出的有效 key(按注册顺序由调用方补齐,这里只清洗)
        val cleanOrder = customOrder.asSequence()
            .filter { it in validKeys }
            .distinct()
            .toList()
        // COMPACT 卡片是单行密集列表,强制单列,避免半行错位
        val effectiveColumns =
            if (cardStyle == AppHubCardStyle.COMPACT) AppHubColumns.ONE else columns
        return copy(
            columns = effectiveColumns,
            hiddenKeys = cleanHidden,
            customOrder = cleanOrder,
        )
    }

    companion object {
        /** 显式恢复默认——设置页「恢复默认」按钮用。 */
        val Default = AppHubLayoutConfig()

        /**
         * 从持久化 JSON 恢复配置,对「后加字段的旧 JSON」向后兼容。
         *
         * Gson 反序列化 data class 走 Unsafe、不执行 Kotlin 构造函数默认值,所以旧 JSON
         * 缺某个键时 Gson 会用 JVM 零值填充(Boolean→false)而非我们声明的默认值。这里
         * 先把 [Default] 序列化成底板,再叠加存储 JSON 的键——存储里有的键覆盖底板,没有的
         * 键保留 Default 值。今后再加字段自动兼容,无需逐个补迁移。
         *
         * 解析失败(空/非法 JSON)一律回退 [Default]。返回值未 normalize,由调用方按当前
         * [AppRegistry] 决定是否清洗失效 key。
         */
        fun fromStoredJson(json: String?): AppHubLayoutConfig {
            if (json.isNullOrBlank()) return Default
            return runCatching {
                val gson = GsonProvider.instance
                val base = gson.toJsonTree(Default).asJsonObject
                val stored = JsonParser.parseString(json).asJsonObject
                // 存储键覆盖底板键;底板补齐缺失键
                stored.entrySet().forEach { (key, value) -> base.add(key, value) }
                gson.fromJson(base, AppHubLayoutConfig::class.java)
            }.getOrDefault(Default)
        }
    }
}

/** 网格列数。ADAPTIVE 按最小卡片宽度自适应(宽屏更多列)。 */
enum class AppHubColumns { ONE, TWO, THREE, ADAPTIVE }

/**
 * 卡片样式。
 * - [HORIZONTAL] 图标左 + 文字右(现状,84dp 高)
 * - [VERTICAL] 图标在上 + 文字在下(启动器风,适合 3~4 列)
 * - [COMPACT] 密集单行列表(小图标 + 文字,强制单列)
 */
enum class AppHubCardStyle { HORIZONTAL, VERTICAL, COMPACT }

/** 密度:控制卡片高度、内边距、图标尺寸、卡片间距。 */
enum class AppHubDensity { COMFORTABLE, COMPACT }

/** 分组方式。BY_CATEGORY 按 AppSpec.group 分组;FLAT 不分组、单一列表。 */
enum class AppHubGroupMode { BY_CATEGORY, FLAT }

/**
 * 排序方式(组内 / 扁平列表通用)。
 * - [DEFAULT] AppRegistry 注册顺序
 * - [NAME] 按标题拼音/字典序
 * - [RECENT] 最近使用优先(用 SessionManager.recentApps)
 * - [FREQUENCY] 使用频率高优先(用 SessionManager 使用计数)
 * - [CUSTOM] 用户拖拽的 [AppHubLayoutConfig.customOrder]
 */
enum class AppHubSortMode { DEFAULT, NAME, RECENT, FREQUENCY, CUSTOM }
