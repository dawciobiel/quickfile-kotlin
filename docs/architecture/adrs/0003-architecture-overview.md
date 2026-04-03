# ADR: Architecture overview (modularity, event bus, UDF)

Status: Accepted

Date: 2026-04-03

## Context

We need to decide the overall architecture direction for a Compose Desktop app that performs filesystem operations.

Key choices:
- Monolith modular vs Core + API + Plugin runtime
- Whether to introduce an event bus (and why)
- UDF (unidirectional data flow) adoption in the UI/state layer

Additional constraints (to clarify later):
- Testability of filesystem logic
- Isolation boundaries between UI, core logic, and integration/protocol code

## Decision

### Overall direction

We choose **Core + API + Plugin runtime**.

Core responsibilities:
- Host application shell (multi-panel UI, state management, settings)
- Defining and versioning the plugin API
- Plugin lifecycle management (install/enable/disable, discovery, startup)

Plugins responsibilities (typical):
- File sources/providers (local, remote protocols, cloud providers)
- File operations implementation (copy/move/delete/rename) and job execution
- Context actions, panel extensions, settings page extensions (as defined by the plugin model ADR)

### Eventing model (host ↔ plugins)

We expose an event bus to plugins (Option 2B), implemented as a **versioned event envelope**:
- `type`: stable event identifier (string)
- `schemaVersion`: integer version for the payload schema
- `payload`: structured data associated with the event type and version

The event envelope is the stable transport contract; individual event types are versioned and must follow backward-compatible evolution rules.

### UI/state flow

We adopt UDF principles for the UI/state layer. For v1 the recommended approach is **UDF-lite per feature**:
- Immutable UI state per feature (e.g., browser panels, transfers/jobs, settings)
- Explicit intents/actions drive state changes
- Side effects (I/O, plugin calls) are handled outside reducers/state updates and report back via typed results/events

## Consequences

- Clear separation between host and plugin code encourages testability and stability boundaries.
- The plugin API and event types become long-lived contracts; they must be versioned and evolved with backward compatibility in mind.
- Event envelope reduces the need for frequent API surface changes, but requires strong governance of event `type` naming and schema evolution rules.
- UDF-lite helps manage multi-panel state and concurrent jobs, and improves debuggability at the cost of some boilerplate.

