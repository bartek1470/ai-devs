package pl.bartek.aidevs.text

import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.sha256Base32
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists

@Service
class TextService(
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    aiModelVendor: AiModelVendor,
    openAiChatModel: OpenAiChatModel,
    ollamaChatModel: OllamaChatModel,
) {
    private val cacheDir = cacheDir.resolve("prompt-text")
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

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun sendToChat(
        messages: List<Message>,
        chatOptions: ChatOptions = defaultChatOptions,
        onPartialResponseReceived: (String) -> Unit = {},
    ): String {
        val prompt = messages
            .joinToString("\n\n") { message ->
                "${message.messageType.value}\n${message.content}"
            }
        val cachedName = "${prompt.sha256Base32()}.txt"

        val cachedPath = cacheDir.resolve(cachedName)
        if (cachedPath.exists()) {
            log.debug { "Using cached response $cachedName for prompt:\n$prompt$" }
            val cachedResponse = Files.readString(cachedPath)
            onPartialResponseReceived.invoke(cachedResponse)
            return cachedResponse
        }

        log.debug { "Cached file $cachedName doesn't exist. Calling chat client. Prompt:\n$prompt" }
        val response = chatClient
            .prompt()
            .messages(messages)
            .options(chatOptions)
            .stream()
            .content()
            .doOnNext(onPartialResponseReceived)
            .collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")
        Files.writeString(cachedPath, response)
        return response
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
