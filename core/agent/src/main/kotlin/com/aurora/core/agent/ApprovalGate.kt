package com.aurora.core.agent

import com.aurora.core.domain.model.ToolRequest

/**
 * Bridge to the human approval UI. The runtime suspends until the gate
 * answers; it never executes the tool without an explicit true.
 */
fun interface ApprovalGate {
    suspend fun awaitApproval(request: ToolRequest, toolId: String): Boolean
}
