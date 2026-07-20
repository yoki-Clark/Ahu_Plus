import groovy.json.JsonSlurper
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 2026-06-22 AboutLibraries: 离线仓库未提供 plugin marker,改为手动生成 aboutlibraries.json
    // (运行时库 aboutlibraries-compose 仍可用)
}

// ── 读取 local.properties(签名等本地敏感配置)──────────────
// 2026-06-24 安全审查:签名信息禁止硬编码,改从 local.properties 读取。
// local.properties 已在 .gitignore 中,绝不会被提交。
// 示例:
//   AHU_RELEASE_STORE_FILE=/path/to/release.jks
//   AHU_RELEASE_STORE_PASSWORD=...
//   AHU_RELEASE_KEY_ALIAS=...
//   AHU_RELEASE_KEY_PASSWORD=...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String? = localProps.getProperty(key)?.takeIf { it.isNotBlank() }

fun configuredValue(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
        ?: localProp(key)

@Suppress("UNCHECKED_CAST")
fun jsonObject(value: Any?, label: String): Map<String, Any?> =
    value as? Map<String, Any?> ?: error("$label 必须是 JSON object")

val releaseStateFile = rootProject.layout.projectDirectory.file("release/release-state.json")
check(releaseStateFile.asFile.isFile) { "缺少单一 Release 状态源: ${releaseStateFile.asFile.path}" }
val releaseStateJson = providers.fileContents(releaseStateFile).asText.get()
val releaseState = jsonObject(JsonSlurper().parseText(releaseStateJson), "release-state.json")
val releaseApplication = jsonObject(releaseState["application"], "application")
val releaseBuild = jsonObject(releaseState["build"], "build")
val releaseVersionName = releaseBuild["versionName"] as? String
    ?: error("build.versionName 必须是字符串")
val releaseVersionCode = (releaseBuild["versionCode"] as? Number)?.toInt()
    ?: error("build.versionCode 必须是整数")
val allowedReleaseCertificates = (releaseApplication["allowedSigningCertificateSha256"] as? List<*>)
    ?.mapNotNull { (it as? String)?.replace(":", "")?.uppercase() }
    ?.toSet()
    .orEmpty()
check(releaseVersionName.isNotBlank()) { "build.versionName 不能为空" }
check(releaseVersionCode > 0) { "build.versionCode 必须大于 0" }
check(allowedReleaseCertificates.isNotEmpty()) { "签名证书 allowlist 不能为空" }
check(releaseApplication["applicationId"] == "com.yourname.ahu_plus") {
    "applicationId 必须保持为 com.yourname.ahu_plus"
}

val releaseSigningKeys = listOf(
    "AHU_RELEASE_STORE_FILE",
    "AHU_RELEASE_STORE_PASSWORD",
    "AHU_RELEASE_KEY_ALIAS",
    "AHU_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningKeys.associateWith(::configuredValue)
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.ahu_plus"
    compileSdk = 36

    defaultConfig {
        // applicationId 保持原值不变:它是 App 在系统里的唯一身份标识,
        // 改了会导致老用户无法平滑升级(自动更新会并排装两个 App)、本地数据全丢。
        // 代码包名(namespace)已改为 com.ahu_plus,此处仅是历史遗留的对外身份,用户不可见。
        applicationId = "com.yourname.ahu_plus"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2026-06-22 体积优化：ABI Split（见下方 splits.abi 块）。
        // 注意：不要在这里加 ndk.abiFilters，会同时影响 universal APK,
        // 导致兜底包也只剩 arm64 libs。splits.abi.include 才是 per-variant 控制。
    }

    // 2026-06-29: 让 unit test 不抛 android.util.Log not mocked 异常
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // ── 签名配置 ─────────────────────────────────────────
    // 只有四项正式签名配置全部存在时才创建 release signingConfig。
    // Release 任务还会先执行 validateReleaseSigning；不允许回退 debug keystore。
    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["AHU_RELEASE_STORE_FILE"]))
                storePassword = requireNotNull(releaseSigningValues["AHU_RELEASE_STORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["AHU_RELEASE_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["AHU_RELEASE_KEY_PASSWORD"])
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 2026-06-22 体积优化：ABI Split
    // 输出 app-arm64-v8a-debug.apk + app-universal-debug.apk(兜底)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // 2026-06-23: 让 java.time (LocalDate/LocalTime/ZoneOffset 等) 在 minSdk 24 也能用
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless the configured Release keystore matches the certificate allowlist."

    doLast {
        val missing = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys.sorted()
        if (missing.isNotEmpty()) {
            throw GradleException("Release 签名配置缺失: ${missing.joinToString()}")
        }

        val storePath = requireNotNull(releaseSigningValues["AHU_RELEASE_STORE_FILE"])
        val store = file(storePath)
        if (!store.isFile) {
            throw GradleException("Release keystore 不存在: ${store.path}")
        }

        val storePassword = requireNotNull(releaseSigningValues["AHU_RELEASE_STORE_PASSWORD"])
        val alias = requireNotNull(releaseSigningValues["AHU_RELEASE_KEY_ALIAS"])
        val keyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                store.inputStream().use { load(it, storePassword.toCharArray()) }
            }
        } catch (_: Exception) {
            throw GradleException("Release keystore 无法使用当前配置打开")
        }
        if (!keyStore.isKeyEntry(alias)) {
            throw GradleException("Release keystore 中不存在配置的私钥别名")
        }
        val certificate = keyStore.getCertificate(alias)
            ?: throw GradleException("Release keystore 中的别名没有证书")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        if (digest !in allowedReleaseCertificates) {
            throw GradleException("Release 签名证书不在 allowlist 中: $digest")
        }
        logger.lifecycle("Release signing identity verified: $digest")
    }
}

tasks.configureEach {
    val releaseArtifactTask = name == "preReleaseBuild" ||
        Regex("^(assemble|bundle|package|sign|makeApk|zipApks).*Release.*$").matches(name)
    if (releaseArtifactTask) {
        dependsOn(validateReleaseSigning)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(libs.conscrypt.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // AboutLibraries 依赖已临时移除 (Aliyun 镜像未缓存 11.6.1),改用 OpenSourceLicensesScreen 手写列表
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
