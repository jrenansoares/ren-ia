package br.com.projetoia.app.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")
val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "",
        builtIn = true,
        models = listOf(
            Model(
                id = DEFAULT_AUTO_MODEL_ID,
                modelId = "openrouter/free",
                displayName = "OpenRouter gratuito",
                abilities = listOf(ModelAbility.TOOL),
                contextLength = 8192
            )
        ),
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Google AI Studio",
        apiKey = "",
        builtIn = true
    ),
    ProviderSetting.OpenAI(name = "Groq", baseUrl = "https://api.groq.com/openai/v1", apiKey = ""),
    ProviderSetting.OpenAI(name = "NVIDIA NIM", baseUrl = "https://integrate.api.nvidia.com/v1", apiKey = ""),
)
