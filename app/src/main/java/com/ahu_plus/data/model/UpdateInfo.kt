package com.ahu_plus.data.model

import com.google.gson.annotations.SerializedName
import java.net.URI

enum class UpdateChannel(val wireValue: String) {
    STABLE("stable"),
    BETA("beta")
}

/**
 * Gitee 远程 version.json 的数据结构。
 *
 * 托管地址: https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json
 */
data class UpdateInfo(
    /** 更新渠道。旧版清单可缺省，由请求该清单时使用的渠道决定。 */
    @SerializedName("channel")
    val channel: String = "",

    /** 最新版本号（显示用），如 "1.4.0" */
    @SerializedName("latestVersion")
    val latestVersion: String = "",

    /** 最新 versionCode（比较用） */
    @SerializedName("latestVersionCode")
    val latestVersionCode: Int = 0,

    /** 最小支持的 versionCode；当前版本低于此值时触发强制更新 */
    @SerializedName("minSupportedVersionCode")
    val minSupportedVersionCode: Int = 0,

    /** APK 下载地址 */
    @SerializedName("downloadUrl")
    val downloadUrl: String = "",

    /** 旧清单中的 APK 备用地址；新清单可以继续保留以兼容旧客户端。 */
    @SerializedName("downloadUrlMirror")
    val downloadUrlMirror: String = "",

    /** APK 文件名（仅作为后缀提示，落盘时会自动附加 versionCode） */
    @SerializedName("apkFileName")
    val apkFileName: String = "ahu_plus_update.apk",

    /** 更新日志列表 */
    @SerializedName("releaseNotes")
    val releaseNotes: List<String> = emptyList(),

    /** 更新详情页链接（可选） */
    @SerializedName("updateUrl")
    val updateUrl: String = "",

    /** APK 文件 sha256（小写十六进制，可选；为空则跳过校验） */
    @SerializedName("sha256")
    val sha256: String = "",

    /** APK 字节大小（可选，便于在弹窗预告体积） */
    @SerializedName("fileSize")
    val fileSize: Long = 0L
) {
    fun releaseNotesText(): String =
        releaseNotes.joinToString("\n") { "• $it" }

    fun downloadUrls(): List<String> =
        listOf(downloadUrl, downloadUrlMirror)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    /** 返回 null 表示清单可供当前客户端安全使用。 */
    fun validationError(expectedChannel: UpdateChannel): String? {
        if (channel.isNotBlank() && !channel.equals(expectedChannel.wireValue, ignoreCase = true)) {
            return "更新渠道不匹配"
        }
        if (latestVersion.isBlank()) return "版本名称为空"
        if (latestVersionCode <= 0) return "版本号无效"
        if (minSupportedVersionCode < 0 || minSupportedVersionCode > latestVersionCode) {
            return "最低支持版本号无效"
        }
        val urls = downloadUrls()
        if (urls.isEmpty()) return "下载地址为空"
        if (urls.any { !it.isHttpsUrl() }) return "下载地址必须使用 HTTPS"
        if (sha256.isNotBlank() && !SHA256_REGEX.matches(sha256)) return "SHA-256 格式无效"
        if (fileSize < 0) return "文件大小无效"
        return null
    }

    /** 是否需要强制更新（当前 versionCode 低于服务端声明的最小支持值） */
    fun isForceUpdate(currentVersionCode: Int): Boolean =
        latestVersionCode > currentVersionCode &&
            minSupportedVersionCode > 0 &&
            currentVersionCode < minSupportedVersionCode
}

private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

private fun String.isHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)

/**
 * 版本检查结果。
 */
enum class CheckResult {
    /** 当前已是最新版本 */
    LATEST,
    /** 有新版本可更新 */
    UPDATE_AVAILABLE,
    /** 当前版本低于 minSupportedVersionCode，必须更新 */
    FORCE_UPDATE,
    /** 网络或解析出错 */
    ERROR
}
