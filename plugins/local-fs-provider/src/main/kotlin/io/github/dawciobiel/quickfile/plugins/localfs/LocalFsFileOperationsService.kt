package io.github.dawciobiel.quickfile.plugins.localfs

import io.grpc.Status
import io.grpc.stub.StreamObserver
import quickfile.pluginapi.v1.CancelOperationRequest
import quickfile.pluginapi.v1.CancelOperationResponse
import quickfile.pluginapi.v1.FileOperationsGrpc
import quickfile.pluginapi.v1.JobLogLine
import quickfile.pluginapi.v1.JobProgress
import quickfile.pluginapi.v1.JobStatusChanged
import quickfile.pluginapi.v1.JobUpdate
import quickfile.pluginapi.v1.OperationKind
import quickfile.pluginapi.v1.StartOperationRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class LocalFsFileOperationsService : FileOperationsGrpc.FileOperationsImplBase() {
    private val cancellationFlags = ConcurrentHashMap<String, AtomicBoolean>()

    override fun startOperation(
        request: StartOperationRequest,
        responseObserver: StreamObserver<JobUpdate>,
    ) {
        if (request.kind != OperationKind.OPERATION_KIND_COPY) {
            responseObserver.onError(
                Status.UNIMPLEMENTED
                    .withDescription("Local filesystem provider currently supports only COPY.")
                    .asRuntimeException(),
            )
            return
        }

        if (request.sourcesCount == 0) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("At least one source location is required.")
                    .asRuntimeException(),
            )
            return
        }

        val destinationRoot =
            runCatching { decodeLocation(request.target) }
                .getOrElse { error ->
                    responseObserver.onError(
                        Status.INVALID_ARGUMENT.withDescription(error.message).asRuntimeException(),
                    )
                    return
                }

        val sources =
            runCatching { request.sourcesList.map(::decodeLocation) }
                .getOrElse { error ->
                    responseObserver.onError(
                        Status.INVALID_ARGUMENT.withDescription(error.message).asRuntimeException(),
                    )
                    return
                }

        val jobId = request.jobId.value
        val cancellationFlag = AtomicBoolean(false)
        cancellationFlags[jobId] = cancellationFlag

        try {
            responseObserver.onNext(statusUpdate(jobId, JobStatusChanged.Status.STATUS_QUEUED, "Operation accepted."))
            responseObserver.onNext(statusUpdate(jobId, JobStatusChanged.Status.STATUS_RUNNING, "Copy started."))

            Files.createDirectories(destinationRoot)
            val allItems = sources.flatMap(::collectCopyUnits)
            val totalUnits = allItems.size.toLong().coerceAtLeast(1L)
            var completedUnits = 0L

            sources.forEach { sourceRoot ->
                val destinationBase = destinationRoot.resolve(sourceRoot.fileName.toString())
                collectCopyUnits(sourceRoot).forEach { sourcePath ->
                    if (cancellationFlag.get()) {
                        responseObserver.onNext(
                            statusUpdate(jobId, JobStatusChanged.Status.STATUS_CANCELLED, "Operation cancelled."),
                        )
                        responseObserver.onCompleted()
                        return
                    }

                    val targetPath =
                        destinationBase.resolve(
                            sourceRoot.relativize(sourcePath).toString(),
                        )

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath)
                    } else {
                        targetPath.parent?.let(Files::createDirectories)
                        Files.copy(
                            sourcePath,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES,
                        )
                    }

                    completedUnits += 1
                    responseObserver.onNext(
                        logUpdate(jobId, "Copied ${sourcePath.fileName} -> $targetPath"),
                    )
                    responseObserver.onNext(
                        progressUpdate(
                            jobId = jobId,
                            doneUnits = completedUnits,
                            totalUnits = totalUnits,
                            phase = "Copying",
                            currentItemName = sourcePath.name,
                        ),
                    )
                }
            }

            responseObserver.onNext(
                statusUpdate(jobId, JobStatusChanged.Status.STATUS_COMPLETED, "Copy completed successfully."),
            )
            responseObserver.onCompleted()
        } catch (error: Exception) {
            responseObserver.onNext(
                logUpdate(jobId, "Copy failed: ${error.message ?: "Unknown error"}", JobLogLine.Level.LEVEL_ERROR),
            )
            responseObserver.onNext(
                statusUpdate(
                    jobId,
                    JobStatusChanged.Status.STATUS_FAILED,
                    error.message ?: "Copy failed.",
                ),
            )
            responseObserver.onCompleted()
        } finally {
            cancellationFlags.remove(jobId)
        }
    }

    override fun cancelOperation(
        request: CancelOperationRequest,
        responseObserver: StreamObserver<CancelOperationResponse>,
    ) {
        val accepted = cancellationFlags[request.jobId.value]?.compareAndSet(false, true) == true
        responseObserver.onNext(
            CancelOperationResponse
                .newBuilder()
                .setAccepted(accepted)
                .build(),
        )
        responseObserver.onCompleted()
    }

    private fun decodeLocation(locationRef: quickfile.pluginapi.v1.LocationRef): Path {
        val raw = locationRef.opaque.toString(StandardCharsets.UTF_8)
        require(raw.isNotBlank()) { "LocationRef.opaque must not be blank." }
        return Path.of(raw).toAbsolutePath().normalize()
    }

    private fun collectCopyUnits(root: Path): List<Path> {
        require(root.exists()) { "Source does not exist: $root" }
        return if (root.isDirectory()) {
            Files.walk(root).use { stream -> stream.toList() }
        } else {
            listOf(root)
        }
    }

    private fun progressUpdate(
        jobId: String,
        doneUnits: Long,
        totalUnits: Long,
        phase: String,
        currentItemName: String,
    ): JobUpdate =
        JobUpdate
            .newBuilder()
            .setJobId(quickfile.pluginapi.v1.JobId.newBuilder().setValue(jobId).build())
            .setProgress(
                JobProgress
                    .newBuilder()
                    .setDoneUnits(doneUnits)
                    .setTotalUnits(totalUnits)
                    .setPhase(phase)
                    .setCurrentItemName(currentItemName)
                    .build(),
            ).build()

    private fun logUpdate(
        jobId: String,
        message: String,
        level: JobLogLine.Level = JobLogLine.Level.LEVEL_INFO,
    ): JobUpdate =
        JobUpdate
            .newBuilder()
            .setJobId(quickfile.pluginapi.v1.JobId.newBuilder().setValue(jobId).build())
            .setLog(
                JobLogLine
                    .newBuilder()
                    .setLine(message)
                    .setLevel(level)
                    .build(),
            ).build()

    private fun statusUpdate(
        jobId: String,
        status: JobStatusChanged.Status,
        message: String,
    ): JobUpdate =
        JobUpdate
            .newBuilder()
            .setJobId(quickfile.pluginapi.v1.JobId.newBuilder().setValue(jobId).build())
            .setStatus(
                JobStatusChanged
                    .newBuilder()
                    .setStatus(status)
                    .setMessage(message)
                    .build(),
            ).build()
}
