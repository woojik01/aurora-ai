package com.aurora.core.domain.model

/**
 * Typed tool request. The model can only produce these through the provider
 * abstraction; raw model text is never parsed into executable requests.
 */
data class ToolSchema(
    val name: String,
    val type: ToolParamType,
    val required: Boolean = true,
)

data class ToolRequest(
    val toolId: String,
    val arguments: Map<String, String> = emptyMap(),
) {
    /** Deterministic encoding used for action hashing and approval binding. */
    fun canonicalArguments(): String =
        arguments.entries
            .sortedBy { it.key }
            .joinToString("&") { entry ->
                // Length-prefixed to avoid ambiguity ("a=1&b=2" vs "a=1&b" + "=2").
                entry.key.length.toString() + ":" + entry.key + "=" + entry.value.length + ":" + entry.value
            }

    /** Returns an empty list when valid; otherwise a list of human-readable validation errors. */
    fun validate(schema: List<ToolSchema>): List<String> {
        val errors = mutableListOf<String>()
        for (expected in schema) {
            if (expected.required && expected.name !in arguments) {
                errors.add("Missing required parameter '${expected.name}' for tool '$toolId'")
            }
        }
        for (name in arguments.keys) {
            if (name !in schema.map { it.name }) {
                errors.add("Unknown parameter '$name' for tool '$toolId'")
            }
        }
        return errors
    }
}

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
)
