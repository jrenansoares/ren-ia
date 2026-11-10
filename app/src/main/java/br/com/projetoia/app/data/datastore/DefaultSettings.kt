package br.com.projetoia.app.data.datastore

import br.com.projetoia.app.data.ai.prompts.LEARNING_MODE_PROMPT
import br.com.projetoia.app.data.model.Assistant
import br.com.projetoia.app.data.model.InjectionPosition
import br.com.projetoia.app.data.model.PromptInjection
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

private val REN_SYSTEM_PROMPT = """
Você opera dentro do terminal Ren. Responda de forma reta, modular e técnica.
Suprima saudações, floreios corporativos e parágrafos de fechamento.
Entregue os dados com máxima eficiência e alinhamento, honrando a estética de linha de comando.
Responda no idioma do usuário. Não afirme ter executado algo sem evidência.
""".trimIndent()

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = REN_SYSTEM_PROMPT
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "",
        systemPrompt = REN_SYSTEM_PROMPT
    ),
)



val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
internal val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),

)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
