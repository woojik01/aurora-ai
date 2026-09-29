package com.aurora.core.agent

import com.aurora.core.ai.AIProvider
import com.aurora.core.ai.ChatMessage
import com.aurora.core.ai.GenerationRequest
import com.aurora.core.domain.model.AgentDefinition
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.ExecutionState
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.PolicyContext
import com.aurora.core.domain.model.PolicyDecision
import com.aurora.core.domain.model.RunEvent
import com.aurora.core.domain.model.RunOutcome
import com.aurora.core.domain.model.ToolRequest
import com.aurora.core.security.PolicyEngine
import com.aurora.core.tool.Tool
import com.aurora.core.tool.ToolRegistry
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Agent execution state machine (PRD-01/PRD-04):
 *
 * QUEUED -> PLANNING -> (AWAITING_APPROVAL -> TOOL_CALL -> VALIDATING -> RUNNING)* -> terminal.
 *
 * Security properties enforced by THIS runtime, not by prompts:
 * - Only structured provider tool calls are considered; model text that
 *   merely looks like a tool call is never executed (PRD-01 AC 5).
 * - Every tool call passes the [PolicyEngine] (default deny).
 * - Tool calls exceeding [AgentDefinition.maxToolCalls] terminate the run.
 * - Recursion deeper than [AgentDefinition.maxExecutionDepth] is rejected.
 * - Wall-clock time beyond [AgentDefinition.maxRuntimeMs] times the run out.
 */
