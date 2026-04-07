# ADR: Plugin SDK packaging & discovery

Status: Accepted

Date: 2026-04-04

## Context

ADR `0002-plugin-model` established that plugins run as **separate processes** (process isolation) and communicate via gRPC/protobuf (ADR `0004-ipc-transport`).

Before implementing `:plugin-runtime`, we need to decide:

1. **Packaging format** — how a plugin is distributed and stored on disk.
2. **Discovery** — how the host finds installed plugins at startup.
3. **Manifest** — what metadata a plugin must declare (ID, version, capabilities, entry point).
4. **Launch** — how the host spawns the plugin process given the package on disk.

Constraints:
- Kotlin/JVM on both sides (host and plugin).
- Cross-platform: Linux, Windows, macOS.
- Plugins are loaded at startup only (no hot-reload in v1; see ADR `0002`).
- Each plugin depends only on `:plugin-api` (published as a Maven artifact), not on host internals.
- Developer experience for third-party plugin authors must be reasonable.

## Decision

### 1. Packaging format — ZIP with a manifest

A plugin is distributed as a single **ZIP archive** with the following layout:

```
my-plugin-1.0.0.zip
├── plugin.json          ← manifest (required)
└── lib/
    ├── my-plugin.jar    ← plugin implementation (fat JAR or thin JAR)
    └── *.jar            ← transitive dependencies (if not fat JAR)
```

Rationale:
- ZIP is universal, self-contained, and trivially extractable on all platforms.
- A single archive makes installation/removal atomic (copy or delete one file).
- Avoids Maven/OSGi resolution at runtime — all dependencies are bundled.

### 2. Manifest format — `plugin.json`

Each plugin must include a `plugin.json` at the root of the ZIP:

```json
{
  "id": "io.example.local-fs",
  "version": "1.0.0",
  "name": "Local Filesystem Provider",
  "description": "Provides access to the local filesystem.",
  "apiVersion": "1",
  "capabilities": ["file-source", "file-operations"],
  "entrypoint": {
    "type": "jvm",
    "mainClass": "io.example.localfs.PluginMain",
    "classpath": ["lib/local-fs-1.0.0.jar"]
  }
}
```

Required fields:

| Field | Type | Description |
|---|---|---|
| `id` | string | Reverse-DNS unique plugin ID |
| `version` | string | Semver plugin version |
| `apiVersion` | string | Minimum plugin-api version required (integer string, e.g. `"1"`) |
| `capabilities` | string[] | Declared capabilities: `file-source`, `file-operations`, `context-action`, `panel-extension`, `settings-extension` |
| `entrypoint.type` | string | Always `"jvm"` in v1 |
| `entrypoint.mainClass` | string | Fully qualified class with `main()` entry point |
| `entrypoint.classpath` | string[] | Paths relative to the extracted plugin directory |

Optional fields: `name`, `description`, `author`, `homepage`, `license`.

### 3. Discovery — well-known plugins directory

The host scans a single **plugins directory** at startup:

- **Default location:** `<app-data-dir>/plugins/`
  - Linux: `$XDG_DATA_HOME/quickfile/plugins/` (fallback: `~/.local/share/quickfile/plugins/`)
  - Windows: `%APPDATA%\QuickFile\plugins\`
  - macOS: `~/Library/Application Support/QuickFile/plugins/`
- Each subdirectory inside `plugins/` is treated as one installed plugin (extracted ZIP).
- The host reads `plugin.json` from each subdirectory; invalid/missing manifests are skipped with a warning.

Installing a plugin = extracting its ZIP into a new subdirectory under `plugins/`.
Removing a plugin = deleting that subdirectory.

A user-level config override (`quickfile.conf`) may specify an additional plugins directory (e.g., for development/testing).

### 4. Launch — host spawns JVM process

When launching a plugin the host:

1. Reads `entrypoint.mainClass` and `entrypoint.classpath` from the manifest.
2. Spawns: `java -cp <resolved-classpath> <mainClass>` as a child process.
3. Passes the per-launch auth token via env var `QUICKFILE_PLUGIN_TOKEN=<random-uuid>`.
4. Reads stdout until the plugin prints `PORT=<number>` (startup handshake per ADR `0004`), then switches to gRPC.
5. If the plugin does not print `PORT=…` within a configurable timeout (default: 10 s), the host kills the process and marks the plugin as failed.

The JVM binary used is the host's own JVM (`ProcessHandle.current().info().command()` or `java.home` system property) to avoid version mismatch.

### 5. What is NOT decided here

- Plugin signature/trust verification — deferred to a security ADR.
- A plugin marketplace or CLI installer — deferred to a future ADR.
- Non-JVM plugin runtimes (e.g., native process) — not supported in v1; `entrypoint.type` field reserves space for this.

## Consequences

- `:plugin-runtime` must implement:
  - ZIP extraction on install (or expect pre-extracted directories)
  - `plugin.json` parsing and validation
  - Directory scanning at startup
  - JVM process spawning with classpath construction
  - Startup handshake timeout and failure handling
- Third-party plugin authors need to produce a fat JAR (or bundled thin JAR + deps) and a valid `plugin.json`; no special tooling required beyond standard Gradle/Maven builds.
- The plugins directory location must be documented and exposed in the app settings.
- Future API versions can add new `capabilities` values and `entrypoint.type` values without breaking existing plugins (additive evolution).
- Process isolation (from ADR `0002`) remains intact — the packaging format does not change the runtime isolation model.
