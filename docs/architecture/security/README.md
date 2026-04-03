# Filesystem Safety & Security

This section documents conventions that reduce the risk of data loss and unexpected behavior in a desktop file manager.

## Topics to cover

- Path handling and validation (preventing accidental operations on unexpected paths)
- Destructive actions (delete/move/overwrite): when confirmations are required
- Error handling for permission failures and missing files
- Optional safety features (e.g., move-to-trash, undo/rollback, dry-run mode)
- Logging/auditing of critical operations (what to record, what to avoid)

## Goal

Make destructive operations predictable and observable, while keeping the UI responsive and preventing accidental data loss.

