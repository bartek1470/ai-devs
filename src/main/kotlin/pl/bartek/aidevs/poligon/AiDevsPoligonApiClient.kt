package pl.bartek.aidevs.poligon

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.ACCEPT
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.LoggingRestClientInterceptor

@Component
class AiDevsPoligonApiClient(
    @Value("\${aidevs.poligon.api-key}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    private val restClient =
        RestClient
            .builder()
            .defaultRequest {
                it
                    .header(ACCEPT, APPLICATION_JSON_VALUE)
                    .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            }.requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    fun <T> sendAnswer(
        uri: String,
        answer: AiDevsPoligonAnswer<T>,
    ): AiDevsPoligonAnswerResponse {
        val authenticatedAnswer = AiDevsPoligonAuthenticatedAnswer(answer.task, answer.answer, apiKey)
        val responseSpec =
            restClient
                .post()
                .uri(uri)
                .body(objectMapper.writeValueAsString(authenticatedAnswer))
                .retrieve()
        return responseSpec.body()
            ?: throw IllegalStateException("Missing response body")
    }

    fun fetchTask0Data(): List<String> {
        val responseSpec =
            restClient
                .get()
                .uri("NEXT COMMITS IN ENVS")
                .header(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .retrieve()
        val body = responseSpec.body<String>() ?: throw IllegalStateException("Missing response body")
        return body.trim().split("\n")
    }

    fun fetchTask5Data(): String =
        restClient
            .get()
            .uri("NEXT COMMITS IN ENVS")
            .retrieve()
            .body(String::class.java) ?: throw IllegalStateException("Cannot get data to process")
}
