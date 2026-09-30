package com.aurora.core.domain.service

import com.aurora.core.domain.model.Conversation
import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.Memory
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus

/**
 * Human-inspectable, line-based backup format (PRD-02 export/import).
 *
 * Guarantees the acceptance criteria round trip: IDs, timestamps,
 * relationships, and deletion state (tombstones) are preserved exactly.
 * Field values escape backslash, pipe, and newline so content is never
 * ambiguous; nulls encode as the empty string.
 */
object BackupCodec {

    private const val HEADER = "# aurora-backup/1"

    fun encode(
        conversations: List<Conversation>,
        messages: List<Message>,
        memories: List<Memory>,
    ): String = buildString {
        appendLine(HEADER)
        appendLine("[conversations]")
        for (c in conversations) {
            appendLine(
                listOf(
                    esc(c.id), esc(c.title), c.createdAt.toString(), c.updatedAt.toString(),
                    if (c.archived) "1" else "0",
                ).joinToString("|"),
            )
        }
        appendLine("[messages]")
        for (m in messages) {
            appendLine(
                listOf(
                    esc(m.id), esc(m.conversationId), m.role.name, esc(m.content),
                    m.timestamp.toString(), m.status.name,
                    m.providerId?.let(::esc) ?: "", m.modelId?.let(::esc) ?: "",
                ).joinToString("|"),
            )
        }
        appendLine("[memories]")
        for (mem in memories) {
            appendLine(
                listOf(
                    esc(mem.id), esc(mem.namespace), esc(mem.type), esc(mem.content),
                    mem.sourceMessageId?.let(::esc) ?: "",
                    mem.createdAt.toString(), mem.updatedAt.toString(),
                    mem.sensitivity.name,
                    mem.deletedAt?.toString() ?: "",
                ).joinToString("|"),
            )
        }
    }

    fun decode(text: String): BackupData {
        val lines = text.lines()
        require(lines.firstOrNull() == HEADER) { "Not an aurora backup" }
        var section = ""
        val conversations = mutableListOf<Conversation>()
        val messages = mutableListOf<Message>()
        val memories = mutableListOf<Memory>()
        for (line in lines.drop(1)) {
            when (line) {
                "[conversations]", "[messages]", "[memories]" -> { section = line; continue }
                "" -> continue
            }
            val f = line.split('|').map(::unesc)
            when (section) {
                "[conversations]" -> conversations += Conversation(
                    id = f[0],
                    title = f[1],
                    createdAt = f[2].toLong(),
                    updatedAt = f[3].toLong(),
                    archived = f[4] == "1",
                )
                "[messages]" -> messages += Message(
                    id = f[0],
                    conversationId = f[1],
                    role = MessageRole.valueOf(f[2]),
                    content = f[3],
                    timestamp = f[4].toLong(),
                    status = MessageStatus.valueOf(f[5]),
                    providerId = f[6].ifEmpty { null },
                    modelId = f[7].ifEmpty { null },
                )
                "[memories]" -> memories += Memory(
                    id = f[0],
                    namespace = f[1],
                    type = f[2],
                    content = f[3],
                    sourceMessageId = f[4].ifEmpty { null },
                    createdAt = f[5].toLong(),
                    updatedAt = f[6].toLong(),
                    sensitivity = DataSensitivity.valueOf(f[7]),
                    deletedAt = f[8].ifEmpty { null }?.toLong(),
                )
            }
        }
        return BackupData(conversations, messages, memories)
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")

    private fun unesc(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'p' -> { sb.append('|'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    else -> { sb.append(c); i += 1 }
                }
            } else {
                sb.append(c); i += 1
            }
        }
        return sb.toString()
    }
}

data class BackupData(
    val conversations: List<Conversation>,
    val messages: List<Message>,
    val memories: List<Memory>,
)
