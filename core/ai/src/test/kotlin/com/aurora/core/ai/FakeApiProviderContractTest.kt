package com.aurora.core.ai

class FakeApiProviderContractTest : ProviderContractTest() {
    override fun provider(): AIProvider = FakeApiProvider()
    override fun expectedProviderId(): String = FakeApiProvider.PROVIDER_ID
}
