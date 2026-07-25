package com.ahu_plus.ui.navigation

import android.content.Intent
import com.ahu_plus.data.GsonProvider

object NavigationIntentCodec {
    const val EXTRA_NAVIGATION_TARGET = "com.ahu_plus.navigation.TARGET_V1"
    const val LEGACY_EXTRA_DEEP_LINK = "deep_link"

    private data class RequestRecord(
        val target: NavigationTargetRecord,
        val source: String,
    )

    fun encode(request: NavigationRequest): String = GsonProvider.instance.toJson(
        RequestRecord(
            target = NavigationTargetCodec.toRecord(request.target),
            source = request.source.name,
        )
    )

    fun put(intent: Intent, request: NavigationRequest): Intent =
        intent.putExtra(EXTRA_NAVIGATION_TARGET, encode(request))

    fun decode(intent: Intent?): NavigationRequest? {
        val encoded = intent?.getStringExtra(EXTRA_NAVIGATION_TARGET)
        if (!encoded.isNullOrBlank()) {
            decodeEncoded(encoded)?.let { return it }
        }
        return legacyTarget(intent?.getStringExtra(LEGACY_EXTRA_DEEP_LINK))?.let {
            NavigationRequest(it, NavigationSource.DEEP_LINK)
        }
    }

    /**
     * 从 encode() 产出的 JSON 字符串解码导航请求,不依赖 Intent,便于纯 JVM 测试。
     */
    internal fun decodeEncoded(encoded: String): NavigationRequest? {
        return runCatching {
            GsonProvider.instance.fromJson(encoded, RequestRecord::class.java)
        }.getOrNull()?.let { record ->
            val target = NavigationTargetCodec.fromRecord(record.target) ?: return@decodeEncoded null
            val source = NavigationSource.entries.firstOrNull { it.name == record.source }
                ?: NavigationSource.DEEP_LINK
            NavigationRequest(target, source)
        }
    }

    fun legacyTarget(value: String?): NavigationTarget? = when (value) {
        "schedule" -> HomeTarget(HomeRoute.SCHEDULE)
        "agenda" -> HomeTarget(HomeRoute.AGENDA)
        "grade" -> HomeTarget(HomeRoute.GRADE)
        "chaoxing" -> ChaoxingTarget()
        "welearn" -> WeLearnTarget()
        else -> null
    }
}
