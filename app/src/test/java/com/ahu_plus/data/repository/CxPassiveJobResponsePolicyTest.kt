package com.ahu_plus.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class CxPassiveJobResponsePolicyTest {

    @Test
    fun `accepts confirmed passive task completion`() {
        assertTrue(CxPassiveJobResponsePolicy.validate("document", 200, "{\"status\":true}").isSuccess)
    }

    @Test
    fun `rejects server refusal`() {
        assertTrue(
            CxPassiveJobResponsePolicy.validate(
                "read",
                200,
                "{\"status\":false,\"msg\":\"未开放\"}",
            ).isFailure,
        )
    }

    @Test
    fun `rejects traffic challenge page even with http 200`() {
        assertTrue(
            CxPassiveJobResponsePolicy.validate(
                "audio",
                200,
                "<html><title>安全验证</title><div>captcha</div></html>",
            ).isFailure,
        )
    }

    @Test
    fun `rejects non successful http status`() {
        assertTrue(CxPassiveJobResponsePolicy.validate("live", 500, "").isFailure)
    }
}
