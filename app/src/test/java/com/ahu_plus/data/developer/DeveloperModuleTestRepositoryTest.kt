package com.ahu_plus.data.developer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModuleTestRepositoryTest {
    @Test
    fun `successful execution returns summary and duration`() = runTest {
        val execution = executeDeveloperModuleTest(timeoutMillis = 1_000L) { "3 items" }

        assertEquals(DeveloperTestStatus.PASSED, execution.status)
        assertEquals("3 items", execution.result)
        assertTrue(execution.durationMillis >= 0L)
    }

    @Test
    fun `failure result exposes type but not exception message`() = runTest {
        val execution = executeDeveloperModuleTest(timeoutMillis = 1_000L) {
            throw IllegalArgumentException("credential=secret-value")
        }

        assertEquals(DeveloperTestStatus.FAILED, execution.status)
        assertTrue(execution.result.contains("IllegalArgumentException"))
        assertFalse(execution.result.contains("secret-value"))
    }

    @Test
    fun `unavailable dependency is reported as skipped`() = runTest {
        val execution = executeDeveloperModuleTest(timeoutMillis = 1_000L) {
            throw DeveloperTestUnavailableException("account is not configured")
        }

        assertEquals(DeveloperTestStatus.SKIPPED, execution.status)
        assertEquals("account is not configured", execution.result)
    }

    @Test
    fun `execution is bounded by timeout`() = runTest {
        val execution = executeDeveloperModuleTest(timeoutMillis = 10L) {
            delay(100L)
            "too late"
        }

        assertEquals(DeveloperTestStatus.TIMED_OUT, execution.status)
    }

    @Test(expected = CancellationException::class)
    fun `external cancellation is not swallowed`() = runTest {
        executeDeveloperModuleTest(timeoutMillis = 1_000L) {
            throw CancellationException("cancelled by caller")
        }
    }
}
