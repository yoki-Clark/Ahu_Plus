package com.ahu_plus.ui.screen.developer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModuleRunCoordinatorTest {
    @Test
    fun `batch excludes individual module tests until it finishes`() {
        val coordinator = DeveloperModuleRunCoordinator()

        assertTrue(coordinator.tryStartBatch())
        assertFalse(coordinator.tryStartSingle("session"))

        coordinator.finishBatch()

        assertTrue(coordinator.tryStartSingle("session"))
    }

    @Test
    fun `active individual module tests prevent a batch and finish independently`() {
        val coordinator = DeveloperModuleRunCoordinator()

        assertTrue(coordinator.tryStartSingle("session"))
        assertTrue(coordinator.tryStartSingle("cache"))
        assertFalse(coordinator.tryStartBatch())

        coordinator.finishSingle("session")

        assertFalse(coordinator.tryStartBatch())

        coordinator.finishSingle("cache")

        assertTrue(coordinator.tryStartBatch())
    }
}
