package com.aurora.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A provider that routes through [FallbackRouter] and records every request
 * in the [UsageLedger] (PRD-03). Because it implements [AIProvider], the
 * agent runtime and UI keep working against the plain contract — provider
 * replacement requires no UI/domain rewrite.
 *
 * The last routing decision is exposed as a [StateFlow] so the UI can show
 * provider, model, and fallback reason — never silently rerouting.
 */
class RoutedProvider(
    private val primary: AIProvider,
    private val fallback: AIProvider,
    private val ledger: UsageLedger,
    /** Per-model estimated price in micros per 1k tokens; unknown → no cost claim. */
    private val priceMicrosPer1k: Map<String, Long> = emptyMap(),
) : AIProvider {

    private val router = FallbackRouter(primary, fallback)

    private val _lastRouting = MutableStateFlow<RoutingSummary?>(null)
    val lastRouting: StateFlow<RoutingSummary?> = _lastRouting.asStateFlow()

    override val providerId: String
        get() = primary.providerId

    override suspend fun listModels(): List<ModelDescriptor> = primary.listModels()

    override suspend fun healthCheck(): Health {
        return when (primary.healthCheck()) {
            Health.OK -> Health.OK
            else -> when (fallback.healthCheck()) {
                Health.OK -> Health.DEGRADED
                else -> Health.UNAVAILABLE
            }
        }
    }

    override suspend fun generate(request: GenerationRequest): GenerationResult {
        val routed = router.generate(request)
        recordUsage(routed, request.requestId)
        _lastRouting.value = RoutingSummary(
            providerId = routed.providerId,
            modelId = routed.modelId,
            fallbackUsed = routed.fallbackUsed,
            fallbackReason = routed.fallbackReason,
        )
        return routed.result
    }

    override fun stream(request: GenerationRequest) = primary.stream(request)

    override suspend fun cancel(requestId: String) {
        primary.cancel(requestId)
        fallback.cancel(requestId)
    }

    override suspend fun estimateUsage(request: GenerationRequest): UsageEstimate =
        primary.estimateUsage(request)

    override fun capabilities(): ProviderCapabilities = primary.capabilities()

    private suspend fun recordUsage(routed: FallbackRouter.Routed, requestId: String) {
        val usage = routed.result.usage ?: return
        val model = fallbackOrPrimaryDescriptor(routed.providerId, routed.modelId)
        val pricingVersion = model?.costEstimateMetadata?.substringAfterLast(':', missingDelimiterValue = null)
        ledger.record(
            providerId = routed.providerId,
            modelId = routed.modelId,
            requestId = requestId,
            usage = usage,
            pricingVersion = pricingVersion,
            costMicrosPer1kTokens = priceMicrosPer1k[routed.modelId],
        )
    }

    private suspend fun fallbackOrPrimaryDescriptor(providerId: String, modelId: String): ModelDescriptor? =
        primary.listModels().firstOrNull { it.providerId == providerId && it.modelId == modelId }
            ?: fallback.listModels().firstOrNull { it.providerId == providerId && it.modelId == modelId }

    /** Test hook: the ledger is intentionally not public API for the UI. */
    internal fun ledgerRefForTest(): UsageLedger = ledger
}

/** What the UI shows after each request (PRD-03 display requirements). */
data class RoutingSummary(
    val providerId: String,
    val modelId: String,
    val fallbackUsed: Boolean,
    val fallbackReason: FailureType?,
)
