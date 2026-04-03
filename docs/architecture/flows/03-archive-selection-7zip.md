# Flow: Archive selection (example: 7zip archiver plugin)

## Goal

The user runs a long-running "archive these files" operation implemented by a dedicated plugin (example: 7zip), with rich progress reporting.

## Participants

- **User**
- **Host UI**: context action / command palette entry "Archive..."
- **Host core**: creates a job, routes to the archiver capability
- **Archiver plugin** (out-of-process): performs compression/archiving

## Preconditions

- User has a selection in a panel.
- An archiver plugin is installed and declares the relevant capability (and is compatible with the host API version).

## Happy path (steps)

1. User selects one or more entries (files/folders as supported).
2. User triggers the archiver action (context menu or command).
3. Host shows minimal UI to collect options (format, destination archive path, compression level) if required.
4. Host core creates a **job** with `jobId` and parameters.
5. Host emits `job.created`.
6. Host starts the archiver operation via RPC. The archiver plugin responds with a **per-job stream** of:
   - `job.progress` (current file, percent, bytes processed, phase like "scanning/compressing/finalizing")
   - `job.log` (optional detailed lines)
   - `job.status.changed` on completion
7. Host UI displays progress in the transfers/jobs UI.
8. On success, host may navigate to the destination folder or refresh listings.

## Why per-job streaming fits

Archiving is a single logical operation with a clear start and end. Progress is naturally expressed as a stream tied to that job (see `plugin-api/README.md`: per-job streams for operation progress).

## Asynchronous plugin-side events (optional)

If the archiver needs to report issues not tied to the job lifecycle (e.g., external tool stderr redirected to diagnostics), prefer:
- `job.log` lines, or
- a separate plugin->host event stream **only** for non-job diagnostics (keep rare and well-typed).

## Failure modes (high level)

- Tool missing / non-zero exit: surfaced as failed job with logs.
- User cancels: job ends in cancelled state; partial archive policy is defined by the plugin + host UX (confirm delete partial output).
