package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * Gitee 远程 version.json 的数据结构。
 *
 * 托管地址: https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json
 */
data class UpdateInfo(
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

    /** 是否需要强制更新（当前 versionCode 低于服务端声明的最小支持值） */
    fun isForceUpdate(currentVersionCode: Int): Boolean =
        minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode
}

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
