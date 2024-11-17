package pl.bartek.aidevs

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class AiModelVendor(
    private val environment: Environment,
    private val openAiChatModel: OpenAiChatModel,
    private val ollamaChatModel: OllamaChatModel,
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
}
