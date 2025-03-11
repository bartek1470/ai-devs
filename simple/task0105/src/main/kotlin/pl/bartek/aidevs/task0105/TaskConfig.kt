package pl.bartek.aidevs.task0105

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0105Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0105")
data class Task0105Config(
    val dataUrl: URL,
)
