package com.ahu_plus.data.network.mail

/**
 * 教育邮箱(Sirius 教育版,通过 WebVPN 反代)的端点常量与 URL 构造工具。
 *
 * 抓包来源:E:\cc项目\0.非项目使用\26.7\7.31-10 的 HAR 文件。
 *
 * 关键设计:
 * - WebVPN(wvpn.ahu.edu.cn)是反向代理入口,内网域名通过 hex 编码嵌入 URL 路径。
 *   hex 段是 `wrdvpnisthebest!`(AES key+IV)加密产物,服务端响应里的 `_host` 是明文。
 * - 实际业务 API 路径前缀固定,hex 段作为常量硬编码。
 * - 公共查询参数(deviceId/system/manufacturer 等)由 [commonQuery] 统一构造。
 *
 * 首版保持与抓包一致的 web 参数(chrome/web),验证协议链通后再切 android 参数做对比。
 */
object MailApi {
    /** WebVPN 反代入口域名。 */
    const val HOST = "wvpn.ahu.edu.cn"

    /**
     * mail.stu.ahu.edu.cn 在 WebVPN 路径中的 hex 编码段。
     * 这是 `wrdvpnisthebest!` AES 加密内网域名后的产物,作为常量直接硬编码。
     * 若 Sirius 域切换(如 mail.staff.ahu.edu.cn),需要同步更新此常量。
     */
    const val HEX_MAIL = "77726476706e69737468656265737421fdf6489069237c45300981b9d6502720b63704"

    /** one.ahu.edu.cn(智慧安大 tp_up 母站)的 hex 编码段,用于 SSO 入口跳转。 */
    const val HEX_ONE = "77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b"

    /** one.ahu.edu.cn 在 /domain/oa/Entry 路径中使用的 hex 编码段别名。 */
    const val HEX_ONE_ENTRY = "77726476706e69737468656265737421f5f9558e3e38721e6f0190a9d6047566363d1097"

    /** one.ahu.edu.cn 在 /wengine-vpn-token-login 路径中使用的 hex 编码段别名。 */
    const val HEX_ONE_TOKEN = "77726476706e69737468656265737421e7e1519269316045300d8db9d6562d"

    /** 学生邮箱真实内网域名。 */
    const val MAIL_HOST = "mail.stu.ahu.edu.cn"

    /** 学生邮箱域(学号后缀)。 */
    const val MAIL_DOMAIN = "stu.ahu.edu.cn"

    /** 网易企业邮箱产品 ID。 */
    const val PRODUCT = "sirius"

    /** 网易企业邮箱前端 appName(首版与抓包一致)。 */
    const val APP_NAME = "sirius-web"

    /** 网易企业邮箱前端版本(首版与抓包一致)。 */
    const val VERSION = "1.65.2"

    /** 模拟的桌面 Chrome UA(与抓包一致,避免触发 Sirius 移动端 UI 分支)。 */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

    /** WebVPN cookie 中转桥端点。 */
    const val PATH_COOKIE_BRIDGE = "/wengine-vpn/cookie"

    /** 智慧安大 tp_up SSO URL 生成端点。 */
    const val PATH_GENERATE_SSO_URL = "/tp_up/up/subgroup/generateSsoUrl"

    /** 网易企业邮箱 SSO 入口。 */
    const val PATH_DOMAIN_OA_ENTRY = "/domain/oa/Entry"

    /** 邮箱入口派发页(从 SSO URL 拿到 sid 后跳转到这里)。 */
    const val PATH_ENTRY_DOOR = "/entry/door"

    /** Sirius 业务 API 根路径(在 wvpn 反代下的 mail.stu.ahu.edu.cn 命名空间)。 */
    private const val BUSINESS_BASE = "/http/$HEX_MAIL"

    /** 构造 Sirius 业务 API 完整 URL。 */
    fun businessUrl(path: String): String =
        "https://$HOST$BUSINESS_BASE$path"

    /** 构造 SSO 入口完整 URL(在 wvpn 反代下的 one.ahu.edu.cn 命名空间)。 */
    fun ssoEntryUrl(hexOne: String = HEX_ONE_ENTRY): String =
        "https://$HOST/https/$hexOne$PATH_DOMAIN_OA_ENTRY"

    /** 构造 entry/door 完整 URL。 */
    fun entryDoorUrl(hexOne: String = HEX_ONE_ENTRY): String =
        "https://$HOST/https/$hexOne$PATH_ENTRY_DOOR"

    /** 构造 generateSsoUrl 完整 URL。 */
    fun generateSsoUrlUrl(hexOne: String = HEX_ONE): String =
        "https://$HOST/https/$hexOne$PATH_GENERATE_SSO_URL"

    /** 构造 wengine-vpn/cookie 中转桥完整 URL。 */
    fun cookieBridgeUrl(host: String, scheme: String, path: String, timestamp: Long): String {
        // 不用 HttpUrl.Builder 以避免对 path 重复编码;手动拼接保持与抓包一致
        return "https://$HOST$PATH_COOKIE_BRIDGE" +
            "?method=get" +
            "&host=${host}" +
            "&scheme=${scheme}" +
            "&path=${path}" +
            "&vpn_timestamp=${timestamp}"
    }

    /**
     * 构造 Sirius 业务 API 的公共查询参数。
     *
     * 首版保持 web/chrome 参数与抓包一致,避免 Sirius 后端因 `_system=android`
     * 触发不同响应路径。验证协议链通后可切换为真实 Android 设备参数。
     */
    fun commonQuery(
        deviceId: String,
    ): Map<String, String> = mapOf(
        "vpn-12-o1-$MAIL_HOST" to "",
        "_host" to MAIL_HOST,
        "p" to "web",
        "_deviceId" to deviceId,
        "_device" to "chrome",
        "_systemVersion" to "10.0",
        "_system" to "web",
        "_manufacturer" to "chrome",
        "_deviceName" to "chrome 151.0.0.0",
        "_appName" to APP_NAME,
        "_version" to VERSION,
    )

    /** 构造完整业务 GET URL(包含公共查询参数)。 */
    fun businessGetUrl(path: String, deviceId: String, extra: Map<String, String> = emptyMap()): String {
        val base = businessUrl(path)
        val params = commonQuery(deviceId) + extra
        val query = params.entries.joinToString("&") { (k, v) ->
            "${k.encodeUrl()}=${v.encodeUrl()}"
        }
        return "$base?$query"
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "/")  // path 段允许 /
            .replace("%3A", ":")  // 时间戳允许 :
}
