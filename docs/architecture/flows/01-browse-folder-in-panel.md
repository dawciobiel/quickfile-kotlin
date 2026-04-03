# Flow: Browse a folder in a panel

## Goal

The user sees a list of file/folder entries for a given location inside a specific panel (multi-panel UI).

## Participants

- **User**
- **Host UI** (Compose): panels, selection, navigation
- **Host core**: panel state, current location per panel, selection model
- **Plugin runtime**: spawns/connects to the plugin process for the active file source
- **File source provider plugin** (out-of-process): lists entries for a `LocationRef`

## Preconditions

- A panel is active and bound to a **source** (e.g., local FS, SMB, cloud) implemented by a provider plugin.
- The plugin process for that source is running or can be started by the host.

## Happy path (steps)

1. User focuses a panel (or switches active panel).
2. Host UI updates internal state: `panel.active.changed` (conceptual event to interested plugins/UI).
3. User navigates to a folder (path/URI/location handle depending on source).
4. Host core resolves the navigation target to a **stable location reference** understood by the provider plugin.
5. Host issues an RPC to the provider plugin: **list entries** for that location (request/response or small paged stream if needed).
6. Provider plugin returns entries (name, type, size, timestamps as available).
7. Host UI renders the listing in that panel.
8. Host may emit `panel.location.changed` and/or `selection.changed` as the user moves focus or changes selection.

## Failure modes (high level)

- Permission denied / inaccessible location: host surfaces an error state in the panel.
- Plugin crash or timeout: host shows degraded state and offers retry; plugin runtime may restart the plugin process.

## Notes

- This flow should stay **fast** and mostly synchronous from the user's perspective; heavy work belongs in explicit jobs (see `02` and `03`).
