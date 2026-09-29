package com.aurora.core.domain.model

/**
 * A user-created agent. Limits are enforced by the runtime, never by prompting (PRD-04).
 */
data class AgentDefinition(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val providerId: String,
    val modelId: String,
    val allowedTools: Set<String>,
    val maxToolCalls: Int,
    val maxExecutionDepth: Int,
    val maxRuntimeMs: Long,
    val maxConcurrentChildAgents: Int = 1,
    val maxOutputBytes: Long = 1_000_000,
    val networkPolicy: NetworkPolicy = NetworkPolicy.LOCAL_ONLY,
    val fileScope: FileScope = FileScope.NONE,
    val approvalPolicy: ApprovalPolicy = ApprovalPolicy.PER_TOOL_DEFAULT,
    val enabled: Boolean = true,
)

data class RunEvent(
    val runId: String,
    val agentId: String?,
    val state: ExecutionState,
    val timestamp: Long,
    val detail: String? = null,
)

sealed interface RunOutcome {
    data class Success(val text: String, val toolCallsUsed: Int) : RunOutcome
    data class Failure(val state: ExecutionState, val reason: String) : RunOutcome
}
