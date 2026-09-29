package com.aurora.core.domain.model

/**
 * Decisions are produced by application policy code only — never by the model.
 */
sealed interface PolicyDecision {
    data object Allowed : PolicyDecision

    data class Denied(val reason: DenialReason, val detail: String) : PolicyDecision

    data class RequiresApproval(val detail: String) : PolicyDecision
}

enum class DenialReason {
    UNKNOWN_TOOL,
    NOT_ALLOWED_FOR_AGENT,
    AGENT_DISABLED,
    NETWORK_FORBIDDEN,
    POLICY_VIOLATION,
}

data class PolicyContext(
    val agentId: String?,
    val enabled: Boolean,
    val allowedTools: Set<String>,
    val approvalPolicy: ApprovalPolicy,
    val networkPolicy: NetworkPolicy,
)
