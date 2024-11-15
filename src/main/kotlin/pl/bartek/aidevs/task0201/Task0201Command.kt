package pl.bartek.aidevs.task5

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.autoconfigure.ollama.OllamaChatProperties
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.unzip
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

@Command(group = "task")
class Task0201Command(
    private val apiClient: AiDevsApiClient,
    private val restClient: RestClient,
    ollamaApi: OllamaApi,
    ollamaChatProperties: OllamaChatProperties,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.task.0201.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0201.answer-url}") private val answerUrl: String,
) {
    private val cacheDir = Paths.get(cacheDir, "02_01")

    init {
        Files.createDirectories(this.cacheDir)
    }

//    private val chatModel =
//        OllamaChatModel(
//            ollamaApi,
//            OllamaOptions
//                .fromOptions(ollamaChatProperties.options)
//                .withTemperature(.0),
//        )
//
//    private val chatClient =
//        ChatClient
//            .builder(chatModel)
//            .defaultAdvisors(SimpleLoggerAdvisor())
//            .build()

    @Command(command = ["task0201"])
    fun run(ctx: CommandContext) {
        fetchInputData()
    }

    private fun fetchInputData() {
        val uriComponents =
            UriComponentsBuilder
                .fromHttpUrl(dataUrl)
                .build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)
        if (Files.exists(extractedZipPath)) {
            log.debug { "Input data already exists: ${extractedZipPath.toAbsolutePath()}" }
            return
        }

        val body =
            restClient
                .get()
                .uri(uriComponents.toUriString())
                .headers { it.contentType = MediaType.APPLICATION_OCTET_STREAM }
                .retrieve()
                .body(ByteArray::class.java)!!
        Files.newOutputStream(zipFilePath).use {
            it.write(body)
        }
        zipFilePath.unzip(extractedZipPath)
        Files.delete(zipFilePath)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
