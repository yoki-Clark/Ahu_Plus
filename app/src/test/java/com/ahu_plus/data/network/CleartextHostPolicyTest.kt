package com.ahu_plus.data.network

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class CleartextHostPolicyTest {
    @Test
    fun `code allowlist matches Android network security config`() {
        val xml = File("src/main/res/xml/network_security_config.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val nodes = document.getElementsByTagName("domain")
        val configured = buildSet {
            for (index in 0 until nodes.length) {
                nodes.item(index).textContent.trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }

        assertEquals(CleartextHostPolicy.allowedHosts, configured)
    }
}
