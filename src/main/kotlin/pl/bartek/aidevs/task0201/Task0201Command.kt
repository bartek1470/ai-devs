package pl.bartek.aidevs.task5

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.autoconfigure.ollama.OllamaChatProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.task0201.Recording
import pl.bartek.aidevs.unzip
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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

    private val chatModel =
        OllamaChatModel(
            ollamaApi,
            OllamaOptions
                .fromOptions(ollamaChatProperties.options)
                .withTemperature(.0),
        )

    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    @Command(command = ["task0201"])
    fun run(ctx: CommandContext) {
        val recordingsPath = fetchInputData()
        val recordingPaths: List<Path> =
            Files
                .list(recordingsPath)
                .filter { it.extension == "m4a" }
                .toList()
        val recordings = transcribe(recordingsPath, *recordingPaths.toTypedArray())
    }

    private fun transcribe(
        outputPath: Path,
        vararg files: Path,
    ): List<Recording> =
        files.map { recordingPath ->
            val transcriptPath = outputPath.resolve("${recordingPath.nameWithoutExtension}.txt")

            if (Files.notExists(transcriptPath)) {
                createTranscript(transcriptPath, outputPath)
            }
            val transcript = Files.readString(transcriptPath)
            Recording(recordingPath, transcriptPath, transcript)
        }

    private fun createTranscript(
        file: Path,
        outputPath: Path,
    ) {
        try {
            log.info { "Transcribing $file" }
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
                    file.toAbsolutePath().toString(),
                ).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                log.debug { "Successfully processed $file" }
            } else {
                log.error { "Error processing $file with exit code $exitCode" }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to execute whisper command for $file" }
            throw IllegalStateException("Failed to transcribe: $file", e)
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
