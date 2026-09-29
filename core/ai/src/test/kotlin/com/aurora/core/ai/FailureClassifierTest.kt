package com.aurora.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class FailureClassifierTest {

    @Test
    fun `timeouts are classified`() {
        assertEquals(FailureType.TIMEOUT, FailureClassifier.classify(SocketTimeoutException()))
        assertEquals(FailureType.TIMEOUT, FailureClassifier.classify(java.util.concurrent.TimeoutException()))
    }

    @Test
    fun `io errors are network failures`() {
        assertEquals(FailureType.NETWORK, FailureClassifier.classify(IOException("connection reset")))
    }

    @Test
    fun `provider exceptions keep their classification`() {
        assertEquals(
            FailureType.QUOTA,
            FailureClassifier.classify(ProviderException(FailureType.QUOTA, "quota exceeded")),
        )
    }

    @Test
    fun `cancellation is recognized`() {
        assertEquals(
            FailureType.CANCELLED,
            FailureClassifier.classify(kotlinx.coroutines.CancellationException("cancelled")),
        )
    }

    @Test
    fun `unknown errors fall through safely`() {
        assertEquals(FailureType.UNKNOWN, FailureClassifier.classify(IllegalStateException("boom")))
        assertEquals(FailureType.UNKNOWN, FailureClassifier.classify(null))
    }
}
