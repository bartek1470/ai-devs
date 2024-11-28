package pl.bartek.aidevs.task0303

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.model.function.FunctionCallbackWrapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class FunctionConfig {
    @Bean
    fun sendDbApiRequest(
        @Value("\${aidevs.api-key}") apiKey: String,
        @Value("\${aidevs.task.0303.api-url}") apiUrl: String,
        restClient: RestClient,
    ): FunctionCallback =
        FunctionCallbackWrapper
            .builder(SendDbApiRequest(apiKey, apiUrl, restClient))
            .withName("sendDbApiRequest")
            .withDescription("Execute database query")
            .withObjectMapper(
                JsonMapper
                    .builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .build()
                    .registerModule(JavaTimeModule())
                    .registerKotlinModule(),
            ).build()
}
