package com.ahu_plus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwSessionResponseClassifierTest {
    @Test fun casRedirectIsExpired() {
        assertTrue(JwSessionResponseClassifier.isExpired(302, "/cas/login?service=jw"))
    }

    @Test fun unrelatedRedirectIsNotExpired() {
        assertFalse(JwSessionResponseClassifier.isExpired(302, "/student/home"))
    }

    @Test fun loginFormIsExpired() {
        assertTrue(JwSessionResponseClassifier.isExpired(200, body = "<input name=\"lt\">") )
    }

    @Test fun serverAndNetworkStyleErrorsDoNotProveExpiry() {
        assertFalse(JwSessionResponseClassifier.isExpired(500))
        assertFalse(JwSessionResponseClassifier.isExpired(503))
    }
}
