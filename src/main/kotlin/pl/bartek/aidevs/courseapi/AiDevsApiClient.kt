package pl.bartek.aidevs.courseapi

import com.fasterxml.jackson.databind.ObjectMapper
import org.jline.terminal.Terminal
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.http.HttpHeaders.ACCEPT
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpHeaders.COOKIE
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.ansiFormatted
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Scanner

@Component
class AiDevsApiClient(
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.submit-flag-url}") private val submitFlagUrl: String,
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient,
) {
    private val cookie = Paths.get(cacheDir).resolve("cookie.txt")

    fun <T> sendAnswer(
        uri: String,
        answer: AiDevsAnswer<T>,
    ): AiDevsAnswerResponse {
        val authenticatedAnswer = AiDevsAuthenticatedAnswer(answer.task, answer.answer, apiKey)
        val responseSpec =
            restClient
                .post()
                .uri(uri)
                .headers { headers ->
                    headers.add(ACCEPT, APPLICATION_JSON_VALUE)
                    headers.add(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                }.body(objectMapper.writeValueAsString(authenticatedAnswer))
                .retrieve()

        return responseSpec.body()
            ?: throw IllegalStateException("Missing response body")
    }

    fun <T> sendAnswerReceiveText(
        uri: String,
        answer: AiDevsAnswer<T>,
    ): String {
        val authenticatedAnswer = AiDevsAuthenticatedAnswer(answer.task, answer.answer, apiKey)
        val responseSpec =
            restClient
                .post()
                .uri(uri)
                .headers { headers ->
                    headers.add(ACCEPT, APPLICATION_JSON_VALUE)
                    headers.add(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                }.body(objectMapper.writeValueAsString(authenticatedAnswer))
                .retrieve()

        return responseSpec.body()
            ?: throw IllegalStateException("Missing response body")
    }

    fun sendFlag(
        flag: String,
        terminal: Terminal,
    ): AiDevsAnswerResponse {
        if (Files.notExists(cookie)) {
            terminal.writer().print("Enter value of PHPSESSID cookie ( ('; '+document.cookie).split(`; PHPSESSID=`).pop().split(';')[0] ): ".ansiFormatted(color = YELLOW))
            terminal.writer().flush()
            val cookieVal = Scanner(System.`in`).next()!!
            Files.write(cookie, cookieVal.toByteArray())
        }
        val key = Files.readString(cookie)
        val responseSpec =
            restClient
                .post()
                .uri(submitFlagUrl)
                .contentType(APPLICATION_FORM_URLENCODED)
                .headers { headers ->
                    headers.add(COOKIE, "PHPSESSID=$key")
                }.body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("key", key)
                        add("flag", flag)
                    },
                ).retrieve()

        val body = responseSpec.body<String>()!!
        val response = objectMapper.readValue(body, AiDevsAnswerResponse::class.java)

        return response
    }
}
