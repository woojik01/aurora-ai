package com.aurora.core.tool

import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.RiskLevel
import com.aurora.core.domain.model.SideEffectClass
import com.aurora.core.domain.model.ToolParamType
import com.aurora.core.domain.model.ToolRequest
import com.aurora.core.domain.model.ToolResult
import com.aurora.core.domain.model.ToolSchema
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake tool for PRD-01 acceptance: exercises the same Tool/registry interfaces
 * that real tools (files, GitHub, web search, ...) will use. Not a real capability.
 */
class FakeEchoTool {

    val executionCount = AtomicInteger(0)

    val tool: Tool = object : Tool {
        override val descriptor = ToolDescriptor(
            toolId = "fake.echo",
            description = "Echoes its input. Fake tool used for foundation testing.",
            parameters = listOf(ToolSchema("input", ToolParamType.STRING, required = true)),
            riskLevel = RiskLevel.LOW,
            requiredPermission = "none",
            networkRequired = false,
            dataSensitivity = DataSensitivity.PUBLIC,
            sideEffect = SideEffectClass.NONE,
            idempotent = true,
            approvalRequired = false,
        )

        override suspend fun execute(request: ToolRequest): ToolResult {
            val validation = request.validate(descriptor.parameters)
            if (validation.isNotEmpty()) {
                return ToolResult(success = false, output = "", error = validation.joinToString("; "))
            }
            executionCount.incrementAndGet()
            return ToolResult(success = true, output = "echo: ${request.arguments["input"]}", error = null)
        }
    }
}
