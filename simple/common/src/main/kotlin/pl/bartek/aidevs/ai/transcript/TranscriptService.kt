package pl.bartek.aidevs.ai.transcript

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import pl.bartek.aidevs.ai.OllamaManager
import pl.bartek.aidevs.course.TaskId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists

private const val AUDIO_TRANSCRIPTION_SUB_DIRECTORY = "audio-transcript"

@Service
class TranscriptService(
    @Value("\${aidevs.cache-dir}") private val cacheDir: Path,
    @Value("\${aidevs.ollama.unload-before-whisper:false}") private val unloadOllamaBeforeWhisper: Boolean,
    @Value("\${python.packages.path}") private val pythonPackagesPath: Path,
    private val ollamaManager: OllamaManager,
) {
    // TODO [bartek1470] second option -> OpenAiAudioTranscriptionModel - via OpenAI API
    // TODO [bartek1470] third option -> https://github.com/ggerganov/whisper.cpp

    fun transcribe(
        file: FileToTranscribe,
        taskId: TaskId,
    ): Recording {
        val audioFile = cacheDir.resolve(taskId.cacheFolderName()).resolve(file.filename)
        val audioFileTranscription =
            audioFile.parent.resolve(AUDIO_TRANSCRIPTION_SUB_DIRECTORY).resolve("${audioFile.nameWithoutExtension}.txt")
        Files.createDirectories(audioFileTranscription.parent)

        if (audioFileTranscription.notExists()) {
            log.debug { "Transcribing ${file.audio.uri} to $audioFileTranscription" }
            if (audioFile.notExists()) {
                log.debug { "File $audioFile does not exist. Transferring it from ${file.audio.uri}" }
                Files.write(audioFile, file.audio.contentAsByteArray)
            }

            invokeLocalWhisper(file.copy(audio = FileSystemResource(audioFile)), audioFileTranscription.parent)
        } else {
            log.debug { "File $audioFileTranscription exists. Using cache" }
        }

        return Recording(file.audio, audioFile, audioFileTranscription)
    }

    private fun invokeLocalWhisper(
        file: FileToTranscribe,
        outputDirectory: Path,
    ) {
        if (unloadOllamaBeforeWhisper) {
            ollamaManager.unloadModels()
        }

        try {
            val language = file.language ?: WhisperLanguage.ENGLISH
            val whisperExecutablePath = pythonPackagesPath.resolve("whisper").toString()
            val process =
                ProcessBuilder(
                    whisperExecutablePath,
                    "--task",
                    "transcribe",
                    "--model",
                    file.model?.modelName
                        ?: if (language == WhisperLanguage.ENGLISH) {
                            WhisperModel.MEDIUM_EN.modelName
                        } else {
                            WhisperModel.MEDIUM.modelName
                        },
                    "--language",
                    language.code,
                    "--output_format",
                    "txt",
                    "--output_dir",
                    outputDirectory.toAbsolutePath().toString(),
                    file.audio.file.absolutePath,
                ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                log.debug { "Successfully processed $file" }
            } else {
                log.error { "Error processing $file with exit code $exitCode" }
                throw IllegalStateException("Error processing $file with exit code $exitCode")
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to execute whisper command for $file" }
            throw IllegalStateException("Failed to transcribe: $file", e)
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
