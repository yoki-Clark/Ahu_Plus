package com.ahu_plus.data.local

import java.time.Instant
import java.time.ZoneId

/**
 * Central freshness rules for persisted snapshots.
 *
 * A zero/negative timestamp is always stale. Failed network attempts must never update the
 * timestamp, so a later page entry can retry naturally.
 */
object DataRefreshPolicy {
    fun isStale(
        updatedAt: Long,
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (updatedAt <= 0L) return true
        val age = nowMillis - updatedAt
        return age < 0L || age >= maxAgeMillis
    }

    fun wasUpdatedToday(
        updatedAt: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (updatedAt <= 0L) return false
        val updatedDate = Instant.ofEpochMilli(updatedAt).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return updatedDate == today
    }
}
