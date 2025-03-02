package pl.bartek.aidevs.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// @Profile("!openai & !ollama & !groq")
@Configuration
class AiConfig {
    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
}
