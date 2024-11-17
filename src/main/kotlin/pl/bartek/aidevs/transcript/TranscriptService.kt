package pl.bartek.aidevs.transcript

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import pl.bartek.aidevs.task0201.Recording
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@Service
class TranscriptService {
    // TODO [bartek1470] second option -> OpenAiAudioTranscriptionModel - via OpenAI API
    // TODO [bartek1470] third option -> https://github.com/ggerganov/whisper.cpp

    fun transcribe(vararg files: FileToTranscribe): List<Recording> =
        files.map { fileToTranscribe ->
            val transcriptParent = fileToTranscribe.path.parent.resolve("transcript")
            Files.createDirectories(transcriptParent)

            val transcriptPath = transcriptParent.resolve("${fileToTranscribe.path.nameWithoutExtension}.txt")

            if (Files.notExists(transcriptPath)) {
                log.debug { "Transcribing $fileToTranscribe" }
                invokeWhisper(fileToTranscribe, transcriptParent)
            } else {
                log.debug { "File $transcriptPath exists" }
            }
            Recording(fileToTranscribe.path, transcriptPath)
        }

    private fun invokeWhisper(
        file: FileToTranscribe,
        outputDirectory: Path,
    ) {
        try {
            val language = file.language ?: WhisperLanguage.ENGLISH
            val process =
                ProcessBuilder(
                    "whisper",
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
                    file.path.toAbsolutePath().toString(),
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
