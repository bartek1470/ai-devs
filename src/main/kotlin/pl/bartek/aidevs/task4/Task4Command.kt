package pl.bartek.aidevs.task4

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.LoggingRestClientInterceptor

@Command(group = "task")
class Task4Command {
    private val restClient =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .baseUrl("NEXT COMMITS IN ENVS")
            .build()

    @Command(command = ["task4"])
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

        println(request)
        println()

        val response =
            restClient
                .post()
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
        println(response)
        println()
        println()
        println(value["steps"])
        println()
        println()
        val message = value["message"]?.textValue()
        val parsedMessage =
            message?.substringAfter("<")?.let {
                Jsoup.parse("<$it").wholeText()
            }
        println(parsedMessage)
        println(value["filename"])
    }
}
