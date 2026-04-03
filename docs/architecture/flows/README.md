# Flows (end-to-end)

This folder documents key user and system workflows. They complement ADRs and `plugin-api/` by describing how pieces interact in time.

## Index

1. `01-browse-folder-in-panel.md` - Navigate and list entries in a panel (file source provider).
2. `02-copy-selection-as-job.md` - Copy selected entries as a cancellable job with progress.
3. `03-archive-selection-7zip.md` - Long-running operation with per-job progress (example: 7zip archiver plugin).

## Conventions

- Flows are **conceptual**; exact RPC names and protobuf messages will live in `plugin-api/` later.
- **Host** means the main app (UI + core + plugin runtime).
- **Plugin** means an out-of-process plugin unless stated otherwise.
