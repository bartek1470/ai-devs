package pl.bartek.aidevs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())

    @Bean
    fun restClient(restClientBuilder: RestClient.Builder): RestClient =
        restClientBuilder
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
}
