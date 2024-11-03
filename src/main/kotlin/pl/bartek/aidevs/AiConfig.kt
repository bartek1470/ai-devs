package pl.bartek.aidevs

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfig {
    @Bean
    fun chatClient(chatClientBuilder: ChatClient.Builder): ChatClient =
        chatClientBuilder
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
}
