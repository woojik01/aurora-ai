package com.aurora.core.domain.service

import com.aurora.core.domain.model.Memory

/**
 * Explicit memory retrieval layer (PRD-02): the assistant may use memories
 * only through this layer — never by scanning the database. Retrieval is
 * deterministic (callers pass an already bounded, filtered list) and the
 * character budget is enforced here, before prompt construction.
 */
object MemoryRetrieval {

    /**
     * Renders up to [maxChars] characters of memory context, newest first.
     * Each entry carries provenance so the UI can show why a memory exists.
     */
    fun buildContext(memories: List<Memory>, maxChars: Int = 2_000): String {
        require(maxChars >= 0) { "maxChars must be >= 0" }
        if (memories.isEmpty() || maxChars == 0) return ""
        val lines = ArrayList<String>(memories.size)
        var used = 0
        for (memory in memories) {
            val line = render(memory)
            if (used + line.length > maxChars) break
            lines.add(line)
            used += line.length
        }
        return lines.joinToString("\n")
    }

    private fun render(memory: Memory): String {
        val source = memory.sourceMessageId?.let { " (from message $it)" } ?: ""
        return "- [${memory.type}] ${memory.content}$source"
    }
}
