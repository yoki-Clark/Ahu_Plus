package com.yourname.ahu_plus.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.components.AhuShapes
import com.yourname.ahu_plus.util.BrowserOpener

/**
 * 开源协议页 (2026-06-22 新增)。
 *
 * 手写列表（避免引入外部 aboutlibraries 库导致的 APK 膨胀）。
 * 列出本应用使用的所有开源项目、版本、用途、License。
 *
 * 数据来源：[app/build.gradle.kts] + [gradle/libs.versions.toml]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开源协议") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
        ) {
            item {
                Text(
                    text = "本应用使用了以下开源项目,感谢各位作者的贡献。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(OPEN_SOURCE_LICENSES) { entry ->
                LicenseCard(entry)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "每个 License 全文请访问对应项目主页查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private data class OpenSourceEntry(
    val name: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val description: String,
    val license: String,
    val category: String,
    val url: String? = null,
)

@Composable
private fun LicenseCard(entry: OpenSourceEntry) {
    val context = LocalContext.current
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
            .then(
                if (entry.url != null) {
                    Modifier.clickable { BrowserOpener.open(context, entry.url) }
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${entry.groupId}:${entry.artifactId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                MetaTag("版本", entry.version)
                Spacer(modifier = Modifier.size(8.dp))
                MetaTag("License", entry.license)
            }
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "分类: ${entry.category}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun MetaTag(label: String, value: String) {
    Box(
        modifier = Modifier
            .padding(end = 0.dp),
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 项目使用的开源项目清单。
 *
 * 维护约定:每次 build.gradle.kts 新增依赖,在此同步添加一条。
 */
