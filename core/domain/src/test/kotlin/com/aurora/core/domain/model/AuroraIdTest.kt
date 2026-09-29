package com.aurora.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraIdTest {

    @Test
    fun `ids generated at increasing times sort lexicographically`() {
        val a = AuroraId.generate(nowMillis = 1_000)
        val b = AuroraId.generate(nowMillis = 2_000)
        assertTrue(a.value < b.value)
    }

    @Test
    fun `ids generated at the same time are still unique`() {
        val ids = (0 until 1000).map { AuroraId.generate(nowMillis = 42_000L) }.toSet()
        assertEquals(1000, ids.size)
    }

    @Test
    fun `ids are 26 characters long`() {
        assertEquals(26, AuroraId.generate().value.length)
    }

    @Test
    fun `ids from different invocations differ`() {
        assertNotEquals(AuroraId.generate(), AuroraId.generate())
    }
}
