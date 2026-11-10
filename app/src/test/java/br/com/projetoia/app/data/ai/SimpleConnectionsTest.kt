package br.com.projetoia.app.data.ai

import java.net.URI
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import br.com.projetoia.app.data.datastore.Settings
import org.junit.Assert.*
import org.junit.Test

class SimpleConnectionsTest {
    @Test fun onlySelectedServiceReceivesTheKey() {
        for (service in ConnectionService.entries) {
            val provider = service.configure("  Bearer test-key  ")
            assertEquals("test-key", provider.apiKey)
            assertEquals(service.endpoint, provider.baseUrl)
            assertEquals("https", URI(provider.baseUrl).scheme)
            assertTrue(service.matches(provider))
        }
        assertEquals(ConnectionService.entries.size, ConnectionService.entries.map { it.endpoint }.distinct().size)
    }

    @Test fun rejectsMalformedPastedKeys() {
        for (key in listOf("", " ", "Bearer ", "key with spaces", "key\nother")) {
            try { ConnectionService.GROQ.configure(key); fail(key) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun reconnectionPreservesIdsAndUnrelatedSettings() {
        val chat = Model(modelId = "openai/gpt-oss-120b", displayName = "My model")
        val speech = Model(modelId = "canopylabs/orpheus-v1-english")
        val old = ProviderSetting.OpenAI(name = "My Groq", baseUrl = ConnectionService.GROQ.endpoint, apiKey = "old", models = listOf(chat, speech))
        val other = ProviderSetting.OpenAI(name = "Other", apiKey = "keep-this-key")
        val settings = Settings(providers = listOf(old, other), favoriteModels = listOf(chat.id))
        val updated = settings.saveConnection(ConnectionService.GROQ, "new", listOf(
            Model(modelId = chat.modelId, chatSupported = true),
            Model(modelId = "openai/gpt-oss-20b"),
            speech,
        ))
        val saved = updated.providers[0] as ProviderSetting.OpenAI
        assertEquals(old.id, saved.id)
        assertEquals("new", saved.apiKey)
        assertEquals(chat.id, saved.models.first { it.modelId == chat.modelId }.id)
        assertEquals("My model", saved.models.first { it.modelId == chat.modelId }.displayName)
        assertTrue(saved.models.any { it.id == speech.id })
        assertFalse(isAutomaticRouteCandidate(saved, speech))
        assertEquals(listOf(chat.id), updated.favoriteModels)
        assertEquals(other, updated.providers[1])
        assertFalse(updated.freeRouter.enabled)
    }

    @Test fun newImportKeepsUnknownCandidatesForVerificationButExcludesNonChatModels() {
        val output = conversationalCatalog(listOf(
            Model(modelId = "openai/gpt-oss-120b"),
            Model(modelId = "openai/gpt-oss-120b"),
            Model(modelId = "canopylabs/orpheus-v1-english"),
            Model(modelId = "meta-llama/llama-prompt-guard-2-22m"),
            Model(modelId = "unknown-model"),
        ))
        assertEquals(listOf("openai/gpt-oss-120b", "unknown-model"), output.map { it.modelId })
        assertNull(output.last().chatSupported)
        assertTrue(canProbeModel(ConnectionService.GROQ.configure("test"), output.last()))
    }

    @Test fun refreshingAnInactiveModelBlocksItsOldApproval() {
        val model = Model(modelId = "openai/gpt-oss-120b")
        val provider = ConnectionService.GROQ.configure("test").copyProvider(models = listOf(model))
        val settings = Settings(providers = listOf(provider))
        val updated = settings.saveConnection(ConnectionService.GROQ, "test", listOf(model.copy(chatSupported = false)))
        assertFalse(isAutomaticRouteCandidate(updated.providers.single(), updated.providers.single().models.single()))
    }
}
