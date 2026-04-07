package io.github.dawciobiel.quickfile.core.jobs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

interface JobRegistry {
    val allJobsFlow: StateFlow<List<JobSnapshot>>

    fun jobFlow(jobId: JobId): StateFlow<JobSnapshot>?

    fun createJob(
        kind: OperationKind,
        initialStatus: JobStatus = JobStatus.PENDING,
        initialMessage: String = "Job created.",
    ): JobSnapshot

    fun updateJob(
        jobId: JobId,
        status: JobStatus,
        progress: JobProgress? = null,
        message: String? = null,
        errorMessage: String? = null,
    ): JobSnapshot?

    fun appendLog(
        jobId: JobId,
        message: String,
    ): JobSnapshot?
}

class InMemoryJobRegistry(
    private val retainedTerminalJobs: Int = 50,
    private val maxLogLinesPerJob: Int = 200,
) : JobRegistry {
    private val jobFlows = linkedMapOf<JobId, MutableStateFlow<JobSnapshot>>()
    private val mutableAllJobsFlow = MutableStateFlow<List<JobSnapshot>>(emptyList())

    override val allJobsFlow: StateFlow<List<JobSnapshot>> = mutableAllJobsFlow.asStateFlow()

    override fun jobFlow(jobId: JobId): StateFlow<JobSnapshot>? = jobFlows[jobId]?.asStateFlow()

    override fun createJob(
        kind: OperationKind,
        initialStatus: JobStatus,
        initialMessage: String,
    ): JobSnapshot {
        val now = Instant.now()
        val snapshot =
            JobSnapshot(
                jobId = newJobId(),
                kind = kind,
                status = initialStatus,
                recentLogLines = listOf(LogLine(timestamp = now, message = initialMessage)),
                createdAt = now,
                updatedAt = now,
            )

        jobFlows[snapshot.jobId] = MutableStateFlow(snapshot)
        refreshAllJobs()
        return snapshot
    }

    override fun updateJob(
        jobId: JobId,
        status: JobStatus,
        progress: JobProgress?,
        message: String?,
        errorMessage: String?,
    ): JobSnapshot? {
        val flow = jobFlows[jobId] ?: return null
        val now = Instant.now()
        flow.update { current ->
            current.copy(
                status = status,
                progress = progress ?: current.progress,
                recentLogLines =
                    message?.let {
                        (current.recentLogLines + LogLine(timestamp = now, message = it))
                            .takeLast(maxLogLinesPerJob)
                    } ?: current.recentLogLines,
                errorMessage = errorMessage,
                updatedAt = now,
            )
        }
        refreshAllJobs()
        return flow.value
    }

    override fun appendLog(
        jobId: JobId,
        message: String,
    ): JobSnapshot? {
        val flow = jobFlows[jobId] ?: return null
        val now = Instant.now()
        flow.update { current ->
            current.copy(
                recentLogLines =
                    (current.recentLogLines + LogLine(timestamp = now, message = message))
                        .takeLast(maxLogLinesPerJob),
                updatedAt = now,
            )
        }
        refreshAllJobs()
        return flow.value
    }

    private fun refreshAllJobs() {
        val snapshots = jobFlows.values.map { it.value }
        val (active, terminal) =
            snapshots.partition { snapshot ->
                snapshot.status !in setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)
            }

        mutableAllJobsFlow.value =
            (active + terminal.takeLast(retainedTerminalJobs))
                .sortedByDescending { it.updatedAt }
    }
}
