package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.ToolSchema

enum class Modality { TEXT, IMAGE_INPUT, VOICE }

data class ProviderCapabilities(
    val supportsToolCalls: Boolean,
    val supportsStreaming: Boolean,
    val local: Boolean,
)

data class ModelDescriptor(
    val providerId: String,
    val modelId: String,
    val contextLimit: Int?,
    val modalities: Set<Modality>,
    val supportsToolCalls: Boolean,
    val supportsStreaming: Boolean,
    val local: Boolean,
    /**
     * e.g. "per-1k-tokens:v3". Null means no pricing metadata is known.
     * Costs derived from metadata are always estimates, never measured billing.
     */
    val costEstimateMetadata: String? = null,
    val available: Boolean = true,
)

data class ChatMessage(val role: MessageRole, val content: String)

data class GenerationRequest(
    val requestId: String,
    val messages: List<ChatMessage>,
    val model: ModelDescriptor,
    /** toolId -> parameter schema */
    val toolSchemas: Map<String, List<ToolSchema>> = emptyMap(),
    val maxTokens: Int? = null,
)

data class UsageEstimate(
    val inputTokens: Int?,
    val outputTokens: Int?,
    /** true unless the provider reports measured usage through billing APIs */
    val estimated: Boolean = true,
)

enum class FinishReason { STOP, TOOL_CALLS, LENGTH, ERROR }

data class GenerationResult(
    val text: String,
    /**
     * Structured tool calls emitted by the provider. Only entries here are
     * ever considered by the runtime — never text that merely looks like a tool call.
     */
    val toolCalls: List<com.aurora.core.domain.model.ToolRequest>,
    val usage: UsageEstimate?,
    val finishReason: FinishReason,
)

enum class Health { OK, DEGRADED, UNAVAILABLE }

enum class FailureType {
    AUTHENTICATION,
    QUOTA,
    TIMEOUT,
    NETWORK,
    PROVIDER_OVERLOAD,
    MALFORMED_RESPONSE,
    UNSUPPORTED_CAPABILITY,
    CANCELLED,
    UNKNOWN,
}
