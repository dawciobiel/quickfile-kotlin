package io.github.dawciobiel.quickfile.core.jobs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryJobRegistryTest {
    @Test
    fun `createJob adds snapshot and initial log`() {
        val registry = InMemoryJobRegistry()

        val created =
            registry.createJob(
                kind = OperationKind.COPY,
                initialStatus = JobStatus.PENDING,
                initialMessage = "Queued copy job.",
            )

        val stored = registry.jobFlow(created.jobId)?.value
        assertNotNull(stored)
        assertEquals(OperationKind.COPY, stored.kind)
        assertEquals(JobStatus.PENDING, stored.status)
        assertEquals("Queued copy job.", stored.recentLogLines.single().message)
        assertEquals(listOf(created.jobId), registry.allJobsFlow.value.map(JobSnapshot::jobId))
    }

    @Test
    fun `updateJob changes status progress and error`() {
        val registry = InMemoryJobRegistry()
        val created = registry.createJob(OperationKind.MOVE, initialMessage = "Created.")

        val updated =
            registry.updateJob(
                jobId = created.jobId,
                status = JobStatus.FAILED,
                progress = JobProgress(completedUnits = 7, totalUnits = 10, details = "70%"),
                message = "Write failed.",
                errorMessage = "Disk full.",
            )

        assertNotNull(updated)
        assertEquals(JobStatus.FAILED, updated.status)
        assertEquals(7, updated.progress?.completedUnits)
        assertEquals("Disk full.", updated.errorMessage)
        assertEquals("Write failed.", updated.recentLogLines.last().message)
    }

    @Test
    fun `retains active jobs and trims terminal jobs`() {
        val registry = InMemoryJobRegistry(retainedTerminalJobs = 1)

        val firstCompleted = registry.createJob(OperationKind.COPY, initialStatus = JobStatus.COMPLETED)
        val secondCompleted = registry.createJob(OperationKind.MOVE, initialStatus = JobStatus.COMPLETED)
        val running = registry.createJob(OperationKind.DELETE, initialStatus = JobStatus.RUNNING)

        val retainedIds = registry.allJobsFlow.value.map(JobSnapshot::jobId)

        assertEquals(2, retainedIds.size)
        assertEquals(running.jobId, retainedIds.first())
        assertEquals(secondCompleted.jobId, retainedIds.last())
        assertNull(registry.allJobsFlow.value.find { it.jobId == firstCompleted.jobId })
    }

    @Test
    fun `clearFinished removes only terminal jobs`() {
        val registry = InMemoryJobRegistry()
        val completed = registry.createJob(OperationKind.COPY, initialStatus = JobStatus.COMPLETED)
        val failed = registry.createJob(OperationKind.MOVE, initialStatus = JobStatus.FAILED)
        val running = registry.createJob(OperationKind.DELETE, initialStatus = JobStatus.RUNNING)

        val removed = registry.clearFinished()

        assertEquals(2, removed)
        assertNull(registry.jobFlow(completed.jobId))
        assertNull(registry.jobFlow(failed.jobId))
        assertNotNull(registry.jobFlow(running.jobId))
        assertEquals(listOf(running.jobId), registry.allJobsFlow.value.map(JobSnapshot::jobId))
    }
}
