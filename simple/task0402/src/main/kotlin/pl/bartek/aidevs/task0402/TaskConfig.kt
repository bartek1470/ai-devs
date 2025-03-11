package pl.bartek.aidevs.task0402

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(Task0402Config::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.task0402")
data class Task0402Config(
    val dataUrl: URL,
)
