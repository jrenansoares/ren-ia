package br.com.projetoia.app.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.*
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `first launch has no embedded credentials or paid default route`() {
        assertTrue(DEFAULT_PROVIDERS.all { it is ProviderSetting.ApiKey && it.apiKey.isBlank() })
        val router = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>()
            .single { it.baseUrl == "https://openrouter.ai/api/v1" }
        assertEquals("openrouter/free", router.models.single().modelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, router.models.single().id)
        assertFalse(DEFAULT_PROVIDERS.any { it.name == "Vercel AI Gateway" })
    }

    @Test
    fun `default providers are treated as not configured until credentials exist`() {
        val unconfigured = Settings(providers = DEFAULT_PROVIDERS)
        assertTrue(unconfigured.isNotConfigured())

        val configured = unconfigured.copy(
            providers = DEFAULT_PROVIDERS.map { provider ->
                if (provider is ProviderSetting.OpenAI && provider.name == "OpenRouter") {
                    provider.copy(apiKey = "sk-or-v1-test")
                } else {
                    provider
                }
            }
        )
        assertFalse(configured.isNotConfigured())
    }
}
