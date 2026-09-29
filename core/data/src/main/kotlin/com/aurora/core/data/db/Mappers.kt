package com.aurora.core.data.db

import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
)

fun ConversationEntity.toModel() = Conversation(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = timestamp,
    status = status.name,
    providerId = providerId,
    modelId = modelId,
)

fun MessageEntity.toModel() = Message(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    content = content,
    timestamp = timestamp,
    status = MessageStatus.valueOf(status),
    providerId = providerId,
    modelId = modelId,
)
