package pl.bartek.aidevs.poligon

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.ACCEPT
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.client.RestClient

@Configuration
class AiDevsPoligonApiClientConfig {
    @Bean
    fun aiDevsPoligonRestClient(
        @Value("\${aidevs.poligon.url}") baseUrl: String,
        objectMapper: ObjectMapper,
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .defaultRequest {
                it
                    .header(ACCEPT, APPLICATION_JSON_VALUE)
                    .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            }.build()
}
