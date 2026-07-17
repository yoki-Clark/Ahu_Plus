package com.ahu_plus.data.developer

import okhttp3.HttpUrl.Companion.toHttpUrl

enum class NetworkDiagnosticCategory {
    AHU,
    MARKET,
    CHAOXING,
    WELEARN,
    PUBLIC_DATA,
    AI_PROVIDER,
}

enum class NetworkProbeMethod {
    HEAD,
    GET,
}

enum class NetworkDiagnosticPhase {
    DNS,
    HTTPS,
}

enum class NetworkDiagnosticStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    WARNING,
    FAILED,
    SKIPPED,
    CANCELLED,
}

enum class NetworkDiagnosticErrorKind {
    DNS,
    TIMEOUT,
    TLS,
    NETWORK_IO,
    INVALID_CONFIGURATION,
    CANCELLED,
    UNKNOWN,
}

data class NetworkHostSpec(
    val id: String,
    val displayName: String,
    val url: String,
    val category: NetworkDiagnosticCategory,
    val method: NetworkProbeMethod = NetworkProbeMethod.HEAD,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Host id must contain lowercase ASCII letters, digits, or underscores" }
        require(displayName.isNotBlank()) { "Host display name must not be blank" }

        val parsedUrl = url.toHttpUrl()
        require(parsedUrl.isHttps) { "Network diagnostics only allow HTTPS URLs" }
        require(parsedUrl.username.isEmpty() && parsedUrl.password.isEmpty()) {
            "Network diagnostic URLs must not contain user info"
        }
    }

    val host: String
        get() = url.toHttpUrl().host

    val redactedUrl: String
        get() = NetworkDiagnosticUrlRedactor.redact(url)

    val usesAhuCertificateCompatibility: Boolean
        get() = NetworkDiagnosticRules.isAhuHost(host)

    val requiresTls12: Boolean
        get() = NetworkDiagnosticRules.requiresTls12(host)

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9_]+")
    }
}

data class NetworkDiagnosticError(
    val kind: NetworkDiagnosticErrorKind,
    val type: String,
    val message: String,
)

data class NetworkDnsResult(
    val status: NetworkDiagnosticStatus = NetworkDiagnosticStatus.PENDING,
    val addresses: List<String> = emptyList(),
    val durationMillis: Long? = null,
    val error: NetworkDiagnosticError? = null,
) {
    val phase: NetworkDiagnosticPhase = NetworkDiagnosticPhase.DNS
}

data class NetworkCertificateSummary(
    val subject: String,
    val issuer: String,
    val serialNumberHex: String,
    val sha256Fingerprint: String,
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
)

data class NetworkHttpResult(
    val status: NetworkDiagnosticStatus = NetworkDiagnosticStatus.PENDING,
    val method: NetworkProbeMethod,
    val requestedUrl: String,
    val finalUrl: String? = null,
    val redirectLocation: String? = null,
    val httpStatusCode: Int? = null,
    val httpStatusMessage: String? = null,
    val protocol: String? = null,
    val durationMillis: Long? = null,
    val tlsVersion: String? = null,
    val cipherSuite: String? = null,
    val peerCertificates: List<NetworkCertificateSummary> = emptyList(),
    val error: NetworkDiagnosticError? = null,
) {
    val phase: NetworkDiagnosticPhase = NetworkDiagnosticPhase.HTTPS
}

data class NetworkDiagnosticResult(
    val hostSpec: NetworkHostSpec,
    val status: NetworkDiagnosticStatus,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val totalDurationMillis: Long? = null,
    val dns: NetworkDnsResult = NetworkDnsResult(),
    val http: NetworkHttpResult = NetworkHttpResult(
        method = hostSpec.method,
        requestedUrl = hostSpec.redactedUrl,
    ),
) {
    val error: NetworkDiagnosticError?
        get() = dns.error ?: http.error
}

object NetworkDiagnosticRules {
    const val ADWMH_HOST = "adwmh.ahu.edu.cn"

    fun isAhuHost(host: String): Boolean {
        val normalized = host.trim().lowercase()
        return normalized == "ahu.edu.cn" || normalized.endsWith(".ahu.edu.cn")
    }

