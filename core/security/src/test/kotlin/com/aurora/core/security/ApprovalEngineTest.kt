package com.aurora.core.security

import com.aurora.core.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApprovalEngineTest {

    @Test
    fun `approval is bound to exact arguments`() {
        val engine = ApprovalEngine(clock = { 1_000L })
        val record = engine.createApproval("files.delete", mapOf("path" to "/data/a.txt"), RiskLevel.HIGH, "d1", "u1")
        assertEquals(
            ApprovalDecision.VALID,
            engine.validate(record, "files.delete", mapOf("path" to "/data/a.txt")),
        )
    }

    @Test
    fun `modified arguments invalidate approval`() {
        val engine = ApprovalEngine(clock = { 1_000L })
        val record = engine.createApproval("files.delete", mapOf("path" to "/data/a.txt"), RiskLevel.HIGH, "d1", "u1")
        assertEquals(
            ApprovalDecision.HASH_MISMATCH,
            engine.validate(record, "files.delete", mapOf("path" to "/data/other.txt")),
        )
    }

    @Test
    fun `expired approval is rejected`() {
        var now = 1_000L
        val timed = ApprovalEngine(clock = { now }, defaultTtlMs = 100)
        val record = timed.createApproval("files.delete", mapOf("path" to "/a"), RiskLevel.HIGH, "d1", "u1")
        assertEquals(
            ApprovalDecision.VALID,
            timed.validate(record, "files.delete", mapOf("path" to "/a")),
        )
        now = 1_200L
        assertEquals(
            ApprovalDecision.EXPIRED,
            timed.validate(record, "files.delete", mapOf("path" to "/a")),
        )
    }

    @Test
    fun `approval cannot be reused for a different tool`() {
        val engine = ApprovalEngine(clock = { 1_000L })
        val record = engine.createApproval("files.delete", mapOf("path" to "/a"), RiskLevel.HIGH, "d1", "u1")
        assertEquals(
            ApprovalDecision.TOOL_MISMATCH,
            engine.validate(record, "files.move", mapOf("path" to "/a")),
        )
    }

    @Test
    fun `action hash is deterministic and argument-sensitive`() {
        val engine = ApprovalEngine()
        assertEquals(
            engine.actionHash("t", mapOf("a" to "1")),
            engine.actionHash("t", mapOf("a" to "1")),
        )
        assertNotEquals(
            engine.actionHash("t", mapOf("a" to "1")),
            engine.actionHash("t", mapOf("a" to "2")),
        )
    }
}
