package com.aurora.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLedgerTest {

    private fun ledger(vararg clock: Long): Pair<UsageLedger, MutableList<Long>> {
        val times = clock.toMutableList() if (clock.isNotEmpty()) else mutableListOf(100L)
        var i = 0
        var seq = 0
        val l = UsageLedger(
            now = { if (i < times.size) times[i] else times.last(); i += 1; times[minOf(i - 1, times.size - 1)] },
            idGen = { seq += 1; com.aurora.core.domain.model.AuroraId.generate(0) },
        )
        return l to times
    }

    @Test
    fun recordStoresProviderModelTokensAndPricing() {
        val (l, _) = ledger(1000)
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
        assertEquals(500, entry.inputTokens)
        assertEquals(500, entry.outputTokens)
        assertEquals("per-1k-tokens:v3", entry.pricingVersion)
        assertTrue(entry.estimated)
        // 1000 tokens * 300 micros / 1000 = 300 micros — an estimate, not a bill.
        assertEquals(300L, entry.estimatedCostMicros)
    }

    @Test
    fun missingPricingMetadataMakesNoCostClaim() {
        val (l, _) = ledger(1000)
        val entry = l.record("p", "m", "r", UsageEstimate(10, 10, estimated = true), pricingVersion = null, costMicrosPer1kTokens = null)
        assertNull(entry.pricingVersion)
        assertNull(entry.estimatedCostMicros)
    }

    @Test
    fun totalsSumWindowAndMarkEstimates() {
        val (l, _) = ledger(1000, 2000, 3000)
        l.record("p", "m1", "r1", UsageEstimate(100, 50, estimated = true), "v3", 1000)
        l.record("p", "m2", "r2", UsageEstimate(200, 150, estimated = true), "v3", 1000)
        val totals = l.totalsSince(since = 0)
        assertEquals(2, totals.requests)
        assertEquals(300L, totals.inputTokens)
        assertEquals(200L, totals.outputTokens)
        assertTrue(totals.allCostsAreEstimates)
        assertEquals((150 * 1000 + 350 * 1000) / 1000, totals.estimatedCostMicros)
    }

    @Test
    fun measuredUsageIsNotSummedAsEstimate() {
        val (l, _) = ledger(1000)
        l.record("p", "m", "r", UsageEstimate(10, 10, estimated = false), "v3", 1000)
        val totals = l.totalsSince(since = 0)
        assertFalse(totals.allCostsAreEstimates)
        assertEquals(0L, totals.estimatedCostMicros)
    }

    @Test
    fun recentEntriesIsBoundedAndNewestFirst() {
        val (l, _) = ledger(1000, 2000, 3000)
        repeat(3) { i -> l.record("p", "m" + i, "r" + i, UsageEstimate(1, 1, estimated = true), null, null) }
        val recent = l.recentEntries(limit = 2)
        assertEquals(2, recent.size)
        assertEquals(3000L, recent.first().timestamp)
    }
}
