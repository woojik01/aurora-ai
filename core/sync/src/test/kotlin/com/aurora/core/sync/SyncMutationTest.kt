package com.aurora.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMutationTest {

    @Test
    fun `delete mutations must be tombstones`() {
        val error = runCatching {
            SyncMutation("memory", "m1", "device-1", 1, 1_000L, SyncOperation.DELETE, tombstone = false)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `non-delete mutations must not be tombstones`() {
        val error = runCatching {
            SyncMutation("memory", "m1", "device-1", 1, 1_000L, SyncOperation.CREATE, tombstone = true)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `revision must be positive`() {
        val error = runCatching {
            SyncMutation("memory", "m1", "device-1", 0, 1_000L, SyncOperation.CREATE, tombstone = false)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `valid mutation round-trips its fields`() {
        val m = SyncMutation("memory", "m1", "device-1", 7, 1_000L, SyncOperation.UPDATE, tombstone = false, payload = "{}")
        assertEquals(7, m.revision)
        assertEquals(SyncOperation.UPDATE, m.operation)
    }
}
