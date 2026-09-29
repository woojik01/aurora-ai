package com.aurora.core.tool

import com.aurora.core.domain.model.DataSensitivity
import com.aurora.core.domain.model.RiskLevel
import com.aurora.core.domain.model.SideEffectClass
import com.aurora.core.domain.model.ToolRequest
import com.aurora.core.domain.model.ToolResult
import com.aurora.core.domain.model.ToolSchema

/**
 * Metadata every tool must declare (PRD-04 tool safety). The model sees
 * descriptions and schemas — never raw platform privileges or credentials.
 */
data class ToolDescriptor(
    val toolId: String,
    val description: String,
    val parameters: List<ToolSchema>,
    val riskLevel: RiskLevel,
    /** Platform permission concept; checked by the policy layer, not by the model. */
    val requiredPermission: String,
    val networkRequired: Boolean,
    val dataSensitivity: DataSensitivity,
    val sideEffect: SideEffectClass,
    val idempotent: Boolean,
    val approvalRequired: Boolean,
)

/**
 * Typed tool contract. Implementations are replaceable and must validate
 * arguments against their own schema before doing any work.
 */
interface Tool {
    val descriptor: ToolDescriptor

    suspend fun execute(request: ToolRequest): ToolResult
}
