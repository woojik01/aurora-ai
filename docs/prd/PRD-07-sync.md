# PRD-07 — PC Companion, Device Pairing & Synchronization

## Goal
Synchronize conversations, memories, tasks, agent definitions, and automation state between Android and a future PC companion without requiring Aurora's primary data to live on a central server.

## Architecture
Android and PC each maintain a complete local database. Synchronization is peer-to-peer where practical, initially over the same trusted LAN/Wi-Fi. A future optional cloud relay may be added without changing the local data model.

## Pairing
Use an explicit user-mediated pairing flow, preferably QR/code plus cryptographic key exchange. Each device receives a persistent public/private keypair. Trust is established only after user confirmation on both sides.

Never pair solely by device name or IP address.

## Sync protocol
Each mutation has:
- entity ID
- entity type
- device ID
- monotonic local revision
- wall-clock timestamp for display only
- operation type
- payload or encrypted reference
- tombstone flag

Conflict resolution must be entity-specific. For append-only messages/audit events, merge by unique ID. For editable records, use deterministic last-writer metadata plus conflict records where silent loss would be harmful. Never resurrect a tombstoned item merely because another device has an older copy.

## Transport security
Use authenticated encrypted transport. Bind sessions to paired device keys. Reject unknown devices. Include nonce/replay protection and request authentication.

## Performance
Sync incrementally; never transfer the entire database on every connection. Compress large text payloads where beneficial. Transfer large attachments separately with checksums, resumability, size limits, and user-visible progress.

## Offline behavior
Both devices remain independently usable offline. Sync is eventual, not required for local operation. If devices diverge, preserve user data and expose conflicts rather than silently discarding one side.

## PC companion
PC should share domain/protocol concepts with Android but must not require Android UI code. It may host a stronger local model runtime and expose it to Android over the authenticated pairing channel.

## Acceptance criteria
- Two paired devices synchronize after both were offline.
- Replay of a sync packet does not duplicate or corrupt data.
- Unpaired devices cannot inject mutations.
- Deleted records remain deleted after reconciliation.
- Large attachments can resume after interruption.
