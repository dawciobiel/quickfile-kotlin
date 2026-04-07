package io.github.dawciobiel.quickfile.plugins.localfs

import io.github.dawciobiel.quickfile.pluginapi.PluginApi
import io.grpc.stub.StreamObserver
import quickfile.pluginapi.v1.ApiVersion
import quickfile.pluginapi.v1.HandshakeRequest
import quickfile.pluginapi.v1.HandshakeResponse
import quickfile.pluginapi.v1.HealthCheckRequest
import quickfile.pluginapi.v1.HealthCheckResponse
import quickfile.pluginapi.v1.PluginControlGrpc
import java.util.UUID

class LocalFsPluginControlService(
    private val pluginId: String,
    private val pluginVersion: String,
) : PluginControlGrpc.PluginControlImplBase() {
    override fun handshake(
        request: HandshakeRequest,
        responseObserver: StreamObserver<HandshakeResponse>,
    ) {
        val response =
            HandshakeResponse
                .newBuilder()
                .setNegotiatedApiVersion(
                    ApiVersion
                        .newBuilder()
                        .setMajor(PluginApi.VERSION)
                        .setMinor(0)
                        .build(),
                ).setSessionToken(UUID.randomUUID().toString())
                .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun healthCheck(
        request: HealthCheckRequest,
        responseObserver: StreamObserver<HealthCheckResponse>,
    ) {
        val response =
            HealthCheckResponse
                .newBuilder()
                .setStatus(HealthCheckResponse.Status.STATUS_OK)
                .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}
