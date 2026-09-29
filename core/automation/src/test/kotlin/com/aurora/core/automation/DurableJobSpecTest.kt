package com.aurora.core.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableJobSpecTest {

    @Test
    fun `specs must carry an id`() {
        val error = runCatching { DurableJobSpec("", TriggerType.MANUAL, false, true) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `valid spec is accepted`() {
        val spec = DurableJobSpec(
            "daily-news",
            TriggerType.RECURRING_SCHEDULE,
            requiresApproval = false,
            isIdempotent = true,
        )
        assertEquals("daily-news", spec.jobId)
        assertEquals(TriggerType.RECURRING_SCHEDULE, spec.trigger)
    }
}
