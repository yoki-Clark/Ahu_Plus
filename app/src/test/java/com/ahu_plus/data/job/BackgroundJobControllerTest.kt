package com.ahu_plus.data.job

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class BackgroundJobControllerTest {

    @Test
    fun `persisted null records are treated as empty`() {
        assertEquals(
            emptyList<BackgroundJobRecord>(),
            decodeBackgroundJobRecords("""{"schemaVersion":1,"records":null}""", 1_000L),
        )
    }

    @Test
    fun `persisted null record element is treated as corrupt cache`() {
        assertEquals(
            emptyList<BackgroundJobRecord>(),
            decodeBackgroundJobRecords("""{"schemaVersion":1,"records":[null]}""", 1_000L),
        )
    }

    @Test
    fun `concurrent starts accept exactly one job per platform`() = runTest {
        val controller = controller()
        val command = BackgroundJobCommand(BackgroundJobPlatform.CHAOXING, BackgroundJobPayload())

        val results = List(8) { async { controller.start(command) } }.awaitAll()

        assertEquals(1, results.count { it is BackgroundJobStartResult.Accepted })
        assertEquals(7, results.count { it is BackgroundJobStartResult.Rejected })
    }

    @Test
    fun `startup converts active records to process interruption`() = runTest {
        val persistence = FakePersistence(
            mutableListOf(
                BackgroundJobRecord(
                    id = "running",
                    platform = BackgroundJobPlatform.WELEARN,
                    payload = BackgroundJobPayload(courseId = "course"),
                    phase = BackgroundJobPhase.RUNNING,
                    createdAtMillis = 100,
                )
            )
        )
        val controller = BackgroundJobController(persistence, { 200L })

        controller.initialize()

        val record = controller.records.value.single()
        assertEquals(BackgroundJobPhase.INTERRUPTED, record.phase)
        assertEquals(BackgroundJobInterruption.PROCESS_TERMINATED, record.interruption)
        assertEquals(200L, record.finishedAtMillis)
    }

    @Test
    fun `cancel is idempotent and produces terminal state`() = runTest {
        val controller = controller()
        val accepted = controller.start(
            BackgroundJobCommand(BackgroundJobPlatform.CHAOXING, BackgroundJobPayload())
        ) as BackgroundJobStartResult.Accepted
        var cancellationCalls = 0
        controller.attachCanceller(accepted.record.id) { cancellationCalls++ }

        assertTrue(controller.cancel(accepted.record.id))
        assertTrue(controller.cancel(accepted.record.id))

        assertEquals(1, cancellationCalls)
        assertEquals(BackgroundJobPhase.CANCELLED, controller.records.value.single().phase)
    }

    @Test
    fun `interrupted job resumes only when platform is idle`() = runTest {
        val controller = controller()
        val accepted = controller.start(
            BackgroundJobCommand(BackgroundJobPlatform.WELEARN, BackgroundJobPayload(courseId = "course"))
        ) as BackgroundJobStartResult.Accepted
        controller.markInterrupted(accepted.record.id, BackgroundJobInterruption.SYSTEM_TIMEOUT)

        val resumed = controller.resume(accepted.record.id)

        assertTrue(resumed is BackgroundJobStartResult.Accepted)
        assertEquals(BackgroundJobPhase.RESUMING, controller.records.value.single().phase)
    }

    @Test
    fun `network failures have a stable user category`() {
        assertEquals(
            BackgroundJobFailure.NETWORK_UNAVAILABLE,
            controller().classifyFailure(UnknownHostException("private host omitted")),
        )
    }

    @Test
    fun `history is limited per platform and expires after thirty days`() {
        val now = 100L * JOB_RETENTION_MILLIS
        val recent = (0 until 25).map { index ->
            BackgroundJobRecord(
                id = "recent-$index",
                platform = BackgroundJobPlatform.CHAOXING,
                payload = BackgroundJobPayload(),
                phase = BackgroundJobPhase.SUCCEEDED,
                createdAtMillis = now - index,
                updatedAtMillis = now - index,
            )
        }
        val expired = BackgroundJobRecord(
            id = "expired",
            platform = BackgroundJobPlatform.CHAOXING,
            payload = BackgroundJobPayload(),
            phase = BackgroundJobPhase.FAILED,
            createdAtMillis = now - JOB_RETENTION_MILLIS - 1,
            updatedAtMillis = now - JOB_RETENTION_MILLIS - 1,
        )

        val pruned = pruneBackgroundJobRecords(recent + expired, now)

        assertEquals(20, pruned.size)
        assertTrue(pruned.none { it.id == "expired" })
    }

    private fun controller(): BackgroundJobController =
        BackgroundJobController(FakePersistence(), { 1_000L })

    private class FakePersistence(
        private val values: MutableList<BackgroundJobRecord> = mutableListOf(),
    ) : BackgroundJobPersistence {
        override suspend fun load(): List<BackgroundJobRecord> = values.toList()

        override suspend fun save(records: List<BackgroundJobRecord>) {
            values.clear()
            values.addAll(records)
        }
    }
}
