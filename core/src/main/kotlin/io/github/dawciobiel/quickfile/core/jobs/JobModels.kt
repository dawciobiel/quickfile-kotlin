package io.github.dawciobiel.quickfile.core.jobs

import java.time.Instant
import java.util.UUID

typealias JobId = String

enum class OperationKind {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    ARCHIVE,
}

enum class JobStatus {
    PENDING,
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class JobProgress(
    val completedUnits: Long,
    val totalUnits: Long,
    val details: String? = null,
)

data class LogLine(
    val timestamp: Instant,
    val message: String,
)

data class JobSnapshot(
    val jobId: JobId,
    val kind: OperationKind,
    val status: JobStatus,
    val progress: JobProgress? = null,
    val recentLogLines: List<LogLine> = emptyList(),
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun newJobId(): JobId = UUID.randomUUID().toString()
