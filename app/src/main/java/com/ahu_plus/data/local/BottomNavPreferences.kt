package com.ahu_plus.data.local

object BottomNavService {
    const val MARKET = "market"
    const val CHAOXING = "chaoxing"
    const val WELEARN = "welearn"

    val all = listOf(MARKET, CHAOXING, WELEARN)
}

internal fun normalizeBottomNavServices(
    selected: List<String>,
    enabled: Set<String>,
): List<String> = selected
    .asSequence()
    .filter { it in enabled && it in BottomNavService.all }
    .distinct()
    .take(2)
    .toList()

internal fun defaultBottomNavServices(enabled: Set<String>): List<String> =
    BottomNavService.all.filter { it in enabled }.take(2)
