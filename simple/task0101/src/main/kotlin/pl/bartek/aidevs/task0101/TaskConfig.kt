package pl.bartek.aidevs.task0101

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(Task0101Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0101")
data class Task0101Config(
    val url: String,
    val username: String,
    val password: String,
)
