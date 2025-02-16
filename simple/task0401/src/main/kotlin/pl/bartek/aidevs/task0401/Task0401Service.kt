package pl.bartek.aidevs.task0401

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.Media
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder
import org.springframework.ai.model.function.FunctionCallingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.boot.ansi.AnsiColor.BRIGHT_YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswerResponse
import pl.bartek.aidevs.course.api.AiDevsAuthenticatedAnswer
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.extractXmlRoot
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import java.nio.file.Files
import kotlin.io.path.absolute
import kotlin.io.path.nameWithoutExtension

@Service
class Task0401Service(
    private val aiDevsProperties: AiDevsProperties,
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
    restClient: RestClient,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0401.cacheFolderName()).absolute()

    private val restClient = restClient.mutate().baseUrl(aiDevsProperties.reportUrl.toString()).build()

    private val xmlMapper =
        XmlMapper
            .builder()
            .defaultUseWrapper(false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build()
            .registerKotlinModule()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val history = mutableListOf<String>()

        val maxImageOperations = 30
        var currentImageOperations = 0
        val maxDescribeImageOperations = 30
        var currentDescribeImageOperations = 0
        terminal.println("AI response:".ansiFormattedAi())

        val availableRepeatableOperations =
            ImageOperation.entries.filter { it != ImageOperation.START }.joinToString(", ")
        val response =
            chatService.sendToChat(
                messages =
                    listOf(
                        UserMessage(
                            """
                            Find image filename where Barbara Zawadzka could appear.
                            1. First step is to use tool `imageOperation` with operation `START` and no filename.
                            2. Use `describeImage` tool to get an image description.
                            3. Use `imageOperation` to fix images and get a new versions of them.
                            4. If a tool tells there's a problem with a new version of an image then try again using the same image but with a different operation.
                            5. Fix all images until it's possible to determine what is in the image.
                            6. Next think what images contains a woman. Those are resulting images.
                            
                            Return the image or images in XML structure, e.g. <result><image>IMG_123.PNG</image><image>IMG1.PNG</image></result>
                            """.trimIndent(),
                        ),
                    ),
                functions =
                    listOf(
                        DefaultFunctionCallbackBuilder()
                            .function(
                                "imageOperation",
                                fun(request: ImageOperationRequest): String {
                                    val operation =
                                        if (request.operation == ImageOperation.START) {
                                            ImageOperation.START.name
                                        } else {
                                            "${request.operation} ${request.filename}"
                                        }
                                    if (currentImageOperations >= maxImageOperations) {
                                        history.add("ERROR -> $operation".ansiFormattedError())
                                        val errorMessage = "ERROR: Exceeded maximum operations! Tool cannot be invoked anymore."
                                        log.error { errorMessage }
                                        return errorMessage
                                    }
                                    if (request.operation == ImageOperation.START &&
                                        history.firstOrNull {
                                            it.startsWith(
                                                ImageOperation.START.name,
                                            )
                                        } != null
                                    ) {
                                        val errorMessage = "ERROR: START cannot be used again!"
                                        log.error { errorMessage }
                                        return errorMessage
                                    }
                                    history.add(operation)

                                    currentImageOperations++
                                    val response =
                                        restClient
                                            .post()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(
                                                objectMapper.writeValueAsString(
                                                    AiDevsAuthenticatedAnswer(
                                                        Task.PHOTOS.taskName,
                                                        operation,
                                                        aiDevsProperties.apiKey,
                                                    ),
                                                ),
                                            ).retrieve()
                                            .body(AiDevsAnswerResponse::class.java)
                                            ?: throw IllegalStateException("Cannot get response")
                                    val messagePrefix = if (response.isError()) "ERROR" else "MESSAGE"
                                    return "$messagePrefix: ${response.message}"
                                },
                            ).description(
                                "Executes an operation and returns information about next images if there are next images. The information might not include URLs to images but an instruction how to get the URLs.",
                            ).inputType(ImageOperationRequest::class.java)
                            .build(),
                        DefaultFunctionCallbackBuilder()
                            .function(
                                "describeImage",
                                fun(request: DescribeImageRequest): String {
                                    if (currentDescribeImageOperations >= maxDescribeImageOperations) {
                                        history.add("ERROR -> ${request.url}".ansiFormattedError())
                                        val errorMessage = "ERROR: Exceeded maximum operations! Tool cannot be invoked anymore."
                                        log.error { errorMessage }
                                        return errorMessage
                                    }
                                    history.add(request.url)
                                    try {
                                        val imageUrl = ImageUrl.parse(request.url)
                                        val imagePath = imageUrl.ensurePersisted(cacheDir)
                                        currentDescribeImageOperations++
                                        terminal.print("AI description of ${imageUrl.filename}: ".ansiFormattedAi())
                                        val response =
                                            chatService.sendToChat(
                                                messages =
                                                    listOf(
                                                        UserMessage(
                                                            """
                                                            Describe what is in the image. If needed suggest to apply one of the operations to fix an image: $availableRepeatableOperations
                                                            """.trimIndent(),
                                                            Media(
                                                                MediaType.IMAGE_PNG,
                                                                FileSystemResource(imagePath),
                                                            ),
                                                        ),
                                                    ),
                                                chatOptions =
                                                    FunctionCallingOptions
                                                        .builder()
                                                        .model(OpenAiApi.ChatModel.GPT_4_O.value)
                                                        .temperature(1.0)
                                                        .build(),
                                                // has to be non-reactive because it's in a tool invocation and having
                                                // it reactive causes blocking the whole program, since the first
                                                // chatService invocation is already reactive
                                                streaming = false,
                                                cachePath = imagePath.parent.resolve("${imagePath.nameWithoutExtension}.txt"),
                                            ) {
                                                terminal.print(it)
                                            }
                                        terminal.println()
                                        return response
                                    } catch (ex: IllegalArgumentException) {
                                        val message = "Cannot parse url"
                                        log.error(ex) { message }
                                        return ex.message ?: "ERROR: $message"
                                    }
                                },
                            ).description("Describes what is on an image specified by the filename")
                            .inputType(DescribeImageRequest::class.java)
                            .build(),
                    ),
                chatOptions =
                    FunctionCallingOptions
                        .builder()
                        .temperature(0.7)
                        .model(OpenAiApi.ChatModel.GPT_4_O.value)
                        .build(),
                cachePath = cacheDir.resolve("images-to-process.txt"),
            ) {
                terminal.print(it)
            }
        terminal.println()
        terminal.println("Summary:".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
        terminal.println(history.joinToString("\n"))

        val images =
            xmlMapper
                .readValue<List<String>>(response.extractXmlRoot()!!)
                .map { it.trim() }
                .map { cacheDir.resolve(it) }
        val barbaraDescription =
            chatService.sendToChat(
                messages =
                    listOf(
                        UserMessage(
                            """
                            Find a woman that appears the most often at provided images - she is Barbara Zawadzka.
                            Next create a description of that women.
                            The description should be like you would write a description of a person for a police officer.
                            Keep an eye on special features of this person.
                            Translate the description to Polish language.
                            Include only the translation in the response.
                            Do not include any comments.
                            """.trimIndent(),
                            images.map { Media(MediaType.IMAGE_PNG, FileSystemResource(it)) },
                        ),
                    ),
                cachePath = cacheDir.resolve("result.txt"),
            )

        val answerObj =
            AiDevsAuthenticatedAnswer(
                Task.PHOTOS.taskName,
                barbaraDescription,
                aiDevsProperties.apiKey,
            )
        val body = objectMapper.writeValueAsString(answerObj)
        val answer =
            restClient
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<AiDevsAnswerResponse>()
                ?: throw IllegalStateException("Cannot get response")

        terminal.println(answer)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
