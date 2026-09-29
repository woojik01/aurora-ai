package com.aurora.core.security

import com.aurora.core.domain.model.ApprovalPolicy
import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.DenialReason
import com.aurora.core.domain.model.NetworkPolicy
import com.aurora.core.domain.model.PolicyContext
import com.aurora.core.domain.model.PolicyDecision
import com.aurora.core.domain.model.RiskLevel
import com.aurora.core.domain.model.SideEffectClass
import com.aurora.core.domain.model.ToolParamType
import com.aurora.core.domain.model.ToolRequest
import com.aurora.core.domain.model.ToolSchema
import com.aurora.core.tool.Tool
import com.aurora.core.tool.ToolDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {

    private val engine = DefaultPolicyEngine()

    private fun tool(
        id: String = "test.tool",
        risk: RiskLevel = RiskLevel.LOW,
        approval: Boolean = false,
        network: Boolean = false,
        sideEffect: SideEffectClass = SideEffectClass.NONE,
    ): Tool = object : Tool {
        override val descriptor = ToolDescriptor(
            toolId = id,
            description = "test",
            parameters = listOf(ToolSchema("path", ToolParamType.STRING)),
            riskLevel = risk,
            requiredPermission = "none",
            networkRequired = network,
            dataSensitivity = DataSensitivity.PUBLIC,
            sideEffect = sideEffect,
            idempotent = true,
            approvalRequired = approval,
        )

        override suspend fun execute(request: ToolRequest) =
            com.aurora.core.domain.model.ToolResult(true, "ok")
    }

    private fun context(
        allowed: Set<String> = setOf("test.tool"),
        enabled: Boolean = true,
        network: NetworkPolicy = NetworkPolicy.LOCAL_ONLY,
        approval: ApprovalPolicy = ApprovalPolicy.PER_TOOL_DEFAULT,
    ) = PolicyContext(
        agentId = "agent-1",
        enabled = enabled,
        allowedTools = allowed,
        approvalPolicy = approval,
        networkPolicy = network,
    )

    @Test
    fun `unknown tool is denied (default deny)`() = runTest {
        val decision = engine.evaluate(ToolRequest("nope"), null, context())
        assertTrue(decision is PolicyDecision.Denied)
        assertEquals(DenialReason.UNKNOWN_TOOL, (decision as PolicyDecision.Denied).reason)
    }

    @Test
    fun `tool outside the agent allowlist is denied`() = runTest {
        val decision = engine.evaluate(
            ToolRequest("test.tool"),
            tool(),
            context(allowed = setOf("other.tool")),
        )
        assertEquals(DenialReason.NOT_ALLOWED_FOR_AGENT, (decision as PolicyDecision.Denied).reason)
    }

    @Test
    fun `disabled agent cannot invoke any tool`() = runTest {
        val decision = engine.evaluate(ToolRequest("test.tool"), tool(), context(enabled = false))
        assertEquals(DenialReason.AGENT_DISABLED, (decision as PolicyDecision.Denied).reason)
    }

    @Test
    fun `network tool is denied for local-only agent`() = runTest {
        val decision = engine.evaluate(
            ToolRequest("test.tool"),
            tool(network = true),
            context(network = NetworkPolicy.LOCAL_ONLY),
        )
        assertEquals(DenialReason.NETWORK_FORBIDDEN, (decision as PolicyDecision.Denied).reason)
    }

    @Test
    fun `high risk tool always requires approval`() = runTest {
        val decision = engine.evaluate(ToolRequest("test.tool"), tool(risk = RiskLevel.HIGH), context())
        assertTrue(decision is PolicyDecision.RequiresApproval)
    }

    @Test
    fun `financial tool requires approval even with per-tool-default policy`() = runTest {
        val decision = engine.evaluate(
            ToolRequest("test.tool"),
            tool(risk = RiskLevel.FINANCIAL, sideEffect = SideEffectClass.FINANCIAL),
            context(approval = ApprovalPolicy.PER_TOOL_DEFAULT),
        )
        assertTrue(decision is PolicyDecision.RequiresApproval)
        assertTrue((decision as PolicyDecision.RequiresApproval).detail.contains("Financial"))
    }

    @Test
    fun `low risk allowlisted tool is allowed`() = runTest {
        val decision = engine.evaluate(ToolRequest("test.tool"), tool(), context())
        assertEquals(PolicyDecision.Allowed, decision)
    }
}
