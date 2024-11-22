package pl.bartek.aidevs

import org.springframework.ai.autoconfigure.ollama.OllamaConnectionProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class AiModelVendor(
    private val environment: Environment,
    private val openAiChatModel: OpenAiChatModel,
    private val ollamaChatModel: OllamaChatModel,
    private val ollamaConnectionProperties: OllamaConnectionProperties,
    @Value("\${aidevs.local.ollama.possible-models}") private val possibleOllamaModels: List<String>,
) {
    fun isOllamaPreferred(): Boolean = environment.acceptsProfiles(Profiles.of("ollama"))

    fun isOpenAiPreferred(): Boolean = environment.acceptsProfiles(Profiles.of("openai"))

    fun defaultChatClient(): ChatClient {
        val chatModel: ChatModel = if (isOllamaPreferred()) ollamaChatModel else openAiChatModel
        return ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
    }

    fun unloadOllamaModels() {
        if (isOllamaPreferred()) {
            val chatClient = defaultChatClient()
            for (model in possibleOllamaModels) {
                chatClient
                    .prompt()
                    .options(
                        OllamaOptions
                            .builder()
                            .withModel(model)
                            .withKeepAlive("0")
                            .build(),
                    ).call()
                    .content()
            }
        }
    }
}
