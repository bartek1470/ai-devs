package pl.bartek.aidevs.task0402

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.autoconfigure.openai.OpenAiConnectionProperties
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.openai.api.model.CreateFineTuningJobRequest
import pl.bartek.aidevs.ai.openai.api.model.CreateFineTuningJobResponse
import pl.bartek.aidevs.ai.openai.api.model.FileResponse
import pl.bartek.aidevs.config.Profile.OPENAI
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.println
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readLines

@Profile(OPENAI)
@Service
class Task0402Service(
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.task.0402.data-url}") private val dataUrl: String,
    openAiConnectionProperties: OpenAiConnectionProperties,

    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0402.cacheFolderName()).absolute()

    private val openAiRestClient =
        restClient
            .mutate()
            .baseUrl(openAiConnectionProperties.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${openAiConnectionProperties.apiKey}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun startFineTuning(terminal: Terminal) {
        val dataPath = fetchData(dataUrl, restClient, cacheDir)
        val fineTuningPath = createFineTuningFile(dataPath)

        val fileResult =
            openAiRestClient
                .post()
                .uri("/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(
                    LinkedMultiValueMap<String, Any>().apply {
                        add("purpose", "fine-tune")
                        add("file", FileSystemResource(fineTuningPath))
                    },
                ).retrieve()
                .toEntity(FileResponse::class.java)

        val fileResponse = fileResult.body ?: throw IllegalStateException("Missing response body")
        terminal.println("Upload file result: ${fileResult.statusCode}\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fileResponse)}")
        if (fileResult.statusCode != HttpStatus.OK) {
            terminal.println("Failed to upload file".ansiFormattedError())
            return
        }

        val createFineTuningJobResult =
            openAiRestClient
                .post()
                .uri("/v1/fine_tuning/jobs")
                .body(
                    CreateFineTuningJobRequest(
                        fileResponse.id,
                        "gpt-4o-mini-2024-07-18", // cheapest
                    ),
                ).retrieve()
                .toEntity(CreateFineTuningJobResponse::class.java)

        val createFineTuningJobResponse = createFineTuningJobResult.body ?: throw IllegalStateException("Missing response body")
        terminal.println("Start of fine-tuning job: ${createFineTuningJobResult.statusCode}\n${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(createFineTuningJobResponse)}")
        if (createFineTuningJobResult.statusCode != HttpStatus.OK) {
            terminal.println("Failed to start fine-tuning job".ansiFormattedError())
            return
        }

        terminal.println("Check progress of ${createFineTuningJobResponse.id} at https://platform.openai.com/finetune")
    }

    private fun createFineTuningFile(dataPath: Path): Path {
        val fineTuningPath = cacheDir.resolve("ai-devs-04-02-fine-tuning.jsonl")
        if (fineTuningPath.exists()) {
            return fineTuningPath
        }

        val correctData =
            dataPath
                .resolve("correct.txt")
                .readLines()
                .map {
                    listOf(
                        SystemMessage("Is this sample correct? Answer true or false"),
                        UserMessage(it),
                        AssistantMessage("true"),
                    )
                }

        val incorrectData =
            dataPath
                .resolve("incorrect.txt")
                .readLines()
                .map {
                    listOf(
                        SystemMessage("Is this sample correct? Answer true or false"),
                        UserMessage(it),
                        AssistantMessage("false"),
                    )
                }

        val fineTuningData: List<String> =
            (correctData + incorrectData)
                .map { messagesOfSample ->
                    messagesOfSample.map {
                        ChatCompletionMessage(
                            it.content,
                            ChatCompletionMessage.Role.valueOf(it.messageType.name),
                        )
                    }
                }.map { messages -> OpenAiApi.ChatCompletionRequest(messages, null) }
                .map { request -> objectMapper.writeValueAsString(request) }

        Files.write(fineTuningPath, fineTuningData)
        return fineTuningPath
    }

    fun verifySamples(
        modelName: String,
        terminal: Terminal,
    ) {
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
