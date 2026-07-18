package com.ahu_plus.data.local

/** Minimal cache contract used by the teaching-affairs notice screens. */
interface JwcNoticeCache {
    fun getJwcNoticeJson(): String?
    fun getJwcNoticeUpdatedAt(): Long
    suspend fun saveJwcNoticeJson(json: String)
    fun getJwcNoticeDetailsJson(): String?
    suspend fun saveJwcNoticeDetailsJson(json: String)
}
