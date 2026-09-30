package com.aurora.core.data.repository

import com.aurora.core.data.db.MemoryDao
import com.aurora.core.data.db.toEntity
import com.aurora.core.data.db.toModel
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.Memory
import com.aurora.core.domain.port.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of the memory port (PRD-02).
 *
 * - All database work runs on Dispatchers.IO.
 * - All queries are bounded by explicit limits.
 * - Deletion is a tombstone; normal retrieval filters tombstones out while
 *   export keeps them so a round trip preserves deletion state.
 */
class MemoryRepositoryImpl(
    private val dao: MemoryDao,
    private val idGen: () -> AuroraId = { AuroraId.generate() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : MemoryRepository {

    override suspend fun createMemory(
        namespace: String,
        type: String,
        content: String,
        sensitivity: DataSensitivity,
        sourceMessageId: String?,
    ): Memory = io {
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(content.isNotBlank()) { "content must not be blank" }
        val time = now()
        val memory = Memory(
            id = idGen().value,
            namespace = namespace,
            type = type,
            content = content,
            sourceMessageId = sourceMessageId,
            createdAt = time,
            updatedAt = time,
            sensitivity = sensitivity,
        )
        dao.insert(memory.toEntity())
        memory
    }

    override suspend fun getMemory(id: String): Memory? = io {
        dao.get(id)?.takeIf { it.deletedAt == null }?.toModel()
    }

    override suspend fun updateMemoryContent(id: String, content: String): Boolean = io {
        require(content.isNotBlank()) { "content must not be blank" }
        dao.updateContent(id, content, now()) > 0
    }

    override suspend fun deleteMemory(id: String): Boolean = io {
        val time = now()
        dao.tombstone(id, deletedAt = time, updatedAt = time) > 0
    }

    override suspend fun retrieveMemories(namespace: String, limit: Int): List<Memory> = io {
        dao.retrieve(namespace, limit).map { it.toModel() }
    }

    override fun observeMemories(namespace: String, limit: Int): Flow<List<Memory>> =
        dao.observe(namespace, limit).map { entities -> entities.map { it.toModel() } }

    override suspend fun exportMemories(limit: Int): List<Memory> = io {
        dao.listAll(limit).map { it.toModel() }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
