package com.aurora.core.automation

/**
 * Durable job contracts for PRD-06. No scheduler implementation is included in
 * PRD-01 — WorkManager wiring arrives with PRD-06. These types exist now so the
 * automation module boundary is stable from the start.
 */
enum class TriggerType { ONE_TIME, RECURRING_SCHEDULE, DEVICE_EVENT, MANUAL, CONDITION_CHECK }

/**
 * A job must declare whether it is idempotent; non-idempotent jobs will never
 * be safely retried by the future scheduler without approval semantics.
 */
data class DurableJobSpec(
    val jobId: String,
    val trigger: TriggerType,
    val requiresApproval: Boolean,
    val isIdempotent: Boolean,
    /** Opaque checkpoint payload so a restarted process resumes instead of duplicating side effects. */
    val checkpoint: String? = null,
) {
    init {
        require(jobId.isNotBlank()) { "jobId must not be blank" }
    }
}

interface DurableJobScheduler {
    suspend fun schedule(spec: DurableJobSpec): Boolean
    suspend fun cancel(jobId: String): Boolean
}
