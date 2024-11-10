package pl.bartek.aidevs

import org.springframework.ai.autoconfigure.ollama.OllamaChatProperties
import org.springframework.ai.autoconfigure.ollama.OllamaConnectionProperties
import org.springframework.ai.autoconfigure.ollama.OllamaEmbeddingProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(
    OllamaChatProperties::class,
    OllamaEmbeddingProperties::class,
    OllamaConnectionProperties::class,
)
@Configuration
class AiConfig {
    @Bean
    fun chatClient(chatClientBuilder: ChatClient.Builder): ChatClient =
        chatClientBuilder
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
}
