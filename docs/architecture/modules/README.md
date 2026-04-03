# Modules & boundaries

This document proposes the Gradle module boundaries for the project, aligned with ADRs:
- `0001-v1-scope` (multi-source file manager, ops-first MVP)
- `0002-plugin-model` (controlled UI extensions, process-isolated providers/ops, event envelope)
- `0003-architecture-overview` (Core + API + Plugin runtime, UDF-lite)

## Goals

- Clear separation between **host app** and **plugins**
- A stable, versioned **plugin API**
- Minimal coupling between UI and filesystem/protocol implementations
- Testable core logic without UI

## Proposed modules (high-level)

### API & contracts (public, versioned)

- `:plugin-api`
  - Public interfaces/types shared between host and plugins (capabilities, IDs, envelopes)
  - Versioning rules and compatibility notes

### Host application (in-process)

- `:app-ui`
  - Compose Desktop application shell (multi-panel layout, navigation, theming)
  - UDF-lite state stores/view-models per feature
  - Renders panel/settings extensions provided by plugins (host-controlled)

- `:core`
  - Domain logic that is independent from Compose
  - Job model (copy/move/delete/rename as cancellable jobs)
  - Selection model, panel model, policies (overwrite, confirmations)

### Plugin runtime (host side, in-process)

- `:plugin-runtime`
  - Plugin discovery/installation metadata
  - Process lifecycle management (start/stop, health checks)
  - IPC/RPC transport and event distribution (event envelope)
  - Version negotiation / capability handshake

### Shared infrastructure (optional split, internal)

- `:logging` (optional)
  - Logging abstraction / structured event model

- `:serialization` (optional)
  - JSON/CBOR/protobuf helpers used for envelopes and RPC payloads

## Plugin projects (separate deliverables)

Plugins are expected to be built and released independently:
- `plugins/local-fs-provider` (example)
- `plugins/smb-provider`
- `plugins/sftp-provider`
- `plugins/cloud-onedrive-provider`
- `plugins/ops-copy`, `plugins/ops-move`, ...

Each plugin depends on `:plugin-api` only (not on host internals).

## Notes

- Keep `:plugin-api` small and stable; treat changes as semver-impacting.
- Prefer adding new optional fields/capabilities over breaking changes.
