package com.aurora.core.ai

import com.aurora.core.domain.model.AuroraId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLedgerTest {

    /** Deterministic clock: each record() consumes the next timestamp. */
    private fun ledger(times: List<Long>): UsageLedger {
        val queue = ArrayDeque(times)
        var seq = 0
        return UsageLedger(
            now = { queue.removeFirstOrNull() ?: times.last() },
            idGen = { seq += 1; AuroraId.generate(0) },
        )
    }

    @Test
    fun recordStoresProviderModelTokensAndPricing() {
        val l = ledger(listOf(1000L))
        val entry = l.record(
            providerId = "fake-api",
            modelId = "fake-api-1",
            requestId = "r1",
            usage = UsageEstimate(inputTokens = 500, outputTokens = 500, estimated = true),
            pricingVersion = "per-1k-tokens:v3",
            costMicrosPer1kTokens = 300,
        )
        assertEquals("fake-api", entry.providerId)
        assertEquals("fake-api-1", entry.modelId)
        assertEquals(1000L, entry.timestamp)
        assertEquals(500, entry.inputTokens)
        assertEquals(500, entry.outputTokens)
        assertEquals("per-1k-tokens:v3", entry.pricingVersion)
        assertTrue(entry.estimated)
        // 1000 tokens * 300 micros / 1000 = 300 micros — an estimate, not a bill.
        assertEquals(300L, entry.estimatedCostMicros)
    }

    @Test
    fun missingPricingMetadataMakesNoCostClaim() {
        val l = ledger(listOf(1000L))
        val entry = l.record("p", "m", "r", UsageEstimate(10, 10, estimated = true), pricingVersion = null, costMicrosPer1kTokens = null)
        assertNull(entry.pricingVersion)
        assertNull(entry.estimatedCostMicros)
    }

    @Test
    fun totalsSumWindowAndMarkEstimates() {
        val l = ledger(listOf(1000L, 2000L, 3000L))
        l.record("p", "m1", "r1", UsageEstimate(100, 50, estimated = true), "v3", 1000)
        l.record("p", "m2", "r2", UsageEstimate(200, 150, estimated = true), "v3", 1000)
        val totals = l.totalsSince(since = 0)
        assertEquals(2, totals.requests)
        assertEquals(300L, totals.inputTokens)
        assertEquals(200L, totals.outputTokens)
        assertTrue(totals.allCostsAreEstimates)
        // (150 + 350) tokens * 1000 micros / 1000 = 500 micros
        assertEquals(500L, totals.estimatedCostMicros)
    }

    @Test
    fun measuredUsageIsNotSummedAsEstimate() {
        val l = ledger(listOf(1000L))
        l.record("p", "m", "r", UsageEstimate(10, 10, estimated = false), "v3", 1000)
        val totals = l.totalsSince(since = 0)
        assertFalse(totals.allCostsAreEstimates)
        assertEquals(0L, totals.estimatedCostMicros)
    }

    @Test
    fun recentEntriesIsBoundedAndNewestFirst() {
        val l = ledger(listOf(1000L, 2000L, 3000L))
        repeat(3) { i -> l.record("p", "m" + i, "r" + i, UsageEstimate(1, 1, estimated = true), null, null) }
        val recent = l.recentEntries(limit = 2)
        assertEquals(2, recent.size)
        assertEquals(3000L, recent.first().timestamp)
    }
}
