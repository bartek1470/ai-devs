package pl.bartek.aidevs.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service

@Service
class OllamaManager(
    private val chatClient: ChatClient,
    private val api: OllamaApi,
) {
    /**
     * [Docs](https://github.com/ollama/ollama/blob/main/docs/api.md#unload-a-model)
     */
    fun unloadModels() {
        val modelResponse: OllamaApi.ListModelResponse = api.listModels()
        val models: List<String> = modelResponse.models.map { it.model }
        for (model in models) {
            chatClient
                .prompt()
                .options(
                    OllamaOptions
                        .builder()
                        .model(model)
                        .keepAlive("0")
                        .build(),
                ).call()
                .content()
        }
    }
}
