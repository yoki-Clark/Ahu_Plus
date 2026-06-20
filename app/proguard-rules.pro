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
