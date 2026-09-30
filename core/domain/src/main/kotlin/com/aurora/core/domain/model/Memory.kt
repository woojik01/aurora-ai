package com.aurora.core.domain.model

/**
 * A durable semantic memory (PRD-02). Conversation history and memory are
 * separate concepts: the assistant may use memories only through an explicit
 * memory retrieval layer, never by scanning the database.
 *
 * [sourceMessageId] and timestamps are provenance metadata so the UI can show
 * why a memory exists. [deletedAt] marks a tombstone: deletion propagates as a
 * tombstone during device sync instead of silently resurrecting data.
 */
data class Memory(
    val id: String,
    val namespace: String,
    val type: String,
    val content: String,
    val sourceMessageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val sensitivity: DataSensitivity = DataSensitivity.PERSONAL,
    val deletedAt: Long? = null,
)
