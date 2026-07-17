package com.ahu_plus.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import com.ahu_plus.BuildConfig
import com.ahu_plus.data.GsonProvider
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.model.CheckResult
import com.ahu_plus.data.model.UpdateChannel
import com.ahu_plus.data.model.UpdateInfo
import com.ahu_plus.data.network.ResilientDns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
    private val checkMutex = Mutex()
    private val lastFetchedByChannel = ConcurrentHashMap<UpdateChannel, UpdateInfo>()

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
         * 稳定渠道 version.json 源列表,按顺序回退:
         *   1. 主源: gitee.com/yao-enqi/Ahu_Plus (主仓库,Release 挂 APK)
         *   2. 旧维护源: gitee.com/yao-enqi/ahu-plus-update (历史几版仍维护)
         *   3. GitHub 备用: raw.githubusercontent.com/yoki-Clark/Ahu_Plus
         */
        private val STABLE_VERSION_JSON_URLS = listOf(
            "https://gitee.com/yao-enqi/Ahu_Plus/raw/master/version.json",
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version.json",
            "https://raw.githubusercontent.com/yoki-Clark/Ahu_Plus/main/version.json"
        )

        /**
         * 内测渠道 version-beta.json 源列表。
         * 与稳定渠道物理隔离:文件名不同、APK 文件不同、sha256 不同,稳定用户永远不会拉到。
         */
        private val BETA_VERSION_JSON_URLS = listOf(
            "https://gitee.com/yao-enqi/ahu-plus-update/raw/master/version-beta.json",
            "https://gitee.com/yao-enqi/Ahu_Plus/raw/master/version-beta.json",
            "https://raw.githubusercontent.com/yoki-Clark/Ahu_Plus/main/version-beta.json"
        )

        /** 兼容旧调用(转发到稳定渠道)。 */
        @Deprecated("Use STABLE_VERSION_JSON_URLS", ReplaceWith("STABLE_VERSION_JSON_URLS"))
        val VERSION_JSON_URLS: List<String> get() = STABLE_VERSION_JSON_URLS

        /** 兼容旧调用 */
        @Deprecated("Use STABLE_VERSION_JSON_URLS", ReplaceWith("STABLE_VERSION_JSON_URLS.first()"))
        const val VERSION_JSON_URL =
            "https://gitee.com/yao-enqi/Ahu_Plus/raw/master/version.json"

        /** 下载目录: app 私有外部存储 (无需 WRITE_EXTERNAL_STORAGE) */
        private const val APK_DIR = "updates"
    }

    private fun currentChannel(): UpdateChannel =
        if (sessionManager.isBetaEnabled()) UpdateChannel.BETA else UpdateChannel.STABLE

    private fun versionJsonUrls(channel: UpdateChannel): List<String> =
        if (channel == UpdateChannel.BETA) BETA_VERSION_JSON_URLS else STABLE_VERSION_JSON_URLS

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 当前渠道最近一次检查结果。保留此属性以兼容分享页面。 */
    val lastFetchedUpdateInfo: UpdateInfo?
        get() = lastFetchedByChannel[currentChannel()]

    @Volatile private var downloadJob: Job? = null
    @Volatile private var activeDownloadCall: Call? = null

    // ── 版本检查 ─────────────────────────────────────

    /**
     * 启动自动检查: 命中"忽略此版本"则不打扰,但强制更新仍会弹窗。
     */
    suspend fun checkForUpdateWithIgnore(): CheckResult = check(ignoreIgnoredVersion = true)

    /**
     * 手动检查: 用户主动点击,忽略"忽略此版本"标记。
     * 检查中/已有下载任务时不重复触发。
     */
    suspend fun checkManually(): CheckResult = check(ignoreIgnoredVersion = false)

    /** 持久化渠道切换并在后台检查新渠道；下载中的任务不会被切换打断。 */
    suspend fun changeChannel(betaEnabled: Boolean) {
        sessionManager.init()
        sessionManager.setBetaEnabled(betaEnabled)
        val selectedChannel = currentChannel()
        lastFetchedByChannel.remove(selectedChannel)

        scope.launch {
            checkMutex.withLock {
                when (_uiState.value) {
                    is UpdateUiState.Downloading,
                    is UpdateUiState.ReadyToInstall -> Unit
                    else -> _uiState.value = UpdateUiState.Idle
                }
            }
            checkManually()
        }
    }

    private suspend fun check(ignoreIgnoredVersion: Boolean): CheckResult {
        // UpdateManager 自己保证初始化，避免依赖 Compose 中多个 LaunchedEffect 的执行顺序。
        sessionManager.init()
        return checkMutex.withLock {
            existingUpdateResult()?.let { return@withLock it }

            if (ignoreIgnoredVersion &&
                sessionManager.getIgnoredVersionCode() in 1..BuildConfig.VERSION_CODE
            ) {
                sessionManager.saveIgnoredVersionCode(0)
            }

            val channel = currentChannel()
            val info = checkRemote(channel) ?: return@withLock CheckResult.ERROR
            // 渠道可在网络请求期间切换，旧渠道结果不得污染新渠道 UI。
            if (channel != currentChannel()) return@withLock CheckResult.LATEST
            if (info.latestVersionCode <= BuildConfig.VERSION_CODE) return@withLock CheckResult.LATEST

            if (info.isForceUpdate(BuildConfig.VERSION_CODE)) {
                _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = true)
                return@withLock CheckResult.FORCE_UPDATE
            }

            if (ignoreIgnoredVersion &&
                info.latestVersionCode <= sessionManager.getIgnoredVersionCode()
            ) {
                return@withLock CheckResult.LATEST
            }

            _uiState.value = UpdateUiState.UpdateReady(info, forceUpdate = false)
            CheckResult.UPDATE_AVAILABLE
        }
    }

    private fun existingUpdateResult(): CheckResult? = when (val state = _uiState.value) {
        UpdateUiState.Idle -> null
        is UpdateUiState.UpdateReady -> if (state.forceUpdate) CheckResult.FORCE_UPDATE else CheckResult.UPDATE_AVAILABLE
        is UpdateUiState.Downloading -> if (state.forceUpdate) CheckResult.FORCE_UPDATE else CheckResult.UPDATE_AVAILABLE
        is UpdateUiState.DownloadFailed -> if (state.forceUpdate) CheckResult.FORCE_UPDATE else CheckResult.UPDATE_AVAILABLE
        is UpdateUiState.ReadyToInstall -> if (state.forceUpdate) CheckResult.FORCE_UPDATE else CheckResult.UPDATE_AVAILABLE
    }

    /** 按顺序使用同一渠道的元数据镜像，首个合法响应为准。 */
    private suspend fun checkRemote(channel: UpdateChannel): UpdateInfo? = withContext(Dispatchers.IO) {
        val urls = versionJsonUrls(channel)
        for ((index, url) in urls.withIndex()) {
            val info = runCatching { fetchVersionJson(url) }
                .onFailure { Log.w(TAG, "checkRemote source[$index] $url failed: ${it.message}") }
                .getOrNull() ?: continue

            val validationError = info.validationError(channel)
            if (validationError != null) {
                Log.w(TAG, "checkRemote source[$index] invalid: $validationError")
                continue
            }

            lastFetchedByChannel[channel] = info
            Log.d(TAG, "checkRemote ${channel.wireValue} source[$index] ok: ${info.latestVersion}(${info.latestVersionCode})")
            return@withContext info
        }
        Log.w(TAG, "checkRemote ${channel.wireValue}: all sources failed")
        lastFetchedByChannel.remove(channel)
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

        val job = scope.launch {
            try {
                performDownload(info, forceUpdate)
            } catch (e: Throwable) {
                val cancelled = e is CancellationException || !currentCoroutineContext().isActive
                if (cancelled) {
                    Log.d(TAG, "download cancelled by user")
                    if (_uiState.value is UpdateUiState.Downloading) {
                        _uiState.value = cancelledState(info, forceUpdate)
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
        downloadJob = job
        job.invokeOnCompletion {
            if (downloadJob === job) downloadJob = null
        }
    }

    /** 取消正在进行的下载 */
    fun cancelDownload() {
        val state = _uiState.value as? UpdateUiState.Downloading ?: return
        downloadJob?.cancel()
        activeDownloadCall?.cancel()
        _uiState.value = cancelledState(state.info, state.forceUpdate)
    }

    private fun cancelledState(info: UpdateInfo, forceUpdate: Boolean): UpdateUiState =
        if (forceUpdate) UpdateUiState.UpdateReady(info, forceUpdate = true) else UpdateUiState.Idle

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
        val targetFile = apkFileFor(info)
        cleanupOldApks(targetFile)

        val tmpFile = File(targetFile.parentFile, targetFile.name + ".tmp")
        targetFile.parentFile?.mkdirs()

        if (targetFile.exists()) {
            val reusable = runCatching {
                validateDownloadedFile(targetFile, info)
            }.onFailure {
                Log.w(TAG, "cached APK invalid, downloading again: ${it.message}")
            }.isSuccess
            if (reusable) {
                publishReadyToInstall(info, targetFile, forceUpdate)
                return
            }
            targetFile.delete()
        }

        var lastFailure: Throwable? = null
        for ((index, url) in info.downloadUrls().withIndex()) {
            if (!currentCoroutineContext().isActive) throw CancellationException("用户取消")
            tmpFile.delete()
            _uiState.value = UpdateUiState.Downloading(
                info = info,
                progress = -1,
                downloadedBytes = 0,
                totalBytes = 0,
                forceUpdate = forceUpdate
            )
            try {
                downloadFromSource(url, tmpFile, info, forceUpdate)
                validateDownloadedFile(tmpFile, info)

                if (targetFile.exists() && !targetFile.delete()) {
                    throw RuntimeException("无法替换旧安装包")
                }
                if (!tmpFile.renameTo(targetFile)) {
                    throw RuntimeException("文件保存失败")
                }

                publishReadyToInstall(info, targetFile, forceUpdate)
                return
            } catch (e: Throwable) {
                tmpFile.delete()
                if (e is CancellationException || !currentCoroutineContext().isActive) {
                    throw CancellationException("用户取消").apply { initCause(e) }
                }
                lastFailure = e
                Log.w(TAG, "APK source[$index] failed: $url", e)
            }
        }

        throw RuntimeException(lastFailure?.message ?: "所有下载源均不可用", lastFailure)
    }

    private suspend fun downloadFromSource(
        url: String,
        tmpFile: File,
        info: UpdateInfo,
        forceUpdate: Boolean
    ) {
        val job = currentCoroutineContext()[Job]
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Ahu_Plus/${BuildConfig.VERSION_NAME} (Android)")
            .get()
            .build()

        Log.d(TAG, "downloading APK from $url")
        val call = client.newCall(request)
        activeDownloadCall = call
        try {
            call.execute().use { response ->
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
                        if (job?.isActive == false) throw CancellationException("用户取消")
                    }
                }
            }
        } finally {
            if (activeDownloadCall === call) activeDownloadCall = null
        }
    }

    private fun validateDownloadedFile(file: File, info: UpdateInfo) {
        if (info.sha256.isNotBlank()) {
            val actual = sha256Hex(file)
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                throw RuntimeException("文件校验失败,请重试")
            }
        } else {
            Log.w(TAG, "version metadata has no SHA-256; relying on APK signature validation")
        }
        validateApkIdentity(file, info)
    }

    private fun publishReadyToInstall(info: UpdateInfo, targetFile: File, forceUpdate: Boolean) {
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

    private fun validateApkIdentity(file: File, info: UpdateInfo) {
        val archiveInfo = getArchivePackageInfo(file)
            ?: throw RuntimeException("安装包无法解析")
        if (archiveInfo.packageName != context.packageName) {
            throw RuntimeException("安装包应用标识不匹配")
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersionCode != info.latestVersionCode.toLong()) {
            throw RuntimeException("安装包版本号与更新信息不一致")
        }

        val installedInfo = getInstalledPackageInfo()
            ?: throw RuntimeException("无法读取当前应用签名")
        val installedCertificates = signingCertificateDigests(installedInfo)
        val archiveCertificates = signingCertificateDigests(archiveInfo)
        if (installedCertificates.isEmpty() ||
            archiveCertificates.isEmpty() ||
            installedCertificates.intersect(archiveCertificates).isEmpty()
        ) {
            throw RuntimeException("安装包签名与当前应用不一致")
        }
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(file: File): PackageInfo? {
        val flags = signingInfoFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfo(): PackageInfo? = runCatching {
        val flags = signingInfoFlags()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }.getOrNull()

    private fun signingInfoFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signingCertificateDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            }
        } else {
            packageInfo.signatures
        }.orEmpty()
        return signatures.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun apkDir(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val dir = File(baseDir, APK_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw RuntimeException("无法创建更新目录")
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
        return digest.digest().toHexString()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}

/** 自动更新 UI 状态机 */
sealed class UpdateUiState {
    /** 无更新待办,或已显式关闭 */
    object Idle : UpdateUiState()

    /** 发现新版本,等待用户选择 "立即更新 / 以后再说 / 忽略此版本" */
    data class UpdateReady(
        val info: com.ahu_plus.data.model.UpdateInfo,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 正在下载;progress=-1 表示总大小未知,UI 用 indeterminate 动画 */
    data class Downloading(
        val info: com.ahu_plus.data.model.UpdateInfo,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 下载失败,允许重试或关闭 */
    data class DownloadFailed(
        val info: com.ahu_plus.data.model.UpdateInfo,
        val message: String,
        val forceUpdate: Boolean
    ) : UpdateUiState()

    /** 下载完成,已触发系统安装 Intent;用户可重试安装 */
    data class ReadyToInstall(
        val info: com.ahu_plus.data.model.UpdateInfo,
        val apkFile: java.io.File,
        val forceUpdate: Boolean
    ) : UpdateUiState()
}
