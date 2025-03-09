package pl.bartek.aidevs.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.DefaultChatOptions
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.stereotype.Service
import pl.bartek.aidevs.config.AiDevsProperties
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists

@Service
class ChatService(
    private val aiDevsProperties: AiDevsProperties,
    private val chatClient: ChatClient,
) {
    fun sendToChatWithImageSupport(
        messages: List<Message>,
        functions: List<FunctionCallback> = listOf(),
        chatOptions: ChatOptions? = null,
        streaming: Boolean = true,
        cachePath: Path? = null,
        responseReceived: (String) -> Unit = {},
    ): String {
        val newChatOptions: ChatOptions =
            chatOptions?.copy<DefaultChatOptions>()?.also { it.model = aiDevsProperties.model.image }
                ?: ChatOptions.builder().model(aiDevsProperties.model.image).build()
        return sendToChat(messages, functions, newChatOptions, streaming, cachePath, responseReceived)
    }

    fun sendToChat(
        messages: List<Message>,
        functions: List<FunctionCallback> = listOf(),
        chatOptions: ChatOptions? = null,
        streaming: Boolean = true,
        cachePath: Path? = null,
        responseReceived: (String) -> Unit = {},
    ): String {
        if (cachePath?.exists() == true) {
            log.debug { "Using cached response $cachePath" }
            val cachedResponse = Files.readString(cachePath)
            responseReceived.invoke(cachedResponse)
            return cachedResponse
        }

        val chatRequestSpec =
            chatClient
                .prompt()
                .messages(messages)
                .functions<Any, Any>(*functions.toTypedArray())

        chatOptions?.also { chatRequestSpec.options(it) }

        val responseContent =
            if (streaming) {
                val chatResponses =
                    chatRequestSpec
                        .stream()
                        .chatResponse()
                        .doOnNext {
                            responseReceived(it.content())
                        }.collect(Collectors.toList())
                        .block() ?: throw IllegalStateException("Cannot get chat response")
                chatResponses.joinToString("") { it.content() }
            } else {
                val chatResponse =
                    chatRequestSpec.call().chatResponse() ?: throw IllegalStateException("Cannot get chat response")
                val content = chatResponse.content()
                responseReceived(content)
                content
            }

        cachePath?.let { Files.writeString(it, responseContent) }
        return responseContent
    }

    private fun ChatResponse.content(): String = result?.output?.text ?: ""

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
