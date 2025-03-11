package pl.bartek.aidevs.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URL
import java.nio.file.Path

@ConfigurationProperties(prefix = "aidevs")
data class AiDevsProperties(
    val model: ModelConfig,
    val cacheDir: Path,
    val downloadsDir: Path,
    val tmpDir: Path,
    val pythonPackagesPath: Path,
    val ollama: AiDevsOllamaProperties,
    val apiKey: String,
    val submitFlagUrl: URL,
    val reportUrl: URL,
)

data class ModelConfig(
    val image: String,
)

data class AiDevsOllamaProperties(
    val unloadModelsBeforeLocalWhisper: Boolean,
)
