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

internal fun migrateLegacyBottomNavServices(
    selected: List<String>,
    enabled: Set<String>,
): List<String> {
    val retained = normalizeBottomNavServices(selected, enabled)
    val fill = BottomNavService.all.filter { it in enabled && it !in retained }
    return (retained + fill).take(2)
}

/**
 * Reconciles availability changes without overwriting an explicit unpin.
 * Only services that became enabled since the last snapshot may fill a free slot.
 */
internal fun reconcileBottomNavServices(
    selected: List<String>,
    previouslyEnabled: Set<String>,
    currentlyEnabled: Set<String>,
): List<String> {
    val retained = normalizeBottomNavServices(selected, currentlyEnabled)
    val newlyEnabled = BottomNavService.all.filter {
        it in currentlyEnabled && it !in previouslyEnabled && it !in retained
    }
    return (retained + newlyEnabled).take(2)
}
