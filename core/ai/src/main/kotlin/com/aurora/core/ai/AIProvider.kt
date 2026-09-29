package com.aurora.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic contract (PRD-03). Local runtimes and API providers are
 * interchangeable behind this interface; the domain/agent layer never sees
 * provider SDKs or network clients.
 */
interface AIProvider {
    val providerId: String

    suspend fun listModels(): List<ModelDescriptor>

    suspend fun healthCheck(): Health

    suspend fun generate(request: GenerationRequest): GenerationResult

    fun stream(request: GenerationRequest): Flow<StreamEvent>

    suspend fun cancel(requestId: String)

    suspend fun estimateUsage(request: GenerationRequest): UsageEstimate

    fun capabilities(): ProviderCapabilities
}

/** Structured streaming events — raw string concatenation in UI state is forbidden (PRD-03). */
sealed interface StreamEvent {
    data class TokenDelta(val text: String) : StreamEvent
    data class ToolCallDelta(val index: Int, val toolId: String, val argumentFragment: String) : StreamEvent
    data class Usage(val usage: UsageEstimate) : StreamEvent
    data object Completed : StreamEvent
    data class Failed(val failure: FailureType, val message: String? = null) : StreamEvent
    data object Cancelled : StreamEvent
}
