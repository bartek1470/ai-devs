package pl.bartek.aidevs.text

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.stereotype.Service
import pl.bartek.aidevs.AiModelVendor
import java.util.stream.Collectors

@Service
class TextService(
    aiModelVendor: AiModelVendor,
    openAiChatModel: OpenAiChatModel,
    ollamaChatModel: OllamaChatModel,
) {
    private val chatModel: ChatModel = if (aiModelVendor.isOllamaPreferred()) ollamaChatModel else openAiChatModel
    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    private val defaultChatOptions: ChatOptions =
        if (aiModelVendor.isOllamaPreferred()) {
            OllamaOptions
                .builder()
                .withModel("llama3.2:3b")
                .build()
        } else {
            OpenAiChatOptions
                .builder()
                .withModel(OpenAiApi.ChatModel.GPT_4_O_MINI)
                .build()
        }

    fun sendToChat(
        messages: List<Message>,
        chatOptions: ChatOptions = defaultChatOptions,
        onPartialResponseReceived: (String) -> Unit = {},
    ): String =
        chatClient
            .prompt()
            .messages(messages)
            .options(chatOptions)
            .stream()
            .content()
            .doOnNext(onPartialResponseReceived)
            .collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")
}
