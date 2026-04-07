# ADR: Error handling & typed errors

Status: Accepted

Date: 2026-04-04

## Context

The application crosses several boundaries where errors can occur:

```
[Plugin process] ──gRPC──► [:plugin-runtime] ──► [:core] ──► [:app-ui]
```

Errors arise at each layer:

1. **Operational errors inside a plugin** — permission denied, no space on disk, file locked, archive tool missing.
2. **IPC/transport errors** — plugin process crashes mid-stream (gRPC stream closes without a terminal `JobStatusChanged`), plugin hangs (timeout), port discovery fails.
3. **Host-side launch errors** — plugin JAR missing, wrong `mainClass`, JVM not found; the gRPC connection is never established.
4. **Validation errors** — host detects an invalid operation before contacting the plugin (e.g., read-only destination).

We need to decide:
- How errors are **represented in Kotlin** across layers.
- How errors **flow** from `:plugin-runtime` through `:core` to `:app-ui`.
- Whether errors are **exceptions** (disrupt control flow) or **values** (expected outcomes modeled in types).

Constraints:
- Most errors in a file manager are **expected cases** (permission denied, full disk) not programmer mistakes — they must be communicated to the user, not crash the app.
- `:core` and `:app-ui` must not handle raw gRPC types (`StatusException`) — those are transport details.
- UI state is driven by `StateFlow` (UDF-lite, ADR `0003`); errors should fit naturally into immutable state snapshots.
- `:core` must be testable without a real plugin or gRPC runtime.

## Decision

We adopt **typed errors as values** using two complementary techniques:

- **`sealed class` hierarchies** (`#3`) — define *what* went wrong in a structured, compiler-enforced way.
- **Errors embedded in UI state** (`#4`) — errors live inside `StateFlow` snapshots; the UI renders them declaratively.

Exceptions are **not** used as the primary error-passing mechanism between layers. They are only used at layer boundaries (`:plugin-runtime` catching gRPC `StatusException`) and immediately converted to typed values. No checked or unchecked exception escapes into `:core` or `:app-ui`.

---

### 1. Two sealed class families

Errors are split into two families matching the two primary UI surfaces:

#### `BrowseError` — errors in panel browsing (`:core`)

Represents failures when listing directory contents. Lives in `PanelState`.

```kotlin
sealed class BrowseError {
    /** Plugin reported access was denied for this location. */
    data class PermissionDenied(val location: String) : BrowseError()

    /** The requested location does not exist or is no longer reachable. */
    data class LocationNotFound(val location: String) : BrowseError()

    /** The plugin process for this source is not running and could not be started. */
    data class PluginUnavailable(val pluginId: String) : BrowseError()

    /** The plugin process crashed or became unresponsive during the request. */
    data class PluginCrashed(val pluginId: String, val cause: String) : BrowseError()

    /** Any other error not covered above; use sparingly. */
    data class Unknown(val message: String) : BrowseError()
}
```

#### `JobError` — errors in file operations (`:core`)

Represents failures during a job (copy/move/delete/rename/archive). Lives in `JobSnapshot` (replaces the current `errorMessage: String?` field).

```kotlin
sealed class JobError {
    /** Plugin reported insufficient disk space. */
    data class NoSpace(val path: String) : JobError()

    /** Plugin reported a permission error on a specific path. */
    data class PermissionDenied(val path: String) : JobError()

    /** One or more items failed; job may be partially complete. */
    data class PartialFailure(val failedCount: Int, val firstError: String) : JobError()

    /** Plugin process crashed or stream closed unexpectedly. */
    data class PluginCrashed(val pluginId: String, val cause: String) : JobError()

    /** Host timed out waiting for plugin response. */
    data class PluginTimeout(val pluginId: String) : JobError()

    /** Any other error not covered above; use sparingly. */
    data class Unknown(val message: String) : JobError()
}
```

---

### 2. Errors embedded in UI state

Errors are **fields** in immutable state snapshots, not thrown from state-producing functions.

