# Testing Strategy

Document the recommended test boundaries and how to test the file manager logic.

## What to define

- Unit tests for pure domain logic (fast, deterministic)
- Integration tests for filesystem operations (using temporary directories)
- UI testing strategy for Compose Desktop (if applicable)
- How to structure tests per module/layer
- What to mock vs what to run against the real filesystem

## Suggested focus (this app)

- Listing and filtering files/folders
- Actions: copy/move/delete/rename and overwrite policy
- Correct handling of edge cases (permissions, missing paths, long paths)

