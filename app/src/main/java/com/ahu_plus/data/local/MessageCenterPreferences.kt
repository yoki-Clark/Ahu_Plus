package com.ahu_plus.data.local

val MESSAGE_PREVIEW_COUNT_OPTIONS = listOf(0, 3, 10)

internal fun normalizeMessagePreviewCount(value: Int): Int =
    value.takeIf { it in MESSAGE_PREVIEW_COUNT_OPTIONS } ?: 3