#### Panel state

```kotlin
data class PanelState(
    val location: LocationRef?,
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val error: BrowseError? = null,   // null = no error
)
```

The UI checks `error != null` and renders an inline error message with a retry action. When the user retries, the store clears `error` and sets `isLoading = true`.

#### Job snapshot (update to ADR `0006`)

```kotlin
data class JobSnapshot(
    val jobId: JobId,
    val kind: OperationKind,
    val status: JobStatus,
    val progress: JobProgress?,
    val recentLogLines: List<LogLine>,
    val error: JobError?,            // replaces errorMessage: String?
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

`error` is non-null only when `status == FAILED`. The UI uses `when (snapshot.error)` to render a specific message per error type.

---

### 3. Layer responsibilities for error conversion

| Layer | Produces | Converts from |
|---|---|---|
| Plugin process | gRPC `Status` codes + `message` string in `JobStatusChanged` | — |
| `:plugin-runtime` | `BrowseError`, `JobError` | gRPC `StatusException`, stream close events, timeouts |
| `:core` | `PanelState(error=…)`, `JobSnapshot(error=…)` | `BrowseError`, `JobError` from `:plugin-runtime` |
| `:app-ui` | Rendered error UI | `BrowseError?`, `JobError?` from `StateFlow` |

**Rule:** Each layer only knows about its own error types. `:plugin-runtime` is the **only** layer that imports or handles gRPC `StatusException`.

#### gRPC status code mapping (`:plugin-runtime`)

| gRPC status | Maps to |
|---|---|
| `PERMISSION_DENIED` | `BrowseError.PermissionDenied` / `JobError.PermissionDenied` |
| `NOT_FOUND` | `BrowseError.LocationNotFound` |
| `RESOURCE_EXHAUSTED` | `JobError.NoSpace` |
| `UNAVAILABLE` / `DEADLINE_EXCEEDED` | `*.PluginCrashed` or `*.PluginTimeout` |
| Stream closed without terminal status | `*.PluginCrashed` |
| All other | `*.Unknown(status.description)` |

---

### 4. What remains as exceptions

A small set of **programmer errors** (bugs, not expected cases) remain as exceptions and are not converted to typed values:

- `IllegalStateException` — invariant violated (e.g., job transitioned to invalid state).
- `IllegalArgumentException` — invalid input to an internal API.
- Unhandled `Exception` in a coroutine — propagates to the coroutine scope's `CoroutineExceptionHandler`, logs, and surfaces as `Unknown` error in state if possible.

These are not user-facing error cases; they indicate bugs and should be logged with full stack traces.

---

### 5. Retry and recovery

Error recovery is the UI's responsibility:

- `BrowseError` in `PanelState` → UI shows error + **Retry** button → user intent `RetryBrowse` → store clears error, re-issues listing.
- `JobError` in `JobSnapshot` (terminal) → job is done; UI may offer **Retry job** as a new job creation intent, not a state mutation on the failed job.

No automatic retry logic in `:core` or `:plugin-runtime` in v1. Future ADR may introduce retry policies for transient errors (`PluginTimeout`, `PluginCrashed`).

## Consequences

- `:core` defines `BrowseError` and `JobError` sealed classes; these become part of the stable internal API between `:core` and `:app-ui`.
- `JobSnapshot.errorMessage: String?` (from ADR `0006`) is replaced by `error: JobError?` — richer and compiler-enforced.
- `:plugin-runtime` is responsible for the gRPC → typed error mapping; it is the only module that imports gRPC status types for error handling.
- UI code uses exhaustive `when (state.error)` — the compiler warns if a new `BrowseError` subtype is added and not handled in the UI.
- `:core` unit tests can produce `BrowseError` / `JobError` values directly without a real plugin process.
- The `Unknown` catch-all cases allow the system to handle future or unexpected error codes gracefully without crashing, at the cost of less specific UI messages.
- No automatic retry in v1 keeps `:core` simple; retry policy is deferred to a future ADR.
