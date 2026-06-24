package com.yourname.ahu_plus.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.yourname.ahu_plus.BuildConfig
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.CheckResult
import com.yourname.ahu_plus.data.model.UpdateInfo
import com.yourname.ahu_plus.data.network.ResilientDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 自动更新管理器。
 *
 * 状态流转(订阅 [uiState]):
 *   Idle ──checkXxx()──► UpdateReady ──downloadApk()──► Downloading ──成功──► ReadyToInstall
 *                            │                              │
 *                            └──dismiss──► Idle             └──失败──► DownloadFailed ──retry──► Downloading
 *
 * Force update(minSupportedVersionCode 不满足)时 [UpdateUiState.UpdateReady.forceUpdate] 为 true,
 * Dialog 据此隐藏 "关闭/忽略" 按钮。
 */
class UpdateManager(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private val gson: Gson = GsonProvider.instance

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS) // APK 可能 30MB+,弱网允许 10 分钟
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .dns(ResilientDns)
            .build()
    }

    companion object {
        private const val TAG = "UpdateManager"

        /**
         * version.json 源列表,按顺序回退:
         *   1. 主源: gitee.com/yao-enqi/Ahu_Plus (主仓库,Release 挂 APK)
         *   2. 旧维护源: gitee.com/yao-enqi/ahu-plus-update (历史几版仍维护)
         *   3. GitHub 备用: raw.githubusercontent.com/yao-enqi/Ahu_Plus
         */
        val VERSION_JSON_URLS = listOf(
            "https://gitee.com/yao-enqi/Ahu_Plus/raw/master/version.json",
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json",
            "https://raw.githubusercontent.com/yao-enqi/Ahu_Plus/main/version.json"
        )

        /** 兼容旧调用 */
        @Deprecated("Use VERSION_JSON_URLS", ReplaceWith("VERSION_JSON_URLS.first()"))
        const val VERSION_JSON_URL =
            "https://gitee.com/yao-enqi/Ahu_Plus/raw/master/version.json"

        /** 下载目录: app 私有外部存储 (无需 WRITE_EXTERNAL_STORAGE) */
        private const val APK_DIR = "updates"
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 最近一次检查到的远程信息(用于 ProfileScreen 兼容旧接口) */
    @Volatile
    var lastFetchedUpdateInfo: UpdateInfo? = null
        private set

    private var downloadJob: Job? = null

    // ── 版本检查 ─────────────────────────────────────

    /**
     * 启动自动检查: 命中"忽略此版本"则不打扰,但强制更新仍会弹窗。
     */
    suspend fun checkForUpdateWithIgnore(): CheckResult {
        val raw = checkRemote() ?: return CheckResult.ERROR
        val info = lastFetchedUpdateInfo ?: return CheckResult.ERROR

        if (info.isForceUpdate(BuildConfig.VERSION_CODE)) {
            _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = true)
            return CheckResult.FORCE_UPDATE
        }
        if (raw == CheckResult.LATEST) return CheckResult.LATEST

        val ignored = sessionManager.getIgnoredVersionCode()
        if (info.latestVersionCode <= ignored) return CheckResult.LATEST

        _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = false)
        return CheckResult.UPDATE_AVAILABLE
    }

    /**
     * 手动检查: 用户主动点击,忽略"忽略此版本"标记。
     * 检查中/已有下载任务时不重复触发。
     */
    suspend fun checkManually(): CheckResult {
        val raw = checkRemote() ?: return CheckResult.ERROR
        val info = lastFetchedUpdateInfo ?: return CheckResult.ERROR

        if (info.isForceUpdate(BuildConfig.VERSION_CODE)) {
            _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = true)
            return CheckResult.FORCE_UPDATE
        }
        if (raw == CheckResult.UPDATE_AVAILABLE) {
            _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = false)
        }
        return raw
    }

    /** 拉 version.json,刷新 [lastFetchedUpdateInfo],返回 LATEST / UPDATE_AVAILABLE / null(出错) */
    private suspend fun checkRemote(): CheckResult? = withContext(Dispatchers.IO) {
        for ((index, url) in VERSION_JSON_URLS.withIndex()) {
            val info = runCatching { fetchVersionJson(url) }
                .onFailure { Log.w(TAG, "checkRemote source[$index] $url failed: ${it.message}") }
                .getOrNull() ?: continue

            lastFetchedUpdateInfo = info
            Log.d(TAG, "checkRemote source[$index] ok: ${info.latestVersion}(${info.latestVersionCode})")
            return@withContext if (info.latestVersionCode > BuildConfig.VERSION_CODE) {
                CheckResult.UPDATE_AVAILABLE
            } else {
                CheckResult.LATEST
            }
        }
        Log.w(TAG, "checkRemote: all sources failed")
        lastFetchedUpdateInfo = null
        null
    }

    private fun fetchVersionJson(url: String): UpdateInfo? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchVersionJson HTTP ${response.code} from $url")
                return null
            }
            val body = response.body?.string() ?: return null
            return gson.fromJson(body, UpdateInfo::class.java)
        }
    }

    // ── 下载 ─────────────────────────────────────────

    /**
     * 启动 APK 下载。重复调用(已在下载中)直接忽略。
     * 完成后自动校验 sha256(若提供) → 触发安装。
     */
    fun downloadApk(info: UpdateInfo, forceUpdate: Boolean = false) {
        if (downloadJob?.isActive == true) {
            Log.d(TAG, "downloadApk: already in progress, ignored")
            return
        }
        _uiState.value = UpdateUiState.Downloading(
            info = info,
            progress = -1,
            downloadedBytes = 0,
            totalBytes = 0,
            forceUpdate = forceUpdate
        )

        downloadJob = scope.launch {
            runCatching { performDownload(info, forceUpdate) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "download cancelled by user")
                        _uiState.value = if (forceUpdate) {
                            UpdateUiState.UpdateReady(info, forceUpdate = true)
                        } else {
                            UpdateUiState.Idle
                        }
                    } else {
                        Log.w(TAG, "download failed", e)
                        _uiState.value = UpdateUiState.DownloadFailed(
                            info = info,
                            message = e.message ?: "下载失败",
                            forceUpdate = forceUpdate
                        )
                    }
                }
        }
    }

    /** 取消正在进行的下载 */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /** 关闭弹窗(非强制更新场景) */
    fun dismiss() {
        if (_uiState.value is UpdateUiState.Downloading) return
        _uiState.value = UpdateUiState.Idle
    }

    /** 标记当前提示版本为"忽略",仅自动检查会受影响 */
    suspend fun ignoreCurrent() {
        val current = _uiState.value
        val info = when (current) {
            is UpdateUiState.UpdateReady -> if (!current.forceUpdate) current.info else null
            is UpdateUiState.DownloadFailed -> if (!current.forceUpdate) current.info else null
            else -> null
        } ?: return
        sessionManager.saveIgnoredVersionCode(info.latestVersionCode)
        _uiState.value = UpdateUiState.Idle
    }

    private suspend fun performDownload(info: UpdateInfo, forceUpdate: Boolean) {
        val job = kotlin.coroutines.coroutineContext[Job]
        val targetFile = apkFileFor(info)
        cleanupOldApks(targetFile)

        val tmpFile = File(targetFile.parentFile, targetFile.name + ".tmp")
        if (tmpFile.exists()) tmpFile.delete()
        targetFile.parentFile?.mkdirs()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)")
            .get()
            .build()

        Log.d(TAG, "downloading APK from ${info.downloadUrl}")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("服务器返回 HTTP ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("响应体为空")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: info.fileSize

            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastProgress = -2
                    while (job?.isActive != false) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (totalBytes > 0) {
                            ((downloaded * 100.0) / totalBytes).toInt().coerceIn(0, 99)
                        } else {
                            -1
                        }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            _uiState.value = UpdateUiState.Downloading(
                                info = info,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                forceUpdate = forceUpdate
                            )
                        }
                    }
                    output.flush()
                    if (job?.isActive == false) {
                        throw kotlinx.coroutines.CancellationException("用户取消")
                    }
                }
            }
        }

        if (info.sha256.isNotBlank()) {
            val actual = sha256Hex(tmpFile)
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                tmpFile.delete()
                throw RuntimeException("文件校验失败,请重试")
            }
        }

        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.delete()
            throw RuntimeException("文件保存失败")
        }

        _uiState.value = UpdateUiState.ReadyToInstall(
            info = info,
            apkFile = targetFile,
            forceUpdate = forceUpdate
        )
        installApk(targetFile)
    }

    /**
     * 重试安装(用户在 ReadyToInstall 时手动点击,例如上次拒绝了权限提示)。
     */
    fun retryInstall() {
        val state = _uiState.value as? UpdateUiState.ReadyToInstall ?: return
        installApk(state.apkFile)
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            Log.w(TAG, "installApk: file not found ${file.absolutePath}")
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed", e)
        }
    }

    private fun apkDir(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun apkFileFor(info: UpdateInfo): File =
        File(apkDir(), "ahu_plus_${info.latestVersionCode}.apk")

    /** 删除目录里非当前目标的旧 apk(及其 .tmp),避免越攒越多 */
    private fun cleanupOldApks(keep: File) {
        apkDir().listFiles()?.forEach { f ->
            if (f.absolutePath != keep.absolutePath && (f.name.endsWith(".apk") || f.name.endsWith(".tmp"))) {
                runCatching { f.delete() }
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** 自动更新 UI 状态机 */
sealed class UpdateUiState {
    /** 无更新待办,或已显式关闭 */
    object Idle : UpdateUiState()

    /** 发现新版本,等待用户选择 "立即更新 / 以后再说 / 忽略此版本" */
    data class UpdateReady(
        val info: com.yourname.ahu_plus.data.model.UpdateInfo,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 正在下载;progress=-1 表示总大小未知,UI 用 indeterminate 动画 */
    data class Downloading(
        val info: com.yourname.ahu_plus.data.model.UpdateInfo,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 下载失败,允许重试或关闭 */
    data class DownloadFailed(
        val info: com.yourname.ahu_plus.data.model.UpdateInfo,
        val message: String,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 下载完成,已触发系统安装 Intent;用户可重试安装 */
    data class ReadyToInstall(
        val info: com.yourname.ahu_plus.data.model.UpdateInfo,
        val apkFile: java.io.File,
        val forceUpdate: Boolean
    ) : UpdateUiState()
}
