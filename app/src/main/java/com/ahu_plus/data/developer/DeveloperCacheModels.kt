package com.ahu_plus.data.developer

/**
 * Runtime type of a value stored in Preferences DataStore.
 */
enum class DeveloperPreferenceType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING_SET,
    UNKNOWN,
}

enum class DeveloperJsonState {
    NOT_APPLICABLE,
    VALID,
    INVALID,
}

/**
 * Functional grouping used by the developer cache inspector.
 */
enum class DeveloperCacheCategory {
    AUTHENTICATION,
    ACADEMIC,
    CAMPUS_SERVICES,
    MARKET,
    CHAOXING,
    WELEARN,
    C_PROGRAMMING,
    PUBLIC_DATA,
    APP_SETTINGS,
    DEVELOPER,
    OTHER,
}

/**
 * A deliberately value-free representation of a DataStore entry.
 *
 * Raw values are never retained here. This makes the model safe to pass to UI and export code.
 * [estimatedBytes] is the UTF-8 key size plus an estimate of the encoded value size; it is not
 * the exact protobuf file size on disk.
 */
data class DeveloperPreferenceEntry(
    val keyName: String,
    val type: DeveloperPreferenceType,
    val estimatedBytes: Long,
    val summary: String,
    val sensitive: Boolean,
    val jsonState: DeveloperJsonState,
    val jsonRecordCount: Int?,
    val category: DeveloperCacheCategory,
)

data class DeveloperCacheCategorySummary(
    val category: DeveloperCacheCategory,
    val entryCount: Int,
    val estimatedBytes: Long,
    val sensitiveEntryCount: Int,
    val validJsonCount: Int,
    val invalidJsonCount: Int,
)

data class DeveloperCacheReport(
    val entries: List<DeveloperPreferenceEntry>,
    val totalEntryCount: Int,
    val totalEstimatedBytes: Long,
    val sensitiveEntryCount: Int,
    val validJsonCount: Int,
    val invalidJsonCount: Int,
    val categories: List<DeveloperCacheCategorySummary>,
)
