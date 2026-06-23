# ============================================================
# Ahu_Plus ProGuard Rules
# ============================================================

# ---------- Gson ----------
# Keep all model classes with @SerializedName (used by Gson reflection)
-keepattributes Signature
-keepattributes *Annotation*

# Keep all data model classes used for Gson serialization/deserialization
-keep class com.yourname.ahu_plus.data.model.** { *; }
-keep class com.yourname.ahu_plus.data.model.jw.** { *; }
-keep class com.yourname.ahu_plus.data.model.course.** { *; }
-keep class com.yourname.ahu_plus.data.model.task.** { *; }

# Gson specific
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---------- Jsoup ----------
-keep class org.jsoup.** { *; }

# ---------- Coil SVG ----------
-keep class coil.decode.** { *; }

# ---------- ZXing ----------
-keep class com.google.zxing.** { *; }

# ---------- AndroidX Glance ----------
-keep class androidx.glance.** { *; }

# ---------- AndroidX Work (WorkManager, used by Glance) ----------
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.impl.** { *; }

# ---------- AndroidX Room (used by WorkManager) ----------
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Database
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ---------- AndroidX Startup ----------
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer

# ---------- Security Crypto ----------
-keep class androidx.security.crypto.** { *; }

# ---------- Kotlin Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------- Kotlin Serialization (if ever added) ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================
# 2026-06-22: 防逆向加固（内测阶段，隐藏测试功能）
# ============================================================

# 激进混淆：合并包路径，所有未 keep 的类归入单一 internal 包
-repackageclasses 'com.yourname.ahu_plus.internal'

# 允许 R8 修改访问修饰符（public → private），利于内联/优化
-allowaccessmodification

# ⚠️ 2026-06-23 移除以下两条激进规则（break OkHttp + 自定义 Dns）:
#   -overloadaggressively    → 让 Kotlin suspend / 重载方法名混淆,OkHttp Builder DSL 找不到
#   -mergeinterfacesaggressively → 把 okhttp3.Dns 接口跟其它接口合并,ResilientDns 失去 override 语义
# 替代方案: 在下面精准 -keep 我们自己依赖的接口实现 + OkHttp 关键 enum/常量

# ---------- 移除日志（release 不输出任何日志） ----------
# 2026-06-24 安全审查:把 w/e 也加进来,Log.w/Log.e 中可能含 cookie/异常栈
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ---------- 不保留局部变量名（减少调试信息泄露） ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# 2026-06-23: 修复 R8 把 OkHttp 自定义 Dns 实现吃掉
# ============================================================

# 保留 OkHttp Dns 接口 (我们的 ResilientDns 实现了它)
-keep interface okhttp3.Dns { *; }

# 保留 OkHttp Protocol enum 的 values()/valueOf() 和所有常量
# (ExamDataRepository 用 .protocols(listOf(Protocol.HTTP_1_1)),需要反射能拿到常量)
-keepclassmembers enum okhttp3.Protocol {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class okhttp3.Protocol {
    public static final ** HTTP_1_1;
    public static final ** HTTP_2;
    public static final ** SPDY_3;
}

# 保留 OkHttp ConnectionSpec 及其内部 Builder
# (.connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)))
-keep class okhttp3.ConnectionSpec { *; }
-keep class okhttp3.ConnectionSpec$Companion { *; }
-keep class okhttp3.ConnectionSpec$Builder { *; }
-keepclassmembers class okhttp3.ConnectionSpec {
    public static final ** COMPATIBLE_TLS;
    public static final ** MODERN_TLS;
    public static final ** CLEARTEXT;
}

# 保留我们自己的抗污染 DNS 实现 (Kotlin object → public static final INSTANCE 字段)
# 不 keep 的话 R8 会把 ResilientDns.INSTANCE 改名或内联,OkHttp.Builder.dns(...) 拿到的是无名实例
-keep class com.yourname.ahu_plus.data.network.ResilientDns { *; }

# 保留 ExamDataRepository 的 OkHttpClient DSL 构建路径
# (里面用了 ConnectionSpec / Protocol / Protocol listOf 等反射敏感的 API)
-keep class com.yourname.ahu_plus.data.repository.ExamDataRepository { *; }
