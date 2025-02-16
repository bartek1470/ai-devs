package pl.bartek.aidevs.task0104

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.ansiFormattedSuccess
import pl.bartek.aidevs.util.println

@Command(
    group = "task",
    command = ["task"],
)
class Task0104Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val restClient: RestClient,
) {
    @Command(
        command = ["0104"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s01e04-techniki-optymalizacji",
    )
    fun run() {
        val request =
            """|Map:
               |p X p p p p
               |p p p X p p
               |p X p X p p
               |o X p p p F
               |
               |Find a resourcePathToProcess in the map from 'o' to 'F' respecting rules below:
               |1. Move through 'p'
               |2. Avoid 'X'
               |3. You always move by one cell
               |4. You are limited by map boundaries
               |5. Think if a move is legal before making it
               |6. If there is no legal moves then take a step back and think again what are legal moves
               |7. Return a found resourcePathToProcess in a format `<RESULT>{"steps": "UP, LEFT, RIGHT, DOWN"}</RESULT>`
            """.trimMargin()

        terminal.println(request)
        terminal.println()

        val response =
            restClient
                .post()
                .uri(
                    aiDevsProperties.task.task0104.answerUrl
                        .toURI(),
                ).contentType(MediaType.APPLICATION_FORM_URLENCODED)
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
        val filename = value["filename"]!!.textValue()
        val fileUrl =
            UriComponentsBuilder
                .fromUri(
                    aiDevsProperties.task.task0104.fileBaseUrl
                        .toURI(),
                ).pathSegment(filename)
                .build()
                .toUriString()

        terminal.println("Steps: ${value["steps"].textValue()}")

        if (parsedMessage == null || filename == "whatever.zip") {
            terminal.println("Wrong answer".ansiFormattedError())
        } else {
            terminal.println("Message: $parsedMessage".ansiFormattedSuccess())
            terminal.println("File to download: $fileUrl".ansiFormattedSuccess())
        }
    }
}
