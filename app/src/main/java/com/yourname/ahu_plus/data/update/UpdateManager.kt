package com.yourname.ahu_plus.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.yourname.ahu_plus.BuildConfig
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CheckResult
import com.yourname.ahu_plus.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 自动更新管理器。
 *
 * 从 Gitee 拉取 version.json，比较版本号，用 OkHttp 下载 APK（自带进度回调），下载完自动安装。
 */
class UpdateManager(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 文件下载需要更长时间
        .build()

    private val gson: Gson = GsonProvider.instance

    companion object {
        const val VERSION_JSON_URL =
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json"
    }

    @Volatile
    var lastFetchedUpdateInfo: UpdateInfo? = null
        private set

    // ── 下载进度相关 ─────────────────────────────────
    /** 是否正在下载 */
    @Volatile
    var isDownloading: Boolean = false
        private set

    /** 下载进度 0~100 */
    @Volatile
    var downloadProgress: Int = 0
        private set

    /** 下载状态回调 */
    var onDownloadComplete: (() -> Unit)? = null

    // ── 版本检查 ─────────────────────────────────────

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_JSON_URL)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext CheckResult.ERROR
            }

            val body = response.body?.string() ?: return@withContext CheckResult.ERROR
            val info = gson.fromJson(body, UpdateInfo::class.java)

            lastFetchedUpdateInfo = info

            if (info.latestVersionCode > BuildConfig.VERSION_CODE) {
                CheckResult.UPDATE_AVAILABLE
            } else {
                CheckResult.LATEST
            }
        } catch (e: Exception) {
            lastFetchedUpdateInfo = null
            CheckResult.ERROR
        }
    }

    suspend fun checkForUpdateWithIgnore(): CheckResult {
        val result = checkForUpdate()
        if (result != CheckResult.UPDATE_AVAILABLE) return result
        val info = lastFetchedUpdateInfo ?: return CheckResult.ERROR
        val ignored = sessionManager.getIgnoredVersionCode()
        return if (info.latestVersionCode > ignored) {
            CheckResult.UPDATE_AVAILABLE
        } else {
            CheckResult.LATEST
        }
    }

    // ── 下载（OkHttp 带进度） ─────────────────────────

    /**
     * 用 OkHttp 下载 APK，实时更新 [downloadProgress]。
     * 下载完成后自动调用 [installApk] 触发安装。
     */
    fun downloadApk(info: UpdateInfo) {
        if (isDownloading) return
        isDownloading = true
        downloadProgress = 0

        Thread {
            try {
                val apkFile = getApkFile(info)
                if (apkFile.exists()) apkFile.delete()
                apkFile.parentFile?.mkdirs()

                val tmpFile = File(apkFile.parentFile, info.apkFileName + ".tmp")
                if (tmpFile.exists()) tmpFile.delete()

                val request = Request.Builder()
                    .url(info.downloadUrl)
                    .get()
                    .build()

                android.util.Log.d("UpdateManag", "starting download from: ${info.downloadUrl}")
                val response: Response = client.newCall(request).execute()
                android.util.Log.d("UpdateManag", "response code: ${response.code}")

                if (!response.isSuccessful) {
                    android.util.Log.e("UpdateManag", "download failed: HTTP ${response.code}")
                    isDownloading = false
                    return@Thread
                }

                val body = response.body ?: run {
                    android.util.Log.e("UpdateManag", "response body is null")
                    isDownloading = false
                    return@Thread
                }

                val totalBytes = body.contentLength()
                android.util.Log.d("UpdateManag", "download started, total=$totalBytes bytes")
                var downloadedBytes = 0L

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tmpFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            downloadProgress = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt()
                        }
                    }
                    outputStream.flush()
                    downloadProgress = 100
                    android.util.Log.d("UpdateManag", "download complete, renaming tmp file")
                    tmpFile.renameTo(apkFile)
                    android.util.Log.d("UpdateManag", "installing...")
                } finally {
                    inputStream.close()
                    outputStream.close()
                }

                installApk(info)
                isDownloading = false
                android.util.Log.d("UpdateManag", "install triggered")
                onDownloadComplete?.invoke()
            } catch (e: Exception) {
                android.util.Log.e("UpdateManag", "download error", e)
                isDownloading = false
                downloadProgress = 0
            }
        }.start()
    }

    /**
     * 安装已下载的 APK。
     */
    fun installApk(info: UpdateInfo) {
        val file = getApkFile(info)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getApkFile(info: UpdateInfo): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), info.apkFileName)
    }
}
