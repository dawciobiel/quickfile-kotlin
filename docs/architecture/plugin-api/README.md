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

