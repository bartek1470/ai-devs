package pl.bartek.aidevs.task0204

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0204Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0204")
data class Task0204Config(
    val dataUrl: URL,
)
