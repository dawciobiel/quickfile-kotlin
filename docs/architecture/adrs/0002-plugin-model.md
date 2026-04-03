# ADR: Plugin model (UI/Protocols/Context actions, runtime)

Status: Accepted

Date: 2026-04-03

## Context

We want an extensible application that can grow into an open-source project.

We must decide what plugins are allowed to do:
- Do plugins extend UI?
- Do plugins add new protocols (e.g., FTP)?
- Do plugins add context actions (right-click / menu actions)?

We also need a runtime/security approach:
- Are plugins loaded dynamically?
- Are they isolated via a classloader?
- Are they sandboxed (and how)?

## Decision

We adopt **Variant B**:

### Capabilities

Plugins may:
- Add new file sources/protocols (e.g., local, SMB, FTP, SFTP, NFS, cloud providers)
- Add context actions (menu items / command palette actions)
- Extend UI, but **only via controlled extension points**:
  - Panel extensions
  - Settings page extensions

Plugins must **not** replace the whole application UI in v1.

### Loading model (v1)

- Plugins are loaded dynamically at application startup.
- No hot-reload in v1; **restart required** after installing/updating/removing plugins.

### Isolation & sandbox

We choose **process isolation** as the baseline for plugin execution.

- Plugins that implement **filesystem/protocol providers** and **file operations** run out-of-process (separate JVM/process).
- The host communicates with plugins via a narrow, versioned API boundary (IPC/RPC).

Important: process isolation **does not automatically prevent malicious behavior** (a plugin process can still execute arbitrary code as the current user). It is chosen because it:
- Improves stability (plugin crashes/hangs are contained)
- Enables OS-level sandboxing in the future (where supported)
- Provides a clear boundary for capability-based permissions (what a plugin is allowed to do)

UI extensions (panels/settings pages) remain **host-controlled** extension points. If we later allow third-party UI code, it must have explicit compatibility and safety rules (separate ADR).

## Consequences

- The host application must define stable extension points for panels and settings pages.
- The plugin API must be versioned and designed with backward compatibility in mind (plugins targeting older app versions).
- Deferring hot-reload simplifies the runtime and reduces state/cleanup complexity.
- Process isolation increases implementation complexity (IPC, lifecycle management, packaging, debugging), but reduces the blast radius of plugin failures.
- Security requires additional measures beyond process isolation (e.g., signed plugins, curated registry, user warnings, and eventually OS sandboxing / permission model).

