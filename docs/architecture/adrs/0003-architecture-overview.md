# ADR: Architecture overview (modularity, event bus, UDF)

Status: Proposed

Date: <YYYY-MM-DD>

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

TBD

## Consequences

TBD

