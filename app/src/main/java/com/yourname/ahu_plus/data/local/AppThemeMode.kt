package com.yourname.ahu_plus.data.local

enum class AppThemeMode(val storageValue: String) {
    DAY("day"),
    DARK("dark"),
    SYSTEM("system");

    fun shouldUseDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
        DAY -> false
        DARK -> true
        SYSTEM -> systemDarkTheme
    }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