    fun requiresTls12(host: String): Boolean = host.trim().equals(ADWMH_HOST, ignoreCase = true)
}

/** Read-only endpoints used by the developer network diagnostics screen. */
object NetworkDiagnosticHosts {
    val all: List<NetworkHostSpec> = listOf(
        host("ahu_cas", "统一身份认证 / 门户", "https://one.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_ycard", "一卡通账单", "https://ycard.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_jw", "教务系统", "https://jw.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_attendance", "考勤系统", "https://kqcard.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_adwmh", "智慧安大支付码", "https://adwmh.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_jwc", "教务处公告", "https://jwc.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_jwc_legacy", "教务处旧站 / WAF", "https://www6.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),
        host("ahu_webvpn", "WebVPN / C 语言平台", "https://wvpn.ahu.edu.cn/", NetworkDiagnosticCategory.AHU),

        host("market", "校园集市", "https://api.zxs-bbs.cn/", NetworkDiagnosticCategory.MARKET),

        host("chaoxing_passport", "超星登录", "https://passport2.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_mooc1", "超星课程资源", "https://mooc1.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_mooc2", "超星课程中心", "https://mooc2-ans.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_mobile", "超星活动与签到", "https://mobilelearn.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_notice", "超星消息", "https://notice.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_note", "超星云盘配置", "https://noteyd.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("chaoxing_pan", "超星云盘上传", "https://pan-yz.chaoxing.com/", NetworkDiagnosticCategory.CHAOXING),
        host("tiku_icodef", "题库 ICodeF", "https://q.icodef.com/", NetworkDiagnosticCategory.CHAOXING),
        host("tiku_enncy", "题库 Enncy", "https://tk.enncy.cn/", NetworkDiagnosticCategory.CHAOXING),
        host("tiku_datam", "题库 Datam", "https://www.datam.site/", NetworkDiagnosticCategory.CHAOXING),

        host("welearn_sso", "WeLearn 登录", "https://sso.sflep.com/", NetworkDiagnosticCategory.WELEARN),
        host("welearn", "WeLearn 课程", "https://welearn.sflep.com/", NetworkDiagnosticCategory.WELEARN),
        host("welearn_courseware", "WeLearn 课件 CDN", "https://centercourseware.sflep.com/", NetworkDiagnosticCategory.WELEARN),

        host("gitee", "Gitee 更新源", "https://gitee.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("gitee_raw", "Gitee Raw CDN", "https://raw.giteeusercontent.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("github", "GitHub", "https://github.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("github_raw", "GitHub Raw CDN", "https://raw.githubusercontent.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("weather", "Open-Meteo 天气", "https://api.open-meteo.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("air_quality", "Open-Meteo 空气质量", "https://air-quality-api.open-meteo.com/", NetworkDiagnosticCategory.PUBLIC_DATA),
        host("openahu", "OpenAHU 服务", "https://openahu.org/", NetworkDiagnosticCategory.PUBLIC_DATA),

        host("ai_openai", "OpenAI", "https://api.openai.com/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_deepseek", "DeepSeek", "https://api.deepseek.com/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_moonshot", "Moonshot", "https://api.moonshot.cn/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_siliconflow", "SiliconFlow", "https://api.siliconflow.cn/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_doubao", "火山方舟", "https://ark.cn-beijing.volces.com/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_qwen", "阿里云百炼", "https://dashscope.aliyuncs.com/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_glm", "智谱 AI", "https://open.bigmodel.cn/", NetworkDiagnosticCategory.AI_PROVIDER),
        host("ai_qianfan", "百度千帆", "https://qianfan.cloud.baidu.com/", NetworkDiagnosticCategory.AI_PROVIDER),
    )

    fun find(id: String): NetworkHostSpec? = all.firstOrNull { it.id == id }

    fun forCategory(category: NetworkDiagnosticCategory): List<NetworkHostSpec> =
        all.filter { it.category == category }

    private fun host(
        id: String,
        displayName: String,
        url: String,
        category: NetworkDiagnosticCategory,
    ) = NetworkHostSpec(
        id = id,
        displayName = displayName,
        url = url,
        category = category,
    )
}
