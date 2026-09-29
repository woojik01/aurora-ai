package com.aurora.core.tool

import com.aurora.core.domain.model.ToolRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun `duplicate registration is rejected`() {
        val registry = ToolRegistry()
        val fake = FakeEchoTool()
        registry.register(fake.tool)
        val second = assertThrows<IllegalArgumentException> { registry.register(fake.tool) }
        assertTrue(second.message!!.contains("already registered"))
    }

    @Test
    fun `unknown tool is not found`() {
        assertNull(ToolRegistry().find("does.not.exist"))
    }

    @Test
    fun `echo tool validates arguments before executing`() = runTest {
        val fake = FakeEchoTool()
        val bad = fake.tool.execute(ToolRequest("fake.echo", emptyMap()))
        assertEquals(false, bad.success)
        assertTrue(bad.error!!.contains("Missing required parameter"))
        assertEquals(0, fake.executionCount.get())
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            assertEquals(T::class, e::class)
            @Suppress("UNCHECKED_CAST")
            return e as T
        }
        throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
    }
}
