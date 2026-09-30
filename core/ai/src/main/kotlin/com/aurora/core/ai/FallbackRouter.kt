package com.aurora.core.ai

/**
 * Automatic fallback routing (PRD-03): when the selected API provider fails,
 * fall back to an available local model — but only when a compatible one
 * exists. Never silently reroute; the outcome always states which provider
 * answered and why fallback happened.
 *
 * Only idempotent generation is routed here. Tool calls with side effects
 * are the runtime's concern and are never retried by the router.
 */
class FallbackRouter(
    private val primary: AIProvider,
    private val fallback: AIProvider,
) {

    data class Routed(
        val result: GenerationResult,
        val providerId: String,
        val modelId: String,
        val fallbackUsed: Boolean,
        val fallbackReason: FailureType?,
    )

    suspend fun generate(request: GenerationRequest): Routed {
        try {
            val result = primary.generate(request)
            return Routed(
                result = result,
                providerId = primary.providerId,
                modelId = request.model.modelId,
                fallbackUsed = false,
                fallbackReason = null,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation is the caller's intent, not a failure to route around.
            throw e
        } catch (e: Exception) {
            val failure = FailureClassifier.classify(e)
            val localModel = findCompatibleLocalModel(request)
                ?: throw e // no compatible local model: surface the original failure
            val localRequest = request.copy(model = localModel)
            val result = fallback.generate(localRequest)
            return Routed(
                result = result,
                providerId = fallback.providerId,
                modelId = localModel.modelId,
                fallbackUsed = true,
                fallbackReason = failure,
            )
        }
    }

    /**
     * A local model is compatible when it is available, handles TEXT, and
     * supports tool calls whenever the request carries tool schemas.
     */
    suspend fun findCompatibleLocalModel(request: GenerationRequest): ModelDescriptor? {
        if (request.model.local) return null // primary already local: no further fallback
        val needsTools = request.toolSchemas.isNotEmpty()
        return fallback.listModels().firstOrNull { model ->
            model.available &&
                model.local &&
                Modality.TEXT in model.modalities &&
                (!needsTools || model.supportsToolCalls)
        }
    }
}
