package com.ahu_plus.data.developer

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Pure inspection and redaction logic for Preferences DataStore values.
 *
 * The only public output is [DeveloperCacheReport], which never contains a raw value. Keep all
 * exports routed through this object so future callers cannot accidentally bypass redaction.
 */
object DeveloperCacheInspector {
    const val REDACTED_SUMMARY = "<redacted>"

    private val exportGson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    private val strictJsonGson: Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    private val sensitiveFragments = listOf(
        "password",
        "passwd",
        "token",
        "cookie",
        "session",
        "jsessionid",
        "identity",
        "identities",
        "jwt",
        "api_key",
        "api-key",
        "apikey",
        "qrcode",
        "auth",
        "authorization",
        "credential",
        "private_key",
        "privatekey",
        "access_key",
        "accesskey",
        "secret",
        "castgc",
        "ticket",
    )

    private val privateDataFragments = listOf(
        "username",
        "phone",
        "idno",
        "user_id",
        "student_info",
        "finance",
        "attendance",
        "bills_json",
        "grades_json",
        "exams_json",
        "course_note_",
        "slot_note_",
        "sign_address",
        "sign_lat",
        "sign_lon",
        "custom_sign_locations",
        "notify_url",
        "notify_tg_chat_id",
        "tiku_config",
    )

    private val recordArrayKeys = listOf(
        "items",
        "records",
        "rows",
        "list",
        "data",
        "results",
        "courses",
        "messages",
        "notices",
        "exams",
        "grades",
        "tasks",
    )

    fun inspect(values: Map<String, Any>): DeveloperCacheReport {
        val entries = values.entries
            .map { (keyName, value) -> inspectEntry(keyName, value) }
            .sortedBy { it.keyName }

        val categories = entries
            .groupBy { it.category }
            .map { (category, groupedEntries) ->
                DeveloperCacheCategorySummary(
                    category = category,
                    entryCount = groupedEntries.size,
                    estimatedBytes = groupedEntries.sumOf { it.estimatedBytes },
                    sensitiveEntryCount = groupedEntries.count { it.sensitive },
                    validJsonCount = groupedEntries.count {
                        it.jsonState == DeveloperJsonState.VALID
                    },
                    invalidJsonCount = groupedEntries.count {
                        it.jsonState == DeveloperJsonState.INVALID
                    },
                )
            }
            .sortedBy { it.category.ordinal }

        return DeveloperCacheReport(
            entries = entries,
            totalEntryCount = entries.size,
            totalEstimatedBytes = entries.sumOf { it.estimatedBytes },
            sensitiveEntryCount = entries.count { it.sensitive },
            validJsonCount = entries.count { it.jsonState == DeveloperJsonState.VALID },
            invalidJsonCount = entries.count { it.jsonState == DeveloperJsonState.INVALID },
            categories = categories,
        )
    }

    fun inspectEntry(keyName: String, value: Any): DeveloperPreferenceEntry {
        val sensitive = isSensitiveKey(keyName)
        val jsonInspection = if (value is String) inspectJson(keyName, value) else JsonInspection.NONE

        return DeveloperPreferenceEntry(
            keyName = keyName,
            type = preferenceTypeOf(value),
            estimatedBytes = estimateBytes(keyName, value),
            summary = summarize(value, sensitive, jsonInspection),
            sensitive = sensitive,
            jsonState = jsonInspection.state,
            jsonRecordCount = jsonInspection.recordCount,
            category = categorizeKey(keyName),
        )
    }

    fun isSensitiveKey(keyName: String): Boolean {
        val normalized = keyName.lowercase(Locale.ROOT)
        val tokens = normalized.split(Regex("[^a-z0-9]+"))

        return sensitiveFragments.any(normalized::contains) ||
            privateDataFragments.any(normalized::contains) ||
            "pwd" in tokens ||
            "qr" in tokens ||
            "bearer" in tokens ||
            normalized.endsWith("_ai_key") ||
            normalized.endsWith("_siliconflow_key") ||
            normalized.endsWith("_provider_key")
    }

