# V1 Roadmap

This document proposes a practical implementation order for QuickFile v1.

It is not an ADR and does not freeze design decisions. Its purpose is to keep work sequenced so that each milestone builds on stable foundations from the accepted ADRs.

## Guiding principle

We should prefer a small number of **vertical slices** over implementing all infrastructure first.

That means:
- define the minimum architecture needed for the next milestone,
- implement one end-to-end user-visible flow,
- stabilize it,
- then widen the surface area.

## Milestones

### Milestone 1 - Foundation

Goal: turn the accepted ADRs into a working project skeleton.

Scope:
- confirm module boundaries between `:app-ui`, `:core`, `:plugin-runtime`, and `:plugin-api`
- add `Decompose` to `:app-ui` and create the initial UI bootstrap
- create a top-level `AppComponent`
- create placeholder child components for left panel, right panel, jobs, and settings
- define the first host-side interfaces needed by UI components

Exit criteria:
- the desktop app starts
- Compose renders a basic two-panel layout
- the UI is wired through Decompose components rather than directly from `main()`

### Milestone 2 - Browse Files End-to-End

Goal: make one file source browseable inside the real UI.

Scope:
- implement plugin discovery from the configured plugins directory
- implement manifest parsing and API version checks
- implement JVM plugin process launch and startup handshake
- define the browsing boundary in `:core`
- connect a panel component to browsing use cases
- render directory entries in at least one panel
- support changing the current location and refreshing it

Exit criteria:
- the app can launch a plugin
- the left and right panel can display directory contents
- changing folder in a panel updates visible state through the intended UI -> core -> runtime -> plugin flow

### Milestone 3 - Local Filesystem Reference Plugin

Goal: create the first real plugin used by the host.

Scope:
- create a reference plugin for the local filesystem
- support listing entries and retrieving enough metadata for panel rendering
- return typed failures for common browsing errors
- validate that host/plugin contracts work in practice, not only on paper

Exit criteria:
- the host can browse the local filesystem through the plugin
- common browsing failures are surfaced cleanly in the panel state

### Milestone 4 - File Operations and Job Lifecycle

Goal: implement the first long-running operation as a managed job.

Scope:
- implement `JobRegistry` in `:core`
- connect `StartOperation` and streamed `JobUpdate` handling
- show jobs/transfers in the UI
- support terminal states: completed, failed, cancelled
- support cancellation from the UI

Exit criteria:
- at least one operation, such as copy, runs as a job
- the user can observe progress and cancel the job
- terminal job state is reflected correctly in UI

### Milestone 5 - Error Model and Runtime Hardening

Goal: make failures predictable and visible instead of ad hoc.

Scope:
- implement typed errors from ADR `0008`
- map gRPC/process failures into host-side error types
- add launch timeout handling and plugin crash handling
- define user-facing error rendering for panels and jobs
- improve logging around plugin lifecycle and job execution

Exit criteria:
- plugin launch failures, browsing failures, and job failures are represented as typed state
- the UI can render meaningful failure states without raw transport exceptions

### Milestone 6 - UX and Interaction Baseline

Goal: make the application usable as a file manager, not only technically functional.

Scope:
- keyboard focus and active panel handling
- basic shortcuts for navigation and operations
- selection model for panel entries
- essential dialogs and confirmations
- settings screen skeleton

Exit criteria:
- the application can be used primarily from keyboard and panel focus
- the core browse and operation flows feel coherent in the UI

### Milestone 7 - Stabilization and Test Coverage

Goal: reduce architectural and behavioral risk before broader feature expansion.

Scope:
- unit tests for `:core`
- tests for Decompose component state transitions in `:app-ui`
- integration tests for host/runtime/plugin contracts
- regression checklist for browse, operations, and error scenarios
- basic packaging sanity checks for plugins

Exit criteria:
- the project has reliable automated coverage for core flows
- the main host/plugin integration points are exercised outside manual testing

## Recommended first vertical slice

If we want the fastest path to something demonstrable, the first vertical slice should be:

1. app startup
2. Decompose `AppComponent`
3. two visible panel components
4. plugin discovery and launch
5. local filesystem plugin
6. browse folder contents in one panel

That slice proves the most important architectural assumptions early:
- the module boundaries are viable,
- Decompose fits the UI structure,
- plugin packaging/discovery works,
- host/plugin IPC works,
- panel state can be driven from real runtime data.

## Suggested near-term task order

1. Add Decompose and create the `:app-ui` application skeleton.
2. Define the minimal panel state and panel intents.
3. Implement plugin discovery, manifest parsing, and process launch.
4. Build the local filesystem reference plugin.
5. Connect one panel to real browse results.
6. Extend to two panels.
7. Only then move to file operations and jobs.

## Out of scope for the first implementation wave

These items should not block the initial vertical slices:
- plugin marketplace or installer UX
- non-JVM plugins
- sophisticated plugin-provided UI extension points
- advanced navigation patterns beyond the main static multi-panel layout
- broad compatibility windows across multiple plugin API versions
