package com.aurora.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    /** Tombstoned memories are immutable: content updates must fail closed. */
    @Query(
        "UPDATE memories SET content = :content, updatedAt = :updatedAt " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun updateContent(id: String, content: String, updatedAt: Long): Int

    /** Soft delete: mark a tombstone so sync can propagate the deletion. */
    @Query(
        "UPDATE memories SET deletedAt = :deletedAt, updatedAt = :updatedAt " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun tombstone(id: String, deletedAt: Long, updatedAt: Long): Int

    /** Bounded normal retrieval — tombstones are never returned. */
    @Query(
        "SELECT * FROM memories WHERE deletedAt IS NULL AND namespace = :namespace " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun retrieve(namespace: String, limit: Int): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE deletedAt IS NULL AND namespace = :namespace " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    fun observe(namespace: String, limit: Int): Flow<List<MemoryEntity>>

    /** Export view: includes tombstones so backup/restore preserves deletion state. */
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun listAll(limit: Int): List<MemoryEntity>
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "aurora.db"

        /**
         * PRD-02: destructive migrations are forbidden in production. Every
         * schema change ships a real migration.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (" +
                        "`id` TEXT NOT NULL, " +
                        "`namespace` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`sourceMessageId` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`sensitivity` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_namespace` ON `memories` (`namespace`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_namespace_updatedAt` ON `memories` (`namespace`, `updatedAt`)")
            }
        }
    }
}
