package io.github.dawciobiel.quickfile.plugins.localfs

import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.stub.StreamObserver
import quickfile.pluginapi.v1.Entry
import quickfile.pluginapi.v1.EntryKind
import quickfile.pluginapi.v1.FileSourceGrpc
import quickfile.pluginapi.v1.ListEntriesRequest
import quickfile.pluginapi.v1.ListEntriesResponse
import quickfile.pluginapi.v1.LocationRef
import java.io.File
import java.nio.charset.StandardCharsets

class LocalFsFileSourceService : FileSourceGrpc.FileSourceImplBase() {
    override fun listEntries(
        request: ListEntriesRequest,
        responseObserver: StreamObserver<ListEntriesResponse>,
    ) {
        val directory =
            runCatching {
                resolveDirectory(request.location)
            }.getOrElse { error ->
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription(error.message)
                        .asRuntimeException(),
                )
                return
            }

        if (!directory.exists()) {
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("Location does not exist: ${directory.path}")
                    .asRuntimeException(),
            )
            return
        }

        if (!directory.isDirectory) {
            responseObserver.onError(
                Status.FAILED_PRECONDITION
                    .withDescription("Location is not a directory: ${directory.path}")
                    .asRuntimeException(),
            )
            return
        }

        val files =
            directory
                .listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                .orEmpty()

        val response =
            ListEntriesResponse
                .newBuilder()
                .addAllEntries(files.map(::toEntry))
                .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private fun resolveDirectory(locationRef: LocationRef): File {
        if (locationRef.opaque.isEmpty) {
            return defaultBrowseRoot()
        }

        val path = locationRef.opaque.toString(StandardCharsets.UTF_8)
        require(path.isNotBlank()) { "LocationRef.opaque must contain a path or be empty." }
        return File(path).absoluteFile.normalize()
    }

    private fun toEntry(file: File): Entry {
        val entryBuilder =
            Entry
                .newBuilder()
                .setName(file.name.ifBlank { file.path })
                .setKind(
                    when {
                        file.isDirectory -> EntryKind.ENTRY_KIND_DIRECTORY
                        file.isFile -> EntryKind.ENTRY_KIND_FILE
                        else -> EntryKind.ENTRY_KIND_UNSPECIFIED
                    },
                ).setSizeBytes(if (file.isFile) file.length() else 0L)
                .setModifiedTimeMs(file.lastModified())

        if (file.isDirectory) {
            entryBuilder.childLocation =
                LocationRef
                    .newBuilder()
                    .setOpaque(ByteString.copyFromUtf8(file.absolutePath))
                    .build()
        }

        return entryBuilder.build()
    }
}
