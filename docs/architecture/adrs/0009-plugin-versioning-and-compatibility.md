# ADR: Plugin versioning & backward compatibility

Status: Accepted

Date: 2026-04-04

## Context

ADR `0004-ipc-transport` introduced `ApiVersion` (major/minor integers) in the `PluginControl.Handshake` RPC and noted that protobuf evolution rules handle message-level compatibility. ADR `0005-plugin-packaging-and-discovery` introduced `apiVersion` as a required field in `plugin.json`.

However, neither ADR defines:
- What constitutes a **breaking change** vs an **additive change** to `:plugin-api`.
- What the host does when a plugin's declared `apiVersion` is incompatible with the running host.
- Whether version negotiation happens before or after the plugin process is started.

Without these rules, plugin authors cannot know when they need to update their plugins, and the host has no defined policy for handling mismatches.

Constraints:
- Both sides (host and plugin) are Kotlin/JVM.
- Plugin `apiVersion` is declared statically in `plugin.json` (ADR `0005`).
- `PluginControl.Handshake` already exists in the proto contract.
- No hot-reload in v1; plugins are loaded at startup only (ADR `0002`).

## Decision

### 1. Versioning scheme — integer API version

`:plugin-api` uses a single **integer API version** (e.g., `1`, `2`, `3`).

- The current version is published as a constant in `:plugin-api` (e.g., `PluginApi.VERSION = 1`).
- Plugin authors declare the minimum API version their plugin requires in `plugin.json` as `"apiVersion": "1"`.
- The host declares the API version it implements; this is a build-time constant in `:plugin-runtime`.

Rationale: a single integer is sufficient for v1, is already reflected in `ApiVersion.major` in the proto, and is simple for plugin authors to reason about.

`ApiVersion.minor` in the proto is reserved for future use (additive signal only, not checked in v1).

---

### 2. Breaking vs additive changes

The following rules define what triggers an API version bump.

#### Breaking changes — MUST increment the integer version

| Category | Examples |
|---|---|
| Remove or rename a proto field or RPC | Remove `ListEntriesRequest.page_token`; rename `StartOperation` |
| Change the semantics of an existing field | `size_bytes` changes meaning from bytes to kilobytes |
| Add a new **required** capability that all plugins must implement | New mandatory RPC that host calls on every plugin |
| Change the `plugin.json` manifest schema in a non-additive way | Rename required field `entrypoint` to `launch` |
| Remove a declared `capability` value that plugins depend on | Remove `file-source` as a recognised capability |

#### Additive changes — do NOT increment the version

| Category | Examples |
|---|---|
| Add a new optional field to an existing proto message | New optional `permissions` field in `Entry` |
| Add a new RPC to an existing service | New optional `PauseOperation` RPC in `FileOperations` |
| Add a new `capability` value | New `thumbnail-provider` capability |
| Add a new optional field to `plugin.json` | New optional `homepage` field |
| Add a new event `type` to the event envelope | New `panel.selection.changed` event type |

Protobuf's built-in evolution rules (unknown fields are preserved, new optional fields default to zero/empty) make additive changes safe on the wire without a version bump.

---

### 3. Incompatibility policy — hard block before process launch

Version compatibility is checked **before the plugin process is started**, by reading `apiVersion` from `plugin.json`.

#### Rule

```
plugin.json "apiVersion"  ≤  host's current API version
```

If this condition is **not met**, the plugin is **blocked**: the host does not launch the plugin process, marks the plugin as `INCOMPATIBLE`, and surfaces a clear error to the user.

Two mismatch scenarios:

| Scenario | Condition | Action |
|---|---|---|
| Plugin too old | `plugin.apiVersion` < host's minimum supported version | Hard block + user error |
| Plugin too new | `plugin.apiVersion` > host's current API version | Hard block + user error |

**v1 simplification:** the host's minimum supported version equals its current version (`minSupported == current == 1`). There is no backward compatibility window in v1 — the host supports exactly one API version. This can be relaxed in a future ADR when a second version exists.

User-facing error message (example):
> Plugin "io.example.sftp" requires API version 2, but this version of QuickFile implements API version 1. Please update QuickFile or use an older version of the plugin.

---

### 4. Role of `PluginControl.Handshake` after version check

The `Handshake` RPC (already in proto) is **not** used for version negotiation in v1. Version compatibility is fully resolved before the process starts (see §3).

`Handshake` retains its existing role:
- Host sends `client_api_version` and receives `negotiated_api_version` back (informational echo).
- Plugin returns `session_token` for subsequent call authentication (ADR `0004`).
- If `Handshake` fails (gRPC error), the plugin is marked `FAILED` (not `INCOMPATIBLE`).

This separation keeps version policy in `:plugin-runtime` (static, before launch) and keeps `Handshake` as a lightweight connection confirmation.

---

### 5. Plugin SDK obligations for authors

Plugin authors must:
- Declare `"apiVersion"` in `plugin.json` matching the `:plugin-api` integer version they compiled against.
- Recompile and re-test against a new API version before bumping `apiVersion` in their manifest.
- Not assume additive changes require a manifest update (optional new fields/RPCs can be ignored safely).

The `:plugin-api` artifact changelog must clearly mark each release as **breaking** or **additive**.

---

### 6. What is deferred

- **Compatibility window** (host supports API versions N and N-1 simultaneously) — deferred until a second API version exists.
- **Plugin signing and trust** — separate security ADR.
- **Automatic plugin update notifications** — separate tooling/marketplace ADR.

## Consequences

- `:plugin-runtime` must read `apiVersion` from `plugin.json` during discovery and compare it to a host-side constant before launching any plugin process.
- Plugins that fail the version check are surfaced in the UI as `INCOMPATIBLE` (distinct from `FAILED`), with a human-readable reason.
- `:plugin-api` must maintain a changelog that clearly distinguishes breaking from additive releases.
- The `ApiVersion.minor` field in proto remains unused in v1; it is not checked during handshake.
- v1 supports exactly one API version — no compatibility window. This is the simplest possible policy and can be extended once a second version is needed.
- Plugin authors have a clear, stable rule: if the API integer does not change, their plugin keeps working.
