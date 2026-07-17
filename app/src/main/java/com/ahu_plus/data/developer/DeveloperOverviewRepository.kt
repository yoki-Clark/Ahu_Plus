package com.ahu_plus.data.developer

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.BuildConfig
import java.security.MessageDigest
import java.text.DecimalFormat
import java.time.ZoneId
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class DeveloperStatusKind { PASS, WARNING, FAIL, INFO }

data class DeveloperStatusItem(
    val id: String,
    val title: String,
    val value: String,
    val detail: String = "",
    val kind: DeveloperStatusKind = DeveloperStatusKind.INFO,
)

data class DeveloperOverview(
    val build: List<DeveloperStatusItem>,
    val device: List<DeveloperStatusItem>,
    val permissions: List<DeveloperStatusItem>,
    val sessions: List<DeveloperStatusItem>,
    val runtime: List<DeveloperStatusItem>,
)

class DeveloperOverviewRepository(
    private val app: AhuPlusApplication,
) {
    private val context: Context = app.applicationContext

    fun snapshot(): DeveloperOverview = DeveloperOverview(
        build = buildInfo(),
        device = deviceInfo(),
        permissions = permissionInfo(),
        sessions = sessionInfo(),
        runtime = runtimeInfo(),
    )

    private fun buildInfo(): List<DeveloperStatusItem> {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return listOf(
            info("version", "应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
            info("build_type", "构建类型", BuildConfig.BUILD_TYPE),
            info("application_id", "Application ID", BuildConfig.APPLICATION_ID),
            info(
                "debuggable",
                "可调试构建",
                yesNo(BuildConfig.DEBUG),
                kind = boolKind(BuildConfig.DEBUG),
            ),
            info("installer", "安装来源", installerPackage().ifBlank { "未知/本地安装" }),
            info("first_install", "首次安装时间", packageInfo.firstInstallTime.toString()),
            info("last_update", "最后更新时间", packageInfo.lastUpdateTime.toString()),
            info("signature", "签名 SHA-256", signingCertificateDigest().ifBlank { "不可用" }),
        )
    }

    private fun deviceInfo(): List<DeveloperStatusItem> {
        val webView = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
        return listOf(
            info("model", "设备", "${Build.MANUFACTURER} ${Build.MODEL}"),
            info("android", "Android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"),
            info("abi", "ABI", Build.SUPPORTED_ABIS.joinToString()),
            info("locale", "语言区域", Locale.getDefault().toLanguageTag()),
            info("timezone", "时区", ZoneId.systemDefault().id),
            info("webview", "WebView", webView ?: "不可用", kind = if (webView == null) DeveloperStatusKind.WARNING else DeveloperStatusKind.INFO),
            info("security_provider", "首选 TLS Provider", java.security.Security.getProviders().firstOrNull()?.name ?: "未知"),
        )
    }

    private fun permissionInfo(): List<DeveloperStatusItem> = buildList {
        add(permission("notifications", "通知权限", Manifest.permission.POST_NOTIFICATIONS, minApi = 33))
        add(permission("camera", "相机权限", Manifest.permission.CAMERA))
        add(permission("fine_location", "精确位置", Manifest.permission.ACCESS_FINE_LOCATION))
        add(permission("coarse_location", "大致位置", Manifest.permission.ACCESS_COARSE_LOCATION))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exactAlarm = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        add(info("exact_alarm", "精确闹钟", yesNo(exactAlarm), kind = boolKind(exactAlarm)))

        val overlay = Settings.canDrawOverlays(context)
        add(info("overlay", "悬浮窗权限", yesNo(overlay), kind = boolKind(overlay)))

        val power = context.getSystemService(PowerManager::class.java)
        val ignoringBattery = power.isIgnoringBatteryOptimizations(context.packageName)
        add(info("battery", "忽略电池优化", yesNo(ignoringBattery), kind = if (ignoringBattery) DeveloperStatusKind.PASS else DeveloperStatusKind.WARNING))

        val installPackages = if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true
        add(info("install_packages", "安装未知应用", yesNo(installPackages), kind = boolKind(installPackages)))

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channels = if (Build.VERSION.SDK_INT >= 26) notificationManager.notificationChannels.size else 0
        add(info("channels", "通知渠道", "$channels 个"))
    }

    private fun sessionInfo(): List<DeveloperStatusItem> {
        val session = app.sessionManager
        val casCookies = runCatching {
            app.casAuthRepository.getCookieJar()
                .loadForRequest("https://one.ahu.edu.cn/".toHttpUrl())
        }.getOrDefault(emptyList())
        return listOf(
            sessionItem("credentials", "统一身份凭据", session.hasCredentials(), "仅显示存在状态"),
            sessionItem("cas", "CAS / 门户", app.casAuthRepository.getJsessionid() != null, "Cookie ${casCookies.size} 个"),
            sessionItem("jw", "教务系统", app.jwAuthRepository.getJwSessionId() != null, if (app.jwAuthRepository.getJwPstSid() != null) "SESSION + PSTSID" else "SESSION"),
            sessionItem("ycard", "一卡通账单", app.ycardRepository.hasSession(), "JWT + SESSION"),
            sessionItem("adwmh", "智慧安大支付码", app.adwmhCardRepository.hasSession(), "TLS 1.2"),
            sessionItem("market", "校园集市", !app.marketRepository.getSavedIdentity().isNullOrBlank(), "已配置 ${app.marketRepository.getAllIdentities().size} 个身份"),
            sessionItem("chaoxing", "超星学习通", app.chaoxingRepository.isLoggedIn(), "独立 Cookie"),
            sessionItem("welearn", "WeLearn", app.weLearnAuthRepository.isLoggedIn(), "独立 Cookie"),
            sessionItem("cprog", "C 语言平台", app.cProgAuthRepository.isLoggedIn(), "JWT + JSESSIONID"),
            sessionItem("evaluation", "评教服务", !session.getEvaluationJwt().isNullOrBlank(), "JWT"),
        )
    }

    private fun runtimeInfo(): List<DeveloperStatusItem> {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        val network = activeNetworkDescription()
        return listOf(
            info(
                "process_uptime",
                "进程运行时间",
                formatDuration(
                    (SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis())
                        .coerceAtLeast(0L),
                ),
            ),
            info("heap", "Java 堆", "${formatBytes(usedHeap)} / ${formatBytes(runtime.maxMemory())}"),
            info("system_memory", "系统可用内存", formatBytes(memory.availMem), if (memory.lowMemory) "系统报告低内存" else "", if (memory.lowMemory) DeveloperStatusKind.WARNING else DeveloperStatusKind.INFO),
            info("files_storage", "内部文件空间", "${formatBytes(filesDir.freeSpace)} 可用 / ${formatBytes(filesDir.totalSpace)}"),
            info("cache_storage", "缓存目录", "${formatBytes(cacheDir.usableSpace)} 可用"),
            info("storage_state", "外部存储状态", Environment.getExternalStorageState()),
            info("network", "当前网络", network.first, network.second, if (network.first == "不可用") DeveloperStatusKind.FAIL else DeveloperStatusKind.INFO),
            info("threads", "活动线程", Thread.activeCount().toString()),
            info("processors", "可用处理器", runtime.availableProcessors().toString()),
        )
    }

    private fun permission(id: String, title: String, permission: String, minApi: Int = 1): DeveloperStatusItem {
        if (Build.VERSION.SDK_INT < minApi) return info(id, title, "系统版本无需申请", kind = DeveloperStatusKind.INFO)
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return info(id, title, yesNo(granted), kind = boolKind(granted))
    }

    private fun sessionItem(id: String, title: String, active: Boolean, detail: String) =
        info(id, title, if (active) "已就绪" else "未就绪", detail, boolKind(active))

    private fun activeNetworkDescription(): Pair<String, String> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return "不可用" to "没有活动网络"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "不可用" to "无法读取网络能力"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "其他网络"
        }
        val details = buildList {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("已验证")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) add("需门户认证")
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) add("计费网络")
        }.joinToString(" · ")
        return transport to details
    }

    @Suppress("DEPRECATION")
    private fun installerPackage(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName.orEmpty()
        } else {
            context.packageManager.getInstallerPackageName(context.packageName).orEmpty()
        }
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    private fun signingCertificateDigest(): String = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo?.apkContentsSigners.orEmpty()
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            ).signatures.orEmpty()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrDefault("")

    private fun info(
        id: String,
        title: String,
        value: String,
        detail: String = "",
        kind: DeveloperStatusKind = DeveloperStatusKind.INFO,
    ) = DeveloperStatusItem(id, title, value, detail, kind)

    private fun boolKind(value: Boolean) = if (value) DeveloperStatusKind.PASS else DeveloperStatusKind.WARNING
    private fun yesNo(value: Boolean) = if (value) "已允许" else "未允许"

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1_024L) return "$bytes B"
        val formatter = DecimalFormat("0.0")
        val kib = bytes / 1_024.0
        if (kib < 1_024.0) return "${formatter.format(kib)} KiB"
        val mib = kib / 1_024.0
        if (mib < 1_024.0) return "${formatter.format(mib)} MiB"
        return "${formatter.format(mib / 1_024.0)} GiB"
    }
}
