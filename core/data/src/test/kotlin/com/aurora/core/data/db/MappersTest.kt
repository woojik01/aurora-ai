package com.aurora.core.data.db

import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `conversation round-trips through the entity`() {
        val model = Conversation("c1", "title", 10, 20, archived = false)
        assertEquals(model, model.toEntity().toModel())
    }

    @Test
    fun `message round-trips through the entity`() {
        val model = Message(
            id = "m1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "hello",
            timestamp = 42,
            status = MessageStatus.SENT,
            providerId = "fake-local",
            modelId = "fake-assistant-1",
        )
        assertEquals(model, model.toEntity().toModel())
    }

    @Test
    fun `message without provider info keeps nulls`() {
        val model = Message("m2", "c1", MessageRole.USER, "hi", 1, MessageStatus.PENDING)
        assertNull(model.toEntity().toModel().providerId)
        assertNull(model.toEntity().toModel().modelId)
    }
}
