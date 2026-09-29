package com.aurora.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setConversationTitle(id: String, title: String, updatedAt: Long)

    /** Bounded by an explicit LIMIT — callers must never load unbounded history. */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    fun observeMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT :limit")
    fun observeConversations(limit: Int): Flow<List<ConversationEntity>>
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val NAME = "aurora.db"
    }
}
