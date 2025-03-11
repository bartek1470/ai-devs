package pl.bartek.aidevs.taskpoligon

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.net.URL

@EnableConfigurationProperties(PoligonTaskConfig::class)
@Configuration
class TaskConfig

@ConfigurationProperties(prefix = "aidevs.task.poligon")
data class PoligonTaskConfig(
    val dataUrl: URL,
    val answerUrl: URL,
)
