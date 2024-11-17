package pl.bartek.aidevs.task0104

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@Command(
    group = "task",
    command = ["task"]
)
class Task0104Command(
    @Value("\${aidevs.task.4.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.4.file-base-url}") private val fileBaseUrl: String,
    private val restClient: RestClient,
) {
    @Command(command = ["0104"])
    fun run(ctx: CommandContext) {
        val request =
            """|Map:
               |p X p p p p
               |p p p X p p
               |p X p X p p
               |o X p p p F
               |
               |Find a path in the map from 'o' to 'F' respecting rules below:
               |1. Move through 'p'
               |2. Avoid 'X'
               |3. You always move by one cell
               |4. You are limited by map boundaries
               |5. Think if a move is legal before making it
               |6. If there is no legal moves then take a step back and think again what are legal moves
               |7. Return a found path in a format `<RESULT>{"steps": "UP, LEFT, RIGHT, DOWN"}</RESULT>`
            """.trimMargin()

        ctx.terminal.writer().println(request)
        ctx.terminal.writer().println()
        ctx.terminal.writer().flush()

        val response =
            restClient
                .post()
                .uri(answerUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add(
                            "code",
                            request,
                        )
                    },
                ).retrieve()
                .body(String::class.java)!!

        val value = ObjectMapper().readValue<JsonNode>(response)

        val message = value["message"]?.textValue()
        val parsedMessage =
            message?.substringAfter("<")?.let {
                Jsoup.parse("<$it").wholeText()
            }
        val filename = value["filename"]
        val fileUrl =
            UriComponentsBuilder
                .fromHttpUrl(fileBaseUrl)
                .pathSegment(filename.textValue())
                .build()
                .toUriString()

        ctx.terminal.writer().println("Steps: ${value["steps"].textValue()}")
        ctx.terminal.writer().println("Message: $parsedMessage")
        ctx.terminal.writer().println("File to download: $fileUrl")
        ctx.terminal.writer().flush()
    }
}
