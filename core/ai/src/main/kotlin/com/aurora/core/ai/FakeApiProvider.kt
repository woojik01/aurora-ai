package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic fake API provider (PRD-03 acceptance: one fake local and one
 * fake API provider pass the same provider test suite). Clearly labelled
 * "fake" — no network calls, no credentials, nothing secret.
 *
 * [failNextRequestWith] injects a classified failure once, so fallback and
 * retry policies can be tested deterministically.
 */
class FakeApiProvider : AIProvider {

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var nextFailure: FailureType? = null

    override val providerId: String = PROVIDER_ID

    fun failNextRequestWith(failure: FailureType) {
        nextFailure = failure
    }

    override suspend fun listModels(): List<ModelDescriptor> = listOf(MODEL)

    override suspend fun healthCheck(): Health =
        if (nextFailure == null) Health.OK else Health.DEGRADED

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        if (request.requestId in cancelled) {
            throw ProviderException(FailureType.CANCELLED, "Request was cancelled")
        }
        nextFailure?.let { failure ->
            nextFailure = null
            throw ProviderException(failure, "Injected failure")
        }
        val lastUser = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val text = "Aurora api echo: $lastUser"
        return GenerationResult(
            text = text,
            toolCalls = emptyList(),
            usage = UsageEstimate(inputTokens = lastUser.length, outputTokens = text.length, estimated = true),
            finishReason = FinishReason.STOP,
        )
    }

    override fun stream(request: GenerationRequest): Flow<StreamEvent> = flow {
        if (request.requestId in cancelled) {
            emit(StreamEvent.Cancelled)
            return@flow
        }
        nextFailure?.let { failure ->
            nextFailure = null
            emit(StreamEvent.Failed(failure, "Injected failure"))
            return@flow
        }
        val lastUser = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val text = "Aurora api echo: $lastUser"
        text.chunked(8).forEach { chunk ->
            if (request.requestId in cancelled) {
                emit(StreamEvent.Cancelled)
                return@flow
            }
            emit(StreamEvent.TokenDelta(chunk))
        }
        emit(StreamEvent.Usage(UsageEstimate(lastUser.length, text.length, estimated = true)))
        emit(StreamEvent.Completed)
    }

    override suspend fun cancel(requestId: String) {
        cancelled.add(requestId)
    }

    override suspend fun estimateUsage(request: GenerationRequest): UsageEstimate {
        val lastUser = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        return UsageEstimate(lastUser.length, null, estimated = true)
    }

    override fun capabilities(): ProviderCapabilities =
        ProviderCapabilities(supportsToolCalls = false, supportsStreaming = true, local = false)

    companion object {
        const val PROVIDER_ID = "fake-api"

        val MODEL = ModelDescriptor(
            providerId = PROVIDER_ID,
            modelId = "fake-api-1",
            contextLimit = 8192,
            modalities = setOf(Modality.TEXT),
            supportsToolCalls = false,
            supportsStreaming = true,
            local = false,
            costEstimateMetadata = "per-1k-tokens:v3",
            available = true,
        )
    }
}
