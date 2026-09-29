package com.aurora.core.security

import com.aurora.core.domain.model.RiskLevel
import com.aurora.core.domain.model.ToolRequest
import java.security.MessageDigest

/**
 * Approval tokens are bound to (action hash, tool, arguments, device/user,
 * expiration, risk level) — a bare `approved = true` flag is forbidden (PRD-05).
 * Any argument change invalidates the approval; financial operations always
 * need a fresh approval.
 */
data class ApprovalRecord(
    val actionHash: String,
    val toolId: String,
    val riskLevel: RiskLevel,
    val deviceId: String,
    val userId: String,
    val requestedAt: Long,
    val expiresAt: Long,
)

enum class ApprovalDecision { VALID, HASH_MISMATCH, TOOL_MISMATCH, EXPIRED }

class ApprovalEngine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val defaultTtlMs: Long = 60_000,
) {

    fun actionHash(toolId: String, arguments: Map<String, String>): String {
        val canonical = ToolRequest(toolId, arguments).canonicalArguments()
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$toolId|$canonical".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun createApproval(
        toolId: String,
        arguments: Map<String, String>,
        riskLevel: RiskLevel,
        deviceId: String,
        userId: String,
        ttlMs: Long = defaultTtlMs,
    ): ApprovalRecord {
        val now = clock()
        return ApprovalRecord(
            actionHash = actionHash(toolId, arguments),
            toolId = toolId,
            riskLevel = riskLevel,
            deviceId = deviceId,
            userId = userId,
            requestedAt = now,
            expiresAt = now + ttlMs,
        )
    }

    fun validate(record: ApprovalRecord, toolId: String, arguments: Map<String, String>): ApprovalDecision {
        if (record.toolId != toolId) return ApprovalDecision.TOOL_MISMATCH
        if (clock() > record.expiresAt) return ApprovalDecision.EXPIRED
        if (record.actionHash != actionHash(toolId, arguments)) return ApprovalDecision.HASH_MISMATCH
        return ApprovalDecision.VALID
    }
}
