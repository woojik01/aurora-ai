package com.aurora.core.data.repository

import com.aurora.core.data.db.MemoryDao
import com.aurora.core.data.db.MemoryEntity
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.DataSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory [MemoryDao] fake — the DAO contract is small enough that a fake
 * keeps the tests pure-JVM (no Room/Robolectric needed).
 */
private class FakeMemoryDao : MemoryDao {
    val rows = MutableStateFlow<List<MemoryEntity>>(emptyList())

    override suspend fun insert(memory: MemoryEntity) {
        rows.value = rows.value + memory
    }

    override suspend fun get(id: String): MemoryEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun updateContent(id: String, content: String, updatedAt: Long): Int {
        if (rows.value.none { it.id == id && it.deletedAt == null }) return 0
        rows.value = rows.value.map {
            if (it.id == id) it.copy(content = content, updatedAt = updatedAt) else it
        }
        return 1
    }

    override suspend fun tombstone(id: String, deletedAt: Long, updatedAt: Long): Int {
        if (rows.value.none { it.id == id && it.deletedAt == null }) return 0
        rows.value = rows.value.map {
            if (it.id == id) it.copy(deletedAt = deletedAt, updatedAt = updatedAt) else it
        }
        return 1
    }

    override suspend fun retrieve(namespace: String, limit: Int): List<MemoryEntity> =
        rows.value
            .filter { it.deletedAt == null && it.namespace == namespace }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    override fun observe(namespace: String, limit: Int): Flow<List<MemoryEntity>> =
        rows.map { list ->
            list
                .filter { it.deletedAt == null && it.namespace == namespace }
                .sortedByDescending { it.updatedAt }
                .take(limit)
        }

    override suspend fun listAll(limit: Int): List<MemoryEntity> =
        rows.value.sortedByDescending { it.updatedAt }.take(limit)
}

class MemoryRepositoryImplTest {

    private var fakeTime = 1_000L

    private fun newRepo(dao: FakeMemoryDao = FakeMemoryDao()): MemoryRepositoryImpl {
        var seq = 0
        return MemoryRepositoryImpl(
            dao = dao,
            idGen = { seq += 1; AuroraId.generate(0) },
            now = { fakeTime += 10; fakeTime },
        )
    }

    @Test
    fun createPersistsAllFields() {
        runBlocking {
            val dao = FakeMemoryDao()
            val repo = newRepo(dao)
            val memory = repo.createMemory(
                namespace = "profile",
                type = "fact",
                content = "User lives in Seoul",
                sensitivity = DataSensitivity.PERSONAL,
                sourceMessageId = "msg-1",
            )
            val stored = repo.getMemory(memory.id)
            assertNotNull(stored)
            assertEquals("profile", stored!!.namespace)
            assertEquals("fact", stored.type)
            assertEquals("User lives in Seoul", stored.content)
            assertEquals("msg-1", stored.sourceMessageId)
            assertEquals(DataSensitivity.PERSONAL, stored.sensitivity)
            assertEquals(memory.createdAt, stored.createdAt)
        }
    }

    @Test
    fun updateChangesContentAndReturnsTrue() {
        runBlocking {
            val repo = newRepo()
            val memory = repo.createMemory("profile", "fact", "old")
            assertTrue(repo.updateMemoryContent(memory.id, "new content"))
            assertEquals("new content", repo.getMemory(memory.id)!!.content)
        }
    }

    @Test
    fun updateFailsOnMissingMemory() {
        runBlocking {
            assertFalse(newRepo().updateMemoryContent("missing", "x"))
        }
    }

    @Test
    fun deleteIsATombstoneHiddenFromRetrieval() {
        runBlocking {
            val repo = newRepo()
            val memory = repo.createMemory("profile", "fact", "keep me")
            val other = repo.createMemory("profile", "fact", "other")

            assertTrue(repo.deleteMemory(memory.id))
            // getMemory and retrieval never return tombstones
            assertNull(repo.getMemory(memory.id))
            assertEquals(listOf(other.id), repo.retrieveMemories("profile").map { it.id })
            // but export preserves the deletion state for backup round trips
            val exported = repo.exportMemories()
            assertEquals(2, exported.size)
            assertNotNull(exported.first { it.id == memory.id }.deletedAt)
        }
    }

    @Test
    fun doubleDeleteReturnsFalse() {
        runBlocking {
            val repo = newRepo()
            val memory = repo.createMemory("profile", "fact", "x")
            assertTrue(repo.deleteMemory(memory.id))
            assertFalse(repo.deleteMemory(memory.id))
        }
    }

    @Test
    fun retrieveIsBoundedAndNewestFirst() {
        runBlocking {
            val repo = newRepo()
            repeat(5) { repo.createMemory("profile", "fact", "m$it") }
            val top3 = repo.retrieveMemories("profile", limit = 3)
            assertEquals(3, top3.size)
            // FakeMemoryDao sorts by updatedAt DESC; ids were created oldest-first.
            val allIds = repo.exportMemories(10).sortedBy { it.createdAt }.map { it.id }
            assertEquals(allIds.takeLast(3).reversed(), top3.map { it.id })
        }
    }

    @Test
    fun retrieveFiltersByNamespace() {
        runBlocking {
            val repo = newRepo()
            repo.createMemory("profile", "fact", "a")
            repo.createMemory("work", "fact", "b")
            assertEquals(listOf("b"), repo.retrieveMemories("work").map { it.content })
        }
    }

    @Test
    fun observeExcludesTombstones() {
        runBlocking {
            val dao = FakeMemoryDao()
            val repo = newRepo(dao)
            repo.createMemory("profile", "fact", "a")
            val visible = repo.createMemory("profile", "fact", "b")
            repo.deleteMemory(visible.id)
            val observed = repo.observeMemories("profile").first()
            assertEquals(listOf("a"), observed.map { it.content })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun createRejectsBlankContent() {
        runBlocking {
            newRepo().createMemory("profile", "fact", " ")
        }
    }
}
