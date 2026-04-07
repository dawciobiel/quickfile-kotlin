package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.core.jobs.OperationKind
import io.github.dawciobiel.quickfile.core.jobs.JobProgress
import io.github.dawciobiel.quickfile.core.jobs.JobStatus

sealed interface FileOperationEvent {
    data class StatusChanged(
        val status: JobStatus,
        val message: String? = null,
    ) : FileOperationEvent

    data class ProgressReported(
        val progress: JobProgress,
    ) : FileOperationEvent

    data class LogReported(
        val message: String,
    ) : FileOperationEvent
}

interface FileOperationsGateway {
    suspend fun startOperation(
        jobId: String,
        kind: OperationKind,
        sourceLocation: String,
        destinationLocation: String? = null,
        onEvent: (FileOperationEvent) -> Unit,
    )

    suspend fun cancel(jobId: String): Boolean
}