    fun categorizeKey(keyName: String): DeveloperCacheCategory {
        val key = keyName.lowercase(Locale.ROOT)
        return when {
            key.startsWith("developer_") || key.startsWith("dev_") ->
                DeveloperCacheCategory.DEVELOPER

            key.startsWith("market_") -> DeveloperCacheCategory.MARKET
            key.startsWith("cx_") || key.startsWith("chaoxing_") ->
                DeveloperCacheCategory.CHAOXING

            key.startsWith("welearn_") -> DeveloperCacheCategory.WELEARN
            key.startsWith("cprog_") -> DeveloperCacheCategory.C_PROGRAMMING
            containsAny(
                key,
                "schedule",
                "semester",
                "grade",
                "gpa_",
                "exam",
                "training_plan",
                "empty_classroom",
                "course_note_",
                "slot_note_",
                "assessment_",
                "evaluation_",
                "homework_",
                "record_index",
            ) -> DeveloperCacheCategory.ACADEMIC

            containsAny(
                key,
                "student_info",
                "finance",
                "attendance",
                "kqcard",
                "adwmh",
                "bill",
                "bathroom",
                "ac_config",
                "lighting_config",
                "campus_config",
                "user_tasks",
            ) -> DeveloperCacheCategory.CAMPUS_SERVICES

            containsAny(key, "weather", "announcement", "jwc_notice", "building_floors") ->
                DeveloperCacheCategory.PUBLIC_DATA

            isAuthenticationKey(key) -> DeveloperCacheCategory.AUTHENTICATION
            isAppSettingKey(key) -> DeveloperCacheCategory.APP_SETTINGS
            else -> DeveloperCacheCategory.OTHER
        }
    }

    fun exportRedactedText(report: DeveloperCacheReport): String = buildString {
        appendLine("Developer DataStore report (redacted)")
        appendLine("entries=${report.totalEntryCount}")
        appendLine("estimatedBytes=${report.totalEstimatedBytes}")
        appendLine("sensitiveEntries=${report.sensitiveEntryCount}")
        appendLine("validJson=${report.validJsonCount}")
        appendLine("invalidJson=${report.invalidJsonCount}")
        appendLine()
        appendLine("Categories:")
        report.categories.forEach { category ->
            append(category.category.name)
            append(" entries=${category.entryCount}")
            append(" estimatedBytes=${category.estimatedBytes}")
            append(" sensitive=${category.sensitiveEntryCount}")
            append(" validJson=${category.validJsonCount}")
            appendLine(" invalidJson=${category.invalidJsonCount}")
        }
        appendLine()
        appendLine("Entries:")
        report.entries.forEach { entry ->
            append(entry.keyName)
            append(" | type=${entry.type.name}")
            append(" | category=${entry.category.name}")
            append(" | estimatedBytes=${entry.estimatedBytes}")
            append(" | sensitive=${entry.sensitive}")
            append(" | json=${entry.jsonState.name}")
            entry.jsonRecordCount?.let { append(" | records=$it") }
            appendLine(" | summary=${entry.summary}")
        }
    }

    fun exportRedactedJson(report: DeveloperCacheReport): String {
        val root = JsonObject().apply {
            addProperty("redacted", true)
            addProperty("totalEntryCount", report.totalEntryCount)
            addProperty("totalEstimatedBytes", report.totalEstimatedBytes)
            addProperty("sensitiveEntryCount", report.sensitiveEntryCount)
            addProperty("validJsonCount", report.validJsonCount)
            addProperty("invalidJsonCount", report.invalidJsonCount)
            add("categories", JsonArray().apply {
                report.categories.forEach { summary ->
                    add(JsonObject().apply {
                        addProperty("category", summary.category.name)
                        addProperty("entryCount", summary.entryCount)
                        addProperty("estimatedBytes", summary.estimatedBytes)
                        addProperty("sensitiveEntryCount", summary.sensitiveEntryCount)
                        addProperty("validJsonCount", summary.validJsonCount)
                        addProperty("invalidJsonCount", summary.invalidJsonCount)
                    })
                }
            })
            add("entries", JsonArray().apply {
                report.entries.forEach { entry ->
                    add(JsonObject().apply {
                        addProperty("keyName", entry.keyName)
                        addProperty("type", entry.type.name)
                        addProperty("estimatedBytes", entry.estimatedBytes)
                        addProperty("summary", entry.summary)
                        addProperty("sensitive", entry.sensitive)
                        addProperty("jsonState", entry.jsonState.name)
                        entry.jsonRecordCount?.let { addProperty("jsonRecordCount", it) }
                        addProperty("category", entry.category.name)
                    })
                }
            })
        }
        return exportGson.toJson(root)
    }

    private fun preferenceTypeOf(value: Any): DeveloperPreferenceType = when (value) {
        is String -> DeveloperPreferenceType.STRING
        is Boolean -> DeveloperPreferenceType.BOOLEAN
        is Int -> DeveloperPreferenceType.INT
        is Long -> DeveloperPreferenceType.LONG
        is Float -> DeveloperPreferenceType.FLOAT
        is Double -> DeveloperPreferenceType.DOUBLE
        is Set<*> -> DeveloperPreferenceType.STRING_SET
        else -> DeveloperPreferenceType.UNKNOWN
    }

