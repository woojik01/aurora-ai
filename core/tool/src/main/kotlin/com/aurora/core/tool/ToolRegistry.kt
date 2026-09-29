package com.aurora.core.tool

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of installed tools. Unknown tools cannot execute — a model
 * inventing a tool id fails closed at the policy engine.
 */
class ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        require(tools.putIfAbsent(tool.descriptor.toolId, tool) == null) {
            "Tool already registered: ${tool.descriptor.toolId}"
        }
    }

    fun find(toolId: String): Tool? = tools[toolId]

    fun all(): List<Tool> = tools.values.sortedBy { it.descriptor.toolId }

    fun clear() = tools.clear()
}
