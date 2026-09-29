package com.aurora.core.sync

/**
 * Sync mutation contracts for PRD-07 (peer-to-peer Android/PC sync).
 * No transport implementation exists yet — these types freeze the protocol
 * vocabulary so later work cannot accidentally weaken invariants:
 * - identity is device-local, never a server auto-increment;
 * - deletion is a tombstone, never a silent drop;
 * - revisions are monotonic per (device, entity).
 */
enum class SyncOperation { CREATE, UPDATE, DELETE }

data class SyncMutation(
    val entityType: String,
    val entityId: String,
    val deviceId: String,
    val revision: Long,
    /** Wall-clock time for display only — never used for ordering decisions. */
    val timestamp: Long,
    val operation: SyncOperation,
    val tombstone: Boolean,
    val payload: String? = null,
) {
    init {
        require(entityType.isNotBlank()) { "entityType must not be blank" }
        require(entityId.isNotBlank()) { "entityId must not be blank" }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(revision > 0) { "revision must be positive" }
        require(timestamp >= 0) { "timestamp must be >= 0" }
        if (operation == SyncOperation.DELETE) {
            require(tombstone) { "DELETE mutations must be tombstones" }
        } else {
            require(!tombstone) { "Only DELETE mutations may carry a tombstone" }
        }
    }
}
