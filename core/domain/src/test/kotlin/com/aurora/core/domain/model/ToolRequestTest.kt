package com.aurora.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRequestTest {

    private val schema = listOf(
        ToolSchema("path", ToolParamType.STRING, required = true),
        ToolSchema("dryRun", ToolParamType.BOOLEAN, required = false),
    )

    @Test
    fun `missing required argument is rejected`() {
        val errors = ToolRequest("fake.echo", mapOf("dryRun" to "true")).validate(schema)
        assertTrue(errors.any { it.contains("Missing required parameter 'path'") })
    }

    @Test
    fun `unknown argument is rejected`() {
        val errors = ToolRequest("fake.echo", mapOf("path" to "/tmp/a", "evil" to "1")).validate(schema)
        assertTrue(errors.any { it.contains("Unknown parameter 'evil'") })
    }

    @Test
    fun `valid request passes`() {
        assertTrue(ToolRequest("fake.echo", mapOf("path" to "/tmp/a")).validate(schema).isEmpty())
    }

    @Test
    fun `canonical arguments are deterministic and unambiguous`() {
        val a = ToolRequest("t", mapOf("b" to "2", "a" to "1&c=3"))
        val b = ToolRequest("t", mapOf("a" to "1&c=3", "b" to "2"))
        val c = ToolRequest("t", mapOf("ab" to "1", "c" to "3"))
        assertEquals(a.canonicalArguments(), b.canonicalArguments())
        assertTrue(a.canonicalArguments() != c.canonicalArguments())
    }
}
