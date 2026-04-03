# ADR: IPC/RPC transport for process-isolated plugins

Status: Accepted

Date: 2026-04-03

## Context

The architecture adopts process-isolated plugins for file sources/providers and file operations (see ADR `0002-plugin-model` and ADR `0003-architecture-overview`).

We need a transport for host ↔ plugin communication that supports:
- Request/response calls (e.g., list entries, start a job)
- Streaming (job progress, job logs, events via the event envelope)
- Version negotiation and capability handshake
- Good developer experience for third-party plugin authors
- Cross-platform support (Linux/Windows/macOS)

Constraints:
- Kotlin/JVM on both sides (host and plugin)
- Compose Desktop host application
- We want a stable public contract (API + event envelope)

## Decision

We choose **Option B: gRPC (protobuf) over localhost TCP** as the primary IPC/RPC transport.

Baseline rules:
- Plugin process exposes a gRPC server bound to `127.0.0.1` on an ephemeral port.
- Host spawns the plugin and discovers the chosen port via a startup handshake (e.g., plugin prints the port to stdout once, then switches to gRPC only).
- A per-launch random token/secret is passed to the plugin (e.g., env var) and required on each call (simple auth to prevent accidental local cross-talk).
- Streaming is used for long-running operations (job progress/logs and event envelope delivery).

Options A and C are not selected as the primary transport:
- JSON over stdio remains useful as a temporary prototyping/debugging aid, but not as the long-term public contract.

### Option A: JSON over stdio (stdin/stdout)

- Host spawns plugin process and communicates via line-delimited JSON messages.
- Pros:
  - Very simple to prototype and debug
  - No network ports, works cross-platform
  - Easy for third-party authors (no extra infra)
- Cons:
  - Needs careful framing/backpressure for large payloads
  - Harder to do high-throughput binary streaming
  - Requires disciplined schema/versioning to avoid breaking plugins

### Option B: gRPC over localhost (TCP) or domain sockets

- Pros:
  - Strongly typed IDL (protobuf), good streaming support
  - Good performance and tooling
  - Clear versioning strategy via protobuf evolution rules
- Cons:
  - More moving parts and dependencies
  - Localhost transport/security needs attention
  - Cross-platform domain sockets add complexity (TCP is simpler)

### Option C: Custom binary protocol over stdio

- Pros:
  - Efficient framing and streaming
  - No ports
- Cons:
  - High implementation cost, poor DX compared to gRPC
  - Harder to evolve and debug

## Consequences

Using gRPC/protobuf implies:
- A stronger, tool-supported compatibility story (protobuf evolution rules, generated stubs).
- Better streaming semantics for jobs/events without inventing framing/backpressure.
- Added runtime complexity (server lifecycle, port discovery, timeouts, error handling).
- A clearer plugin SDK story (published `.proto` files + generated Kotlin/Java bindings).

Operational considerations:
- The host must manage plugin process lifecycle (start, health checks, shutdown) and handle hung plugins with timeouts.
- Logging/diagnostics should include per-plugin process identifiers and gRPC error mapping.

Once selected, the transport impacts:
- Plugin SDK expectations (dependencies, generated stubs, tooling)
- Message schema evolution and compatibility strategy
- Runtime reliability (timeouts, retries, health checks)
- Observability (logging, tracing per plugin process)

