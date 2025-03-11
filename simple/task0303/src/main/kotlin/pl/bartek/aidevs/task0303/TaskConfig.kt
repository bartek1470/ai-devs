package pl.bartek.aidevs.task0303

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0303Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0303")
data class Task0303Config(
    val apiUrl: URL,
)
