package me.rerere.ai.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.registry.ModelRegistry
import java.util.Locale

private val nonChatTasks = setOf(
    "tts", "stt", "asr", "whisper", "orpheus", "speech", "audio", "realtime", "live",
    "embedding", "embeddings", "embed", "rerank", "reranker", "guard", "safeguard",
    "moderation", "classifier", "reward", "transcribe", "transcription", "translation", "safety", "parakeet", "riva",
    "imagen", "veo", "flux", "image", "imgen", "sora", "seedance", "ocr", "parse", "retriever",
)

/** A catalog default of CHAT is not evidence that a model can hold a conversation. */
fun Model.supportsConversation(): Boolean {
    if (type != ModelType.CHAT || Modality.TEXT !in inputModalities || Modality.TEXT !in outputModalities) return false
    val name = modelId.lowercase(Locale.ROOT)
    val tokens = name.split(Regex("[^a-z0-9]+"))
    if (tokens.any {
            it in nonChatTasks || it.startsWith(
                "embed"
            ) || it.startsWith("rerank") || it.endsWith("guard")
        } ||
        listOf("stable-diffusion", "dall-e", "hunyuan-video", "qwen-mt").any { it in name }) {
        return false
    }
    if (chatSupported != null) return chatSupported
    // Legacy installations have no catalog flag. Recognize the built-in free
    // router and the SDK's known conversational models; unknown IDs stay hidden.
    return name in setOf("openrouter/free", "llama-3.3-70b-versatile", "llama-3.1-8b-instant") ||
        ModelRegistry.MODEL_ABILITIES.getData(modelId).isNotEmpty()
}

/** Read only explicit provider metadata; never guess from the display name. */
internal fun catalogChatSupport(model: JsonObject): Boolean? {
    if (model["active"]?.jsonPrimitive?.booleanOrNull == false) return false
    val kind = model["type"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)
    if (kind in setOf("embedding", "rerank", "audio", "image", "moderation")) return false
    val architecture = model["architecture"]?.jsonObject ?: return null
    val input = architecture["input_modalities"]?.jsonArray ?: return null
    val output = architecture["output_modalities"]?.jsonArray ?: return null
    return input.any { it.jsonPrimitive.contentOrNull == "text" } &&
        output.any { it.jsonPrimitive.contentOrNull == "text" }
}
