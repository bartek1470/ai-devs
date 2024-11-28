package pl.bartek.aidevs.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
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
        functionBeanNames: List<String> = listOf(),
        chatOptions: ChatOptions? = null,
        onPartialResponseReceived: (String) -> Unit = {},
    ): String {
        val cachedName = "${messages.hashCode()}.txt"

        val cachedPath = cacheDir.resolve(cachedName)
        if (cachedPath.exists()) {
            log.debug { "Using cached response $cachedName" }
            val cachedResponse = Files.readString(cachedPath)
            onPartialResponseReceived.invoke(cachedResponse)
            return cachedResponse
        }

        log.debug { "Cached file $cachedName doesn't exist. Calling chat client" }

        val chatRequestSpec =
            chatClient
                .prompt()
                .messages(messages)
                .functions(*functionBeanNames.toTypedArray())

        chatOptions?.also { chatRequestSpec.options(it) }

        val response =
            chatRequestSpec
                .stream()
                .chatResponse()
                .doOnNext {
                    it
                        .content()
                        .takeIf { it.isNotBlank() }
                        ?.also(onPartialResponseReceived)
                }.collect(Collectors.toList())
                .block() ?: throw IllegalStateException("Cannot get chat response")

        val responseContent = response.joinToString("") { it.content() }
        Files.writeString(cachedPath, responseContent)
        return responseContent
    }

    private fun ChatResponse.content(): String = result?.output?.content ?: ""

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
