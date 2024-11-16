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
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
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
        val recordingsPath = fetchInputData()
        val recordings: List<String> =
            Files
                .list(recordingsPath)
                .filter { it.extension == "m4a" }
                .map { it.absolutePathString() }
                .toList()
        transcribe(recordingsPath, *recordings.toTypedArray())
    }

    private fun transcribe(
        outputPath: Path,
        vararg files: String,
    ) {
        val filenames = Files.list(outputPath).map { "${it.nameWithoutExtension}.txt" }.collect(Collectors.toSet())
        if (files.none { !filenames.contains(it) }) {
            log.info { "Transcriptions already exist" }
            return
        }

        try {
            log.info { "Processing $files" }
            val process =
                ProcessBuilder(
                    "whisper",
                    "--task",
                    "transcribe",
                    "--model",
                    "medium",
                    "--language",
                    "Polish",
                    "--output_format",
                    "txt",
                    "--output_dir",
                    outputPath.toAbsolutePath().toString(),
                    *files,
                ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                log.debug { "Successfully processed $files" }
            } else {
                log.error { "Error processing $files with exit code $exitCode" }
            }
        } catch (e: Exception) {
            log.error { "Failed to execute whisper command for $files: ${e.message}" }
            throw e
        }
    }

    private fun fetchInputData(): Path {
        val uriComponents =
            UriComponentsBuilder
                .fromHttpUrl(dataUrl)
                .build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)
        if (Files.exists(extractedZipPath)) {
            log.info { "Input data already exists: ${extractedZipPath.toAbsolutePath()}. Skipping" }
            return extractedZipPath
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
        return extractedZipPath
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
