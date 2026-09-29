package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLocalProviderTest {

    private val provider = FakeLocalProvider()

    private fun request(text: String, id: String = "req-1") = GenerationRequest(
        requestId = id,
        messages = listOf(
            ChatMessage(MessageRole.SYSTEM, "You are Aurora."),
            ChatMessage(MessageRole.USER, text),
        ),
        model = FakeLocalProvider.MODEL,
    )

    @Test
    fun `generate echoes the last user message`() = runTest {
        val result = provider.generate(request("hello world"))
        assertEquals("Aurora local echo: hello world", result.text)
        assertEquals(FinishReason.STOP, result.finishReason)
        assertTrue(result.toolCalls.isEmpty())
        assertTrue(result.usage.estimated)
    }

    @Test
    fun `stream emits token deltas usage and completion`() = runTest {
        val events = mutableListOf<StreamEvent>()
        provider.stream(request("stream me", "req-2")).collect { events.add(it) }
        assertTrue(events.any { it is StreamEvent.TokenDelta })
        assertTrue(events.any { it is StreamEvent.Usage })
        assertEquals(StreamEvent.Completed, events.last())
        // Concatenated deltas reconstruct the same text as generate().
        val streamedText = events.filterIsInstance<StreamEvent.TokenDelta>().joinToString("") { it.text }
        assertEquals(provider.generate(request("stream me", "req-2b")).text, streamedText)
    }

    @Test
    fun `cancel prevents generation`() = runTest {
        provider.cancel("req-3")
        val error = runCatching { provider.generate(request("x", "req-3")) }.exceptionOrNull()
        assertEquals(FailureType.CANCELLED, FailureClassifier.classify(error))
    }

    @Test
    fun `model descriptor marks itself local and available`() {
        val model = provider.listModels().single()
        assertTrue(model.local)
        assertTrue(model.available)
        assertEquals(FakeLocalProvider.PROVIDER_ID, model.providerId)
    }
}
