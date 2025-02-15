package pl.bartek.aidevs.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URL
import java.nio.file.Path

@ConfigurationProperties(prefix = "aidevs")
data class AiDevsProperties(
    val cacheDir: Path,
    val apiKey: String,
    val submitFlagUrl: URL,
    val reportUrl: URL,
    val task: TaskProperties,
)

data class TaskProperties(
    val poligon: PoligonTask,
    val task0101: Task0101,
    val task0102: Task0102,
    val task0103: Task0103,
    val task0104: Task0104,
    val task0105: Task0105,
    val task0201: BasicTask,
    val task0203: BasicTask,
    val task0204: BasicTask,
    val task0205: Task0205,
    val task0301: BasicTask,
    val task0302: Task0302,
    val task0303: Task0303,
    val task0304: Task0304,
    val task0402: Task0402,
    val task0403: Task0403,
    val task0405: Task0405,
)

data class PoligonTask(
    val dataUrl: URL,
    val answerUrl: URL,
)

data class Task0101(
    val url: String,
    val username: String,
    val password: String,
)

data class Task0102(
    val conversationUrl: URL,
)

data class Task0103(
    val dataUrl: URL,
)

data class Task0104(
    val fileBaseUrl: URL,
    val answerUrl: URL,
)

data class Task0105(
    val dataUrl: URL,
)

data class BasicTask(
    val dataUrl: URL,
)

data class Task0205(
    val dataUrl: URL,
    val articleUrl: URL,
)

data class Task0302(
    val dataUrl: URL,
    val dataPassword: String,
)

data class Task0303(
    val apiUrl: URL,
)

data class Task0304(
    val dataUrl: URL,
    val apiUrl: URL,
)

data class Task0402(
    val dataUrl: URL,
)

data class Task0403(
    val baseUrl: URL,
    val questionsUrl: URL,
)

data class Task0405(
    val dataUrl: URL,
    val questionsUrl: URL,
)
