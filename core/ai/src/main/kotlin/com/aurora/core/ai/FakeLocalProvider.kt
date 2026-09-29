package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.ToolRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic, fully offline provider used for PRD-01 acceptance:
 * a fake provider exercising the exact same runtime interfaces that future
 * real local runtimes (llama.cpp/Ollama adapters) and API providers will use.
 *
 * It is clearly labelled "fake" — it is not a real model runtime.
 */
class FakeLocalProvider : AIProvider {

    override val providerId: String = PROVIDER_ID

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    override suspend fun listModels(): List<ModelDescriptor> = listOf(MODEL)

    override suspend fun healthCheck(): Health = Health.OK

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        if (request.requestId in cancelled) {
            throw ProviderException(FailureType.CANCELLED, "Request was cancelled")
        }
        val lastUser = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val text = "Aurora local echo: $lastUser"
        return GenerationResult(
            text = text,
            toolCalls = emptyList<ToolRequest>(),
            usage = UsageEstimate(inputTokens = null, outputTokens = text.length, estimated = true),
            finishReason = FinishReason.STOP,
        )
    }

    override fun stream(request: GenerationRequest): Flow<StreamEvent> = flow {
        if (request.requestId in cancelled) {
            emit(StreamEvent.Cancelled)
            return@flow
        }
        val text = "Aurora local echo: " +
            (request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: "")
        text.chunked(8).forEach { chunk ->
            if (request.requestId in cancelled) {
                emit(StreamEvent.Cancelled)
                return@flow
            }
            emit(StreamEvent.TokenDelta(chunk))
        }
        emit(StreamEvent.Usage(UsageEstimate(null, text.length, estimated = true)))
        emit(StreamEvent.Completed)
    }

    override suspend fun cancel(requestId: String) {
        cancelled.add(requestId)
    }

    override suspend fun estimateUsage(request: GenerationRequest): UsageEstimate =
        UsageEstimate(null, null, estimated = true)

    override fun capabilities(): ProviderCapabilities =
        ProviderCapabilities(supportsToolCalls = false, supportsStreaming = true, local = true)

    companion object {
        const val PROVIDER_ID = "fake-local"

        val MODEL = ModelDescriptor(
            providerId = PROVIDER_ID,
            modelId = "fake-assistant-1",
            contextLimit = 4096,
            modalities = setOf(Modality.TEXT),
            supportsToolCalls = false,
            supportsStreaming = true,
            local = true,
            costEstimateMetadata = null,
            available = true,
        )
    }
}
