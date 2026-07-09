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
        versionCode = 25
        versionName = "2.2.2.1"

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
    // 优先使用 local.properties 中配置的正式签名;
    // 未配置时回退到本机 Android SDK 自带的 debug.keystore(仅限本机自测,
    // 不应分发安装包)。开源版本 fork 后请务必生成自己的 keystore。
    signingConfigs {
        create("release") {
            val storeFilePath = localProp("AHU_RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProp("AHU_RELEASE_STORE_PASSWORD")
                    ?: error("AHU_RELEASE_STORE_PASSWORD 未在 local.properties 配置")
                keyAlias = localProp("AHU_RELEASE_KEY_ALIAS")
                    ?: error("AHU_RELEASE_KEY_ALIAS 未在 local.properties 配置")
                keyPassword = localProp("AHU_RELEASE_KEY_PASSWORD")
                    ?: error("AHU_RELEASE_KEY_PASSWORD 未在 local.properties 配置")
            } else {
                // Fallback: 本机 debug.keystore,密码已知,仅用于本地自测
                val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
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
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.conscrypt.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // AboutLibraries 依赖已临时移除 (Aliyun 镜像未缓存 11.6.1),改用 OpenSourceLicensesScreen 手写列表
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
