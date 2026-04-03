# ADR: V1 scope (filesystem, previews, window model)

Status: Accepted

Date: 2026-04-03

## Context

We are defining the MVP for a local desktop file manager (Kotlin + Compose Desktop).

Key questions we need to settle for v1:
- Only local filesystem, or also remote protocols (SMB/FTP/SFTP)?
  - The app may also support cloud-style providers (Dropbox, Microsoft OneDrive, Google Drive, NextCloud).
- Should v1 include only file operations (list/copy/move/delete/rename) or also previews (images/text)?
- One window vs multi-panel (e.g., split views, dual panes)?

## Decision

### Sources (v1+ direction)

The application is intended to support multiple sources of files:
- Local filesystem
- Remote protocols: SMB, FTP, SFTP, NFS
- Cloud providers: Dropbox, Microsoft OneDrive, Google Drive, NextCloud

Each source is planned to be implemented as a dedicated plugin.

### Previews (v1)

V1 focuses on core file operations only:
- list / copy / move / delete / rename

Preview support (images/text) is out of scope for v1.

Core file operations are planned to be implemented via one or more plugins (potentially one plugin per operation).

### Window & layout model

The preferred UX is multi-panel (Total Commander-like). The architecture should keep the UI adaptable (layout/view can evolve without rewriting the core).

## Consequences

- The plugin API must support multiple "file sources" and a uniform way to browse them.
- The plugin API should model file operations (copy/move/delete/rename) as cancellable, observable jobs to keep the UI responsive.
- Deferring previews reduces v1 scope and lowers the complexity of content rendering, caching, and security concerns.
- Multi-panel UX affects state management and navigation (multiple active locations, selections, operations in parallel).

