package com.ahu_plus.data.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkDiagnosticModelsTest {
    @Test
    fun `preset ids are unique and every endpoint is HTTPS`() {
        val hosts = NetworkDiagnosticHosts.all

        assertTrue(hosts.isNotEmpty())
        assertEquals(hosts.size, hosts.map { it.id }.toSet().size)
        assertTrue(hosts.all { it.url.startsWith("https://") })
        assertTrue(hosts.all { it.method == NetworkProbeMethod.HEAD })
    }

    @Test
    fun `AHU hosts automatically use compatibility certificate policy`() {
        val ahuHosts = NetworkDiagnosticHosts.forCategory(NetworkDiagnosticCategory.AHU)

        assertTrue(ahuHosts.isNotEmpty())
        assertTrue(ahuHosts.all { it.usesAhuCertificateCompatibility })
        assertFalse(NetworkDiagnosticHosts.find("market")!!.usesAhuCertificateCompatibility)
    }

    @Test
    fun `adwmh alone forces TLS 1_2`() {
        val adwmh = NetworkDiagnosticHosts.find("ahu_adwmh")

        assertNotNull(adwmh)
        assertTrue(adwmh!!.requiresTls12)
        assertTrue(NetworkDiagnosticHosts.all.filterNot { it.id == "ahu_adwmh" }.none { it.requiresTls12 })
    }

    @Test
    fun `custom non HTTPS endpoint is rejected`() {
        try {
            NetworkHostSpec(
                id = "unsafe",
                displayName = "Unsafe",
                url = "http://example.com/",
                category = NetworkDiagnosticCategory.PUBLIC_DATA,
            )
            fail("Expected an IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `AHU matching does not accept suffix lookalikes`() {
        assertTrue(NetworkDiagnosticRules.isAhuHost("AHU.EDU.CN"))
        assertTrue(NetworkDiagnosticRules.isAhuHost("one.ahu.edu.cn"))
        assertFalse(NetworkDiagnosticRules.isAhuHost("evilahu.edu.cn"))
        assertFalse(NetworkDiagnosticRules.isAhuHost("ahu.edu.cn.example.com"))
    }
}
