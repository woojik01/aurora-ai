package com.aurora.core.security

import com.aurora.core.domain.model.ApprovalPolicy
import com.aurora.core.domain.model.DenialReason
import com.aurora.core.domain.model.NetworkPolicy
import com.aurora.core.domain.model.PolicyContext
import com.aurora.core.domain.model.PolicyDecision
import com.aurora.core.domain.model.RiskLevel
import com.aurora.core.domain.model.SideEffectClass
import com.aurora.core.domain.model.ToolRequest
import com.aurora.core.tool.Tool

/**
 * The single authorization point between a model-emitted tool request and
 * execution (PRD-01 AC 5, PRD-05). Default deny; fail closed; no decision
 * depends on model output.
 *
 * This engine decides *whether* something may run. *Whether a human has
 * approved it* is a separate concern handled by [ApprovalEngine] — approval
 * never substitutes authorization, and authorization never substitutes approval.
 */
interface PolicyEngine {
    suspend fun evaluate(request: ToolRequest, tool: Tool?, context: PolicyContext): PolicyDecision
}

class DefaultPolicyEngine : PolicyEngine {

    override suspend fun evaluate(
        request: ToolRequest,
        tool: Tool?,
        context: PolicyContext,
    ): PolicyDecision {
        // Fail closed: unknown tool -> deny.
        if (tool == null) {
            return PolicyDecision.Denied(DenialReason.UNKNOWN_TOOL, "Tool not registered: ${request.toolId}")
        }
        if (!context.enabled) {
            return PolicyDecision.Denied(DenialReason.AGENT_DISABLED, "Calling agent is disabled")
        }
        val descriptor = tool.descriptor
        if (descriptor.toolId !in context.allowedTools) {
            return PolicyDecision.Denied(
                DenialReason.NOT_ALLOWED_FOR_AGENT,
                "Tool '${descriptor.toolId}' is not in the calling agent's allowed tool set",
            )
        }
        if (descriptor.networkRequired && context.networkPolicy == NetworkPolicy.LOCAL_ONLY) {
            return PolicyDecision.Denied(
                DenialReason.NETWORK_FORBIDDEN,
                "Tool '${descriptor.toolId}' requires network but the agent is local-only",
            )
        }

        // Financial operations always require fresh approval; no policy setting can bypass this.
        if (descriptor.riskLevel == RiskLevel.FINANCIAL || descriptor.sideEffect == SideEffectClass.FINANCIAL) {
            return PolicyDecision.RequiresApproval(
                "Financial operations always require explicit user approval"
            )
        }

        if (descriptor.approvalRequired || descriptor.riskLevel == RiskLevel.HIGH) {
            return PolicyDecision.RequiresApproval("Tool '${descriptor.toolId}' requires user approval")
        }

        if (context.approvalPolicy == ApprovalPolicy.ALWAYS_REQUIRE) {
            return PolicyDecision.RequiresApproval("Agent approval policy requires approval for every tool")
        }

        return PolicyDecision.Allowed
    }
}
