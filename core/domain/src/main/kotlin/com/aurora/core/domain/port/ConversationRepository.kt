package com.aurora.core.domain.port

import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary of the domain layer. Implementations live in :core:data;
 * the domain never depends on Room, Android, or UI.
 *
 * Queries are bounded: callers must pass explicit limits; there is no
 * "load everything" operation (PRD-01 performance baseline).
 */
interface ConversationRepository {
    suspend fun createConversation(title: String): Conversation
    suspend fun getConversation(id: String): Conversation?
    suspend fun setConversationTitle(conversationId: String, title: String)
    suspend fun appendMessage(message: Message)
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
    fun observeMessages(conversationId: String, limit: Int = 200): Flow<List<Message>>
    fun observeConversations(limit: Int = 100): Flow<List<Conversation>>
}
