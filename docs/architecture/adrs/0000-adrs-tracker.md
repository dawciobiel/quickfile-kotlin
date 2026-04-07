# ADR Tracker (v0/v1 strategic decisions)

This file lists the architectural decisions we plan to document. Each decision gets its own ADR file.

## Planned ADRs

1. `0001-v1-scope.md` - MVP scope for filesystem and UX
2. `0002-plugin-model.md` - plugin capabilities and runtime/loading model
3. `0003-architecture-overview.md` - overall architecture style and state flow
4. `0004-ipc-transport.md` - IPC/RPC transport between host and plugin processes
5. `0005-plugin-packaging-and-discovery.md` - plugin ZIP packaging, manifest format, directory discovery, JVM launch
6. `0006-job-model-and-lifecycle.md` - job identity, state machine, Kotlin representation (JobRegistry/JobSnapshot/StateFlow), ownership, persistence, concurrency
7. `0007-app-ui-state-management.md` - UDF store structure, library choice (plain StateFlow / Decompose / Circuit / Ballast), navigation model
8. `0008-error-handling-and-typed-errors.md` - sealed class error hierarchies (BrowseError, JobError), errors as state values, gRPC status mapping, layer responsibilities
9. `0009-plugin-versioning-and-compatibility.md` - integer API version scheme, breaking vs additive change rules, hard block policy, Handshake role
10. `0010-ui-localization-and-i18n.md` - host-owned i18n, locale selection, fallback rules, and plugin-contributed translation bundles

## Status

- Most ADRs start as `Status: Proposed`
- When you make a decision, we update `Status` to `Accepted` (or `Rejected`) and fill in the rationale
