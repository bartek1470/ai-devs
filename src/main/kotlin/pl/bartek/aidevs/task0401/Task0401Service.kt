package pl.bartek.aidevs.task0401

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.BRIGHT_YELLOW
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.course.api.AiDevsAnswerResponse
import pl.bartek.aidevs.course.api.AiDevsAuthenticatedAnswer
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.task0401.ImageOperation.START
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.println
import java.net.URI
import kotlin.io.path.Path

@Service
class Task0401Service(
    @Value("\${aidevs.task.0401.photos-url}") private val photosUrl: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
    restClient: RestClient,
) {
    private val restClient =
        restClient
            .mutate()
            .baseUrl(photosUrl)
            .build()

    fun run(terminal: Terminal) {
        val startResponse =
            restClient
                .post()
                .body(objectMapper.writeValueAsString(AiDevsAuthenticatedAnswer(Task.PHOTOS.taskName, "START", apiKey)))
                .retrieve()
                .body(AiDevsAnswerResponse::class.java) ?: throw IllegalStateException("Cannot get response")

        val response =
            chatService.sendToChat(
                listOf(
                    SystemMessage(
                        """
                        Extract URLs to photos from a given message. Put them in separate lines.
                        """.trimIndent(),
                    ),
                    UserMessage(startResponse.message),
                ),
                chatOptions = PortableFunctionCallingOptions.builder().withTemperature(0.0).build(),
            )

        val urls =
            response
                .split("\n")
                .map { it.trim() }
                .map { URI.create(it)!!.toURL() }
                .associateBy { Path(it.file).fileName.toString() }
                .mapValues { mutableListOf(ImageLog(START, it.value)) }

        terminal.println("Summary:".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
        terminal.println(
            urls.entries.joinToString("\n") {
                "${it.key.ansiFormatted(color = YELLOW)}:\n\t${it.value.joinToString("\n\t")}"
            },
        )
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