private val OPEN_SOURCE_LICENSES = listOf(
    // ── AndroidX ─────────────────────────────────────────
    OpenSourceEntry(
        name = "AndroidX Core KTX",
        groupId = "androidx.core",
        artifactId = "core-ktx",
        version = "1.10.1",
        description = "Kotlin 扩展,用于 Android 核心库 (Activity Context / SharedPreferences 等)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Lifecycle Runtime KTX",
        groupId = "androidx.lifecycle",
        artifactId = "lifecycle-runtime-ktx",
        version = "2.6.1",
        description = "Lifecycle 协程支持 (lifecycleScope 等)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Activity Compose",
        groupId = "androidx.activity",
        artifactId = "activity-compose",
        version = "1.8.0",
        description = "Compose 与 Activity 的集成 (setContent / BackHandler 等)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Compose BOM",
        groupId = "androidx.compose",
        artifactId = "compose-bom",
        version = "2026.02.01",
        description = "Jetpack Compose 版本对齐 BOM (UI / Material3 / Foundation 等)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Compose Material 3",
        groupId = "androidx.compose.material3",
        artifactId = "material3",
        version = "(BOM)",
        description = "Material Design 3 Compose 组件 (Card / BottomSheet / Snackbar 等)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Navigation Compose",
        groupId = "androidx.navigation",
        artifactId = "navigation-compose",
        version = "2.8.5",
        description = "应用内 Compose 路由 (NavHost + composable destinations)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX DataStore Preferences",
        groupId = "androidx.datastore",
        artifactId = "datastore-preferences",
        version = "1.1.1",
        description = "异步、事务化的本地偏好存储 (替代 SharedPreferences)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Security Crypto",
        groupId = "androidx.security",
        artifactId = "security-crypto",
        version = "1.1.0-alpha06",
        description = "基于 Tink 的 SharedPreferences / Key 加密 (用于备份 JSON 加密)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    OpenSourceEntry(
        name = "AndroidX Glance AppWidget",
        groupId = "androidx.glance",
        artifactId = "glance-appwidget",
        version = "1.1.1",
        description = "Compose 风格的桌面小部件框架 (今日课程 widget)。",
        license = "Apache-2.0",
        category = "AndroidX",
    ),
    // ── Kotlin ────────────────────────────────────────────
    OpenSourceEntry(
        name = "Kotlinx Coroutines (Android)",
        groupId = "org.jetbrains.kotlinx",
        artifactId = "kotlinx-coroutines-android",
        version = "(Kotlin 2.2.10)",
        description = "Kotlin 协程库 (Dispatchers.IO / Flow / async/await 等)。",
        license = "Apache-2.0",
        category = "Kotlin",
    ),
    // ── 网络 ──────────────────────────────────────────────
    OpenSourceEntry(
        name = "OkHttp",
        groupId = "com.squareup.okhttp3",
        artifactId = "okhttp",
        version = "4.12.0",
        description = "Square 出品的 HTTP 客户端 (网络请求 / 文件下载 / WebSocket)。",
        license = "Apache-2.0",
        category = "网络",
    ),
    // ── 序列化 ────────────────────────────────────────────
    OpenSourceEntry(
        name = "Gson",
        groupId = "com.google.code.gson",
        artifactId = "gson",
        version = "2.11.0",
        description = "Google JSON 序列化库 (云备份 JSON / 课表缓存解析)。",
        license = "Apache-2.0",
        category = "序列化",
    ),
    // ── HTML 解析 ─────────────────────────────────────────
    OpenSourceEntry(
        name = "jsoup",
        groupId = "org.jsoup",
        artifactId = "jsoup",
        version = "1.18.1",
        description = "Java HTML 解析器 (考试安排 HTML / 教务通知解析)。",
        license = "MIT",
        category = "HTML 解析",
    ),
    // ── 二维码 ────────────────────────────────────────────
    OpenSourceEntry(
        name = "ZXing Core",
        groupId = "com.google.zxing",
        artifactId = "core",
        version = "3.5.3",
        description = "多格式 1D/2D 条码识别 (备用 QR 解码工具)。",
        license = "Apache-2.0",
        category = "二维码",
    ),
    // ── 图片加载 ──────────────────────────────────────────
    OpenSourceEntry(
        name = "Coil Compose",
        groupId = "io.coil-kt",
        artifactId = "coil-compose",
        version = "2.7.0",
        description = "Kotlin 协程驱动的 Android 图片加载库 (校徽 SVG 渲染)。",
        license = "Apache-2.0",
        category = "图片加载",
    ),
    // ── TLS ──────────────────────────────────────────────
    OpenSourceEntry(
        name = "Conscrypt (Android)",
        groupId = "org.conscrypt",
        artifactId = "conscrypt-android",
        version = "2.5.2",
        description = "Google BoringSSL Java Provider,强制替换 Android 默认 TLS 解决国产 ROM 兼容问题。",
        license = "Apache-2.0",
        category = "TLS",
    ),
    // ── 参考项目 ─────────────────────────────────────────
    OpenSourceEntry(
        name = "Samueli924/chaoxing",
        groupId = "com.github",
        artifactId = "Samueli924-chaoxing",
        version = "-",
        description = "超星学习通API逆向工程参考实现，提供了课程列表/章节/任务点/签到等接口分析",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/Samueli924/chaoxing",
    ),
    OpenSourceEntry(
        name = "OpenAHU/AHUTong",
        groupId = "com.github",
        artifactId = "OpenAHU-AHUTong",
        version = "-",
        description = "安徽大学校园助手参考实现，借鉴了智慧安大 QR 码/支付码接口的认证流程",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/OpenAHU/AHUTong",
    ),
    OpenSourceEntry(
        name = "aglorice/new_xxt",
        groupId = "com.github",
        artifactId = "aglorice-new_xxt",
        version = "v0.5.6",
        description = "超星学习通作业模块 API 逆向工程参考实现，提供作业列表/题目解析/答案提交接口。",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/aglorice/new_xxt",
    ),
    OpenSourceEntry(
        name = "fossabot/FxxkStar",
        groupId = "com.github",
        artifactId = "fossabot-FxxkStar",
        version = "-",
        description = "超星学习通 API 和非官方客户端，提供最完整的课后习题 API 封装（课程/章节/作业/签到/消息等）。",
        license = "AGPL-3.0",
        category = "参考项目",
        url = "https://github.com/fossabot/FxxkStar",
    ),
    OpenSourceEntry(
        name = "cyear/chaoxing",
        groupId = "com.github",
        artifactId = "cyear-chaoxing",
        version = "-",
        description = "超星学习通自动化脚本（经典实现），提供了 DES 加密/题库对接/视频进度等基础参考。",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/cyear/chaoxing",
    ),
    OpenSourceEntry(
        name = "XilyFeAAAA/Chaoxing",
        groupId = "com.github",
        artifactId = "XilyFeAAAA-Chaoxing",
        version = "-",
        description = "超星学习通前后端刷课平台，内置题库+GPT接口，支持账号密码/二维码登录。",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/XilyFeAAAA/Chaoxing",
    ),
    OpenSourceEntry(
        name = "kukuqi666/chaoxing-script-ai",
        groupId = "com.github",
        artifactId = "kukuqi666-chaoxing-script-ai",
        version = "-",
        description = "基于 OpenAI 兼容接口的超星智能答题脚本，使用 GPT-4o-mini 自动答题。",
        license = "MIT",
        category = "参考项目",
        url = "https://github.com/kukuqi666/chaoxing-script-ai",
    ),
)
