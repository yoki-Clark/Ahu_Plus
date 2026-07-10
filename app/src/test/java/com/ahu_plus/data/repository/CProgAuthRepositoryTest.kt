package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CProgAuthRepositoryTest {

    @Test
    fun `webvpn wrapped legacy base url falls back to campus direct endpoint`() {
        val wrapped = "https://wvpn.ahu.edu.cn/http-8080/opaque-resource-id"

        assertEquals(
            CProgAuthRepository.DEFAULT_BASE_URL,
            CProgAuthRepository.normalizeBaseUrl(wrapped),
        )
    }

    @Test
    fun `direct custom endpoint is retained and trailing slash removed`() {
        assertEquals(
            "http://10.0.0.8:8080",
            CProgAuthRepository.normalizeBaseUrl("http://10.0.0.8:8080/"),
        )
    }

    @Test
    fun `blank base url uses default`() {
        assertEquals(
            CProgAuthRepository.DEFAULT_BASE_URL,
            CProgAuthRepository.normalizeBaseUrl("  "),
        )
    }

    @Test
    fun `webvpn ajax endpoint includes upstream marker`() {
        val url = CProgAuthRepository.buildEndpointUrl(
            baseUrl = CProgAuthRepository.WEBVPN_BASE_URL,
            path = "/site/achievement/gradeTable/query",
            query = mapOf("tk" to "jwt"),
            proxiedAjax = true,
        )

        assertEquals(
            setOf("vpn-12-o1-172.17.106.232:8080", "tk"),
            url.queryParameterNames,
        )
    }
}
