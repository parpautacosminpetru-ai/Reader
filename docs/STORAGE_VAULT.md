# Document Vault — Worker 1

## Goal

Provide Reader with a durable, app-owned, offline document library that other modules can depend on.

## Storage rules

- Imported source files are copied into Reader's private internal storage under `files/vault/`.
- Source binaries are stored as files, never as database BLOBs.
- Room stores structured metadata only.
- Import uses Android's Storage Access Framework and does not require broad storage permissions.
- Android automatic cloud backup is disabled for Reader so the vault remains local by default.
- Deleting a vault item removes both its metadata and app-owned file.
- A failed metadata insert cleans up the copied file so imports are transactional from the user's perspective.

## Current milestone

- import one or many documents;
- persist metadata across app restarts;
- list imported documents;
- show file type and human-readable size;
- delete documents;
- keep all imported bytes available offline.

## Future storage work

- logical folders/collections;
- favorites and recent documents;
- thumbnails and derived-cache policy;
- crash recovery/reconciliation for orphan files;
- storage quota reporting;
- export/share contracts;
- encrypted vault option;
- migration/versioning tests as the schema evolves.
