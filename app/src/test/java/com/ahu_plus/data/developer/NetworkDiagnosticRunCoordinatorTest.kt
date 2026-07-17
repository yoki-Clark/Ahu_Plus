package com.ahu_plus.data.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticRunCoordinatorTest {
    @Test
    fun `batch excludes individual probes and row cancel targets the batch`() {
        val coordinator = NetworkDiagnosticRunCoordinator()

        assertTrue(coordinator.tryStartBatch())
        coordinator.startBatchHost("ahu_cas")

        assertFalse(coordinator.tryStartSingle("market"))
        assertEquals(setOf("ahu_cas"), coordinator.runningIds)
        assertEquals(
            NetworkDiagnosticCancelTarget.BATCH,
            coordinator.cancelTarget("ahu_cas"),
        )

        coordinator.finishBatchHost("ahu_cas")
        coordinator.finishBatch()

        assertFalse(coordinator.isBatchActive)
        assertTrue(coordinator.runningIds.isEmpty())
        assertTrue(coordinator.tryStartSingle("market"))
    }

    @Test
    fun `active individual probes prevent a batch and finish independently`() {
        val coordinator = NetworkDiagnosticRunCoordinator()

        assertTrue(coordinator.tryStartSingle("market"))
        assertTrue(coordinator.tryStartSingle("weather"))
        assertFalse(coordinator.tryStartBatch())
        assertEquals(
            NetworkDiagnosticCancelTarget.SINGLE,
            coordinator.cancelTarget("market"),
        )

        coordinator.finishSingle("market")

        assertEquals(setOf("weather"), coordinator.runningIds)
        assertEquals(
            NetworkDiagnosticCancelTarget.NONE,
            coordinator.cancelTarget("market"),
        )
        assertFalse(coordinator.tryStartBatch())

        coordinator.finishSingle("weather")
        assertTrue(coordinator.tryStartBatch())
    }

    @Test
    fun `only the current batch row can cancel the batch`() {
        val coordinator = NetworkDiagnosticRunCoordinator()

        assertTrue(coordinator.tryStartBatch())
        coordinator.startBatchHost("ahu_jw")

        assertEquals(
            NetworkDiagnosticCancelTarget.NONE,
            coordinator.cancelTarget("market"),
        )
        assertEquals(
            NetworkDiagnosticCancelTarget.BATCH,
            coordinator.cancelTarget("ahu_jw"),
        )
    }

    @Test
    fun `cancelled batch releases its current row before a single probe starts`() {
        val coordinator = NetworkDiagnosticRunCoordinator()

        assertTrue(coordinator.tryStartBatch())
        coordinator.startBatchHost("ahu_jw")

        // Mirrors the batch coroutine's finally block when the current host is cancelled.
        coordinator.finishBatch()

        assertFalse(coordinator.isBatchActive)
        assertTrue(coordinator.runningIds.isEmpty())
        assertEquals(
            NetworkDiagnosticCancelTarget.NONE,
            coordinator.cancelTarget("ahu_jw"),
        )
        assertTrue(coordinator.tryStartSingle("market"))
        assertEquals(
            NetworkDiagnosticCancelTarget.SINGLE,
            coordinator.cancelTarget("market"),
        )
    }
}
