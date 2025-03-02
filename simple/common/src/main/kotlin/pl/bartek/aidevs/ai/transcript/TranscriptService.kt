package pl.bartek.aidevs.ai.transcript

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import pl.bartek.aidevs.ai.OllamaManager
import pl.bartek.aidevs.config.AiDevsProperties
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

@Service
class TranscriptService(
    private val aiDevsProperties: AiDevsProperties,
    private val ollamaManager: OllamaManager?,
) {
    @PostConstruct
    fun init() {
        Files.createDirectories(aiDevsProperties.tmpDir)
    }

    // TODO [bartek1470] second option -> OpenAiAudioTranscriptionModel - via OpenAI API
    // TODO [bartek1470] third option -> https://github.com/ggerganov/whisper.cpp

    fun transcribe(transcriptionRequest: TranscriptionRequest): String {
        log.info { "Transcribing $transcriptionRequest" }
        return invokeLocalWhisper(transcriptionRequest)
    }

    private fun invokeLocalWhisper(transcriptionRequest: TranscriptionRequest): String {
        if (aiDevsProperties.ollama.unloadModelsBeforeLocalWhisper) {
            ollamaManager?.unloadModels()
        }

        val resultPath = aiDevsProperties.tmpDir.resolve("${transcriptionRequest.path.nameWithoutExtension}.txt")
        val exitCode =
            try {
                val whisperExecutablePath = aiDevsProperties.pythonPackagesPath.resolve("whisper").toString()
                val process =
                    ProcessBuilder(
                        whisperExecutablePath,
                        "--task",
                        "transcribe",
                        "--model",
                        transcriptionRequest.model.modelName,
                        "--language",
                        transcriptionRequest.language.code,
                        "--output_format",
                        "txt",
                        "--output_dir",
                        resultPath.parent.toAbsolutePath().toString(),
                        transcriptionRequest.path.absolutePathString(),
                    ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()

                process.waitFor()
            } catch (e: Exception) {
                log.error(e) { "Failed to execute whisper command for $transcriptionRequest" }
                throw e
            }

        if (exitCode != 0) {
            val message = "Error processing $transcriptionRequest with exit code $exitCode"
            log.error { message }
            throw IllegalStateException(message)
        }

        log.debug { "Successfully processed $transcriptionRequest" }
        val resultContent = Files.readString(resultPath)
        Files.delete(resultPath)
        return resultContent
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
