package com.aurora.core.data.repository

import com.aurora.core.data.db.ConversationDao
import com.aurora.core.data.db.toEntity
import com.aurora.core.data.db.toModel
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageStatus
import com.aurora.core.domain.port.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of the domain persistence port. All database work
 * runs on Dispatchers.IO — never on the main thread (PRD-01 performance rule).
 * All queries are bounded by explicit limits.
 */
class ConversationRepositoryImpl(
    private val dao: ConversationDao,
    private val idGen: () -> AuroraId = { AuroraId.generate() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ConversationRepository {

    override suspend fun createConversation(title: String): Conversation = io {
        val id = idGen().value
        val time = now()
        val conversation = Conversation(id, title, time, time)
        dao.insertConversation(conversation.toEntity())
        conversation
    }

    override suspend fun getConversation(id: String): Conversation? = io {
        dao.getConversation(id)?.toModel()
    }

    override suspend fun setConversationTitle(conversationId: String, title: String) {
        io { dao.setConversationTitle(conversationId, title, now()) }
    }

    override suspend fun appendMessage(message: Message) {
        io { dao.insertMessage(message.toEntity()) }
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        io { dao.updateMessageStatus(messageId, status.name) }
    }

    override fun observeMessages(conversationId: String, limit: Int): Flow<List<Message>> =
        dao.observeMessages(conversationId, limit)
            // Room returns newest-first (LIMIT from the end); UI wants chronological order.
            .map { entities -> entities.sortedBy { it.timestamp }.map { it.toModel() } }

    override fun observeConversations(limit: Int): Flow<List<Conversation>> =
        dao.observeConversations(limit).map { entities -> entities.map { it.toModel() } }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
