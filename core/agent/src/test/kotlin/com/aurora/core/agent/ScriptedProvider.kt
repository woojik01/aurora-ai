package com.aurora.core.agent

import com.aurora.core.ai.AIProvider
import com.aurora.core.ai.FailureType
import com.aurora.core.ai.GenerationRequest
import com.aurora.core.ai.GenerationResult
import com.aurora.core.ai.Health
import com.aurora.core.ai.Modality
import com.aurora.core.ai.ModelDescriptor
import com.aurora.core.ai.ProviderCapabilities
import com.aurora.core.ai.ProviderException
import com.aurora.core.ai.StreamEvent
import com.aurora.core.ai.UsageEstimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test double implementing [AIProvider] with a scripted list of results.
 * It proves the real [AIProvider] interface is used end-to-end by the runtime
 * (PRD-01 AC 4) and lets tests simulate model misbehavior deterministically.
 */
class ScriptedProvider(
    override val providerId: String = "scripted",
    private val script: MutableList<GenerationResult>,
) : AIProvider {

    val generateCalls = mutableListOf<String>()
    private val cancelled = mutableSetOf<String>()

    /** Simulated latency; uses virtual time under kotlinx-coroutines-test. */
    var delayMillis: Long = 0

    override suspend fun listModels(): List<ModelDescriptor> = listOf(
        ModelDescriptor(providerId, "scripted-1", 4096, setOf(Modality.TEXT), true, true, local = true),
    )

    override suspend fun healthCheck(): Health = Health.OK

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        generateCalls.add(request.requestId)
        if (request.requestId in cancelled) {
            throw ProviderException(FailureType.CANCELLED, "cancelled")
        }
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
        val next = script.removeFirstOrNull()
            ?: throw ProviderException(FailureType.MALFORMED_RESPONSE, "script exhausted")
        return next
    }

    override fun stream(request: GenerationRequest): Flow<StreamEvent> = flow {
        emit(StreamEvent.TokenDelta("stub"))
        emit(StreamEvent.Completed)
    }

    override suspend fun cancel(requestId: String) {
        cancelled.add(requestId)
    }

    override suspend fun estimateUsage(request: GenerationRequest): UsageEstimate =
        UsageEstimate(null, null, estimated = true)

    override fun capabilities(): ProviderCapabilities =
        ProviderCapabilities(supportsToolCalls = true, supportsStreaming = true, local = true)
}
