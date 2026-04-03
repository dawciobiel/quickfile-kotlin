# Flow: Copy selection as a job

## Goal

The user copies selected entries from a source location to a destination location, with observable progress and cancellation.

## Participants

- **User**
- **Host UI**: multi-panel selection, drag/drop or explicit copy action
- **Host core**: job orchestration, overwrite policy hooks (confirmations are host/UI responsibility)
- **Plugin runtime**: routes the job to the correct operation implementation (plugin)
- **Operation plugin(s)** (out-of-process): performs copy (may stream progress)

## Preconditions

- Source panel has a non-empty selection.
- Destination panel (or destination picker) provides a valid target location compatible with the operation.

## Happy path (steps)

1. User selects entries in the source panel.
2. User initiates **copy** (keyboard shortcut, toolbar, or context action).
3. Host core creates a **job** record with a stable `jobId` and initial metadata (source, destination, counts).
4. Host emits `job.created` (to UI and interested plugins via the event model).
5. Host calls the operation plugin to **start copy** for that job.
6. Operation plugin streams progress back to the host:
   - `job.progress` (bytes/items, phase)
   - optional `job.log` lines
   - `job.status.changed` transitions (running -> completed/failed/cancelled)
7. Host UI shows progress in a transfers/jobs surface (panel or overlay).
8. On completion, host refreshes affected panels as needed (listing refresh may be another provider call).

## Cancellation

- User requests cancel.
- Host sends **cancel** for the job to the operation plugin (or cancels the client stream, depending on the final RPC design).
- Final state must always arrive as `job.status.changed` (cancelled/failed/completed).

## Failure modes (high level)

- Partial copy / conflicts: host policy decides overwrite behavior; errors are surfaced via job status and logs.
- Plugin hang: host applies timeouts and surfaces failure; may restart plugin depending on policy.

## Notes

- This flow is the template for **move/delete/rename**: same job model, different operation plugin or capability.
