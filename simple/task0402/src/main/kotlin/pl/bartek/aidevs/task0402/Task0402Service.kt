package pl.bartek.aidevs.task0402

import com.fasterxml.jackson.databind.ObjectMapper
import org.jline.terminal.Terminal
import org.springframework.ai.autoconfigure.openai.OpenAiConnectionProperties
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage
import org.springframework.context.annotation.Profile
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.openai.api.model.CreateFineTuningJobRequest
import pl.bartek.aidevs.ai.openai.api.model.CreateFineTuningJobResponse
import pl.bartek.aidevs.ai.openai.api.model.FileResponse
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.config.Profile.OPENAI
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.println
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readLines

private const val SYSTEM_MESSAGE_CONTENT = "Is this sample correct? Answer true or false"

@Profile(OPENAI)
@Service
class Task0402Service(
    private val aiDevsProperties: AiDevsProperties,
    private val task0402Config: Task0402Config,
    openAiConnectionProperties: OpenAiConnectionProperties,
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0402.cacheFolderName()).absolute()

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
        val dataPath =
            fetchData(
                task0402Config.dataUrl.toString(),
                restClient,
                cacheDir,
            )
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
        val jsonFileResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fileResponse)
        terminal.println("Upload file result: ${fileResult.statusCode}\n$jsonFileResponse")
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
        val createFineTuningJobJsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(createFineTuningJobResponse)
        terminal.println("Start of fine-tuning job: ${createFineTuningJobResult.statusCode}\n$createFineTuningJobJsonResponse")
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
                        SystemMessage(SYSTEM_MESSAGE_CONTENT),
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
                        SystemMessage(SYSTEM_MESSAGE_CONTENT),
                        UserMessage(it),
                        AssistantMessage("false"),
                    )
                }

        val fineTuningData: List<String> =
            (correctData + incorrectData)
                .map { messagesOfSample ->
                    messagesOfSample.map {
                        ChatCompletionMessage(
                            it.text,
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
        val dataPath =
            fetchData(
                task0402Config.dataUrl.toString(),
                restClient,
                cacheDir,
            )
        val samplesToVerify =
            dataPath
                .resolve("verify.txt")
                .readLines()
                .map { line -> line.split("=") }
                .associateBy({ it[0] }, { it[1] })

        val results =
            samplesToVerify
                .mapValues { entry ->
                    chatService.sendToChat(
                        messages =
                            listOf(
                                SystemMessage(SYSTEM_MESSAGE_CONTENT),
                                UserMessage(entry.value),
                            ),
                        chatOptions =
                            ChatOptions
                                .builder()
                                .model(modelName)
                                .temperature(0.0)
                                .build(),
                        streaming = false,
                        cachePath = cacheDir.resolve("sample-${entry.key}.txt"),
                    )
                }.mapValues {
                    it.value.toBoolean()
                }

        terminal.println("Results:\n$results")
        val correctResults =
            results
                .filterValues { it }
                .toList()
                .map { it.first }
        val answer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.RESEARCH, correctResults))
        terminal.println(answer)
    }
}
