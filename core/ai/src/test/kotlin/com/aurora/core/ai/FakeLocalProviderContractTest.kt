package com.aurora.core.ai

class FakeLocalProviderContractTest : ProviderContractTest() {
    override fun provider(): AIProvider = FakeLocalProvider()
    override fun expectedProviderId(): String = FakeLocalProvider.PROVIDER_ID
}
