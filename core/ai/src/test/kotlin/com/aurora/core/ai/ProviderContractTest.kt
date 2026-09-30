package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single provider test suite (PRD-03 acceptance): a fake local provider
 * and a fake API provider must both pass these contract tests, proving the
 * contract is provider-agnostic and replacement needs no UI/domain rewrite.
 */
abstract class ProviderContractTest {

    protected abstract fun provider(): AIProvider

    protected open fun expectedProviderId(): String? = null

    @Test
    fun listModelsIsNotEmptyAndDescribed() {
        runBlocking {
            val models = provider().listModels()
            assertTrue(models.isNotEmpty())
            for (m in models) {
                assertEquals(provider().providerId, m.providerId)
                assertTrue(m.modalities.isNotEmpty())
            }
            expectedProviderId()?.let { assertEquals(it, provider().providerId) }
        }
    }

    @Test
    fun healthCheckIsOkOnACleanProvider() {
        runBlocking {
            assertEquals(Health.OK, provider().healthCheck())
        }
    }

    @Test
    fun generateAnswersLastUserMessageWithUsage() {
        runBlocking {
            val p = provider()
            val model = p.listModels().first()
            val result = p.generate(
                GenerationRequest(
                    requestId = "req-1",
                    messages = listOf(
                        ChatMessage(MessageRole.SYSTEM, "sys"),
                        ChatMessage(MessageRole.USER, "hello"),
                    ),
                    model = model,
                ),
            )
            assertTrue(result.text.isNotEmpty())
            assertEquals(FinishReason.STOP, result.finishReason)
            assertNotNull(result.usage)
        }
    }

    @Test
    fun streamEndsWithCompletedAndNeverRawAppends() {
        runBlocking {
            val p = provider()
            val model = p.listModels().first()
            val events = p.stream(
                GenerationRequest(
                    requestId = "req-2",
                    messages = listOf(ChatMessage(MessageRole.USER, "hi")),
                    model = model,
                ),
            ).toList()
            assertTrue(events.isNotEmpty())
            assertEquals(StreamEvent.Completed, events.last())
            assertTrue(events.any { it is StreamEvent.TokenDelta })
        }
    }

    @Test
    fun cancelThenGenerateThrowsClassifiedCancellation() {
        runBlocking {
            val p = provider()
            val model = p.listModels().first()
            p.cancel("req-3")
            val error = runCatching {
                p.generate(
                    GenerationRequest(
                        requestId = "req-3",
                        messages = listOf(ChatMessage(MessageRole.USER, "x")),
                        model = model,
                    ),
                )
            }.exceptionOrNull()
            assertTrue(error is ProviderException)
            assertEquals(FailureType.CANCELLED, (error as ProviderException).failureType)
        }
    }

    @Test
    fun capabilitiesMatchDescriptorFlags() {
        runBlocking {
            val p = provider()
            val caps = p.capabilities()
            val model = p.listModels().first()
            assertEquals(model.supportsStreaming, caps.supportsStreaming)
            assertEquals(model.local, caps.local)
        }
    }

    @Test
    fun estimateUsageIsAlwaysMarkedEstimatedOrMeasured() {
        runBlocking {
            val p = provider()
            val model = p.listModels().first()
            val estimate = p.estimateUsage(
                GenerationRequest(
                    requestId = "req-4",
                    messages = listOf(ChatMessage(MessageRole.USER, "x")),
                    model = model,
                ),
            )
            // fake providers never expose measured billing: estimated is true
            assertTrue(estimate.estimated)
        }
    }
}
