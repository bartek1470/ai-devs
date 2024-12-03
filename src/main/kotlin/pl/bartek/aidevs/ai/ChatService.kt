package pl.bartek.aidevs.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists

@Service
class ChatService(
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    private val chatClient: ChatClient,
) {
    private val cacheDir = cacheDir.resolve("prompt")

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun sendToChat(
        messages: List<Message>,
        functions: List<FunctionCallback> = listOf(),
        chatOptions: ChatOptions? = null,
        streaming: Boolean = true,
        responseReceived: (String) -> Unit = {},
    ): String {
        val cachedName = "${messages.hashCode()}.txt"

        val cachedPath = cacheDir.resolve(cachedName)
        if (cachedPath.exists()) {
            log.debug { "Using cached response $cachedName" }
            val cachedResponse = Files.readString(cachedPath)
            responseReceived.invoke(cachedResponse)
            return cachedResponse
        }

        log.debug { "Cached file $cachedName doesn't exist. Calling chat client" }

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

        Files.writeString(cachedPath, responseContent)
        return responseContent
    }

    private fun ChatResponse.content(): String = result?.output?.content ?: ""

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
