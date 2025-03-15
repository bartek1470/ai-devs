package pl.bartek.aidevs.ai.transcript

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import pl.bartek.aidevs.ai.OllamaManager
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.util.logCommandInfo
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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
                logCommandInfo("python", arrayOf("--version"))
                logCommandInfo("whisper")

                val process =
                    ProcessBuilder(
                        "whisper",
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
                    ).start()

                val logOutputJob = useStreamUntilProcessAlive(process.inputStream, process) { whisperProcessLog.info { it } }
                val logErrorJob = useStreamUntilProcessAlive(process.errorStream, process) { whisperProcessLog.error { it } }

                runBlocking {
                    joinAll(logOutputJob, logErrorJob)
                    process.waitFor()
                }
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

    private fun useStreamUntilProcessAlive(
        inputStream: InputStream,
        process: Process,
        lineConsumer: (String) -> Unit,
    ) = CoroutineScope(Dispatchers.IO).launch {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            while (process.isAlive) {
                val line = reader.readLine() ?: break
                if (line.isNotEmpty()) {
                    lineConsumer.invoke(line)
                }
            }
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
        private val whisperProcessLog = KotlinLogging.logger("whisper")
    }
}
