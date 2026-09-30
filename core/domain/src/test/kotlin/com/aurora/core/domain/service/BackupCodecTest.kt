package com.aurora.core.domain.service

import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.Memory
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {

    private val conversation = Conversation("c1", "trip|with pipes", 10, 20, archived = true)
    private val message = Message(
        id = "m1",
        conversationId = "c1",
        role = MessageRole.USER,
        content = "line1\nline2 with | pipe and \\ backslash",
        timestamp = 30,
        status = MessageStatus.SENT,
        providerId = "fake",
        modelId = "local-1",
    )
    private val aliveMemory = Memory("mem1", "profile", "fact", "Likes tea", sourceMessageId = "m1", createdAt = 40, updatedAt = 50)
    private val deletedMemory = Memory("mem2", "profile", "fact", "old", createdAt = 40, updatedAt = 60, deletedAt = 99)

    @Test
    fun roundTripPreservesEverything() {
        val encoded = BackupCodec.encode(listOf(conversation), listOf(message), listOf(aliveMemory, deletedMemory))
        val decoded = BackupCodec.decode(encoded)

        assertEquals(listOf(conversation), decoded.conversations)
        assertEquals(listOf(message), decoded.messages)
        assertEquals(listOf(aliveMemory, deletedMemory), decoded.memories)
        // deletion state survives the round trip
        assertNull(decoded.memories[0].deletedAt)
        assertEquals(99L, decoded.memories[1].deletedAt)
    }

    @Test
    fun emptyBackupRoundTrips() {
        val decoded = BackupCodec.decode(BackupCodec.encode(emptyList(), emptyList(), emptyList()))
        assertEquals(0, decoded.conversations.size)
        assertEquals(0, decoded.messages.size)
        assertEquals(0, decoded.memories.size)
    }

    @Test
    fun nullableFieldsSurviveRoundTrip() {
        val plain = Message("m2", "c1", MessageRole.ASSISTANT, "hi", 1, MessageStatus.SENT)
        val decoded = BackupCodec.decode(BackupCodec.encode(emptyList(), listOf(plain), emptyList()))
        assertNull(decoded.messages[0].providerId)
        assertNull(decoded.messages[0].modelId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsForeignFormat() {
        BackupCodec.decode("some random text")
    }
}
