package com.aurora.core.domain.port

import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.Memory
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for semantic memories (PRD-02).
 *
 * Rules enforced by implementations:
 * - All queries are bounded by an explicit limit; there is no "load everything".
 * - Normal retrieval never returns tombstoned memories.
 * - Deletion is a tombstone ([Memory.deletedAt]) so sync can propagate it.
 * - Export ([exportMemories]) includes tombstones so a backup/restore round
 *   trip preserves IDs, timestamps, relationships, and deletion state.
 */
interface MemoryRepository {

    suspend fun createMemory(
        namespace: String,
        type: String,
        content: String,
        sensitivity: DataSensitivity = DataSensitivity.PERSONAL,
        sourceMessageId: String? = null,
    ): Memory

    /** Returns the memory, or null when it does not exist or is tombstoned. */
    suspend fun getMemory(id: String): Memory?

    /** @return false when the memory does not exist or is tombstoned. */
    suspend fun updateMemoryContent(id: String, content: String): Boolean

    /** Soft delete (tombstone). @return false when already tombstoned or missing. */
    suspend fun deleteMemory(id: String): Boolean

    /** Bounded, deterministic retrieval (newest first). Never returns tombstones. */
    suspend fun retrieveMemories(namespace: String, limit: Int = 50): List<Memory>

    /** Reactive bounded view of a namespace (newest first). Never returns tombstones. */
    fun observeMemories(namespace: String, limit: Int = 50): Flow<List<Memory>>

    /** Full export including tombstones, bounded by an explicit limit. */
    suspend fun exportMemories(limit: Int = 1000): List<Memory>
}
