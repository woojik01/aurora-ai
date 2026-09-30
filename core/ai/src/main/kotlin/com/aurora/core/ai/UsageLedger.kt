package com.aurora.core.ai

import com.aurora.core.domain.model.AuroraId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local usage ledger (PRD-03 cost accounting). Records provider/model,
 * timestamp, token counts when available, pricing version, currency, and
 * estimate status. Costs are ALWAYS estimates here — measured billing data
 * never enters the ledger unless a provider exposes it, and then
 * [LedgerEntry.estimated] is false.
 */
class UsageLedger(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> AuroraId = { AuroraId.generate() },
) {

    private val entries = CopyOnWriteArrayList<LedgerEntry>()

    /**
     * Records one request. [costMicrosPer1kTokens] comes from pricing
     * metadata (e.g. "per-1k-tokens:v3"); null means "no pricing metadata
     * known" and the entry carries no cost claim at all.
     */
    fun record(
        providerId: String,
        modelId: String,
        requestId: String,
        usage: UsageEstimate,
        pricingVersion: String?,
        currency: String = "USD",
        costMicrosPer1kTokens: Long?,
    ): LedgerEntry {
        val tokens = (usage.inputTokens ?: 0) + (usage.outputTokens ?: 0)
        val estimatedCost = costMicrosPer1kTokens?.takeIf { usage.estimated && tokens > 0 }
            ?.let { per1k -> tokens.toLong() * per1k / 1_000 }
        val entry = LedgerEntry(
            id = idGen().value,
            providerId = providerId,
            modelId = modelId,
            requestId = requestId,
            timestamp = now(),
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            pricingVersion = pricingVersion,
            currency = currency,
            estimatedCostMicros = estimatedCost,
            estimated = usage.estimated,
        )
        entries.add(entry)
        return entry
    }

    /** Bounded view — there is no "load everything" operation (PRD-02 rule). */
    fun recentEntries(limit: Int = 100): List<LedgerEntry> =
        entries.sortedByDescending { it.timestamp }.take(limit)

    /** Totals since [since]. Token sums only count known token counts. */
    fun totalsSince(since: Long): UsageTotals {
        val window = entries.filter { it.timestamp >= since }
        return UsageTotals(
            requests = window.size,
            inputTokens = window.sumOf { (it.inputTokens ?: 0).toLong() },
            outputTokens = window.sumOf { (it.outputTokens ?: 0).toLong() },
            estimatedCostMicros = window.filter { it.estimated }.sumOf { it.estimatedCostMicros ?: 0L },
            allCostsAreEstimates = window.none { !it.estimated },
        )
    }

    fun clear() {
        entries.clear()
    }
}

data class LedgerEntry(
    val id: String,
    val providerId: String,
    val modelId: String,
    val requestId: String,
    val timestamp: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val pricingVersion: String?,
    val currency: String,
    /** Null when no pricing metadata is known — no cost claim is made. */
    val estimatedCostMicros: Long?,
    /** True unless the provider reported measured billing usage. */
    val estimated: Boolean,
)

data class UsageTotals(
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val estimatedCostMicros: Long,
    val allCostsAreEstimates: Boolean,
)
