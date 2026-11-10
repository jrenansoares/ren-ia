package br.com.projetoia.app.data.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportsConversation
import me.rerere.ai.registry.ModelRegistry
import br.com.projetoia.app.data.datastore.Settings

internal enum class ConnectionService(val title: String, val endpoint: String, val keyUrl: String) {
    GOOGLE(
        "Google AI Studio",
        "https://generativelanguage.googleapis.com/v1beta",
        "https://aistudio.google.com/apikey"
    ),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "https://platform.deepseek.com/api_keys"),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "https://build.nvidia.com"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/keys"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/settings/keys"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "https://console.anthropic.com/settings/keys"),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", "https://console.mistral.ai/api-keys/"),
    COHERE("Cohere", "https://api.cohere.ai/compatibility/v1", "https://dashboard.cohere.com/api-keys"),
    OLLAMA("Ollama Cloud", "https://ollama.com/v1", "https://ollama.com/settings/keys"),
    HUGGINGFACE("HuggingFace", "https://router.huggingface.co/v1", "https://huggingface.co/settings/tokens"),
    AGNES("Agnes AI", "https://apihub.agnes-ai.com/v1", "https://platform.agnes-ai.com"),
    SILICONFLOW("SiliconFlow", "https://api.siliconflow.com/v1", "https://siliconflow.com"),
    ROUTEWAY("Routeway", "https://api.routeway.ai/v1", "https://routeway.ai"),
    BAZAARLINK("BazaarLink", "https://bazaarlink.ai/api/v1", "https://bazaarlink.ai"),
    AINATIVE("AINative Studio", "https://api.ainative.studio/api/v1", "https://ainative.studio"),
    AION("Aion Labs", "https://api.aionlabs.ai/v1", "https://aionlabs.ai"),
    REQUESTY("Requesty", "https://router.requesty.ai/v1", "https://requesty.ai"),
    NAVY("NavyAI", "https://api.navy/v1", "https://api.navy"),
    NARA("NaraRouter", "https://router.bynara.id/v1", "https://router.bynara.id"),
    SEALION("SEA-LION", "https://api.sea-lion.ai/v1", "https://sea-lion.ai"),
    REKA("Reka", "https://api.reka.ai/v1", "https://platform.reka.ai"),
    XKIRO("xKiro", "https://api.xkiro.com/v1", "https://xkiro.com"),
    ORCA("OrcaRouter", "https://api.orcarouter.ai/v1", "https://orcarouter.ai"),
    UNO("UnoRouter", "https://api.unorouter.com/v1", "https://unorouter.com"),
    ANYAPI("AnyAPI", "https://api.anyapi.ai/v1", "https://anyapi.ai"),
    MODELSCOPE("ModelScope", "https://api-inference.modelscope.cn/v1", "https://modelscope.cn/my/myaccesstoken"),
    BAI("B.AI", "https://api.b.ai/v1", "https://b.ai"),
    LONGCAT("LongCat", "https://api.longcat.chat/openai/v1", "https://longcat.chat"),
    VOLCENGINE("Volcengine Ark", "https://ark.cn-beijing.volces.com/api/v3", "https://console.volcengine.com/ark"),
    BAIDU("Baidu Qianfan", "https://qianfan.baidubce.com/v2", "https://console.bce.baidu.com/qianfan"),
    RADEON(
        "AMD Radeon Cloud",
        "https://developer.amd.com.cn/radeon/api/v1",
        "https://developer.amd.com.cn/radeon/tokenfactory"
    ),
    IFLYTEK("iFlytek Spark", "https://spark-api-open.xf-yun.com/v1", "https://console.xfyun.cn"),
    ELECTRONHUB("ElectronHub", "https://api.electronhub.ai/v1", "https://app.electronhub.ai"),
    ROUTER9("Router9", "https://api.router9.com/v1", "https://www.router9.com"),
    SEPTOR("Septor Labs", "https://api.septorlabs.com/v1", "https://septorlabs.com/dashboard"),
    CLOD("CLōD", "https://api.clod.io/v1", "https://app.clod.io"),
    BLAZE("BlazeAPI", "https://api.blazeapi.org/paid/v1", "https://blazeapi.org/dashboard"),
    LUCIDITY("Lucidity Composite", "https://composite.lucidity.sh/v1", "https://composite.lucidity.sh"),
    AIRFORCE("Api.Airforce", "https://api.airforce/v1", "https://api.airforce"),
    DREAMPROMPTING("DreamPrompting", "https://dreamprompting.com/api/v1", "https://dreamprompting.com"),
    WATERFALL("Waterfall", "https://api.getwaterfall.org/v1", "https://getwaterfall.org"),
    LOGFARE("Logfare", "https://logfare.ai/v1", "https://logfare.ai"),
    LLM7("LLM7", "https://api.llm7.io/v1", "https://llm7.io"),
    ;

    fun matches(provider: ProviderSetting): Boolean {
        if (provider !is ProviderSetting.ApiKey || provider.baseUrl.trimEnd('/') != endpoint) return false
        return when (this) {
            GOOGLE -> provider is ProviderSetting.Google && !provider.vertexAI
            ANTHROPIC -> provider is ProviderSetting.Claude
            else -> provider is ProviderSetting.OpenAI
        }
    }

    fun configure(key: String, existing: ProviderSetting? = null): ProviderSetting.ApiKey {
        val normalized = key.trim().replaceFirst(Regex("^Bearer(?:\\s+|$)", RegexOption.IGNORE_CASE), "").trim()
        require(normalized.isNotBlank() && normalized.none { it.isWhitespace() })
        return when {
            existing is ProviderSetting.Google && matches(
                existing
            ) -> existing.copy(apiKey = normalized, enabled = true)
            existing is ProviderSetting.OpenAI && matches(
                existing
            ) -> existing.copy(apiKey = normalized, enabled = true)
            existing is ProviderSetting.Claude && matches(existing) -> existing.copy(apiKey = normalized, enabled = true)
            this == GOOGLE -> ProviderSetting.Google(name = title, baseUrl = endpoint, apiKey = normalized)
            this == ANTHROPIC -> ProviderSetting.Claude(name = title, baseUrl = endpoint, apiKey = normalized)
            else -> ProviderSetting.OpenAI(name = title, baseUrl = endpoint, apiKey = normalized)
        }
    }
}

