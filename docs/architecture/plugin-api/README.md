# Plugin API (v1 proposal)

This document describes the minimal public contracts exposed to plugins. It complements ADR `0002-plugin-model` and ADR `0003-architecture-overview`.

## Design principles

- The API is **versioned** and must evolve with backward compatibility in mind.
- Prefer a small number of stable extension points.
- Plugins run **out-of-process** for providers and file operations (process isolation baseline).
- UI extension points are **host-controlled** (panels and settings pages only).

## Core concepts

- **Plugin**: an independently released component that adds capabilities.
- **Host**: the main application (Core + UI + Plugin runtime).
- **Capability**: what the plugin provides (source provider, operations, actions, UI extensions).

## Capabilities (initial set)

### File source provider

Allows browsing entries from a specific source (local filesystem, SMB/SFTP/NFS, cloud).

Minimum responsibilities:
- List directory entries
- Provide metadata (name, type, size, timestamps where available)
- Provide a stable identifier for an entry within the source

### File operations

Operations are modeled as **jobs**:
- copy / move / delete / rename

Jobs must be:
- cancellable
- observable (progress/status)

### Context actions

Plugins can register actions exposed by the host:
- command palette entries
- context menu entries (based on selection)

### UI extensions (host-controlled)

Allowed:
- Panel extensions (rendered inside host layout)
- Settings page extensions

Not allowed in v1:
- Replacing the whole application UI

## Event bus contract (event envelope)

Plugins receive events from the host via an event bus.

To keep compatibility manageable, events are transported via a **versioned envelope**:

- `type`: stable string identifier (e.g., `job.progress`, `panel.active.changed`)
- `schemaVersion`: integer schema version for the payload
- `payload`: structured data for that `type` and version

### Event naming conventions

- Use dot-separated, lower-case identifiers: `area.topic.verb`
- Prefer stable nouns and verbs: `panel.active.changed`, `selection.changed`
- Use plural domains for streams: `jobs.*`, `transfers.*`
- Avoid embedding UI widget details (keep it conceptual)

### Stability levels (recommended)

- **stable**: safe for third-party plugins; backward-compatible evolution rules apply
- **experimental**: may change without notice; plugins should opt-in explicitly

### Minimal stable event types (v1)

Keep v1 small. Suggested initial stable set:

- `app.lifecycle.changed`
  - emitted on startup/shutdown, plugin load/unload, major mode changes

- `panel.active.changed`
  - active panel changed (e.g., left/right, focused pane)

- `panel.location.changed`
  - a panel navigated to a new location within a source/provider

- `selection.changed`
  - selection changed within a panel

- `job.created`
  - a job was created (copy/move/delete/rename)

- `job.progress`
  - progress updates for a job (bytes/items done, total, current phase)

- `job.status.changed`
  - job transitioned between states (queued/running/paused/completed/failed/cancelled)

- `job.log`
  - structured log entries associated with a job (for UI "Transfers" and diagnostics)

- `notifications.toast`
  - non-blocking user notification requests (host decides how to display)

Notes:
- Providers and operation plugins should treat unknown events as ignorable.
- If a plugin needs richer host state, prefer explicit RPC queries over adding many event types.

### Plugin → host reporting (recommended)

For progress and logs related to a concrete operation (e.g., archiving, copy/move/delete/rename), prefer **per-job streams** (RPC returns/opens a stream for that job).

If the plugin must report events that are not tied to a single job (e.g., provider connectivity, auth/session expiry, health), use a **dedicated plugin → host event stream** with the same event envelope (`type`, `schemaVersion`, `payload`).

### Evolution rules (recommended)

- Adding new optional fields is backward compatible.
- Avoid renaming/removing fields; if needed, introduce a new `schemaVersion`.
- New event types may be introduced without affecting existing plugins.

### Capability handshake

At startup the plugin should declare:
- which capabilities it provides
- which event `type` values it understands (optionally with supported `schemaVersion` ranges)

The host may:
- refuse to load incompatible plugins
- disable parts of a plugin based on missing capabilities or version mismatch

## Open questions (to be resolved before implementation)

- Transport choice for IPC/RPC (e.g., JSON over stdin/stdout, gRPC, custom protocol)
- Plugin packaging and distribution model (local folder, registry, signed artifacts)
- Permission model and optional OS sandboxing (future ADR)

