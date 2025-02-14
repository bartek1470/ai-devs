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

    /**
     * Do not use `pl.bartek.aidevs.config.RestClientConfig.restClientBuilder` since it will be customized by Spring AI
     * with retry template and this `RestClient` should just send a request and don't throw exception if there was
     * a client or server error HTTP status code
     */
    @Bean
    fun restClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
}
