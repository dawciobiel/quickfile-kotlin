package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import io.github.dawciobiel.quickfile.core.jobs.JobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobSnapshot
import io.github.dawciobiel.quickfile.core.jobs.JobProgress
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind
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

data class JobsState(
    val summary: String,
    val jobs: List<JobSnapshot> = emptyList(),
)

interface JobsComponent {
    val state: StateFlow<JobsState>

    fun launchDemoJob()

    fun cancelJob(jobId: String)
}

class DefaultJobsComponent(
    componentContext: ComponentContext,
    private val jobRegistry: JobRegistry,
) : JobsComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var demoJobCounter = 0
    private val runningDemoJobs = linkedMapOf<String, Job>()
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
                }
            },
        )

        scope.launch {
            jobRegistry.allJobsFlow.collect { jobs ->
                mutableState.value =
                    JobsState(
                        summary = buildSummary(jobs),
                        jobs = jobs,
                    )
            }
        }
    }

    override fun launchDemoJob() {
        val demoIndex = ++demoJobCounter
        val job =
            scope.launch {
                val kind = if (demoIndex % 2 == 0) OperationKind.MOVE else OperationKind.COPY
                val snapshot =
                    jobRegistry.createJob(
                        kind = kind,
                        initialStatus = JobStatus.PENDING,
                        initialMessage = "Queued demo ${kind.name.lowercase()} job.",
                    )
                runningDemoJobs[snapshot.jobId] =
                    kotlinx.coroutines.currentCoroutineContext()[Job] ?: return@launch

                delay(250)
                jobRegistry.updateJob(
                    jobId = snapshot.jobId,
                    status = JobStatus.RUNNING,
                    progress = JobProgress(10, 100, "10%"),
                    message = "Preparing source entries...",
                )

                delay(400)
                jobRegistry.updateJob(
                    jobId = snapshot.jobId,
                    status = JobStatus.RUNNING,
                    progress = JobProgress(55, 100, "55%"),
                    message = "Transferring data...",
                )

                delay(400)
                val shouldFail = demoIndex % 3 == 0
                if (shouldFail) {
                    jobRegistry.updateJob(
                        jobId = snapshot.jobId,
                        status = JobStatus.FAILED,
                        progress = JobProgress(72, 100, "72%"),
                        message = "I/O error while writing destination file.",
                        errorMessage = "Destination device stopped responding.",
                    )
                } else {
                    jobRegistry.updateJob(
                        jobId = snapshot.jobId,
                        status = JobStatus.COMPLETED,
                        progress = JobProgress(100, 100, "100%"),
                        message = "Job completed successfully.",
                    )
                }
                runningDemoJobs.remove(snapshot.jobId)
            }
        job.invokeOnCompletion {
            runningDemoJobs.entries.removeIf { (_, runningJob) -> runningJob == job }
        }
    }

    override fun cancelJob(jobId: String) {
        val runningJob = runningDemoJobs[jobId] ?: return
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
}
