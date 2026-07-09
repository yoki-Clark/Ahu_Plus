package com.ahu_plus.data.model

/**
 * AI 大模型平台注册表(2026-06-21)。
 *
 * 每个实例携带默认配置,用于"选平台→自动填端点+模型下拉"模式。
 * 所有平台均兼容 OpenAI Chat Completions 协议。
 *
 * 模型列表基于各平台 2026-06 官方文档最新主推。
 */
enum class AiPlatform(
    /** 显示名称(下拉列表展示) */
    val displayName: String,
    /** 选中后自动填充的 Base URL (不含 /chat/completions 路径) */
    val defaultBaseUrl: String,
    /** 该平台主推的模型列表(下拉展示),为空时用户需手动输入 */
    val defaultModels: List<String>,
) {
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModels = listOf("deepseek-v4-pro", "deepseek-v4-flash"),
    ),
    SILICONFLOW(
        displayName = "硅基流动",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModels = listOf(
            "Pro/deepseek-ai/DeepSeek-V3.2",
            "Pro/zai-org/GLM-4.7",
            "Qwen/Qwen3.5-122B-A10B",
            "Qwen/Qwen3.5-35B-A3B",
        ),
    ),
    ZHIPU(
        displayName = "智谱 GLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModels = listOf("glm-5.2", "glm-4-plus", "glm-4-flash", "glm-z1-air"),
    ),
    DASHSCOPE(
        displayName = "通义千问 Qwen",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModels = listOf("qwen3.7-max", "qwen3.7-plus", "qwen3.6-flash"),
    ),
    MOONSHOT(
        displayName = "月之暗面 Kimi",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultModels = listOf("kimi-k2.7-code", "kimi-k2.7-code-highspeed", "kimi-k2.6", "kimi-k2.5"),
    ),
    QIANFAN(
        displayName = "百度千帆",
        defaultBaseUrl = "https://qianfan.cloud.baidu.com/v2",
        defaultModels = listOf("ernie-5.0", "ernie-5.0-thinking-preview", "ernie-x1.1-preview", "ernie-4.5-turbo-128k"),
    ),
    DOUBAO(
        displayName = "字节豆包",
        defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModels = listOf("ep-xxxxxxx（请从方舟控制台获取接入点 ID）"),
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModels = listOf("gpt-5", "gpt-5-mini", "gpt-5-nano", "o3", "o3-mini", "o4-mini"),
    ),
    CUSTOM(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultModels = emptyList(),
    ),
    ;

    companion object {
        /**
         * 根据 baseUrl 推断当前平台(用于从已保存的 URL 恢复下拉选中项)。
         * 精确匹配优先,fallback 到 CUSTOM。
         */
        fun fromBaseUrl(baseUrl: String): AiPlatform {
            if (baseUrl.isBlank()) return CUSTOM
            return entries.firstOrNull { it != CUSTOM && baseUrl.startsWith(it.defaultBaseUrl) }
                ?: CUSTOM
        }

        /**
         * 从 displayName 查找平台(用于从持久化的平台名恢复)。
         */
        fun fromName(name: String): AiPlatform {
            return entries.firstOrNull { it.displayName == name || it.name == name }
                ?: DEEPSEEK
        }
    }
}
