package io.github.dawciobiel.quickfile.pluginruntime

import com.google.protobuf.ByteString
import io.github.dawciobiel.quickfile.core.jobs.JobProgress
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind as CoreOperationKind
import io.github.dawciobiel.quickfile.pluginruntime.discovery.DiscoveredPlugin
import io.grpc.StatusRuntimeException
import quickfile.pluginapi.v1.CancelOperationRequest
import quickfile.pluginapi.v1.JobStatusChanged
import quickfile.pluginapi.v1.LocationRef
import quickfile.pluginapi.v1.OperationKind
import quickfile.pluginapi.v1.StartOperationRequest
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class PluginProcessFileOperationsGateway(
    private val plugin: DiscoveredPlugin,
) : FileOperationsGateway, AutoCloseable {
    @Volatile
    private var client: LaunchedPluginClient? = null

    override suspend fun startOperation(
        jobId: String,
        kind: CoreOperationKind,
        sourceLocation: String,
        destinationLocation: String?,
        onEvent: (FileOperationEvent) -> Unit,
    ) {
        val launchedClient = ensureClient()
        val requestBuilder =
            StartOperationRequest
                .newBuilder()
                .setJobId(quickfile.pluginapi.v1.JobId.newBuilder().setValue(jobId).build())
                .setKind(kind.toPluginOperationKind())
                .addSources(toLocationRef(sourceLocation))

        destinationLocation?.let {
            requestBuilder.setTarget(toLocationRef(it))
        }

        val updates = launchedClient.fileOperations.startOperation(requestBuilder.build())

        while (updates.hasNext()) {
            val update = updates.next()
            when {
                update.hasProgress() -> {
                    val progress = update.progress
                    onEvent(
                        FileOperationEvent.ProgressReported(
                            JobProgress(
                                completedUnits = progress.doneUnits,
                                totalUnits = progress.totalUnits,
                                details =
                                    listOf(progress.phase, progress.currentItemName)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { null },
                            ),
                        ),
                    )
                }

                update.hasLog() -> {
                    onEvent(FileOperationEvent.LogReported(update.log.line))
                }

                update.hasStatus() -> {
                    onEvent(
                        FileOperationEvent.StatusChanged(
                            status = update.status.status.toCoreStatus(),
                            message = update.status.message.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }

    override suspend fun cancel(jobId: String): Boolean {
        return runCatching {
            ensureClient().fileOperations.cancelOperation(
                CancelOperationRequest
                    .newBuilder()
                    .setJobId(quickfile.pluginapi.v1.JobId.newBuilder().setValue(jobId).build())
                    .build(),
            ).accepted
        }.getOrElse { error ->
            if (error is StatusRuntimeException) {
                false
            } else {
                throw error
            }
        }
    }

    private fun ensureClient(): LaunchedPluginClient {
        client?.takeIf { it.process.isAlive }?.let { return it }

        return synchronized(this) {
            client?.takeIf { it.process.isAlive }?.let { return@synchronized it }
            launchPluginClient(plugin).also { launched ->
                client = launched
            }
        }
    }

    override fun close() {
        val launchedClient =
            synchronized(this) {
                client.also {
                    client = null
                }
            } ?: return

        launchedClient.channel.shutdownNow()
        launchedClient.process.destroy()
        if (!launchedClient.process.waitFor(1, TimeUnit.SECONDS)) {
            launchedClient.process.destroyForcibly()
        }
    }

    private fun toLocationRef(location: String): LocationRef =
        LocationRef
            .newBuilder()
            .setOpaque(ByteString.copyFrom(location, StandardCharsets.UTF_8))
            .build()
}

private fun CoreOperationKind.toPluginOperationKind(): OperationKind =
    when (this) {
        CoreOperationKind.COPY -> OperationKind.OPERATION_KIND_COPY
        CoreOperationKind.MOVE -> OperationKind.OPERATION_KIND_MOVE
        CoreOperationKind.DELETE -> OperationKind.OPERATION_KIND_DELETE
        CoreOperationKind.RENAME -> OperationKind.OPERATION_KIND_RENAME
        CoreOperationKind.ARCHIVE -> OperationKind.OPERATION_KIND_ARCHIVE
    }

private fun JobStatusChanged.Status.toCoreStatus(): JobStatus =
    when (this) {
        JobStatusChanged.Status.STATUS_QUEUED -> JobStatus.PENDING
        JobStatusChanged.Status.STATUS_RUNNING -> JobStatus.RUNNING
        JobStatusChanged.Status.STATUS_COMPLETED -> JobStatus.COMPLETED
        JobStatusChanged.Status.STATUS_FAILED -> JobStatus.FAILED
        JobStatusChanged.Status.STATUS_CANCELLED -> JobStatus.CANCELLED
        JobStatusChanged.Status.STATUS_PAUSED -> JobStatus.RUNNING
        JobStatusChanged.Status.STATUS_UNSPECIFIED,
        JobStatusChanged.Status.UNRECOGNIZED,
        -> JobStatus.RUNNING
    }
