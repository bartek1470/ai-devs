package pl.bartek.aidevs.task0301

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0301Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0301")
data class Task0301Config(
    val dataUrl: URL,
)