internal fun conversationalCatalog(models: List<Model>): List<Model> = models
    .filter { it.chatSupported != false && it.copy(chatSupported = true).supportsConversation() }
    .distinctBy { it.modelId }
    .map { model ->
        model.copy(
            abilities = (model.abilities + ModelRegistry.MODEL_ABILITIES.getData(model.modelId)).distinct(),
            inputModalities = (model.inputModalities + ModelRegistry.MODEL_INPUT_MODALITIES.getData(
                model.modelId
            )).distinct(),
        )
    }

/** Merge against the latest settings, preserving model UUIDs used by conversations. */
internal fun Settings.saveConnection(service: ConnectionService, key: String, catalog: List<Model>): Settings {
    val existing = providers.firstOrNull { service.matches(it) }
    val configured = service.configure(key, existing)
    val refreshed = catalog.associateBy { it.modelId }
    val loaded = conversationalCatalog(catalog).associateBy { it.modelId }
    val oldModels = configured.models
    val merged = oldModels.map { old ->
        refreshed[old.modelId]?.let { fresh ->
            old.copy(chatSupported = fresh.chatSupported, abilities = (old.abilities + fresh.abilities).distinct())
        } ?: old
    } + loaded.values.filter { fresh -> oldModels.none { it.modelId == fresh.modelId } }
    val updated = configured.copyProvider(models = merged)
    return copy(providers = if (existing ==
        null) {
        providers + updated
    } else {
        providers.map { if (it.id == existing.id) updated else it }
    })
}
