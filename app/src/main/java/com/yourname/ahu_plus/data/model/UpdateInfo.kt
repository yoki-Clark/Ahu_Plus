package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

/**
 * Gitee 远程 version.json 的数据结构。
 *
 * 托管地址: https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json
 */
data class UpdateInfo(
    /** 最新版本号（显示用），如 "1.1" */
    @SerializedName("latestVersion")
    val latestVersion: String = "",

    /** 最新 versionCode（比较用），如 2 */
    @SerializedName("latestVersionCode")
    val latestVersionCode: Int = 0,

    /** 最小支持的 versionCode（低于此版本强制更新，暂未使用） */
    @SerializedName("minSupportedVersionCode")
    val minSupportedVersionCode: Int = 1,

    /** 主下载地址（Gitee Releases） */
    @SerializedName("downloadUrl")
    val downloadUrl: String = "",

    /** APK 文件名，用于 DownloadManager 区分缓存文件 */
    @SerializedName("apkFileName")
    val apkFileName: String = "ahu_plus_update.apk",

    /** 更新日志列表 */
    @SerializedName("releaseNotes")
    val releaseNotes: List<String> = emptyList(),

    /** 更新详情页链接（可选） */
    @SerializedName("updateUrl")
    val updateUrl: String = ""
) {
    /** 将 releaseNotes 列表合并为纯文本，供 Dialog 展示 */
    fun releaseNotesText(): String =
        releaseNotes.joinToString("\n") { "• $it" }
}

/**
 * 版本检查结果。
 */
enum class CheckResult {
    /** 当前已是最新版本 */
    LATEST,
    /** 有新版本可更新 */
    UPDATE_AVAILABLE,
    /** 网络或解析出错 */
    ERROR
}
