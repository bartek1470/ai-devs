package pl.bartek.aidevs.poligon

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class AiDevsPoligonApiClient(
    @Value("\${aidevs.poligon.api-key}") private val apiKey: String,
    private val aiDevsPoligonRestClient: RestClient,
    private val objectMapper: ObjectMapper,
) {
    fun <T> sendAnswer(answer: AiDevsPoligonAnswer<T>): AiDevsPoligonAnswerResponse {
        val authenticatedAnswer = AiDevsPoligonAuthenticatedAnswer(answer.task, answer.answer, apiKey)
        val responseSpec =
            aiDevsPoligonRestClient
                .post()
                .uri("/verify")
                .body(objectMapper.writeValueAsString(authenticatedAnswer))
                .retrieve()
        return responseSpec.body()
            ?: throw IllegalStateException("Missing response body")
    }

    fun fetchTask0Data(): List<String> {
        val responseSpec =
            aiDevsPoligonRestClient
                .get()
                .uri("/dane.txt")
                .header(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .retrieve()
        val body = responseSpec.body<String>() ?: throw IllegalStateException("Missing response body")
        return body.trim().split("\n")
    }
}
