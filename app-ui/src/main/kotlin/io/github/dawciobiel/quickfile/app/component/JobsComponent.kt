package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import io.github.dawciobiel.quickfile.core.jobs.JobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobSnapshot
import io.github.dawciobiel.quickfile.core.jobs.JobProgress
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind
import io.github.dawciobiel.quickfile.pluginruntime.FileOperationEvent
import io.github.dawciobiel.quickfile.pluginruntime.FileOperationsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class JobsFilter {
    ALL,
    ACTIVE,
    FINISHED,
}

data class JobsState(
    val summary: String,
    val jobs: List<JobSnapshot> = emptyList(),
    val selectedFilter: JobsFilter = JobsFilter.ALL,
    val finishedJobsCount: Int = 0,
)

interface JobsComponent {
    val state: StateFlow<JobsState>

    fun launchDemoJob()

    fun launchFileOperationJob(
        kind: OperationKind,
        entryName: String,
        sourceLocation: String,
        destinationLocation: String? = null,
    )

    fun cancelJob(jobId: String)

    fun setFilter(filter: JobsFilter)

    fun clearFinished()
}

class DefaultJobsComponent(
    componentContext: ComponentContext,
    private val jobRegistry: JobRegistry,
    private val fileOperationsGateway: FileOperationsGateway,
    private val onOperationTerminal: () -> Unit = {},
) : JobsComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var demoJobCounter = 0
    private val runningDemoJobs = linkedMapOf<String, Job>()
    private val runningOperationJobs = linkedSetOf<String>()
    private val mutableState =
        MutableStateFlow(
            JobsState(
                summary = "No jobs yet.",
            ),
        )

    override val state: StateFlow<JobsState> = mutableState.asStateFlow()

    init {
        lifecycle.subscribe(
            object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    scope.cancel()
                    (fileOperationsGateway as? AutoCloseable)?.close()
                }
            },
        )

        scope.launch {
            jobRegistry.allJobsFlow.collect { jobs ->
                val filter = mutableState.value.selectedFilter
                val finishedJobsCount = jobs.count { it.status in terminalStatuses }
                mutableState.value =
                    JobsState(
                        summary = buildSummary(jobs),
                        jobs = jobs.filterFor(filter),
                        selectedFilter = filter,
                        finishedJobsCount = finishedJobsCount,
                    )
            }
        }
    }

    override fun launchDemoJob() {
        val demoIndex = ++demoJobCounter
        val kind = if (demoIndex % 2 == 0) OperationKind.MOVE else OperationKind.COPY
        scheduleSimulatedJob(
            kind = kind,
            queuedMessage = "Queued demo ${kind.name.lowercase()} job.",
            runningStartMessage = "Preparing source entries...",
            transferMessage = "Transferring data...",
            completionMessage = "Job completed successfully.",
            failureMessage = "I/O error while writing destination file.",
            failureError = "Destination device stopped responding.",
            shouldFail = demoIndex % 3 == 0,
        )
    }

    override fun launchFileOperationJob(
        kind: OperationKind,
        entryName: String,
        sourceLocation: String,
        destinationLocation: String?,
    ) {
        val snapshot =
            jobRegistry.createJob(
                kind = kind,
                initialStatus = JobStatus.PENDING,
                initialMessage =
                    when (kind) {
                        OperationKind.COPY -> "Queued copy of $entryName to $destinationLocation."
                        OperationKind.MOVE -> "Queued move of $entryName to $destinationLocation."
                        OperationKind.RENAME -> "Queued rename of $entryName to $destinationLocation."
                        OperationKind.DELETE -> "Queued delete of $entryName."
                        else -> "Queued ${kind.name.lowercase()} of $entryName."
                    },
            )
        runningOperationJobs += snapshot.jobId

        scope.launch {
            runCatching {
                fileOperationsGateway.startOperation(
                    jobId = snapshot.jobId,
                    kind = kind,
                    sourceLocation = sourceLocation,
                    destinationLocation = destinationLocation,
                ) { event ->
                    applyOperationEvent(snapshot.jobId, event)
                }
            }.onFailure { error ->
                jobRegistry.updateJob(
                    jobId = snapshot.jobId,
                    status = JobStatus.FAILED,
                    message = "${kind.name.lowercase().replaceFirstChar(Char::uppercase)} execution failed.",
                    errorMessage = error.message ?: "Unknown ${kind.name.lowercase()} error.",
                )
            }
            runningOperationJobs -= snapshot.jobId
        }
    }

    override fun cancelJob(jobId: String) {
        runningDemoJobs[jobId]?.let { runningJob ->
            scope.launch {
                jobRegistry.updateJob(
                    jobId = jobId,
                    status = JobStatus.CANCELLING,
                    message = "Cancelling job...",
                )
                runningJob.cancel()
                delay(120)
                jobRegistry.updateJob(
                    jobId = jobId,
                    status = JobStatus.CANCELLED,
                    message = "Job cancelled by user.",
                )
            }
            return
        }

        if (jobId in runningOperationJobs) {
            scope.launch {
                jobRegistry.updateJob(
                    jobId = jobId,
                    status = JobStatus.CANCELLING,
                    message = "Cancelling job...",
                )
                if (!fileOperationsGateway.cancel(jobId)) {
                    jobRegistry.appendLog(jobId, "Cancellation request was not accepted.")
                }
            }
        }
    }

    override fun setFilter(filter: JobsFilter) {
        val allJobs = jobRegistry.allJobsFlow.value
        mutableState.value =
            mutableState.value.copy(
                selectedFilter = filter,
                jobs = allJobs.filterFor(filter),
                finishedJobsCount = allJobs.count { it.status in terminalStatuses },
                summary = buildSummary(allJobs),
            )
    }

    override fun clearFinished() {
        jobRegistry.clearFinished()
    }

    private fun buildSummary(jobs: List<JobSnapshot>): String {
        if (jobs.isEmpty()) {
            return "No jobs yet."
        }

        val running = jobs.count { it.status == JobStatus.RUNNING }
        val pending = jobs.count { it.status == JobStatus.PENDING }
        val failed = jobs.count { it.status == JobStatus.FAILED }
        return "${jobs.size} tracked job(s) | running: $running | pending: $pending | failed: $failed"
    }

    private fun scheduleSimulatedJob(
        kind: OperationKind,
        queuedMessage: String,
        runningStartMessage: String,
        transferMessage: String,
        completionMessage: String,
        failureMessage: String,
        failureError: String?,
        shouldFail: Boolean,
    ) {
        val job =
            scope.launch {
                val snapshot =
                    jobRegistry.createJob(
                        kind = kind,
                        initialStatus = JobStatus.PENDING,
                        initialMessage = queuedMessage,
                    )
                runningDemoJobs[snapshot.jobId] =
                    kotlinx.coroutines.currentCoroutineContext()[Job] ?: return@launch

                delay(250)
                jobRegistry.updateJob(
                    jobId = snapshot.jobId,
                    status = JobStatus.RUNNING,
                    progress = JobProgress(10, 100, "10%"),
                    message = runningStartMessage,
                )

                delay(400)
                jobRegistry.updateJob(
                    jobId = snapshot.jobId,
                    status = JobStatus.RUNNING,
                    progress = JobProgress(55, 100, "55%"),
                    message = transferMessage,
                )

                delay(400)
                if (shouldFail) {
                    jobRegistry.updateJob(
                        jobId = snapshot.jobId,
                        status = JobStatus.FAILED,
                        progress = JobProgress(72, 100, "72%"),
                        message = failureMessage,
                        errorMessage = failureError,
                    )
                } else {
                    jobRegistry.updateJob(
                        jobId = snapshot.jobId,
                        status = JobStatus.COMPLETED,
                        progress = JobProgress(100, 100, "100%"),
                        message = completionMessage,
                    )
                }
                runningDemoJobs.remove(snapshot.jobId)
            }
        job.invokeOnCompletion {
            runningDemoJobs.entries.removeIf { (_, runningJob) -> runningJob == job }
        }
    }

    private fun applyOperationEvent(
        jobId: String,
        event: FileOperationEvent,
    ) {
        when (event) {
            is FileOperationEvent.LogReported -> {
                jobRegistry.appendLog(jobId, event.message)
            }

            is FileOperationEvent.ProgressReported -> {
                val currentStatus = jobRegistry.jobFlow(jobId)?.value?.status ?: JobStatus.RUNNING
                jobRegistry.updateJob(
                    jobId = jobId,
                    status = if (currentStatus == JobStatus.PENDING) JobStatus.RUNNING else currentStatus,
                    progress = event.progress,
                )
            }

            is FileOperationEvent.StatusChanged -> {
                jobRegistry.updateJob(
                    jobId = jobId,
                    status = event.status,
                    message = event.message,
                    errorMessage = if (event.status == JobStatus.FAILED) event.message else null,
                )
                if (event.status in terminalStatuses) {
                    onOperationTerminal()
                }
            }
        }
    }

    private companion object {
        val terminalStatuses = setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)
    }
}

private fun List<JobSnapshot>.filterFor(filter: JobsFilter): List<JobSnapshot> =
    when (filter) {
        JobsFilter.ALL -> this
        JobsFilter.ACTIVE -> filter { it.status !in setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED) }
        JobsFilter.FINISHED -> filter { it.status in setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED) }
    }
