package pl.bartek.aidevs.task0104

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0104Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0104")
data class Task0104Config(
    val fileBaseUrl: URL,
    val answerUrl: URL,
)
