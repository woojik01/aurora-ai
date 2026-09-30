package com.aurora.core.domain.service

import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.Memory
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRetrievalTest {

    private fun memory(id: String, content: String, type: String = "fact", source: String? = null) = Memory(
        id = id,
        namespace = "profile",
        type = type,
        content = content,
        sourceMessageId = source,
        createdAt = 0,
        updatedAt = 0,
        sensitivity = DataSensitivity.PERSONAL,
    )

    @Test
    fun emptyMemoriesProduceEmptyContext() {
        assertEquals("", MemoryRetrieval.buildContext(emptyList()))
    }

    @Test
    fun contextCarriesTypeAndProvenance() {
        val context = MemoryRetrieval.buildContext(
            listOf(memory("m1", "Lives in Seoul", source = "msg-9")),
        )
        assertEquals("- [fact] Lives in Seoul (from message msg-9)", context)
    }

    @Test
    fun memoriesWithoutSourceHaveNoProvenanceSuffix() {
        val context = MemoryRetrieval.buildContext(listOf(memory("m1", "Likes tea")))
        assertEquals("- [fact] Likes tea", context)
    }

    @Test
    fun budgetDropsEntriesThatDoNotFitWhole() {
        val a = memory("a", "a".repeat(10))
        val b = memory("b", "b".repeat(10))
        // First entry renders to exactly 20 chars ("- [fact] " + 10). Allow one.
        val context = MemoryRetrieval.buildContext(listOf(a, b), maxChars = 20)
        assertEquals("- [fact] aaaaaaaaaa", context)
    }

    @Test
    fun zeroBudgetProducesEmptyContext() {
        assertEquals("", MemoryRetrieval.buildContext(listOf(memory("a", "x")), maxChars = 0))
    }
}
