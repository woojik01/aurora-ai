package com.aurora.core.ai

import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.ToolParamType
import com.aurora.core.domain.model.ToolSchema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackRouterTest {

    private fun request(model: ModelDescriptor, toolSchemas: Map<String, List<ToolSchema>> = emptyMap()) =
        GenerationRequest(
            requestId = "r",
            messages = listOf(ChatMessage(MessageRole.USER, "hello")),
            model = model,
            toolSchemas = toolSchemas,
        )

    @Test
    fun apiSuccessUsesPrimaryWithoutFallback() {
        runBlocking {
            val router = FallbackRouter(FakeApiProvider(), FakeLocalProvider())
            val routed = router.generate(request(FakeApiProvider.MODEL))
            assertFalse(routed.fallbackUsed)
            assertEquals(FakeApiProvider.PROVIDER_ID, routed.providerId)
            assertEquals(FakeApiProvider.MODEL.modelId, routed.modelId)
            assertNull(routed.fallbackReason)
        }
    }

    @Test
    fun apiNetworkFailureFallsBackToLocalModel() {
        runBlocking {
            val api = FakeApiProvider()
            api.failNextRequestWith(FailureType.NETWORK)
            val router = FallbackRouter(api, FakeLocalProvider())
            val routed = router.generate(request(FakeApiProvider.MODEL))
            assertTrue(routed.fallbackUsed)
            assertEquals(FailureType.NETWORK, routed.fallbackReason)
            assertEquals(FakeLocalProvider.PROVIDER_ID, routed.providerId)
            assertEquals(FakeLocalProvider.MODEL.modelId, routed.modelId)
            assertTrue(routed.result.text.isNotEmpty())
        }
    }

    @Test
    fun quotaFailureAlsoFallsBack() {
        runBlocking {
            val api = FakeApiProvider()
            api.failNextRequestWith(FailureType.QUOTA)
            val router = FallbackRouter(api, FakeLocalProvider())
            assertEquals(FailureType.QUOTA, router.generate(request(FakeApiProvider.MODEL)).fallbackReason)
        }
    }

    @Test
    fun noCompatibleLocalModelSurfacesOriginalFailure() {
        runBlocking {
            val api = FakeApiProvider()
            api.failNextRequestWith(FailureType.AUTHENTICATION)
            val router = FallbackRouter(api, FakeLocalProvider())
            // Tool schemas request a tool-capable model; the fake local model
            // does not support tool calls → no compatible fallback exists.
            val tools = mapOf(
                "fake.echo" to listOf(ToolSchema(name = "text", type = ToolParamType.STRING)),
            )
            val error = runCatching { router.generate(request(FakeApiProvider.MODEL, tools)) }.exceptionOrNull()
            assertTrue(error is ProviderException)
            assertEquals(FailureType.AUTHENTICATION, (error as ProviderException).failureType)
        }
    }

    @Test
    fun compatibleLocalModelRequiresTextModalityAndAvailability() {
        runBlocking {
            val router = FallbackRouter(FakeApiProvider(), FakeLocalProvider())
            assertNotNull(router.findCompatibleLocalModel(request(FakeApiProvider.MODEL)))
        }
    }

    @Test
    fun localPrimaryNeverFallsBackFurther() {
        runBlocking {
            val router = FallbackRouter(FakeLocalProvider(), FakeLocalProvider())
            assertNull(router.findCompatibleLocalModel(request(FakeLocalProvider.MODEL)))
        }
    }
}
