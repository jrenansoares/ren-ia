package me.rerere.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ChatModelSupportTest {
    @Test fun legacyConversationModelsRemainAvailable() {
        for (id in listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "openrouter/free", "llama-3.3-70b-versatile", "gemini-2.5-flash", "anthropic/claude-sonnet-4.6", "anthropic/claude-opus-4.6")) {
            assertTrue(id, Model(modelId = id).supportsConversation())
        }
        assertFalse("anthropic/claude-sonnet-4.6:batch", Model(modelId = "anthropic/claude-sonnet-4.6:batch").supportsConversation())
    }

    @Test fun audioAndSecurityModelsAreExcludedEvenWithMisleadingChatMetadata() {
        for (id in listOf("canopylabs/orpheus-v1-english", "canopylabs/orpheus-arabic-saudi", "whisper-large-v3", "meta-llama/llama-prompt-guard-2-22m", "openai/gpt-oss-safeguard-20b", "gemini-2.5-flash-preview-tts", "gemini-live-2.5-flash", "gpt-4o-mini-transcribe", "gpt-4o-realtime-preview")) {
            assertFalse(id, Model(modelId = id, chatSupported = true, abilities = listOf(ModelAbility.TOOL)).supportsConversation())
        }
    }

    @Test fun embeddingRerankingAndImageModelsAreExcluded() {
        for (id in listOf("text-embedding-3-small", "nvidia/llama-3.2-nv-embedqa-1b-v2", "jina-reranker-v3", "gemini-2.5-flash-image", "black-forest-labs/flux-schnell", "veo-3.1-generate-preview")) {
            assertFalse(id, Model(modelId = id, chatSupported = true).supportsConversation())
        }
    }

    @Test fun unknownIdsAreNotMadeEligibleByDisplayNameOrManualAbilities() {
        assertFalse(Model(modelId = "unknown-v1", displayName = "GPT OSS chat", abilities = listOf(ModelAbility.TOOL)).supportsConversation())
        assertFalse(Model(modelId = "allam-2-7b").supportsConversation())
        assertTrue(Model(modelId = "new-dialog-model", chatSupported = true).supportsConversation())
    }

    @Test fun explicitIncompatibilityAndModalitiesOverrideRecognizedNames() {
        assertFalse(Model(modelId = "openai/gpt-oss-120b", chatSupported = false).supportsConversation())
        assertFalse(Model(modelId = "openai/gpt-oss-120b", outputModalities = listOf(Modality.IMAGE)).supportsConversation())
        assertFalse(Model(modelId = "openai/gpt-oss-120b", type = ModelType.EMBEDDING).supportsConversation())
    }

    @Test fun catalogReadsModalitiesAndRejectsInactiveModels() {
        fun metadata(value: String) = catalogChatSupport(Json.parseToJsonElement(value).jsonObject)
        assertEquals(true, metadata("""{"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]}}"""))
        assertEquals(false, metadata("""{"architecture":{"input_modalities":["text"],"output_modalities":["audio"]}}"""))
        assertEquals(false, metadata("""{"active":false}"""))
        assertEquals(false, metadata("""{"type":"embedding"}"""))
        assertNull(metadata("""{"id":"unknown","name":"Chat model"}"""))
    }

    @Test fun oldStoredModelsDecodeWithoutTheNewCapabilityFlag() {
        val model = Json.decodeFromString<Model>("""{"modelId":"openai/gpt-oss-120b"}""")
        assertNull(model.chatSupported)
        assertTrue(model.supportsConversation())
    }
}