    private fun estimateBytes(keyName: String, value: Any): Long =
        utf8Size(keyName) + when (value) {
            is String -> utf8Size(value)
            is Boolean -> 1L
            is Int, is Float -> 4L
            is Long, is Double -> 8L
            is Set<*> -> value.sumOf { item -> utf8Size(item?.toString().orEmpty()) } +
                (value.size - 1).coerceAtLeast(0)
            else -> utf8Size(value::class.java.name)
        }

    private fun summarize(
        value: Any,
        sensitive: Boolean,
        jsonInspection: JsonInspection,
    ): String {
        if (sensitive) return REDACTED_SUMMARY

        if (jsonInspection.state == DeveloperJsonState.INVALID) {
            return "Invalid JSON"
        }
        jsonInspection.element?.let { return summarizeJson(it, jsonInspection.recordCount) }

        return when (value) {
            is String -> summarizeText(value)
            is Boolean, is Int, is Long, is Float, is Double -> value.toString()
            is Set<*> -> "String set (${value.size} items)"
            else -> "Unsupported value (${value::class.java.simpleName})"
        }
    }

    private fun summarizeJson(element: JsonElement, recordCount: Int?): String = when {
        element.isJsonArray -> "JSON array (${element.asJsonArray.size()} items)"
        element.isJsonObject -> {
            val suffix = recordCount?.let { ", records=$it" }.orEmpty()
            "JSON object (${element.asJsonObject.size()} fields$suffix)"
        }
        element.isJsonNull -> "JSON null"
        else -> "JSON primitive"
    }

    private fun summarizeText(value: String): String {
        if (value.isEmpty()) return "<empty>"
        return "Text (${value.length} chars)"
    }

    private fun inspectJson(keyName: String, value: String): JsonInspection {
        val trimmed = value.trim()
        if (!isJsonCandidate(keyName, trimmed)) return JsonInspection.NONE

        return runCatching {
            require(trimmed.isNotEmpty())
            strictJsonGson.fromJson(trimmed, JsonElement::class.java)
                ?: error("JSON parser returned null")
        }
            .fold(
                onSuccess = { element ->
                    JsonInspection(
                        state = DeveloperJsonState.VALID,
                        recordCount = inferRecordCount(element),
                        element = element,
                    )
                },
                onFailure = {
                    JsonInspection(
                        state = DeveloperJsonState.INVALID,
                        recordCount = null,
                        element = null,
                    )
                },
            )
    }

    private fun isJsonCandidate(keyName: String, trimmedValue: String): Boolean {
        if (trimmedValue.startsWith('{') || trimmedValue.startsWith('[')) return true
        val key = keyName.lowercase(Locale.ROOT)
        return containsAny(
            key,
            "_json",
            "_cache",
            "_config",
            "_identities",
            "_locations",
            "_options",
        )
    }

    private fun inferRecordCount(element: JsonElement): Int? {
        if (element.isJsonArray) return element.asJsonArray.size()
        if (!element.isJsonObject) return null

        val root = element.asJsonObject
        findRecordArray(root)?.let { return it.size() }

        listOf("data", "result", "payload").forEach { containerName ->
            val container = root.get(containerName)
            if (container?.isJsonObject == true) {
                findRecordArray(container.asJsonObject)?.let { return it.size() }
            }
        }
        return null
    }

    private fun findRecordArray(jsonObject: JsonObject): JsonArray? {
        recordArrayKeys.forEach { key ->
            val candidate = jsonObject.get(key)
            if (candidate?.isJsonArray == true) return candidate.asJsonArray
        }
        return null
    }

    private fun isAuthenticationKey(key: String): Boolean = containsAny(
        key,
        "session",
        "jsessionid",
        "username",
        "password",
        "cookie",
        "token",
        "jwt",
        "identity",
        "authorization",
        "credential",
    )

    private fun isAppSettingKey(key: String): Boolean = containsAny(
        key,
        "theme",
        "enabled",
        "layout",
        "font_scale",
        "row_height",
        "col_width",
        "palette",
        "show_",
        "recent_apps",
        "favorite_app",
        "reminder",
        "agenda_",
        "guide_",
        "ignored_version",
        "beta_",
    )

    private fun containsAny(value: String, vararg fragments: String): Boolean =
        fragments.any(value::contains)

    private fun utf8Size(value: String): Long =
        value.toByteArray(StandardCharsets.UTF_8).size.toLong()

    private data class JsonInspection(
        val state: DeveloperJsonState,
        val recordCount: Int?,
        val element: JsonElement?,
    ) {
        companion object {
            val NONE = JsonInspection(
                state = DeveloperJsonState.NOT_APPLICABLE,
                recordCount = null,
                element = null,
            )
        }
    }
}