class AgentRuntime(
    private val provider: AIProvider,
    private val toolRegistry: ToolRegistry,
    private val policyEngine: PolicyEngine,
    private val approvalGate: ApprovalGate,
    private val eventSink: suspend (RunEvent) -> Unit = {},
) {

    suspend fun run(
        definition: AgentDefinition,
        request: GenerationRequest,
        depth: Int = 0,
    ): RunOutcome {
        val runId = AuroraId.generate().value
        val now: () -> Long = { System.currentTimeMillis() }
        suspend fun emit(state: ExecutionState, detail: String? = null) {
            eventSink(RunEvent(runId, definition.id, state, now(), detail))
        }

        // Runtime-enforced recursion limit.
        if (depth > definition.maxExecutionDepth) {
            emit(ExecutionState.FAILED, "Agent recursion depth exceeded: $depth > ${definition.maxExecutionDepth}")
            return RunOutcome.Failure(
                ExecutionState.FAILED,
                "Agent recursion depth exceeded: $depth > ${definition.maxExecutionDepth}",
            )
        }
        if (!definition.enabled) {
            emit(ExecutionState.DENIED, "Agent is disabled")
            return RunOutcome.Failure(ExecutionState.DENIED, "Agent '${definition.id}' is disabled")
        }

        emit(ExecutionState.QUEUED)

        val context = PolicyContext(
            agentId = definition.id,
            enabled = definition.enabled,
            allowedTools = definition.allowedTools,
            approvalPolicy = definition.approvalPolicy,
            networkPolicy = definition.networkPolicy,
        )

        var toolCallsUsed = 0
        val messages = request.messages.toMutableList()

        val outcome: RunOutcome? = withTimeoutOrNull(definition.maxRuntimeMs) {
            emit(ExecutionState.PLANNING)
            while (true) {
                val result = provider.generate(request.copy(messages = messages.toList()))

                if (result.toolCalls.isEmpty()) {
                    if (result.text.toByteArray(Charsets.UTF_8).size > definition.maxOutputBytes) {
                        emit(ExecutionState.FAILED, "Output exceeded maxOutputBytes")
                        return@withTimeoutOrNull RunOutcome.Failure(
                            ExecutionState.FAILED,
                            "Output exceeded maxOutputBytes",
                        )
                    }
                    return@withTimeoutOrNull RunOutcome.Success(result.text, toolCallsUsed)
                }

                for (toolCall in result.toolCalls) {
                    if (toolCallsUsed >= definition.maxToolCalls) {
                        emit(
                            ExecutionState.FAILED,
                            "Tool call limit reached: $toolCallsUsed >= ${definition.maxToolCalls}",
                        )
                        return@withTimeoutOrNull RunOutcome.Failure(
                            ExecutionState.FAILED,
                            "Tool call limit reached: $toolCallsUsed >= ${definition.maxToolCalls}",
                        )
                    }
                    toolCallsUsed++

                    val tool: Tool? = toolRegistry.find(toolCall.toolId)
                    val decision = policyEngine.evaluate(toolCall, tool, context)

                    when (decision) {
                        is PolicyDecision.Denied -> {
                            // Tool is NOT executed. The denial is fed back as tool output.
                            emit(ExecutionState.DENIED, "${decision.reason}: ${decision.detail}")
                            messages.add(
                                ChatMessage(
                                    MessageRole.TOOL,
                                    "tool-call denied (${decision.reason}): ${decision.detail}",
                                )
                            )
                        }
                        is PolicyDecision.RequiresApproval -> {
                            emit(ExecutionState.AWAITING_APPROVAL, decision.detail)
                            if (tool == null || !approvalGate.awaitApproval(toolCall, toolCall.toolId)) {
                                emit(ExecutionState.DENIED, "User did not approve: ${toolCall.toolId}")
                                messages.add(
                                    ChatMessage(MessageRole.TOOL, "tool-call not approved by user: ${toolCall.toolId}")
                                )
                            } else {
                                messages.addAll(executeTool(toolCall, tool, ::emit))
                            }
                        }
                        PolicyDecision.Allowed -> {
                            if (tool == null) {
                                emit(ExecutionState.DENIED, "Allowed tool missing at execution time")
                                messages.add(ChatMessage(MessageRole.TOOL, "tool-call denied: tool missing"))
                            } else {
                                messages.addAll(executeTool(toolCall, tool, ::emit))
                            }
                        }
                    }
                }
                emit(ExecutionState.RUNNING)
                // Loop back to the provider with the tool results appended.
            }
            @Suppress("UNREACHABLE_CODE")
            RunOutcome.Failure(ExecutionState.FAILED, "unreachable")
        }

        return when (outcome) {
            null -> {
                emit(ExecutionState.TIMED_OUT, "Exceeded maxRuntimeMs=${definition.maxRuntimeMs}")
                RunOutcome.Failure(
                    ExecutionState.TIMED_OUT,
                    "Agent run exceeded maxRuntimeMs=${definition.maxRuntimeMs}",
                )
            }
            else -> {
                if (outcome is RunOutcome.Success) {
                    emit(ExecutionState.SUCCEEDED)
                } else {
                    emit((outcome as RunOutcome.Failure).state)
                }
                outcome
            }
        }
    }

    private suspend fun executeTool(
        toolCall: ToolRequest,
        tool: Tool,
        emit: suspend (ExecutionState, String?) -> Unit,
    ): List<ChatMessage> {
        emit(ExecutionState.TOOL_CALL, toolCall.toolId)
        val validation = toolCall.validate(tool.descriptor.parameters)
        if (validation.isNotEmpty()) {
            emit(ExecutionState.DENIED, "Invalid arguments: ${validation.joinToString("; ")}")
            return listOf(
                ChatMessage(
                    MessageRole.TOOL,
                    "tool-call rejected, invalid arguments: ${validation.joinToString("; ")}",
                )
            )
        }
        val toolResult = try {
            tool.execute(toolCall)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tools must sanitize their own messages; no secrets reach here.
            com.aurora.core.domain.model.ToolResult(
                success = false,
                output = "",
                error = e.message ?: "tool failure",
            )
        }
        emit(ExecutionState.VALIDATING, toolCall.toolId)
        return listOf(
            ChatMessage(
                MessageRole.TOOL,
                if (toolResult.success) "tool ok: ${toolResult.output}" else "tool error: ${toolResult.error}",
            )
        )
    }
}
