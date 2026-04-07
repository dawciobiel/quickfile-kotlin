# TODO List

This file tracks the near-term implementation plan derived from the accepted ADRs and the v1 roadmap.

## Now

- [ ] Milestone 2: define the browse boundary in `:core`
- [ ] Replace demo panel data with a real browse flow
- [ ] Implement plugin discovery from the configured plugins directory
- [ ] Implement `plugin.json` parsing and API version checks
- [ ] Implement JVM plugin process launch and startup handshake

## Next

- [ ] Create the local filesystem reference plugin
- [ ] Connect one panel to real directory listing data
- [ ] Extend the browse flow to both panels
- [ ] Introduce typed browse errors in panel state
- [ ] Add the first integration tests for host/runtime/plugin startup

## Later

- [ ] Implement `JobRegistry` in `:core`
- [ ] Add file operations as observable jobs
- [ ] Add cancellation and terminal job state handling
- [ ] Add keyboard focus, active panel handling, and shortcuts
- [ ] Add settings navigation and persistent preferences

## Backlog

- [ ] Design host-controlled plugin UI extension points
- [ ] Add plugin packaging/install UX
- [ ] Evaluate broader plugin API compatibility windows after v1
- [ ] Add a broader regression checklist for browse and operations
