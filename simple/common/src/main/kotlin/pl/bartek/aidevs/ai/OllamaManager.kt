package pl.bartek.aidevs.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("ollama")
@Service
class OllamaManager(
    private val api: OllamaApi,
) {
    /**
     * [Docs](https://github.com/ollama/ollama/blob/main/docs/api.md#unload-a-model)
     */
    fun unloadModels() {
        log.trace { "Unloading models" }
        val modelResponse: OllamaApi.ListModelResponse = api.listModels()
        val models: List<String> = modelResponse.models.map { it.model }
        log.trace { "Will unload models: $models" }
        for (model in models) {
            log.debug { "Unloading model $model" }
            api.chat(
                OllamaApi.ChatRequest
                    .builder(model)
                    .keepAlive("0")
                    .build(),
            )
        }
        log.debug { "All models unloaded" }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
