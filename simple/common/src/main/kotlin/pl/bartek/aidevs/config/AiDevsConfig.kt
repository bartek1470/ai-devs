package pl.bartek.aidevs.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URL
import java.nio.file.Path

@ConfigurationProperties(prefix = "aidevs")
data class AiDevsProperties(
    val cacheDir: Path,
    val downloadsDir: Path,
    val tmpDir: Path,
    val pythonPackagesPath: Path,
    val ollama: AiDevsOllamaProperties,
    val apiKey: String,
    val submitFlagUrl: URL,
    val reportUrl: URL,
    val task: TaskProperties,
)

data class AiDevsOllamaProperties(
    val unloadModelsBeforeLocalWhisper: Boolean,
)

data class TaskProperties(
    val poligon: PoligonTask,
    val task0101: Task0101,
    val task0102: ApiTask,
    val task0103: DataTask,
    val task0104: Task0104,
    val task0105: DataTask,
    val task0201: DataTask,
    val task0203: DataTask,
    val task0204: DataTask,
    val task0205: Task0205,
    val task0301: DataTask,
    val task0302: Task0302,
    val task0303: ApiTask,
    val task0304: Task0304,
    val task0402: DataTask,
    val task0403: Task0403,
    val task0405: Task0405,
)

data class DataTask(
    val dataUrl: URL,
)

data class ApiTask(
    val apiUrl: URL,
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

data class Task0104(
    val fileBaseUrl: URL,
    val answerUrl: URL,
)

data class Task0205(
    val dataUrl: URL,
    val articleUrl: URL,
)

data class Task0302(
    val dataUrl: URL,
    val dataPassword: String,
)

data class Task0304(
    val dataUrl: URL,
    val apiUrl: URL,
)

data class Task0403(
    val baseUrl: URL,
    val questionsUrl: URL,
)

data class Task0405(
    val dataUrl: URL,
    val questionsUrl: URL,
)
