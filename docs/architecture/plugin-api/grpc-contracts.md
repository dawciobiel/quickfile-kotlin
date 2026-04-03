# gRPC / protobuf contracts (v1 draft)

This document sketches the **host ↔ plugin** RPC surface. The plugin process runs a **gRPC server**; the host is the **client** (see ADR `0004-ipc-transport`).

Proto sources live in `proto/` next to this file. They are **documentation-first** until copied into a Gradle `:plugin-api` module.

## Design goals

- Small number of services, each with a clear responsibility.
- **Per-job progress** via **server-streaming** RPC responses.
- **Plugin → host** asynchronous events via a **dedicated server-stream** (same `EventEnvelope` as the logical event bus).
- **Version negotiation** at handshake time (`ApiVersion` + capability flags).

## Services (overview)

| Service | Responsibility | Maps to flow |
|--------|----------------|--------------|
| `PluginControl` | Handshake, health, capabilities | Plugin startup |
| `FileSource` | List entries for a location | `flows/01-browse-folder-in-panel.md` |
| `FileOperations` | Start long-running jobs (copy/move/…), stream updates | `flows/02-copy-selection-as-job.md` |
| `PluginEvents` | Stream of plugin-originated `EventEnvelope` (non-job / optional) | `flows/03-archive-selection-7zip.md`, provider health |

## Host → plugin event bus (optional v1)

The logical event bus (host → plugin) can be modeled as:

- **RPC** `SendHostEvent(EventEnvelope)` (unary), or
- **Client-streaming** `OpenHostEventChannel(stream EventEnvelope)` for lower overhead.

This draft includes **unary `SendHostEvent`** for simplicity. If volume grows, switch to client streaming without changing `EventEnvelope`.

## Job updates

`FileOperations` uses a single streamed message type `JobUpdate` with a `oneof` body:

- progress
- log line
- status transition

This matches the stable event names `job.progress`, `job.log`, `job.status.changed` while keeping one RPC stream per job.

## Mapping to ADR `0004` (startup)

1. Host spawns plugin OS process.
2. Plugin reads auth token from env (or similar).
3. Plugin starts gRPC server on `127.0.0.1` ephemeral port.
4. Plugin prints port to stdout (single line) for host discovery.
5. Host connects and calls `PluginControl.Handshake`.

## Compatibility

- Bump `ApiVersion.major` for breaking RPC changes.
- Prefer additive changes to messages (protobuf evolution).
- Keep `EventEnvelope` stable; evolve payloads via `schema_version` + `type`.
